"""IPC Server for Glypdl using Unix Domain Sockets.

Allows external companion processes (such as the Native Messaging Host for browser extensions)
to communicate directly with a running Glypdl instance.
"""

import json
import os
import socket
import threading
from pathlib import Path
from typing import Callable, Optional

import gi
gi.require_version('GLib', '2.0')
from gi.repository import GLib

from glypdl import __version__
from glypdl.utils.paths import get_ipc_socket_path


class IPCServer:
    """Non-blocking Unix domain socket IPC server for Glypdl."""

    def __init__(self, on_job_received: Optional[Callable[[dict], None]] = None):
        self.socket_path: Path = get_ipc_socket_path()
        self.on_job_received = on_job_received
        self._server_sock: Optional[socket.socket] = None
        self._running = False
        self._thread: Optional[threading.Thread] = None

    def start(self):
        """Start the IPC server in a background daemon thread."""
        if self._running:
            return

        self.socket_path.parent.mkdir(parents=True, exist_ok=True)
        if self.socket_path.exists():
            try:
                # Check if an existing socket is actively responding
                test_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
                test_sock.connect(str(self.socket_path))
                test_sock.close()
                # If connect succeeded, another instance is already running
                return
            except OSError:
                # Stale socket from previous run, remove it
                try:
                    self.socket_path.unlink(missing_ok=True)
                except Exception:
                    pass

        try:
            self._server_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            self._server_sock.bind(str(self.socket_path))
            self._server_sock.listen(5)
            self._server_sock.settimeout(1.0)
            self._running = True

            self._thread = threading.Thread(target=self._listen_loop, daemon=True)
            self._thread.start()
        except Exception as exc:
            self._running = False
            if self._server_sock:
                try:
                    self._server_sock.close()
                except Exception:
                    pass
                self._server_sock = None

    def stop(self):
        """Stop the IPC server and remove the socket file."""
        self._running = False
        if self._server_sock:
            try:
                self._server_sock.close()
            except Exception:
                pass
            self._server_sock = None

        if self.socket_path.exists():
            try:
                self.socket_path.unlink(missing_ok=True)
            except Exception:
                pass

    def is_running(self) -> bool:
        return self._running

    def _listen_loop(self):
        while self._running and self._server_sock:
            try:
                client_sock, _ = self._server_sock.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            except Exception:
                continue

            t = threading.Thread(target=self._handle_client, args=(client_sock,), daemon=True)
            t.start()

    def _handle_client(self, client_sock: socket.socket):
        try:
            client_sock.settimeout(5.0)
            data = b""
            while True:
                chunk = client_sock.recv(4096)
                if not chunk:
                    break
                data += chunk
                if b"\n" in data or len(data) > 1024 * 1024:
                    break

            if not data:
                return

            line = data.decode("utf-8").strip()
            if not line:
                return

            try:
                msg = json.loads(line)
            except Exception as e:
                resp = {"success": False, "error": f"Invalid JSON: {e}"}
                client_sock.sendall(json.dumps(resp).encode("utf-8") + b"\n")
                return

            resp = self._process_message(msg)
            client_sock.sendall(json.dumps(resp).encode("utf-8") + b"\n")
        except Exception as exc:
            try:
                resp = {"success": False, "error": str(exc)}
                client_sock.sendall(json.dumps(resp).encode("utf-8") + b"\n")
            except Exception:
                pass
        finally:
            try:
                client_sock.close()
            except Exception:
                pass

    def _process_message(self, msg: dict) -> dict:
        action = msg.get("action", "").lower()
        if action == "ping":
            return {
                "success": True,
                "action": "ping",
                "app": "Glypdl",
                "version": __version__,
                "status": "running"
            }
        elif action in ("get_status", "status"):
            return {
                "success": True,
                "app": "Glypdl",
                "version": __version__,
                "running": True
            }
        elif action in ("download", "download_job", "add_url"):
            url = msg.get("url") or (msg.get("source") or {}).get("url")
            if not url or not isinstance(url, str) or not url.startswith(("http://", "https://")):
                return {
                    "success": False,
                    "error": "Invalid or missing URL (must start with http:// or https://)"
                }

            if self.on_job_received:
                GLib.idle_add(self.on_job_received, msg)

            return {
                "success": True,
                "action": "download",
                "message": f"Download job for {url} queued successfully."
            }
        elif action == "download_batch":
            jobs = msg.get("jobs", [])
            if not isinstance(jobs, list) or not jobs:
                return {"success": False, "error": "No jobs provided in batch"}

            valid_count = 0
            for job in jobs:
                if isinstance(job, dict):
                    url = job.get("url") or (job.get("source") or {}).get("url")
                    if url and isinstance(url, str) and url.startswith(("http://", "https://")):
                        if self.on_job_received:
                            GLib.idle_add(self.on_job_received, job)
                        valid_count += 1

            return {
                "success": True,
                "action": "download_batch",
                "message": f"{valid_count} download jobs queued successfully."
            }
        else:
            return {
                "success": False,
                "error": f"Unknown action: {action}"
            }
