"""Tests for DownloadItem model and states."""

import unittest
from glypdl.models.download import DownloadItem, DownloadState, DownloadMode


class TestDownloadModel(unittest.TestCase):
    def test_download_item_properties(self):
        item = DownloadItem(
            url="https://example.com/test",
            title="Test Title",
            uploader="Test Uploader",
            duration=180,
            mode=DownloadMode.VIDEO_AUDIO,
            quality="1080p",
            cookies_from_browser="firefox:default-release"
        )

        self.assertEqual(item.url, "https://example.com/test")
        self.assertEqual(item.title, "Test Title")
        self.assertEqual(item.cookies_from_browser, "firefox:default-release")
        self.assertEqual(item.state, DownloadState.FETCHING_INFO)
        self.assertEqual(item.progress, 0.0)

        # Progress update
        item.update_progress(50.0, 500000, 1000000, 102400.0, 5)
        self.assertEqual(item.progress, 50.0)
        self.assertEqual(item.downloaded_bytes, 500000)
        self.assertEqual(item.total_bytes, 1000000)
        self.assertEqual(item.speed, 102400.0)
        self.assertEqual(item.eta, 5)

        # Completion
        item.mark_completed("/tmp/output.mp4")
        self.assertEqual(item.state, DownloadState.COMPLETED)
        self.assertEqual(item.output_path, "/tmp/output.mp4")
        self.assertEqual(item.progress, 100.0)

        # Serialization
        data = item.to_dict()
        self.assertEqual(data["id"], item.id)
        self.assertEqual(data["url"], item.url)
        self.assertEqual(data["title"], item.title)
        self.assertEqual(data["state"], "COMPLETED")
        self.assertEqual(data["output_path"], "/tmp/output.mp4")


if __name__ == '__main__':
    unittest.main()
