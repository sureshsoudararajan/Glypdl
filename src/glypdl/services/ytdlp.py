"""Service for discovering and interfacing with system-installed yt-dlp and ffmpeg."""

import os
import shutil
import subprocess
from typing import Optional, List
from glypdl.utils.paths import get_temp_dir


class YtDlpService:
    """Interface to the system-installed yt-dlp command-line tool."""

    def __init__(self, settings=None):
        self.settings = settings

    def detect(self) -> Optional[str]:
        """Find the yt-dlp executable path. Prioritizes bundled app binary."""
        if self.settings:
            custom_path = self.settings.get_custom_ytdlp_path() if hasattr(self.settings, 'get_custom_ytdlp_path') else self.settings.get('ytdlp_path', '')
            if custom_path and shutil.which(custom_path):
                return custom_path
        
        # 1. First priority: Always check Glypdl's own bundled engines
        candidates = [
            "/app/bin/yt-dlp",
            "/usr/share/glypdl/bin/yt-dlp",
            "/usr/local/share/glypdl/bin/yt-dlp",
            os.path.expanduser("~/.local/share/glypdl/bin/yt-dlp"),
        ]
        for c in candidates:
            if os.path.isfile(c) and os.access(c, os.X_OK):
                return c

        # 2. System PATH
        found = shutil.which('yt-dlp')
        if found:
            return found

        return None

    def get_version(self, path: Optional[str] = None) -> Optional[str]:
        """Get the yt-dlp version string."""
        binary = path or self.detect()
        if not binary:
            return None
        try:
            result = subprocess.run(
                [binary, '--version'],
                capture_output=True,
                text=True,
                check=True,
                timeout=5
            )
            return result.stdout.strip()
        except (subprocess.CalledProcessError, FileNotFoundError, subprocess.TimeoutExpired):
            return None

    def is_available(self) -> bool:
        """Check if yt-dlp is installed and available."""
        return self.detect() is not None

    def get_path(self) -> str:
        """Get the path to yt-dlp or raise RuntimeError."""
        path = self.detect()
        if not path:
            raise RuntimeError("yt-dlp executable not found on system")
        return path

    def detect_ffmpeg(self) -> Optional[str]:
        """Find the ffmpeg executable path. Prioritizes bundled / local app binary."""
        if self.settings:
            custom_path = self.settings.get_custom_ffmpeg_path() if hasattr(self.settings, 'get_custom_ffmpeg_path') else self.settings.get('ffmpeg_path', '')
            if custom_path and shutil.which(custom_path):
                return custom_path

        # 1. First priority: Glypdl private data / bundled paths
        from glypdl.utils.paths import get_bin_dir
        candidates = [
            "/app/bin/ffmpeg",
            "/usr/share/glypdl/bin/ffmpeg",
            str(get_bin_dir() / "ffmpeg"),
            "/usr/local/share/glypdl/bin/ffmpeg",
        ]
        for c in candidates:
            if os.path.isfile(c) and os.access(c, os.X_OK):
                return c

        # 2. System PATH
        found = shutil.which('ffmpeg')
        if found:
            return found

        return None

    def ffmpeg_available(self) -> bool:
        """Check if ffmpeg is available."""
        return self.detect_ffmpeg() is not None

    def install_ffmpeg_async(self, on_progress=None, on_done=None, on_error=None):
        """Asynchronously download and extract standalone static ffmpeg into user data bin dir."""
        import threading
        import platform
        import urllib.request
        import tarfile
        import tempfile
        from gi.repository import GLib
        from glypdl.utils.paths import get_bin_dir

        def _worker():
            try:
                machine = platform.machine().lower()
                if "aarch64" in machine or "arm64" in machine:
                    url = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linuxarm64-gpl.tar.xz"
                else:
                    url = "https://github.com/yt-dlp/FFmpeg-Builds/releases/download/latest/ffmpeg-master-latest-linux64-gpl.tar.xz"

                bin_dir = get_bin_dir()
                bin_dir.mkdir(parents=True, exist_ok=True)
                target_ffmpeg = bin_dir / "ffmpeg"
                target_ffprobe = bin_dir / "ffprobe"

                if target_ffmpeg.exists() and os.access(target_ffmpeg, os.X_OK):
                    if on_done:
                        GLib.idle_add(on_done, str(target_ffmpeg))
                    return

                with tempfile.TemporaryDirectory() as tmpdir:
                    archive_path = os.path.join(tmpdir, "ffmpeg.tar.xz")
                    urllib.request.urlretrieve(url, archive_path)

                    with tarfile.open(archive_path, "r:xz") as tar:
                        for member in tar.getmembers():
                            basename = os.path.basename(member.name)
                            if basename in ("ffmpeg", "ffprobe") and member.isfile():
                                member_file = tar.extractfile(member)
                                if member_file:
                                    dest_path = bin_dir / basename
                                    with open(dest_path, "wb") as f:
                                        f.write(member_file.read())
                                    dest_path.chmod(0o755)

                if target_ffmpeg.exists():
                    if on_done:
                        GLib.idle_add(on_done, str(target_ffmpeg))
                else:
                    raise RuntimeError("FFmpeg extraction completed but binary was not found.")

            except Exception as exc:
                if on_error:
                    GLib.idle_add(on_error, str(exc))

        t = threading.Thread(target=_worker, daemon=True)
        t.start()

    def build_download_args(self, url: str, format_spec: Optional[str] = None, 
                            output_template: Optional[str] = None, 
                            download_dir: Optional[str] = None, 
                            temp_dir: Optional[str] = None,
                            cookie_file: Optional[str] = None, 
                            extract_audio: bool = False,
                            audio_format: Optional[str] = None,
                            extra_args: Optional[List[str]] = None) -> List[str]:
        """Build safe argument list for yt-dlp download execution."""
        path = self.get_path()
        args = [path, '--newline', '--progress', '--no-warnings']
        
        # If ffmpeg is available and custom path exists, specify it
        ffmpeg_bin = self.detect_ffmpeg()
        if ffmpeg_bin:
            ffmpeg_dir = os.path.dirname(ffmpeg_bin)
            args.extend(['--ffmpeg-location', ffmpeg_dir])

        if extract_audio:
            args.append('-x')
            if audio_format and audio_format.lower() not in ('best', ''):
                args.extend(['--audio-format', audio_format.lower()])
            args.extend(['--audio-quality', '0'])

        if format_spec:
            args.extend(['-f', format_spec])
        
        if output_template:
            args.extend(['-o', output_template])
        
        if download_dir:
            args.extend(['-P', download_dir])
            
        # Isolate temporary files, fragments, and intermediate conversions
        effective_temp = temp_dir or str(get_temp_dir())
        if effective_temp:
            os.makedirs(effective_temp, exist_ok=True)
            args.extend(['-P', f'temp:{effective_temp}'])
            
        if cookie_file and os.path.isfile(cookie_file):
            args.extend(['--cookies', cookie_file])
            
        if extra_args:
            args.extend(extra_args)
            
        args.append(url)
        return args

    def build_metadata_args(self, url: str, cookie_file: Optional[str] = None) -> List[str]:
        """Build argument list for extracting video metadata."""
        path = self.get_path()
        args = [path, '-J', '--no-warnings', '--no-playlist']
        if cookie_file and os.path.isfile(cookie_file):
            args.extend(['--cookies', cookie_file])
        args.append(url)
        return args

    def build_playlist_args(self, url: str, cookie_file: Optional[str] = None) -> List[str]:
        """Build argument list for extracting playlist metadata."""
        path = self.get_path()
        args = [path, '-J', '--flat-playlist', '--no-warnings']
        if cookie_file and os.path.isfile(cookie_file):
            args.extend(['--cookies', cookie_file])
        args.append(url)
        return args
