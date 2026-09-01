"""Application settings management using GLib.KeyFile."""

import os
from pathlib import Path
from typing import Any
import gi

gi.require_version('GLib', '2.0')
from gi.repository import GLib

from glypdl.utils.paths import get_config_dir, get_default_download_dir


class Settings:
    """Manages application settings stored in XDG config directory."""

    GROUP_NAME = 'General'

    DEFAULTS = {
        'download_dir': str(get_default_download_dir()),
        'max_concurrent': 2,
        'auto_start': False,
        'notifications': True,
        'color_scheme': 'system',  # 'system', 'light', 'dark'
        'filename_template': '%(title)s.%(ext)s',
        'overwrite': False,
        'ytdlp_path': '',
        'ffmpeg_path': '',
        'extra_args': '',
        'verbose_logging': False,
        'cookie_method': 'none',  # 'none', 'browser', 'file'
        'use_cookies': False,      # Legacy backward-compatibility
        'cookie_file': '',
        'browser_name': 'auto',
        'browser_profile': '',
        'browser_keyring': 'auto'
    }

    def __init__(self, config_path=None):
        self.keyfile = GLib.KeyFile.new()
        if config_path is not None:
            self.config_path = Path(config_path)
        else:
            self.config_path = self.get_config_path()
        self.load()

    def get_config_path(self) -> Path:
        """Get the path to the configuration file using XDG config specification."""
        config_dir = get_config_dir()
        config_dir.mkdir(parents=True, exist_ok=True)
        return config_dir / 'config.ini'

    def load(self):
        """Load settings from config file."""
        if self.config_path.exists():
            try:
                self.keyfile.load_from_file(
                    str(self.config_path),
                    GLib.KeyFileFlags.NONE
                )
            except GLib.Error as e:
                print(f"Failed to load config: {e}")

    def save(self):
        """Save settings to config file."""
        try:
            self.config_path.parent.mkdir(parents=True, exist_ok=True)
            self.keyfile.save_to_file(str(self.config_path))
        except Exception as e:
            try:
                data = self.keyfile.to_data()
                if data and data[0]:
                    self.config_path.write_text(data[0])
            except Exception as e2:
                print(f"Failed to save config: {e2}")

    def get(self, key: str, default: Any = None) -> Any:
        """Get a configuration value, returning default if not found."""
        if default is None:
            default = self.DEFAULTS.get(key)

        if not self.keyfile.has_group(self.GROUP_NAME):
            return default

        try:
            if isinstance(default, bool):
                return self.keyfile.get_boolean(self.GROUP_NAME, key)
            elif isinstance(default, int):
                return self.keyfile.get_integer(self.GROUP_NAME, key)
            elif isinstance(default, float):
                return self.keyfile.get_double(self.GROUP_NAME, key)
            else:
                return self.keyfile.get_string(self.GROUP_NAME, key)
        except GLib.Error:
            return default

    def set(self, key: str, value: Any):
        """Set a configuration value and save."""
        if isinstance(value, bool):
            self.keyfile.set_boolean(self.GROUP_NAME, key, value)
        elif isinstance(value, int):
            self.keyfile.set_integer(self.GROUP_NAME, key, value)
        elif isinstance(value, float):
            self.keyfile.set_double(self.GROUP_NAME, key, value)
        else:
            self.keyfile.set_string(self.GROUP_NAME, key, str(value))

        self.save()

    def get_color_scheme(self) -> str:
        """Get active color scheme ('system', 'light', 'dark')."""
        return self.get('color_scheme', 'system')

    def set_color_scheme(self, scheme: str):
        """Set color scheme."""
        self.set('color_scheme', scheme)

    def get_custom_ytdlp_path(self) -> str:
        """Get custom yt-dlp binary path."""
        return self.get('ytdlp_path', '')

    def get_custom_ffmpeg_path(self) -> str:
        """Get custom ffmpeg binary path."""
        return self.get('ffmpeg_path', '')

    def get_cookie_method(self) -> str:
        """Get active cookie authentication method ('none', 'browser', 'file')."""
        method = self.get('cookie_method', 'none')
        # Migrate legacy use_cookies boolean if cookie_method not explicitly set
        if method == 'none' and self.get('use_cookies', False):
            if self.get('cookie_file'):
                return 'file'
        return method

    def set_cookie_method(self, method: str):
        """Set active cookie authentication method."""
        self.set('cookie_method', method)
        self.set('use_cookies', method != 'none')

    def get_browser_name(self) -> str:
        return self.get('browser_name', 'auto')

    def get_browser_profile(self) -> str:
        return self.get('browser_profile', '')

    def get_browser_keyring(self) -> str:
        return self.get('browser_keyring', 'auto')

