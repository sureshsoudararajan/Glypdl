"""Tests for DownloadManager queue management."""

import unittest
from unittest.mock import MagicMock
from glypdl.services.downloader import DownloadManager
from glypdl.models.download import DownloadItem, DownloadState, DownloadMode
from glypdl.models.settings import Settings


class TestDownloadManager(unittest.TestCase):
    def setUp(self):
        self.mock_ytdlp = MagicMock()
        self.mock_ytdlp.build_download_args.return_value = ["yt-dlp", "https://example.com"]
        self.settings = Settings()
        self.settings.set('max_concurrent', 2)
        self.manager = DownloadManager(self.mock_ytdlp, self.settings)

    def test_format_spec_generation(self):
        # Audio mode
        item_audio = DownloadItem(url="https://example.com", mode=DownloadMode.AUDIO, audio_format="mp3")
        spec_audio = self.manager._build_format_spec(item_audio)
        self.assertEqual("bestaudio/best", spec_audio)

        # Video only mode
        item_video = DownloadItem(url="https://example.com", mode=DownloadMode.VIDEO, quality="1080p")
        spec_video = self.manager._build_format_spec(item_video)
        self.assertEqual(spec_video, "bestvideo[height<=1080]/best")

        # Video + audio mode
        item_va = DownloadItem(url="https://example.com", mode=DownloadMode.VIDEO_AUDIO, quality="720p")
        spec_va = self.manager._build_format_spec(item_va)
        self.assertEqual("bestvideo[height<=720]+bestaudio/best[height<=720]", spec_va)

    def test_speed_parser(self):
        self.assertEqual(self.manager._parse_speed_value("10.5MiB/s"), 10.5 * 1024 * 1024)
        self.assertEqual(self.manager._parse_speed_value("500KiB/s"), 500 * 1024)
        self.assertEqual(self.manager._parse_speed_value(""), 0.0)

    def test_eta_parser(self):
        self.assertEqual(self.manager._parse_eta_value("00:41"), 41)
        self.assertEqual(self.manager._parse_eta_value("02:15"), 135)
        self.assertEqual(self.manager._parse_eta_value("01:10:05"), 4205)
        self.assertEqual(self.manager._parse_eta_value(""), 0)

    def test_state_detection(self):
        self.assertEqual(
            self.manager._detect_state_from_line("[Merger] Merging formats"),
            DownloadState.MERGING
        )
        self.assertEqual(
            self.manager._detect_state_from_line("[ExtractAudio] Extracting"),
            DownloadState.CONVERTING
        )
        self.assertEqual(
            self.manager._detect_state_from_line("[download] Destination: video.mp4"),
            DownloadState.DOWNLOADING
        )


if __name__ == '__main__':
    unittest.main()
