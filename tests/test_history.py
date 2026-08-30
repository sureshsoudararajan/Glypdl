"""Tests for HistoryService SQLite operations."""

import unittest
import tempfile
import shutil
from pathlib import Path
from glypdl.services.history import HistoryService
from glypdl.models.download import DownloadItem, DownloadState, DownloadMode


class TestHistoryService(unittest.TestCase):
    def setUp(self):
        self.test_dir = tempfile.mkdtemp()

    def tearDown(self):
        shutil.rmtree(self.test_dir, ignore_errors=True)

    def test_history_crud(self):
        db_file = Path(self.test_dir) / "test_history.db"
        service = HistoryService(db_path=str(db_file))

        # Add item
        item = DownloadItem(
            url="https://example.com/video1",
            title="Sample Video 1",
            uploader="Uploader 1",
            duration=120,
            mode=DownloadMode.VIDEO_AUDIO,
            quality="1080p",
            download_path="/tmp/video1.mp4",
            total_bytes=1024000
        )
        item.state = DownloadState.COMPLETED
        service.add_entry(item)

        # Get all
        entries = service.get_all()
        self.assertEqual(len(entries), 1)
        self.assertEqual(entries[0]["title"], "Sample Video 1")
        self.assertEqual(entries[0]["url"], "https://example.com/video1")

        # Search
        search_res = service.search("Sample")
        self.assertEqual(len(search_res), 1)
        search_empty = service.search("Nonexistent")
        self.assertEqual(len(search_empty), 0)

        # Remove entry
        service.remove_entry(entries[0]["id"])
        self.assertEqual(len(service.get_all()), 0)

        # Clear all
        service.add_entry(item)
        self.assertEqual(len(service.get_all()), 1)
        service.clear_all()
        self.assertEqual(len(service.get_all()), 0)


if __name__ == '__main__':
    unittest.main()
