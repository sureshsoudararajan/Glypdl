"""Native Messaging Host implementation for Firefox and Chromium WebExtensions.

Handles the 32-bit length-prefixed JSON protocol over stdin/stdout and bridges requests
to the running Glypdl desktop application via IPC Unix Domain Sockets.
"""

import json
import os
import socket
import struct
import subprocess
import sys
from pathlib import Path
from typing import Any, Dict, List, Optional

from glypdl import __version__
from glypdl.utils.paths import get_ipc_socket_path

HOST_NAME = "io.github.sureshsoudararajan.glypdl"
PROTOCOL_VERSION = 1
MAX_MESSAGE_SIZE = 1024 * 1024  # 1 MB


def read_message(stream=sys.stdin.buffer) -> Optional[Dict[str, Any]]:
    """Read a 32-bit length-prefixed JSON message from the input stream."""
    raw_length = stream.read(4)
    if len(raw_length) < 4:
        return None
    message_length = struct.unpack("@I", raw_length)[0]
    if message_length > MAX_MESSAGE_SIZE:
        raise ValueError(f"Message exceeds maximum size limit ({message_length} > {MAX_MESSAGE_SIZE})")

    raw_message = stream.read(message_length)
    if len(raw_message) < message_length:
        return None

    return json.loads(raw_message.decode("utf-8"))


def write_message(message: Dict[str, Any], stream=sys.stdout.buffer) -> None:
    """Write a 32-bit length-prefixed JSON message to the output stream."""
    encoded = json.dumps(message, ensure_ascii=False).encode("utf-8")
    stream.write(struct.pack("@I", len(encoded)))
    stream.write(encoded)
    stream.flush()


def forward_to_glypdl_ipc(message: Dict[str, Any], timeout: float = 3.0) -> Dict[str, Any]:
    """Forward a protocol message to the Glypdl desktop application via IPC socket."""
    sock_path = get_ipc_socket_path()
    if not sock_path.exists():
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "success": False,
            "connected": False,
            "error": "Glypdl desktop application is not currently running."
        }

    try:
        s = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        s.settimeout(timeout)
        s.connect(str(sock_path))
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
        if not data:
            return {
                "protocolVersion": PROTOCOL_VERSION,
                "success": False,
                "error": "Empty response received from Glypdl IPC."
            }

        response = json.loads(data.decode("utf-8").strip())
        response["protocolVersion"] = PROTOCOL_VERSION
        response["connected"] = True
        return response
    except Exception as exc:
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "success": False,
            "connected": False,
            "error": f"Failed to connect to Glypdl IPC: {exc}"
        }


def process_host_message(msg: Dict[str, Any]) -> Dict[str, Any]:
    """Validate and process a message received from the browser extension."""
    if not isinstance(msg, dict):
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "success": False,
            "error": "Invalid request: Message must be a JSON object."
        }

    action = str(msg.get("action", "")).lower()

    if action == "ping":
        ipc_resp = forward_to_glypdl_ipc({"action": "ping"})
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "success": True,
            "action": "ping",
            "host": "glypdl-native-host",
            "hostVersion": __version__,
            "glypdlRunning": ipc_resp.get("connected", False),
            "glypdlVersion": ipc_resp.get("version", __version__)
        }

    elif action in ("get_status", "status"):
        ipc_resp = forward_to_glypdl_ipc({"action": "get_status"})
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "success": True,
            "action": "get_status",
            "glypdlConnected": ipc_resp.get("connected", False),
            "version": __version__,
            "details": ipc_resp
        }

    elif action in ("download", "download_job"):
        url = msg.get("url") or (msg.get("source") or {}).get("url")
        if not url or not isinstance(url, str) or not url.startswith(("http://", "https://")):
            return {
                "protocolVersion": PROTOCOL_VERSION,
                "success": False,
                "error": "Invalid URL. Only http:// and https:// URLs are supported."
            }

        ipc_resp = forward_to_glypdl_ipc(msg)
        if not ipc_resp.get("connected", False):
            # If Glypdl is not running, attempt to launch Glypdl and re-try
            try:
                subprocess.Popen(["glypdl", url], start_new_session=True)
                return {
                    "protocolVersion": PROTOCOL_VERSION,
                    "success": True,
                    "message": "Glypdl was launched with the requested download."
                }
            except Exception as e:
                return {
                    "protocolVersion": PROTOCOL_VERSION,
                    "success": False,
                    "error": f"Glypdl is not running and could not be auto-launched: {e}"
                }
        return ipc_resp

    elif action == "download_batch":
        jobs = msg.get("jobs", [])
        if not isinstance(jobs, list) or not jobs:
            return {
                "protocolVersion": PROTOCOL_VERSION,
                "success": False,
                "error": "No jobs provided in batch."
            }

        ipc_resp = forward_to_glypdl_ipc(msg)
        return ipc_resp

    else:
        return {
            "protocolVersion": PROTOCOL_VERSION,
            "success": False,
            "error": f"Unsupported action: '{action}'."
        }


