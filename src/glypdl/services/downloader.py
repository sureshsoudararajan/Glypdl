"""Download manager service for running yt-dlp downloads."""

import os
import shutil
import subprocess
import threading
import time
import re
from pathlib import Path
from gi.repository import GLib

from glypdl.models.download import DownloadItem, DownloadState, DownloadMode
from glypdl.utils.formatting import parse_progress_line, format_size, format_speed
from glypdl.utils.paths import get_temp_dir


class DownloadManager:
    """Manages download queue and runs yt-dlp subprocess downloads."""

    def __init__(self, ytdlp_service, settings):
        self.ytdlp_service = ytdlp_service
        self.settings = settings
        self.active_downloads = {}  # dict[str, subprocess.Popen]
        self.download_queue = []
        self.max_concurrent = self.settings.get('max_concurrent', 2)
        self._queue_lock = threading.Lock()

    def start_download(self, download_item):
        """Start or queue a download."""
        with self._queue_lock:
            if len(self.active_downloads) < self.max_concurrent:
                self._start_download_thread(download_item)
            else:
                download_item.state = DownloadState.QUEUED
                self.download_queue.append(download_item)

    def cancel_download(self, download_id):
        """Cancel an active or queued download."""
        with self._queue_lock:
            if download_id in self.active_downloads:
                proc = self.active_downloads[download_id]
                try:
                    proc.terminate()
                    proc.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    proc.kill()
                except Exception:
                    pass
            else:
                for item in self.download_queue:
                    if item.id == download_id and getattr(item, 'is_temp_cookie', False) and item.cookie_file:
                        try:
                            if os.path.isfile(item.cookie_file):
                                os.unlink(item.cookie_file)
                        except Exception:
                            pass
                        item.cookie_file = ''
                self.download_queue = [
                    item for item in self.download_queue
                    if item.id != download_id
                ]

    def retry_download(self, download_item):
        """Reset a failed download and re-queue it."""
        download_item.state = DownloadState.QUEUED
        download_item.progress = 0.0
        download_item.downloaded_bytes = 0
        download_item.error_message = ''
        self.start_download(download_item)

    def _process_queue(self):
        """Start next queued downloads if slots are available."""
        with self._queue_lock:
            while (len(self.active_downloads) < self.max_concurrent
                   and self.download_queue):
                next_item = self.download_queue.pop(0)
                self._start_download_thread(next_item)

    def _start_download_thread(self, download_item):
        """Launch a download in a background thread."""
        thread = threading.Thread(
            target=self._run_download,
            args=(download_item,),
            daemon=True
        )
        thread.start()

    def _run_download(self, download_item):
        """Run the yt-dlp subprocess and track progress. Runs in a thread."""
        download_id = download_item.id
        url = download_item.url
        start_time = time.monotonic()
        last_ui_update_time = 0.0  # For throttling progress updates

        # Build format spec and audio settings
        format_spec = self._build_format_spec(download_item)
        is_audio = download_item.mode == DownloadMode.AUDIO
        audio_fmt = download_item.audio_format or 'mp3'

        output_template = download_item.filename_template or self.settings.get(
            'filename_template', '%(title)s.%(ext)s'
        )
        final_download_dir = download_item.download_dir or self.settings.get(
            'download_dir', ''
        )
        cookie_file = download_item.cookie_file or ''
        cookies_from_browser = download_item.cookies_from_browser or ''

        # For audio downloads, isolate the entire download + conversion in a dedicated subfolder
        # so yt-dlp NEVER inspects, reuses, overwrites or deletes existing video files in download_dir
        isolated_work_dir = None
        if is_audio:
            isolated_work_dir = os.path.join(str(get_temp_dir()), f"audio_{download_id}")
            os.makedirs(isolated_work_dir, exist_ok=True)
            active_download_dir = isolated_work_dir
        else:
            active_download_dir = final_download_dir

        extra_args = list(download_item.extra_args or [])
        extra_settings_args = self.settings.get('extra_args', '')
        if extra_settings_args:
            extra_args.extend(extra_settings_args.split())

        args = self.ytdlp_service.build_download_args(
            url=url,
            format_spec=format_spec,
            output_template=output_template,
            download_dir=active_download_dir,
            cookie_file=cookie_file if cookie_file else None,
            cookies_from_browser=cookies_from_browser if cookies_from_browser else None,
            extract_audio=is_audio,
            audio_format=audio_fmt if is_audio else None,
            extra_args=extra_args if extra_args else None
        )

        GLib.idle_add(self._set_state, download_item, DownloadState.DOWNLOADING)

        try:
            proc = subprocess.Popen(
                args,
                stdout=subprocess.PIPE,
                stderr=subprocess.STDOUT,
                text=True,
                bufsize=1
            )

            with self._queue_lock:
                self.active_downloads[download_id] = proc
            download_item.process = proc

            # Speed smoothing buffer
            speed_samples = []
            output_lines = []

            for line in proc.stdout:
                line = line.strip()
                if not line:
                    continue
                output_lines.append(line)

                # Detect post-processing states
                new_state = self._detect_state_from_line(line)
                if new_state:
                    GLib.idle_add(self._set_state, download_item, new_state)

                # Parse progress
                progress = parse_progress_line(line)
                if progress.get('status') == 'downloading':
                    now = time.monotonic()
                    elapsed = now - start_time

                    # Parse speed from yt-dlp output
                    speed_val = self._parse_speed_value(progress.get('speed', ''))
                    if speed_val > 0:
                        speed_samples.append(speed_val)
                        if len(speed_samples) > 6:
                            speed_samples.pop(0)
                        smoothed_speed = sum(speed_samples) / len(speed_samples)
                    else:
                        smoothed_speed = 0.0

                    # Parse ETA
                    eta_val = self._parse_eta_value(progress.get('eta', ''))
                    pct = progress.get('percent', 0.0)
                    dl_bytes = progress.get('downloaded_bytes', 0)
                    tot_bytes = progress.get('total_bytes', 0)

                    # Throttle UI updates to ~8-10 FPS to prevent UI jitter/shaking
                    if (now - last_ui_update_time >= 0.12) or pct >= 100.0:
                        last_ui_update_time = now
                        GLib.idle_add(
                            self._update_progress,
                            download_item,
                            pct,
                            dl_bytes,
                            tot_bytes,
                            smoothed_speed,
                            eta_val,
                            elapsed,
                            progress.get('fragment_index', 0),
                            progress.get('fragment_count', 0)
                        )

                # Detect output path from various yt-dlp output messages (for non-audio)
                if not is_audio:
                    if '[MoveFiles] Moving file' in line and ' to ' in line:
                        dest = line.split(' to ', 1)[-1].strip().strip('"').strip("'")
                        GLib.idle_add(self._set_output_path, download_item, dest)
                    elif '[download] Destination:' in line:
                        dest = line.split('Destination:', 1)[-1].strip().strip('"').strip("'")
                        GLib.idle_add(self._set_output_path, download_item, dest)
                    elif '[Merger] Merging formats into' in line:
                        dest = line.split('into', 1)[-1].strip().strip('"').strip("'")
                        GLib.idle_add(self._set_output_path, download_item, dest)
                    elif 'has already been downloaded' in line and '[download]' in line:
                        dest = line.split('[download]', 1)[-1].replace('has already been downloaded', '').strip().strip('"').strip("'")
                        GLib.idle_add(self._set_output_path, download_item, dest)

            proc.wait()

            if proc.returncode == 0:
                final_file_path = download_item.output_path or ''

                # If audio was downloaded in isolated_work_dir, move the resulting audio file to final_download_dir
                if is_audio and isolated_work_dir and os.path.isdir(isolated_work_dir):
                    produced_files = os.listdir(isolated_work_dir)
                    for fname in produced_files:
                        src = os.path.join(isolated_work_dir, fname)
                        if os.path.isfile(src):
                            # Target destination path
                            dst = os.path.join(final_download_dir, fname)
                            
                            # If a file with the exact same name already exists in destination,
                            # ensure we don't overwrite it
                            if os.path.exists(dst):
                                base, ext = os.path.splitext(fname)
                                counter = 1
                                while os.path.exists(os.path.join(final_download_dir, f"{base} ({counter}){ext}")):
                                    counter += 1
                                dst = os.path.join(final_download_dir, f"{base} ({counter}){ext}")

                            shutil.move(src, dst)
                            final_file_path = dst

                    # Clean up temp folder
                    try:
                        shutil.rmtree(isolated_work_dir, ignore_errors=True)
                    except Exception:
                        pass

                GLib.idle_add(
                    self._mark_completed, download_item,
                    final_file_path
                )
            else:
                combined_err = " ".join(output_lines[-6:]).lower() if output_lines else ""
                if "database is locked" in combined_err or "sqlite3.operationalerror" in combined_err:
                    stderr_output = "Browser cookie database is locked. Try closing your browser completely and retry, or switch to cookies.txt."
                elif "could not find" in combined_err and "cookie" in combined_err:
                    stderr_output = "Browser profile not found. Verify your browser profile in Settings or switch to cookies.txt."
                elif "keyring" in combined_err or "decrypt" in combined_err or "secret" in combined_err:
                    stderr_output = "Cookie decryption failed. Check desktop keyring permissions or switch to cookies.txt."
                elif output_lines:
                    stderr_output = output_lines[-1]
                else:
                    stderr_output = f"Download failed with exit code {proc.returncode}"
                GLib.idle_add(self._mark_failed, download_item, stderr_output)

        except Exception as e:
            GLib.idle_add(self._mark_failed, download_item, str(e))

        finally:
            with self._queue_lock:
                self.active_downloads.pop(download_id, None)
            download_item.process = None

            # Securely discard temporary cookie file if used
            if getattr(download_item, 'is_temp_cookie', False) and download_item.cookie_file:
                try:
                    if os.path.isfile(download_item.cookie_file):
                        os.unlink(download_item.cookie_file)
                except Exception:
                    pass
                download_item.cookie_file = ''

            self._process_queue()

    def _build_format_spec(self, download_item):
        """Build yt-dlp format spec from download mode and quality."""
        mode = download_item.mode
        quality = download_item.quality or 'Best'

        if mode == DownloadMode.AUDIO:
            return "bestaudio/best"

        height_map = {
            '4320p': '4320', '2160p': '2160', '1440p': '1440',
            '1080p': '1080', '720p': '720', '480p': '480',
            '360p': '360', '240p': '240', '144p': '144'
        }
        height = height_map.get(quality, '')

        if mode == DownloadMode.VIDEO:
            if height:
                return f"bestvideo[height<={height}]/best"
            return "bestvideo/best"

        # VIDEO_AUDIO
        if height:
            return f"bestvideo[height<={height}]+bestaudio/best[height<={height}]"
        return "bestvideo+bestaudio/best"

    def _detect_state_from_line(self, line):
        """Detect download state transitions from yt-dlp output."""
        if '[Merger]' in line:
            return DownloadState.MERGING
        if '[ExtractAudio]' in line:
            return DownloadState.CONVERTING
        if '[ffmpeg]' in line and 'Converting' in line:
            return DownloadState.CONVERTING
        if '[download]' in line and 'Destination' in line:
            return DownloadState.DOWNLOADING
        return None

    def _parse_speed_value(self, speed_str):
        """Parse speed string like '10.8MiB/s' to bytes/sec."""
        if not speed_str:
            return 0.0
        match = re.match(r'([\d.]+)\s*([KMGT]?i?B)/s', speed_str, re.IGNORECASE)
        if not match:
            return 0.0
        value = float(match.group(1))
        unit = match.group(2).upper().replace('I', '')
        multipliers = {'B': 1, 'KB': 1024, 'MB': 1024**2, 'GB': 1024**3, 'TB': 1024**4}
        return value * multipliers.get(unit, 1)

    def _parse_eta_value(self, eta_str):
        """Parse ETA string like '00:41', '1:23:45', or '41s' to seconds."""
        if not eta_str or eta_str.lower() == 'unknown':
            return 0
        eta_str = eta_str.rstrip('sS')
        parts = eta_str.split(':')
        try:
            if len(parts) == 3:
                return int(parts[0]) * 3600 + int(parts[1]) * 60 + int(parts[2])
            elif len(parts) == 2:
                return int(parts[0]) * 60 + int(parts[1])
            elif len(parts) == 1:
                return int(parts[0])
        except ValueError:
            pass
        return 0

    # GLib.idle_add callback helpers (run on GTK main thread)

    def _set_state(self, item, state):
        item.state = state
        return False

    def _set_output_path(self, item, path):
        item.output_path = path
        return False

    def _update_progress(self, item, percent, dl_bytes, tot_bytes, speed, eta, elapsed, frag_idx, frag_count):
        item.progress = percent
        if dl_bytes > 0:
            item.downloaded_bytes = dl_bytes
        if tot_bytes > 0:
            item.total_bytes = tot_bytes
        item.speed = speed
        item.eta = eta
        item.elapsed = elapsed
        item.fragment_index = frag_idx
        item.fragment_count = frag_count
        return False

    def _mark_completed(self, item, output_path):
        item.mark_completed(output_path)
        return False

    def _mark_failed(self, item, error_message):
        item.mark_failed(error_message)
        return False
