"""Cookie management service for Glypdl."""

import os
import json
from pathlib import Path
from gi.repository import GLib

from glypdl.utils.paths import get_config_dir


class CookieService:
    """Manages Netscape cookie profiles for site authentication."""

    def __init__(self, config_dir=None):
        if config_dir is None:
            self.config_dir = str(get_config_dir())
        else:
            self.config_dir = str(config_dir)
            
        os.makedirs(self.config_dir, exist_ok=True)
        self.profiles_file = os.path.join(self.config_dir, 'profiles.json')
        self.profiles = []
        self.load_profiles()

    def load_profiles(self):
        if os.path.exists(self.profiles_file):
            try:
                with open(self.profiles_file, 'r', encoding='utf-8') as f:
                    data = json.load(f)
                    self.profiles = [{'name': p.get('name'), 'path': p.get('path')} 
                                     for p in data if 'name' in p and 'path' in p]
            except Exception:
                self.profiles = []
        else:
            self.profiles = []

    def save_profiles(self):
        try:
            with open(self.profiles_file, 'w', encoding='utf-8') as f:
                json.dump(self.profiles, f, indent=4)
        except Exception as e:
            print(f"Failed to save profiles: {e}")

    def add_profile(self, name: str, cookie_file_path: str):
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
        self.profiles = [p for p in self.profiles if p['name'] != name]
        self.save_profiles()

    def get_profile(self, name: str) -> dict:
        for p in self.profiles:
            if p['name'] == name:
                return p
        return None

    def get_profiles(self) -> list:
        return self.profiles

    def validate_cookie_file(self, path: str) -> bool:
        if not path:
            return False
        p = Path(path)
        return p.exists() and p.is_file() and os.access(path, os.R_OK)

    def get_cookie_args(self, profile_name=None, cookie_file=None) -> list:
        if cookie_file and self.validate_cookie_file(cookie_file):
            return ['--cookies', cookie_file]
            
        if profile_name:
            profile = self.get_profile(profile_name)
            if profile and self.validate_cookie_file(profile['path']):
                return ['--cookies', profile['path']]
                
        return []
