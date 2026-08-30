"""Tests for MetadataService format parsing and analysis."""

import unittest
from glypdl.services.metadata import MetadataService
from glypdl.services.ytdlp import YtDlpService


class TestMetadataService(unittest.TestCase):
    def test_parse_formats(self):
        service = MetadataService(YtDlpService())
        
        mock_metadata = {
            "title": "Test Video",
            "uploader": "Test Channel",
            "duration": 300,
            "formats": [
                {
                    "format_id": "137",
                    "ext": "mp4",
                    "resolution": "1920x1080",
                    "height": 1080,
                    "width": 1920,
                    "fps": 30,
                    "vcodec": "avc1.640028",
                    "acodec": "none",
                    "filesize": 150000000
                },
                {
                    "format_id": "136",
                    "ext": "mp4",
                    "resolution": "1280x720",
                    "height": 720,
                    "width": 1280,
                    "fps": 30,
                    "vcodec": "avc1.4d401f",
                    "acodec": "none",
                    "filesize": 80000000
                },
                {
                    "format_id": "140",
                    "ext": "m4a",
                    "acodec": "mp4a.40.2",
                    "vcodec": "none",
                    "abr": 128,
                    "filesize": 12000000
                }
            ]
        }

        parsed = service.parse_formats(mock_metadata)
        self.assertIn("1080p", parsed["available_qualities"])
        self.assertIn("720p", parsed["available_qualities"])
        self.assertEqual(len(parsed["video_formats"]), 2)
        self.assertEqual(len(parsed["audio_formats"]), 1)


if __name__ == '__main__':
    unittest.main()
