"""Tests for formatting and progress parsing utilities."""

import unittest
from glypdl.utils.formatting import (
    format_size,
    format_speed,
    format_duration,
    format_eta,
    parse_progress_line
)


class TestFormatting(unittest.TestCase):
    def test_format_size(self):
        self.assertEqual(format_size(0), "0 B")
        self.assertEqual(format_size(500), "500 B")
        self.assertEqual(format_size(1024), "1.00 KB")
        self.assertEqual(format_size(1024 * 1024), "1.00 MB")
        self.assertEqual(format_size(1536 * 1024 * 1024), "1.50 GB")
        self.assertEqual(format_size(1024 * 1024 * 1024 * 1024), "1.00 TB")

    def test_format_speed(self):
        self.assertEqual(format_speed(0), "0 B/s")
        self.assertEqual(format_speed(1024 * 1024 * 9.7), "9.70 MB/s")

    def test_format_duration(self):
        self.assertEqual(format_duration(0), "0:00")
        self.assertEqual(format_duration(45), "0:45")
        self.assertEqual(format_duration(765), "12:45")
        self.assertEqual(format_duration(3665), "1:01:05")
        # Float duration (from yt-dlp metadata)
        self.assertEqual(format_duration(214.0), "3:34")
        self.assertEqual(format_duration(45.67), "0:46")
        self.assertEqual(format_duration("125.4"), "2:05")
        self.assertEqual(format_duration(None), "0:00")

    def test_format_eta(self):
        self.assertEqual(format_eta(41), "41s")
        self.assertEqual(format_eta(133), "2m 13s")
        self.assertEqual(format_eta(3905), "1h 5m")
        # Float ETA
        self.assertEqual(format_eta(41.2), "41s")
        self.assertEqual(format_eta("133.8"), "2m 14s")
        self.assertEqual(format_eta(None), "0s")

    def test_parse_progress_line(self):
        # Standard yt-dlp download line
        line = "[download]  78.4% of 1.82GiB at  9.70MiB/s ETA 00:41"
        parsed = parse_progress_line(line)
        self.assertEqual(parsed["status"], "downloading")
        self.assertEqual(parsed["percent"], 78.4)
        self.assertEqual(parsed["speed"], "9.70MB/s")
        self.assertEqual(parsed["eta"], "00:41")
        self.assertGreater(parsed["total_bytes"], 0)
        self.assertGreater(parsed["downloaded_bytes"], 0)

        # Fragment line (e.g. HLS/DASH)
        frag_line = "[download] 100% of 15.00MiB at 3.50MiB/s (frag 10/50)"
        parsed_frag = parse_progress_line(frag_line)
        self.assertEqual(parsed_frag["status"], "downloading")
        self.assertEqual(parsed_frag["percent"], 100.0)
        self.assertEqual(parsed_frag["fragment_index"], 10)
        self.assertEqual(parsed_frag["fragment_count"], 50)
        self.assertEqual(parsed_frag["total_bytes"], 15 * 1024 * 1024)

        # Post processing lines
        merger_line = "[Merger] Merging formats into 'output.mp4'"
        self.assertEqual(parse_progress_line(merger_line)["status"], "merging")

        audio_line = "[ExtractAudio] Destination: output.mp3"
        self.assertEqual(parse_progress_line(audio_line)["status"], "extracting_audio")


if __name__ == '__main__':
    unittest.main()
