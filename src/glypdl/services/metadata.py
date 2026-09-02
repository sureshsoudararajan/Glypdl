"""Service for fetching and parsing video and playlist metadata using yt-dlp."""

import json
import subprocess
import threading
from typing import Optional, Dict, Any, List
from gi.repository import GLib

from glypdl.utils.thumbnails import load_thumbnail_async


class MetadataService:
    """Handles asynchronous retrieval and format analysis of media metadata."""

    def __init__(self, ytdlp_service):
        self.ytdlp_service = ytdlp_service

    def _normalize_entries(self, raw_entries: list, original_url: str = "", main_uploader: str = "", main_extractor: str = "") -> list:
        """Normalize raw yt-dlp playlist entries into consistent items with verified, non-empty URLs."""
        normalized = []
        for entry in raw_entries or []:
            if not entry:
                continue

            vid_id = str(entry.get('id') or '').strip()
            extractor = (entry.get('extractor') or main_extractor or '').lower()
            uploader = entry.get('uploader') or entry.get('channel') or main_uploader or ''

            # Robust URL extraction
            vid_url = entry.get('webpage_url') or entry.get('url') or entry.get('original_url') or ''

            if not vid_url:
                if 'instagram' in extractor or 'instagram.com' in original_url:
                    if uploader and vid_id:
                        vid_url = f"https://www.instagram.com/stories/{uploader}/{vid_id}/"
                    elif vid_id:
                        vid_url = f"https://www.instagram.com/p/{vid_id}/"
                    else:
                        vid_url = original_url
                elif 'tiktok' in extractor or 'tiktok.com' in original_url:
                    vid_url = f"https://www.tiktok.com/@{uploader}/video/{vid_id}" if uploader and vid_id else original_url
                elif 'youtube' in extractor or 'youtube.com' in original_url or 'youtu.be' in original_url:
                    vid_url = f"https://www.youtube.com/watch?v={vid_id}" if vid_id else original_url
                else:
                    vid_url = original_url

            # Extract thumbnail
            thumb_url = ""
            if entry.get('thumbnails'):
                thumb_url = entry['thumbnails'][-1].get('url', '')
            if not thumb_url:
                thumb_url = entry.get('thumbnail', '')

            title = entry.get('title') or entry.get('fulltitle') or (f"Media by {uploader}" if uploader else "Untitled Media")

            normalized.append({
                'id': vid_id,
                'url': vid_url,
                'title': title,
                'duration': entry.get('duration') or 0,
                'uploader': uploader,
                'thumbnail': thumb_url,
                'selected': True
            })
        return normalized

    def fetch_async(self, url: str, callback, error_callback=None, cookie_file: Optional[str] = None, cookies_from_browser: Optional[str] = None):
        """Fetch metadata asynchronously (single video or playlist) and invoke callback(metadata_dict) on GTK main thread."""
        def _fetch():
            try:
                is_likely_playlist = 'playlist' in url or 'list=' in url

                if is_likely_playlist:
                    playlist_args = self.ytdlp_service.build_playlist_args(url, cookie_file=cookie_file, cookies_from_browser=cookies_from_browser)
                    p_res = subprocess.run(
                        playlist_args,
                        capture_output=True,
                        text=True,
                        check=True,
                        timeout=40
                    )
                    data = json.loads(p_res.stdout)
                    if data.get('_type') == 'playlist' or 'entries' in data:
                        normalized_entries = self._normalize_entries(
                            data.get('entries'),
                            original_url=url,
                            main_uploader=data.get('uploader') or data.get('channel') or '',
                            main_extractor=data.get('extractor') or ''
                        )
                        if not normalized_entries:
                            err = "No active media or stories found at this URL. Stories may have expired (24-hour limit) or the account has not posted any active stories."
                            if error_callback:
                                GLib.idle_add(error_callback, err, url)
                            else:
                                GLib.idle_add(callback, None)
                            return

                        playlist_dict = {
                            '_type': 'playlist',
                            'original_url': url,
                            'title': data.get('title') or (f"Stories by {data.get('uploader')}" if 'instagram' in url else "Playlist"),
                            'uploader': data.get('uploader') or data.get('channel') or '',
                            'playlist_count': len(normalized_entries),
                            'entries': normalized_entries,
                            'used_cookie_file': cookie_file or '',
                            'used_cookies_from_browser': cookies_from_browser or ''
                        }
                        GLib.idle_add(callback, playlist_dict)
                        return

                # Fetch single video or multi-item metadata
                args = self.ytdlp_service.build_metadata_args(url, cookie_file=cookie_file, cookies_from_browser=cookies_from_browser)
                result = subprocess.run(
                    args,
                    capture_output=True,
                    text=True,
                    check=True,
                    timeout=30
                )
                metadata_dict = json.loads(result.stdout)

                # If yt-dlp resolved the URL into a playlist / story album
                if metadata_dict.get('_type') == 'playlist' or 'entries' in metadata_dict:
                    normalized_entries = self._normalize_entries(
                        metadata_dict.get('entries'),
                        original_url=url,
                        main_uploader=metadata_dict.get('uploader') or metadata_dict.get('channel') or '',
                        main_extractor=metadata_dict.get('extractor') or ''
                    )
                    if not normalized_entries:
                        err = "No active media or stories found at this URL. Stories may have expired (24-hour limit) or the account has not posted any active stories."
                        if error_callback:
                            GLib.idle_add(error_callback, err, url)
                        else:
                            GLib.idle_add(callback, None)
                        return

                    playlist_dict = {
                        '_type': 'playlist',
                        'original_url': url,
                        'title': metadata_dict.get('title') or (f"Stories by {metadata_dict.get('uploader')}" if 'instagram' in url else "Playlist"),
                        'uploader': metadata_dict.get('uploader') or metadata_dict.get('channel') or '',
                        'playlist_count': len(normalized_entries),
                        'entries': normalized_entries,
                        'used_cookie_file': cookie_file or '',
                        'used_cookies_from_browser': cookies_from_browser or ''
                    }
                    GLib.idle_add(callback, playlist_dict)
                    return

                # Single video / post metadata
                metadata_dict['original_url'] = url
                if not metadata_dict.get('webpage_url'):
                    metadata_dict['webpage_url'] = url
                metadata_dict['used_cookie_file'] = cookie_file or ''
                metadata_dict['used_cookies_from_browser'] = cookies_from_browser or ''

                # Fetch and cache thumbnail in background if available
                thumb_url = metadata_dict.get('thumbnail')
                if thumb_url:
                    load_thumbnail_async(thumb_url, lambda cached_path: metadata_dict.__setitem__('thumbnail_path', cached_path))
                elif self.ytdlp_service.ffmpeg_available() and (url.endswith('.m3u8') or '.m3u8' in url or url.endswith('.mp4') or '.mp4?' in url):
                    # Generate video frame snapshot using FFmpeg for HLS / direct streams without thumbnail
                    try:
                        import os
                        from glypdl.utils.thumbnails import get_cached_thumbnail_path
                        ffmpeg_bin = self.ytdlp_service.detect_ffmpeg() or 'ffmpeg'
                        cache_path = str(get_cached_thumbnail_path(url))
                        subprocess.run([
                            ffmpeg_bin, '-y', '-ss', '00:00:01', '-i', url,
                            '-vframes', '1', '-q:v', '2', cache_path
                        ], capture_output=True, timeout=8)
                        if os.path.isfile(cache_path) and os.path.getsize(cache_path) > 0:
                            metadata_dict['thumbnail'] = cache_path
                            metadata_dict['thumbnail_path'] = cache_path
                    except Exception:
                        pass

                GLib.idle_add(callback, metadata_dict)

            except subprocess.CalledProcessError as e:
                err_msg = e.stderr.strip() if e.stderr else "Failed to retrieve metadata"
                if error_callback:
                    GLib.idle_add(error_callback, err_msg, url)
                else:
                    GLib.idle_add(callback, None)
            except Exception as e:
                if error_callback:
                    GLib.idle_add(error_callback, str(e), url)
                else:
                    GLib.idle_add(callback, None)

        thread = threading.Thread(target=_fetch, daemon=True)
        thread.start()

    def parse_formats(self, metadata: dict) -> dict:
        """Analyze metadata formats list and extract structured video, audio, and quality options."""
        video_formats = []
        audio_formats = []
        available_qualities = set()

        formats = metadata.get('formats', [])
        for fmt in formats:
            has_video = fmt.get('vcodec') is not None and fmt.get('vcodec') != 'none'
            has_audio = fmt.get('acodec') is not None and fmt.get('acodec') != 'none'

            format_dict = {
                'format_id': fmt.get('format_id', ''),
                'ext': fmt.get('ext', ''),
                'filesize': fmt.get('filesize') or fmt.get('filesize_approx') or 0,
                'format_note': fmt.get('format_note', ''),
                'fps': fmt.get('fps'),
                'tbr': fmt.get('tbr')
            }

            if has_video:
                height = fmt.get('height')
                format_dict.update({
                    'resolution': fmt.get('resolution') or (f"{fmt.get('width')}x{height}" if height else ''),
                    'vcodec': fmt.get('vcodec', ''),
                    'height': height,
                    'has_video': True,
                    'has_audio': has_audio
                })
                video_formats.append(format_dict)
                if height:
                    available_qualities.add(f"{height}p")

            if has_audio and not has_video:
                format_dict.update({
                    'acodec': fmt.get('acodec', ''),
                    'abr': fmt.get('abr')
                })
                audio_formats.append(format_dict)

        def quality_key(q: str):
            try:
                return int(q.replace('p', ''))
            except ValueError:
                return 0

        # Sort standard qualities descending
        sorted_qualities = sorted(list(available_qualities), key=quality_key, reverse=True)
        all_qualities = ['Best'] + sorted_qualities if sorted_qualities else ['Best', '1080p', '720p', '480p', '360p']

        return {
            'video_formats': video_formats,
            'audio_formats': audio_formats,
            'available_qualities': all_qualities
        }
