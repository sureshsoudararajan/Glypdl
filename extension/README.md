# Glypdl Firefox Extension

The **Glypdl Firefox Extension** is a native companion for **Glypdl**, the lightweight desktop media downloader powered by `yt-dlp` and `ffmpeg`.

It provides an **IDM-style media detection experience** that detects video & audio streams directly on the web and sends download requests seamlessly to your Glypdl desktop download queue.

---

## 🌟 Key Features

* **⚡ Zero-Friction Media Detection**:
  * Automatically detects HTML5 `<video>`, `<audio>`, `<source>` elements.
  * Specialized YouTube detection passing canonical video URLs to `yt-dlp` for authoritative format determination.
  * Detects HLS (`.m3u8`) and DASH (`.mpd`) video manifests.
  * Detects direct media links (`.mp4`, `.webm`, `.mp3`, `.m4a`, `.flac`, `.wav`, `.ogg`, `.opus`).
* **🎬 IDM-Style In-Page UI**:
  * **Video Player Overlay Button**: Non-intrusive `[↓ Download]` button appearing over HTML5 video players.
  * **Floating Notification Badge**: Modern corner notification badge displaying thumbnail, resolution, duration, and download action.
  * **Toolbar Popup**: Overview of all detected streams on the active tab with individual and batch `[Download All]` triggers.
* **🔒 Privacy & Local Security**:
  * **100% Local**: No tracking, no analytics, no external servers.
  * **Zero Cookie Transmission**: The extension **never** extracts or sends browser cookies. Authenticated media is handled by Glypdl's local cookie engine.
  * **No DRM Circumvention**: Protected streams (Widevine/EME) are flagged as `🔒 DRM Protected` without attempting decryption.
* **🐧 Native & Flatpak Firefox Support**:
  * Full Native Messaging integration for standard Firefox and LibreWolf.
  * Sandboxed Flatpak Firefox support with clear setup guides.

---

## 🚀 Installation & Setup

### 1. Register the Native Messaging Host (One-Time Setup)

Make sure the Glypdl desktop app is installed, then:
1. Launch **Glypdl**.
2. Open **Preferences** (`Ctrl+,`) $\rightarrow$ **Extension** tab.
3. Click **`[Register Host]`**.

This registers `io.github.sureshsoudararajan.glypdl` for standard Firefox, LibreWolf, and Flatpak Firefox.

### 2. Load the Extension in Firefox

#### For Temporary / Development Testing:
1. Open Firefox and navigate to `about:debugging#/runtime/this-firefox`.
2. Click **Load Temporary Add-on…**.
3. Select `extension/manifest.json` or `extension/glypdl-firefox-extension.xpi`.

#### For Permanent Installation:
1. Install via `about:addons` using the built `.xpi` file or Mozilla Add-ons store.

---

## ⌨️ Shortcuts & Context Menus

* **Keyboard Shortcut**: <kbd>Alt</kbd> + <kbd>Shift</kbd> + <kbd>D</kbd> &mdash; Send detected media on active tab to Glypdl.
* **Context Menus**:
  * Right-click any link: **Download link with Glypdl**.
  * Right-click any video or audio player: **Download media with Glypdl**.
  * Right-click any page: **Download page with Glypdl**.

---

## 🧪 Building & Testing

```bash
cd extension
npm install

# Run automated tests (23 tests)
npm test

# Build production XPI package
npm run build
```

The output package is created at `extension/glypdl-firefox-extension.xpi`.
