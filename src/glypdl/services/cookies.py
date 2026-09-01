"""Cookie management and browser cookie extraction service for Glypdl."""

import os
import re
import json
import shutil
import subprocess
import threading
import configparser
from pathlib import Path
from typing import List, Dict, Optional, Tuple, Callable
from gi.repository import GLib

from glypdl.utils.paths import get_config_dir


def is_flatpak_environment() -> bool:
    """Check if Glypdl is running inside a Flatpak sandbox."""
    return os.path.exists('/.flatpak-info') or bool(os.environ.get('FLATPAK_ID'))


class CookieService:
    """Manages browser cookie extraction and Netscape cookies.txt profiles."""

    # Browsers natively supported by yt-dlp
    SUPPORTED_BROWSERS = [
        {"id": "chrome", "name": "Google Chrome", "icon": "google-chrome"},
        {"id": "chromium", "name": "Chromium", "icon": "chromium"},
        {"id": "firefox", "name": "Mozilla Firefox", "icon": "firefox"},
        {"id": "brave", "name": "Brave Browser", "icon": "brave"},
        {"id": "edge", "name": "Microsoft Edge", "icon": "microsoft-edge"},
        {"id": "opera", "name": "Opera", "icon": "opera"},
        {"id": "vivaldi", "name": "Vivaldi", "icon": "vivaldi"},
        {"id": "whale", "name": "Naver Whale", "icon": "web-browser"},
    ]

    SUPPORTED_KEYRINGS = [
        {"id": "auto", "name": "Automatic (Recommended)"},
        {"id": "gnomekeyring", "name": "GNOME Keyring"},
        {"id": "kwallet", "name": "KWallet (Auto / v5 / v6)"},
        {"id": "basictext", "name": "Basic Text (Unencrypted)"},
    ]

    def __init__(self, config_dir=None):
        if config_dir is None:
            self.config_dir = str(get_config_dir())
        else:
            self.config_dir = str(config_dir)

        os.makedirs(self.config_dir, exist_ok=True)
        self.profiles_file = os.path.join(self.config_dir, 'profiles.json')
        self.profiles: List[Dict[str, str]] = []
        self._cached_browsers: Optional[List[Dict]] = None
        self.load_profiles()

    # =========================================================================
    # Browser Cookie Discovery & Spec Construction
    # =========================================================================

    def get_supported_browsers(self) -> List[Dict]:
        """Return the list of all yt-dlp supported browsers."""
        return list(self.SUPPORTED_BROWSERS)

    def get_supported_keyrings(self) -> List[Dict]:
        """Return the list of supported cookie decryption keyrings."""
        return list(self.SUPPORTED_KEYRINGS)

    def discover_installed_browsers(self, force_refresh: bool = False) -> List[Dict]:
        """
        Discover browsers installed on the host system or in Flatpak packages,
        detecting their available user profiles.
        """
        if self._cached_browsers is not None and not force_refresh:
            return self._cached_browsers

        home = Path.home()
        discovered = []

        browser_checks = {
            "chrome": {
                "binaries": ["google-chrome", "google-chrome-stable", "chrome"],
                "paths": [
                    home / ".config" / "google-chrome",
                    home / ".var" / "app" / "com.google.Chrome" / "config" / "google-chrome",
                ],
                "type": "chromium"
            },
            "chromium": {
                "binaries": ["chromium", "chromium-browser"],
                "paths": [
                    home / ".config" / "chromium",
                    home / ".var" / "app" / "org.chromium.Chromium" / "config" / "chromium",
                ],
                "type": "chromium"
            },
            "firefox": {
                "binaries": ["firefox", "firefox-esr"],
                "paths": [
                    home / ".mozilla" / "firefox",
                    home / ".var" / "app" / "org.mozilla.firefox" / ".mozilla" / "firefox",
                ],
                "type": "firefox"
            },
            "brave": {
                "binaries": ["brave-browser", "brave"],
                "paths": [
                    home / ".config" / "BraveSoftware" / "Brave-Browser",
                    home / ".var" / "app" / "com.brave.Browser" / "config" / "BraveSoftware" / "Brave-Browser",
                ],
                "type": "chromium"
            },
            "edge": {
                "binaries": ["microsoft-edge", "microsoft-edge-stable", "microsoft-edge-dev"],
                "paths": [
                    home / ".config" / "microsoft-edge",
                    home / ".config" / "microsoft-edge-dev",
                    home / ".var" / "app" / "com.microsoft.Edge" / "config" / "microsoft-edge",
                ],
                "type": "chromium"
            },
            "opera": {
                "binaries": ["opera"],
                "paths": [
                    home / ".config" / "opera",
                    home / ".var" / "app" / "com.opera.Opera" / "config" / "opera",
                ],
                "type": "chromium"
            },
            "vivaldi": {
                "binaries": ["vivaldi", "vivaldi-stable"],
                "paths": [
                    home / ".config" / "vivaldi",
                    home / ".var" / "app" / "com.vivaldi.Vivaldi" / "config" / "vivaldi",
                ],
                "type": "chromium"
            },
            "whale": {
                "binaries": ["naver-whale", "whale"],
                "paths": [
                    home / ".config" / "naver-whale",
                ],
                "type": "chromium"
            }
        }

        for b_info in self.SUPPORTED_BROWSERS:
            b_id = b_info["id"]
            spec = browser_checks.get(b_id)
            if not spec:
                continue

            # Check if binary or any config directory exists
            has_binary = any(bool(shutil.which(b)) for b in spec["binaries"])
            config_paths = [p for p in spec["paths"] if p.exists() and p.is_dir()]

            # Determine availability
            is_installed = has_binary or bool(config_paths)

            # Discover user profiles
            profiles = ["Default"]
            if config_paths:
                primary_path = config_paths[0]
                if spec["type"] == "chromium":
                    detected = self._discover_chromium_profiles(primary_path)
                    if detected:
                        profiles = detected
                elif spec["type"] == "firefox":
                    detected = self._discover_firefox_profiles(primary_path)
                    if detected:
                        profiles = detected

            discovered.append({
                "id": b_id,
                "name": b_info["name"],
                "icon": b_info["icon"],
                "is_installed": is_installed,
                "profiles": profiles
            })

        # Sort: installed browsers first, then by name
        discovered.sort(key=lambda x: (not x["is_installed"], x["name"]))
        self._cached_browsers = discovered
        return discovered

    def _discover_chromium_profiles(self, config_dir: Path) -> List[str]:
        """Discover profile directory names in a Chromium-based browser configuration."""
        profiles = []
        try:
            # 1. Check Local State for profile names
            local_state_file = config_dir / "Local State"
            if local_state_file.exists() and local_state_file.is_file():
                try:
                    with open(local_state_file, 'r', encoding='utf-8', errors='ignore') as f:
                        data = json.load(f)
                    info_cache = data.get("profile", {}).get("info_cache", {})
                    for prof_dir in info_cache.keys():
                        if prof_dir not in profiles:
                            profiles.append(prof_dir)
                except Exception:
                    pass

            # 2. Inspect physical subdirectories
            if not profiles:
                if (config_dir / "Default").is_dir():
                    profiles.append("Default")
                for item in sorted(config_dir.iterdir()):
                    if item.is_dir() and (item.name.startswith("Profile ") or item.name == "Default"):
                        if item.name not in profiles:
                            profiles.append(item.name)
        except Exception:
            pass

        return profiles if profiles else ["Default"]

    def _discover_firefox_profiles(self, config_dir: Path) -> List[str]:
        """Discover profile names from Firefox profiles.ini or folder names."""
        profiles = []
        try:
            ini_path = config_dir / "profiles.ini"
            if ini_path.exists() and ini_path.is_file():
                cp = configparser.ConfigParser()
                cp.read(str(ini_path))
                for sec in cp.sections():
                    if sec.startswith("Profile") or sec.startswith("Install"):
                        name = cp.get(sec, "Name", fallback=None)
                        if name and name not in profiles:
                            profiles.append(name)
                        # Also add Path basename
                        path_val = cp.get(sec, "Path", fallback=None)
                        if path_val:
                            base = os.path.basename(path_val)
                            if base and base not in profiles and not name:
                                profiles.append(base)

            # Fallback scan directories
            if not profiles:
                for item in config_dir.iterdir():
                    if item.is_dir() and ("default" in item.name or ".release" in item.name):
                        profiles.append(item.name)
        except Exception:
            pass

        return profiles if profiles else ["default-release", "default"]

    def build_browser_spec(self, browser_name: str, profile: Optional[str] = None, keyring: Optional[str] = None) -> str:
        """
        Construct yt-dlp's `--cookies-from-browser` argument string.
        Format: BROWSER[+KEYRING][:PROFILE]
        """
        if not browser_name or browser_name == 'none':
            return ""

        # Normalize browser ID
        b_name = browser_name.lower().strip()

        # Append optional keyring if specific keyring requested
        keyring_suffix = ""
        if keyring and keyring.lower() not in ('auto', 'default', ''):
            keyring_suffix = f"+{keyring.lower().strip()}"

        # Append profile if specified and non-empty
        profile_suffix = ""
        if profile and profile.strip():
            p = profile.strip()
            profile_suffix = f":{p}"

        return f"{b_name}{keyring_suffix}{profile_suffix}"

    def test_browser_cookies_async(
        self,
        ytdlp_service,
        browser_spec: str,
        callback: Callable[[bool, str, str], None],
        test_url: str = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
    ):
        """
        Test extracting cookies from the specified browser configuration asynchronously.
        Calls callback(success: bool, message: str, technical_details: str) on the GTK main loop.
        """
        def _worker():
            try:
                ytdlp_path = ytdlp_service.get_path()
                args = [
                    ytdlp_path,
                    "--cookies-from-browser", browser_spec,
                    "--simulate",
                    "--no-warnings",
                    "--no-playlist",
                    test_url
                ]

                proc = subprocess.run(
                    args,
                    capture_output=True,
                    text=True,
                    timeout=15
                )

                stderr_out = proc.stderr.strip()
                stdout_out = proc.stdout.strip()
                combined_output = (stderr_out + "\n" + stdout_out).strip()

                if proc.returncode == 0:
                    GLib.idle_add(
                        callback,
                        True,
                        f"Browser cookies successfully read from '{browser_spec}'.",
                        combined_output or "OK"
                    )
                else:
                    # Provide helpful diagnostic message based on error signature
                    lower_err = combined_output.lower()
                    if "database is locked" in lower_err or "locked" in lower_err or "sqlite3.operationalerror" in lower_err:
                        user_msg = "Could not read cookies because the browser's cookie database is locked. Try closing your web browser completely and test again."
                    elif "could not find" in lower_err or "profile" in lower_err:
                        user_msg = "Could not find the specified browser profile. Verify that the profile exists in your browser."
                    elif "keyring" in lower_err or "secret" in lower_err or "decrypt" in lower_err:
                        user_msg = "Cookie decryption failed. Check your desktop keyring (GNOME Keyring / KWallet) or try selecting a specific keyring in Settings."
                    elif is_flatpak_environment():
                        user_msg = "The Flatpak sandbox restricted access to the host browser. Please use a cookies.txt file instead."
                    else:
                        user_msg = f"yt-dlp could not extract cookies from '{browser_spec}'."

                    GLib.idle_add(
                        callback,
                        False,
                        user_msg,
                        combined_output or f"Exit code: {proc.returncode}"
                    )
            except subprocess.TimeoutExpired:
                GLib.idle_add(
                    callback,
                    False,
                    "Cookie extraction check timed out after 15 seconds.",
                    "TimeoutExpired"
                )
            except Exception as exc:
                GLib.idle_add(
                    callback,
                    False,
                    f"Failed to test browser cookies: {exc}",
                    str(exc)
                )

        t = threading.Thread(target=_worker, daemon=True)
        t.start()

    # =========================================================================
    # Netscape cookies.txt Profiles Management (Legacy & Preserved)
    # =========================================================================

    def load_profiles(self):
        """Load saved Netscape cookies.txt profiles from disk."""
        if os.path.exists(self.profiles_file):
            try:
                with open(self.profiles_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.profiles = [
                        {'name': p.get('name'), 'path': p.get('path')}
                        for p in data if 'name' in p and 'path' in p
                    ]
            except Exception:
                self.profiles = []
        else:
            self.profiles = []

    def save_profiles(self):
        """Save Netscape cookies.txt profiles to disk."""
        try:
            with open(self.profiles_file, 'w', encoding='utf-8') as f:
                json.dump(self.profiles, f, indent=4)
        except Exception as e:
            print(f"Failed to save cookie profiles: {e}")

    def add_profile(self, name: str, cookie_file_path: str):
        """Add or update a cookies.txt file profile."""
        if not self.validate_cookie_file(cookie_file_path):
            raise ValueError(f"Cookie file at {cookie_file_path} is invalid or not readable.")

        for p in self.profiles:
            if p['name'] == name:
                p['path'] = cookie_file_path
                self.save_profiles()
                return

        self.profiles.append({'name': name, 'path': cookie_file_path})
        self.save_profiles()

    def remove_profile(self, name: str):
        """Remove a cookies.txt profile by name."""
        self.profiles = [p for p in self.profiles if p['name'] != name]
        self.save_profiles()

    def get_profile(self, name: str) -> Optional[dict]:
        """Get profile dict by name."""
        for p in self.profiles:
            if p['name'] == name:
                return p
        return None

    def get_profiles(self) -> List[dict]:
        """Return all saved cookies.txt profiles."""
        return list(self.profiles)

    def validate_cookie_file(self, path: str) -> bool:
        """Verify that the given path exists, is a regular file, and is readable."""
        if not path:
            return False
        p = Path(path)
        return p.exists() and p.is_file() and os.access(path, os.R_OK)

    def get_cookie_args(self, profile_name=None, cookie_file=None) -> List[str]:
        """Get command line argument for cookies.txt."""
        if cookie_file and self.validate_cookie_file(cookie_file):
            return ['--cookies', cookie_file]

        if profile_name:
            profile = self.get_profile(profile_name)
            if profile and self.validate_cookie_file(profile['path']):
                return ['--cookies', profile['path']]

        return []
