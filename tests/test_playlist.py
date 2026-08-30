"""Tests for YouTube Playlist Preview Card and selection handling."""

import unittest
from unittest.mock import MagicMock
from glypdl.widgets.playlist_card import PlaylistPreviewCard
from glypdl.models.download import DownloadMode


class TestPlaylistPreviewCard(unittest.TestCase):
    def setUp(self):
        self.sample_playlist = {
            '_type': 'playlist',
            'title': 'Test Playlist',
            'uploader': 'Test Channel',
            'playlist_count': 3,
            'entries': [
                {
                    'id': 'vid1',
                    'url': 'https://www.youtube.com/watch?v=vid1',
                    'title': 'Video One',
                    'duration': 120,
                    'uploader': 'Test Channel',
                    'thumbnail': 'https://example.com/thumb1.jpg',
                    'selected': True
                },
                {
                    'id': 'vid2',
                    'url': 'https://www.youtube.com/watch?v=vid2',
                    'title': 'Video Two',
                    'duration': 240,
                    'uploader': 'Test Channel',
                    'thumbnail': 'https://example.com/thumb2.jpg',
                    'selected': True
                },
                {
                    'id': 'vid3',
                    'url': 'https://www.youtube.com/watch?v=vid3',
                    'title': 'Video Three',
                    'duration': 360,
                    'uploader': 'Test Channel',
                    'thumbnail': 'https://example.com/thumb3.jpg',
                    'selected': True
                }
            ]
        }

    def test_playlist_card_initialization(self):
        card = PlaylistPreviewCard(playlist_data=self.sample_playlist)
        self.assertEqual(len(card.entries), 3)
        self.assertEqual(len(card._check_buttons), 3)
        self.assertIn("3 of 3", card.selection_count_label.get_text())
        self.assertTrue(card.download_btn.get_sensitive())

    def test_toggle_select_all(self):
        card = PlaylistPreviewCard(playlist_data=self.sample_playlist)
        
        # Click Deselect All
        card._on_toggle_all_clicked()
        self.assertEqual(sum(1 for e in card.entries if e.get('selected')), 0)
        self.assertIn("0 of 3", card.selection_count_label.get_text())
        self.assertFalse(card.download_btn.get_sensitive())

        # Click Select All again
        card._on_toggle_all_clicked()
        self.assertEqual(sum(1 for e in card.entries if e.get('selected')), 3)
        self.assertIn("3 of 3", card.selection_count_label.get_text())
        self.assertTrue(card.download_btn.get_sensitive())

    def test_download_signal_emission(self):
        card = PlaylistPreviewCard(playlist_data=self.sample_playlist)
        
        # Deselect second video
        card._check_buttons[1].set_active(False)
        self.assertFalse(card.entries[1]['selected'])

        received_items = []
        received_mode = None

        def _on_download(widget, items, mode, quality, audio_format):
            nonlocal received_items, received_mode
            received_items = items
            received_mode = mode

        card.connect('download-playlist-requested', _on_download)
        card._on_download_clicked()

        self.assertEqual(len(received_items), 2)
        self.assertEqual(received_items[0]['id'], 'vid1')
        self.assertEqual(received_items[1]['id'], 'vid3')


if __name__ == '__main__':
    unittest.main()
