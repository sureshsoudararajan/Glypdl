"""Thumbnail caching and loading utility for Glypdl."""

import hashlib
import os
import urllib.request
import threading
from pathlib import Path
import gi

gi.require_version('GLib', '2.0')
gi.require_version('Gdk', '4.0')
gi.require_version('GdkPixbuf', '2.0')
from gi.repository import GLib, Gdk, GdkPixbuf

from glypdl.utils.paths import get_thumbnail_cache_dir


def get_cached_thumbnail_path(url: str) -> Path:
    """Return local path for cached thumbnail corresponding to given URL."""
    cache_dir = get_thumbnail_cache_dir()
    cache_dir.mkdir(parents=True, exist_ok=True)
    url_hash = hashlib.sha256(url.encode('utf-8')).hexdigest()
    # Detect extension from URL or default to .jpg
    ext = ".jpg"
    clean_url = url.split("?")[0]
    for e in [".jpg", ".jpeg", ".png", ".webp"]:
        if clean_url.lower().endswith(e):
            ext = e
            break
    return cache_dir / f"{url_hash}{ext}"


def load_scaled_texture(file_path: str, max_width: int, max_height: int):
    """Load an image from disk, scale it to max_width x max_height, and return a Gdk.Texture."""
    if not file_path or not os.path.isfile(file_path):
        return None
    try:
        pixbuf = GdkPixbuf.Pixbuf.new_from_file_at_scale(
            file_path,
            max_width * 2,
            max_height * 2,
            True
        )
        success, buffer = pixbuf.save_to_bufferv('png', [], [])
        if success:
            gbytes = GLib.Bytes.new(buffer)
            return Gdk.Texture.new_from_bytes(gbytes)
    except Exception:
        pass
    try:
        return Gdk.Texture.new_from_filename(file_path)
    except Exception:
        return None


def load_thumbnail_async(url: str, callback, error_callback=None):
    """Download thumbnail if not cached, and invoke callback(local_path_str) on GTK main thread."""
    if not url:
        if callback:
            GLib.idle_add(callback, None)
        return

    cached_path = get_cached_thumbnail_path(url)
    if cached_path.exists() and cached_path.stat().st_size > 0:
        if callback:
            GLib.idle_add(callback, str(cached_path))
        return

    def worker():
        try:
            req = urllib.request.Request(
                url,
                headers={'User-Agent': 'Mozilla/5.0 (X11; Linux x86_64)'}
            )
            with urllib.request.urlopen(req, timeout=10) as resp:
                data = resp.read()
                cached_path.write_bytes(data)
            if callback:
                GLib.idle_add(callback, str(cached_path))
        except Exception as e:
            if error_callback:
                GLib.idle_add(error_callback, str(e))
            elif callback:
                GLib.idle_add(callback, None)

    thread = threading.Thread(target=worker, daemon=True)
    thread.start()
