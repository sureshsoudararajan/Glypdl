# Glypdl Firefox Extension Architecture

## Overview

The Glypdl Firefox Extension acts as a lightweight browser frontend and detection bridge for the Glypdl desktop application.

```text
┌─────────────────────────────────────────────────────────────┐
│                       FIREFOX BROWSER                       │
│                                                             │
│  ┌───────────────────────┐       ┌───────────────────────┐  │
│  │   Content Scripts     │       │     Popup & Options   │  │
│  │  • DomMediaObserver   │       │  • Toolbar popup      │  │
│  │  • SpaObserver        │       │  • Preferences page   │  │
│  │  • PlayerOverlayButton│       │  • Diagnostics UI     │  │
│  │  • FloatingMediaPanel │       └───────────┬───────────┘  │
│  └───────────┬───────────┘                   │              │
│              │ (Runtime Messages)            │              │
│              ▼                               ▼              │
│  ┌───────────────────────────────────────────────────────┐  │
│  │                 Background Script                     │  │
│  │  • Tab Media Map (Deduplicated Media Store)           │  │
│  │  • ConnectionManager                                  │  │
│  │    ├─ NativeMessagingConnection                       │  │
│  │    └─ FlatpakPortalConnection                         │  │
│  │  • ContextMenus & Commands                            │  │
│  └───────────────────────────┬───────────────────────────┘  │
└──────────────────────────────┼──────────────────────────────┘
                               │ Native Messaging (Length-Prefixed JSON)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                 NATIVE MESSAGING HOST                       │
│  `bin/glypdl-host` / `src/glypdl/services/native_host.py`   │
│  • Reads/writes 32-bit length-prefixed JSON protocol        │
│  • Validates message schemas & URL safety                   │
│  • Connects to Glypdl Desktop Application                   │
└──────────────────────────────┬──────────────────────────────┘
                               │ Unix Domain Socket (`$XDG_RUNTIME_DIR/glypdl/ipc.sock`)
                               ▼
┌─────────────────────────────────────────────────────────────┐
│                  GLYPDL DESKTOP APPLICATION                 │
│  • `IPCServer`: Receives asynchronous download requests     │
│  • `DownloadManager`: Adds jobs to active queue & history   │
│  • `YtDlpService` / `CookieService`: Downloads & extracts   │
└─────────────────────────────────────────────────────────────┘
```

---

## Key Design Principles

1. **Authoritative Backend**:
   The extension does not attempt to merge or stitch video streams. For sites supported by `yt-dlp` (YouTube, Instagram, Vimeo, etc.), the extension passes the canonical webpage URL. Glypdl and `yt-dlp` determine the authoritative audio/video stream combinations.

2. **Deduplication and Resource Isolation**:
   `MediaDeduplicator` groups media requests by URL and type to avoid cluttering the UI with duplicate chunk requests.

3. **Loose Coupling**:
   The `GlypdlConnection` interface isolates browser communication specifics (`NativeMessagingConnection` vs `FlatpakPortalConnection`), ensuring the core detection and UI logic is completely decoupled from the transport protocol. This makes future Chromium support straightforward.

4. **Security & Validation**:
   Every message crossing the Native Messaging boundary is strictly validated against Protocol v1 schema limits (< 1 MB, valid HTTP/HTTPS schemes only, no shell interpolation).
