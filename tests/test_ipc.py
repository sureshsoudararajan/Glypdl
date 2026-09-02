"""Unit tests for the IPC Server and socket messaging."""

import json
import socket
import time
import unittest
from unittest.mock import MagicMock
from pathlib import Path

from glypdl.services.ipc import IPCServer
from glypdl.utils.paths import get_ipc_socket_path


class TestIPCServer(unittest.TestCase):
    """Test IPC server lifecycle, message handling, and connection dispatch."""

    def setUp(self):
        self.received_jobs = []
        self.server = IPCServer(on_job_received=self._on_job)
        self.server.start()
        time.sleep(0.1)

    def tearDown(self):
        self.server.stop()
        time.sleep(0.1)

    def _on_job(self, job_dict):
        self.received_jobs.append(job_dict)

    def _send_ipc_message(self, message: dict) -> dict:
        sock_path = str(get_ipc_socket_path())
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(2.0)
        s.connect(sock_path)
        payload = json.dumps(message) + "\n"
        s.sendall(payload.encode("utf-8"))

        data = b""
        while True:
            chunk = s.recv(4096)
            if not chunk:
                break
            data += chunk
            if b"\n" in data:
                break
        s.close()
        return json.loads(data.decode("utf-8").strip())

    def test_ipc_server_running(self):
        self.assertTrue(self.server.is_running())
        self.assertTrue(get_ipc_socket_path().exists())

    def test_ping_action(self):
        resp = self._send_ipc_message({"action": "ping"})
        self.assertTrue(resp.get("success"))
        self.assertEqual(resp.get("app"), "Glypdl")
        self.assertEqual(resp.get("status"), "running")

    def test_get_status_action(self):
        resp = self._send_ipc_message({"action": "get_status"})
        self.assertTrue(resp.get("success"))
        self.assertTrue(resp.get("running"))

    def test_download_action_valid_url(self):
        url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        resp = self._send_ipc_message({"action": "download", "url": url, "title": "Test Video"})
        self.assertTrue(resp.get("success"))

    def test_download_action_invalid_url(self):
        resp = self._send_ipc_message({"action": "download", "url": "ftp://bad.scheme"})
        self.assertFalse(resp.get("success"))
        self.assertIn("Invalid", resp.get("error", ""))


if __name__ == "__main__":
    unittest.main()
