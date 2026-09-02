"""Unit tests for the Native Messaging Host protocol and security validation."""

import io
import json
import struct
import unittest
from unittest.mock import patch

from glypdl.services.native_host import (
    HOST_NAME,
    PROTOCOL_VERSION,
    MAX_MESSAGE_SIZE,
    read_message,
    write_message,
    process_host_message,
    get_manifest_content,
    get_target_manifest_paths,
)


class TestNativeHost(unittest.TestCase):
    """Test 32-bit length-prefixed I/O, security validation, and manifest generation."""

    def test_read_write_message_cycle(self):
        msg = {"protocolVersion": 1, "action": "ping", "test": "payload"}
        stream = io.BytesIO()
        write_message(msg, stream=stream)

        # Rewind and read
        stream.seek(0)
        read_back = read_message(stream=stream)
        self.assertEqual(read_back, msg)

    def test_read_oversized_message_rejected(self):
        # 4 bytes indicating 10MB message size
        huge_size = MAX_MESSAGE_SIZE + 1024
        stream = io.BytesIO(struct.pack("@I", huge_size) + b"X" * 100)
        with self.assertRaises(ValueError):
            read_message(stream=stream)

    def test_process_ping_message(self):
        resp = process_host_message({"action": "ping"})
        self.assertEqual(resp.get("protocolVersion"), PROTOCOL_VERSION)
        self.assertTrue(resp.get("success"))
        self.assertEqual(resp.get("action"), "ping")

    def test_process_invalid_url_download(self):
        resp = process_host_message({"action": "download", "url": "javascript:alert(1)"})
        self.assertFalse(resp.get("success"))
        self.assertIn("Invalid URL", resp.get("error", ""))

    def test_process_unsupported_action(self):
        resp = process_host_message({"action": "arbitrary_eval_not_supported"})
        self.assertFalse(resp.get("success"))
        self.assertIn("Unsupported action", resp.get("error", ""))

    def test_manifest_generation(self):
        manifest = get_manifest_content("/usr/bin/glypdl-host")
        self.assertEqual(manifest["name"], HOST_NAME)
        self.assertEqual(manifest["path"], "/usr/bin/glypdl-host")
        self.assertEqual(manifest["type"], "stdio")
        self.assertIn("glypdl@suresh.io", manifest["allowed_extensions"])

    def test_target_manifest_paths(self):
        paths = get_target_manifest_paths()
        self.assertTrue(len(paths) >= 3)
        self.assertTrue(any("mozilla" in str(p) for p in paths))
        self.assertTrue(any("librewolf" in str(p) for p in paths))


if __name__ == "__main__":
    unittest.main()
