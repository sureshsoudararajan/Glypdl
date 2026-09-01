"""Tests for CookieService security, profile management, and direct browser cookie extraction."""

import unittest
import tempfile
import shutil
from pathlib import Path
from glypdl.services.cookies import CookieService, is_flatpak_environment


class TestCookieService(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_cookie_profiles_preservation(self):
        """Verify legacy Netscape cookies.txt profiles continue working seamlessly."""
        cookie_file = Path(self.test_dir) / "cookies.txt"
        cookie_file.write_text("# Netscape HTTP Cookie File\n")

        service = CookieService(config_dir=self.test_dir)
        self.assertEqual(len(service.get_profiles()), 0)

        # Add profile
        service.add_profile("YouTube", str(cookie_file))
        profiles = service.get_profiles()
        self.assertEqual(len(profiles), 1)
        self.assertEqual(profiles[0]["name"], "YouTube")
        self.assertEqual(profiles[0]["path"], str(cookie_file))

        # Get cookie args
        args = service.get_cookie_args(profile_name="YouTube")
        self.assertEqual(args, ["--cookies", str(cookie_file)])

        # Remove profile
        service.remove_profile("YouTube")
        self.assertEqual(len(service.get_profiles()), 0)

    def test_cookie_security(self):
        """Invalid or non-existent cookie file paths must be rejected."""
        service = CookieService(config_dir=self.test_dir)
        with self.assertRaises(ValueError):
            service.add_profile("Invalid", "/path/to/nonexistent/file.txt")

    def test_supported_browsers_and_keyrings(self):
        """Verify supported browsers and keyrings conform to yt-dlp specification."""
        service = CookieService(config_dir=self.test_dir)
        browsers = service.get_supported_browsers()
        browser_ids = [b["id"] for b in browsers]

        self.assertIn("chrome", browser_ids)
        self.assertIn("chromium", browser_ids)
        self.assertIn("firefox", browser_ids)
        self.assertIn("librewolf", browser_ids)
        self.assertIn("brave", browser_ids)
        self.assertIn("edge", browser_ids)
        self.assertIn("opera", browser_ids)
        self.assertIn("vivaldi", browser_ids)

        keyrings = service.get_supported_keyrings()
        keyring_ids = [k["id"] for k in keyrings]
        self.assertIn("auto", keyring_ids)
        self.assertIn("gnomekeyring", keyring_ids)
        self.assertIn("kwallet", keyring_ids)
        self.assertIn("basictext", keyring_ids)

    def test_build_browser_spec_syntax(self):
        """Verify BROWSER[+KEYRING][:PROFILE] command spec generator."""
        service = CookieService(config_dir=self.test_dir)

        # Standard browser
        self.assertEqual(service.build_browser_spec("chrome"), "chrome")
        self.assertEqual(service.build_browser_spec("firefox"), "firefox")

        # LibreWolf support
        self.assertTrue(service.build_browser_spec("librewolf").startswith("firefox"))
        self.assertEqual(service.build_browser_spec("librewolf", profile="/custom/path/librewolf"), "firefox:/custom/path/librewolf")

        # Browser with profile
        self.assertEqual(service.build_browser_spec("chrome", profile="Default"), "chrome:Default")
        self.assertEqual(service.build_browser_spec("firefox", profile="default-release"), "firefox:default-release")

        # Browser with keyring
        self.assertEqual(service.build_browser_spec("brave", keyring="gnomekeyring"), "brave+gnomekeyring")
        self.assertEqual(service.build_browser_spec("edge", keyring="kwallet", profile="Profile 1"), "edge+kwallet:Profile 1")

        # Automatic keyring ignored from spec string
        self.assertEqual(service.build_browser_spec("chromium", keyring="auto", profile="Default"), "chromium:Default")

        # Empty / None handling
        self.assertEqual(service.build_browser_spec(""), "")
        self.assertEqual(service.build_browser_spec("none"), "")

    def test_browser_discovery(self):
        """Verify browser discovery returns a structured list without crashing."""
        service = CookieService(config_dir=self.test_dir)
        discovered = service.discover_installed_browsers(force_refresh=True)
        self.assertIsInstance(discovered, list)
        self.assertGreater(len(discovered), 0)
        for b in discovered:
            self.assertIn("id", b)
            self.assertIn("name", b)
            self.assertIn("profiles", b)
            self.assertIn("is_installed", b)
            self.assertIsInstance(b["profiles"], list)
            self.assertGreater(len(b["profiles"]), 0)


if __name__ == '__main__':
    unittest.main()