def run_native_host_loop():
    """Main loop for the Native Messaging Host process."""
    try:
        while True:
            msg = read_message()
            if msg is None:
                break
            response = process_host_message(msg)
            write_message(response)
    except (KeyboardInterrupt, BrokenPipeError):
        pass
    except Exception as exc:
        try:
            write_message({
                "protocolVersion": PROTOCOL_VERSION,
                "success": False,
                "error": f"Host process error: {exc}"
            })
        except Exception:
            pass


def get_manifest_content(host_binary_path: Optional[str] = None) -> Dict[str, Any]:
    """Generate the Native Messaging Host manifest for Firefox."""
    if not host_binary_path:
        import shutil
        candidates = [
            # 1. Project source layout: bin/glypdl-host next to src/
            str(Path(__file__).resolve().parents[3] / "bin" / "glypdl-host"),
            # 2. Installed via package manager (PKGBUILD / pip)
            str(Path(sys.prefix) / "bin" / "glypdl-host"),
            # 3. System PATH lookup
            shutil.which("glypdl-host") or "",
            # 4. Common system paths
            "/usr/bin/glypdl-host",
            "/usr/local/bin/glypdl-host",
        ]
        host_binary_path = "/usr/bin/glypdl-host"  # final fallback
        for c in candidates:
            if c and os.path.isfile(c) and os.access(c, os.X_OK):
                host_binary_path = str(Path(c).resolve())
                break

    return {
        "name": HOST_NAME,
        "description": "Glypdl Native Messaging Host for Firefox and LibreWolf",
        "path": host_binary_path,
        "type": "stdio",
        "allowed_extensions": [
            "glypdl@suresh.io"
        ]
    }


def get_target_manifest_paths() -> List[Path]:
    """Return all target directory paths where Firefox, LibreWolf, and Flatpak Firefox look for manifests."""
    home = Path.home()
    paths = [
        # Standard Firefox
        home / ".mozilla" / "native-messaging-hosts" / f"{HOST_NAME}.json",
        # Flatpak Firefox
        home / ".var" / "app" / "org.mozilla.firefox" / ".mozilla" / "native-messaging-hosts" / f"{HOST_NAME}.json",
        # LibreWolf Standard & Dot-LibreWolf
        home / ".config" / "librewolf" / "native-messaging-hosts" / f"{HOST_NAME}.json",
        home / ".librewolf" / "native-messaging-hosts" / f"{HOST_NAME}.json",
        # Flatpak LibreWolf
        home / ".var" / "app" / "io.gitlab.librewolf-community" / ".librewolf" / "native-messaging-hosts" / f"{HOST_NAME}.json",
    ]
    return paths


def install_manifests(host_binary_path: Optional[str] = None) -> List[str]:
    """Install the Native Messaging Manifest into all discovered browser host directories."""
    manifest = get_manifest_content(host_binary_path)
    installed_to = []

    for target in get_target_manifest_paths():
        try:
            target.parent.mkdir(parents=True, exist_ok=True)
            with open(target, "w", encoding="utf-8") as f:
                json.dump(manifest, f, indent=2)
            installed_to.append(str(target))
        except Exception:
            pass

    return installed_to
