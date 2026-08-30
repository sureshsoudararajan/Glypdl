"""Tests for CookieService security and profile management."""

import unittest
import tempfile
import shutil
from pathlib import Path
from glypdl.services.cookies import CookieService


class TestCookieService(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_cookie_profiles(self):
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
        # Invalid or non-existent file should be rejected
        service = CookieService(config_dir=self.test_dir)
        with self.assertRaises(ValueError):
            service.add_profile("Invalid", "/path/to/nonexistent/file.txt")


if __name__ == '__main__':
    unittest.main()
