"""Tests for Settings model and config file persistence."""

import unittest
import tempfile
import shutil
from pathlib import Path
from glypdl.models.settings import Settings


class TestSettings(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_settings_defaults(self):
        s = Settings(config_path=Path(self.test_dir) / "defaults_config.ini")
        self.assertEqual(s.get('max_concurrent'), 2)
        self.assertTrue(s.get('notifications'))
        self.assertEqual(s.get('color_scheme'), 'system')
        self.assertEqual(s.get('filename_template'), '%(title)s.%(ext)s')
        self.assertFalse(s.get('overwrite'))
        self.assertFalse(s.get('use_cookies'))
        self.assertEqual(s.get('cookie_file'), '')

    def test_settings_save_and_load(self):
        cfg_file = Path(self.test_dir) / "test_config.ini"
        s = Settings(config_path=cfg_file)
        
        s.set('max_concurrent', 5)
        s.set('color_scheme', 'dark')
        s.set('filename_template', '%(uploader)s - %(title)s.%(ext)s')
        s.set('overwrite', True)

        # Read with fresh settings instance
        s2 = Settings(config_path=cfg_file)

        self.assertEqual(s2.get('max_concurrent'), 5)
        self.assertEqual(s2.get('color_scheme'), 'dark')
        self.assertEqual(s2.get('filename_template'), '%(uploader)s - %(title)s.%(ext)s')
        self.assertTrue(s2.get('overwrite'))


if __name__ == '__main__':
    unittest.main()
