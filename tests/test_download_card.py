"""Tests for DownloadCard widget layout, sizing stability, and dynamic updates."""

import unittest
import gi

gi.require_version('Gtk', '4.0')
gi.require_version('Adw', '1')
from gi.repository import Gtk, Adw, Pango

from glypdl.models.download import DownloadItem, DownloadState, DownloadMode
from glypdl.widgets.download_card import DownloadCard


class TestDownloadCardLayout(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Initialize GTK application context if needed
        cls.app = Adw.Application(application_id="io.github.suresh.Glypdl.Tests")

    def setUp(self):
        self.item = DownloadItem(
            url="https://example.com/video",
            title="Stable Layout Test Video with a Very Long Title that Might Ellipsize",
            uploader="Test Channel",
            mode=DownloadMode.VIDEO_AUDIO,
            quality="1080p"
        )
        self.card = DownloadCard(download_item=self.item)

    def test_progress_bar_horizontal_expansion_and_alignment(self):
        """Verify the progress bar always requests full horizontal fill."""
        self.assertTrue(self.card.progress_bar.get_hexpand())
        self.assertEqual(self.card.progress_bar.get_halign(), Gtk.Align.FILL)
        self.assertEqual(self.card.progress_bar.get_valign(), Gtk.Align.CENTER)

    def test_card_and_container_horizontal_expansion(self):
        """Verify the entire card and its main containers expand horizontally."""
        self.assertTrue(self.card.get_hexpand())
        self.assertEqual(self.card.get_halign(), Gtk.Align.FILL)
        self.assertTrue(self.card.stats_box.get_hexpand())
        self.assertEqual(self.card.stats_box.get_halign(), Gtk.Align.FILL)

    def test_labels_do_not_determine_container_width(self):
        """Verify dynamic text labels are configured to never force container resizing."""
        # Labels have ellipsize and max_width_chars set so dynamic text never blows up requisition
        self.assertEqual(self.card.title_label.get_ellipsize(), Pango.EllipsizeMode.END)
        self.assertEqual(self.card.info_sublabel.get_ellipsize(), Pango.EllipsizeMode.END)
        self.assertEqual(self.card.progress_text.get_ellipsize(), Pango.EllipsizeMode.END)
        self.assertEqual(self.card.speed_eta_text.get_ellipsize(), Pango.EllipsizeMode.END)
        self.assertEqual(self.card.status_label.get_ellipsize(), Pango.EllipsizeMode.END)

        self.assertFalse(self.card.progress_text.get_hexpand())
        self.assertTrue(self.card.speed_eta_text.get_hexpand())
        self.assertEqual(self.card.speed_eta_text.get_halign(), Gtk.Align.END)

    def test_progress_updates_under_various_network_conditions(self):
        """Verify progress fraction updates accurately under extreme speed/ETA fluctuations."""
        self.item.state = DownloadState.DOWNLOADING

        # 1. 0% download with zero speed / no ETA
        self.item.update_progress(0.0, 0, 1073741824, 0.0, 0)
        self.assertAlmostEqual(self.card.progress_bar.get_fraction(), 0.0)
        self.assertEqual(self.card.progress_text.get_text(), "1.00 GB (0.0%)")
        self.assertEqual(self.card.speed_eta_text.get_text(), "")

        # 2. Very slow network (50 B/s, ETA 12h 45m)
        self.item.update_progress(10.0, 107374182, 1073741824, 50.0, 45900)
        self.assertAlmostEqual(self.card.progress_bar.get_fraction(), 0.10)
        self.assertEqual(self.card.progress_text.get_text(), "102.40 MB / 1.00 GB (10.0%)")
        self.assertIn("50 B/s", self.card.speed_eta_text.get_text())
        self.assertIn("ETA", self.card.speed_eta_text.get_text())

        # 3. Very fast network / rapid speed burst (150 MB/s, ETA 5s)
        self.item.update_progress(50.0, 536870912, 1073741824, 157286400.0, 5)
        self.assertAlmostEqual(self.card.progress_bar.get_fraction(), 0.50)
        self.assertEqual(self.card.progress_text.get_text(), "512.00 MB / 1.00 GB (50.0%)")
        self.assertIn("150.00 MB/s", self.card.speed_eta_text.get_text())
        self.assertIn("5s", self.card.speed_eta_text.get_text())

        # 4. 99% download with tiny ETA
        self.item.update_progress(99.5, 1068372787, 1073741824, 83886080.0, 1)
        self.assertAlmostEqual(self.card.progress_bar.get_fraction(), 0.995)
        self.assertEqual(self.card.progress_text.get_text(), "1018.88 MB / 1.00 GB (99.5%)")

        # 5. 100% completed
        self.item.mark_completed("/path/to/downloaded_video.mp4")
        self.assertAlmostEqual(self.card.progress_bar.get_fraction(), 1.0)
        self.assertFalse(self.card.progress_bar.get_visible())
        self.assertTrue(self.card.open_file_btn.get_visible())
        self.assertTrue(self.card.open_folder_btn.get_visible())

    def test_multiple_simultaneous_downloads_stability(self):
        """Verify multiple cards maintain independent and stable configurations."""
        item1 = DownloadItem(url="https://example.com/1", title="Download 1")
        item2 = DownloadItem(url="https://example.com/2", title="Download 2")
        item3 = DownloadItem(url="https://example.com/3", title="Download 3")

        card1 = DownloadCard(download_item=item1)
        card2 = DownloadCard(download_item=item2)
        card3 = DownloadCard(download_item=item3)

        item1.state = DownloadState.DOWNLOADING
        item2.state = DownloadState.DOWNLOADING
        item3.state = DownloadState.DOWNLOADING

        item1.update_progress(25.0, 250, 1000, 500.0, 2)
        item2.update_progress(75.0, 750, 1000, 15000000.0, 1)
        item3.update_progress(0.0, 0, 1000, 0.0, 0)

        self.assertAlmostEqual(card1.progress_bar.get_fraction(), 0.25)
        self.assertAlmostEqual(card2.progress_bar.get_fraction(), 0.75)
        self.assertAlmostEqual(card3.progress_bar.get_fraction(), 0.0)

        self.assertTrue(card1.progress_bar.get_hexpand())
        self.assertTrue(card2.progress_bar.get_hexpand())
        self.assertTrue(card3.progress_bar.get_hexpand())


if __name__ == '__main__':
    unittest.main()
