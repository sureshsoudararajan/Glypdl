"""Tests for YtDlpService detection and command building."""

import unittest
import shutil
from glypdl.services.ytdlp import YtDlpService


class TestYtDlpService(unittest.TestCase):
    def test_ytdlp_detection(self):
        service = YtDlpService()
        path = service.detect()
        self.assertIsNotNone(path)
        self.assertEqual(shutil.which('yt-dlp'), path)
        self.assertTrue(service.is_available())

    def test_ffmpeg_detection(self):
        service = YtDlpService()
        self.assertTrue(service.ffmpeg_available())

    def test_build_download_args(self):
        service = YtDlpService()
        args = service.build_download_args(
            url="https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            format_spec="bestvideo+bestaudio/best",
            output_template="%(title)s.%(ext)s",
            download_dir="/tmp/downloads",
            extra_args=["--no-mtime"]
        )
        self.assertIn(service.get_path(), args[0])
        self.assertIn("--newline", args)
        self.assertIn("--progress", args)
        self.assertIn("-f", args)
        self.assertIn("bestvideo+bestaudio/best", args)
        self.assertIn("-o", args)
        self.assertIn("%(title)s.%(ext)s", args)
        self.assertIn("-P", args)
        self.assertIn("/tmp/downloads", args)
        self.assertIn("--no-mtime", args)
        self.assertIn("--no-playlist", args)
        self.assertEqual("https://www.youtube.com/watch?v=dQw4w9WgXcQ", args[-1])

    def test_build_audio_download_args(self):
        service = YtDlpService()
        args = service.build_download_args(
            url="https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            format_spec="bestaudio/best",
            output_template="%(title)s.%(ext)s",
            extract_audio=True,
            audio_format="mp3"
        )
        self.assertIn("-x", args)
        self.assertIn("--audio-format", args)
        self.assertIn("mp3", args)

    def test_build_download_args_with_browser_cookies(self):
        service = YtDlpService()
        args = service.build_download_args(
            url="https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            cookies_from_browser="chrome:Default"
        )
        self.assertIn("--cookies-from-browser", args)
        idx = args.index("--cookies-from-browser")
        self.assertEqual(args[idx + 1], "chrome:Default")

    def test_build_metadata_args_with_browser_cookies(self):
        service = YtDlpService()
        args = service.build_metadata_args(
            url="https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            cookies_from_browser="firefox:default-release"
        )
        self.assertIn("--cookies-from-browser", args)
        idx = args.index("--cookies-from-browser")
        self.assertEqual(args[idx + 1], "firefox:default-release")

    def test_build_playlist_args_with_browser_cookies(self):
        service = YtDlpService()
        args = service.build_playlist_args(
            url="https://www.youtube.com/playlist?list=PL12345",
            cookies_from_browser="brave+gnomekeyring:Default"
        )
        self.assertIn("--cookies-from-browser", args)
        idx = args.index("--cookies-from-browser")
        self.assertEqual(args[idx + 1], "brave+gnomekeyring:Default")



if __name__ == '__main__':
    unittest.main()
