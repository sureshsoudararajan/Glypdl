# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.0] - 2026-09-05

### Added

- **Native Android Application (`Glypdl-1.2.0.apk`)**:
  - Built from the ground up using **Kotlin**, **Jetpack Compose**, **Material 3**, **Hilt (Dependency Injection)**, and **Room Database**.
  - Powered by embedded **`youtubedl-android`** with bundled Python runtime, **yt-dlp**, **FFmpeg**, and **aria2c**.
  - **Comprehensive Site Support**: Download video and audio streams from YouTube, Instagram, Facebook, TikTok, and any platform supported by yt-dlp.
  - **In-App Authenticated Browser**: Built-in interactive WebKit browser session manager that captures and securely stores Netscape-format cookies for Instagram, Facebook, and arbitrary login-walled websites.
  - **Multi-Quality & Stream Formats**: Format resolution selection up to 4K / 1080p, audio extraction (MP3 / M4A), and FFmpeg-backed video/audio multiplexing.
  - **Robust Background Service**: Foreground service (`DownloadForegroundService`) with ongoing progress notifications, speed metrics, ETA, and persistent completion/failure notifications that stay in the notification shade until dismissed.
  - **Queue & Multi-Download Management**: Concurrent downloads (configurable up to 10), pause, resume, cancel, and automatic retry capabilities.
  - **Search & Download History**: SQLite-backed history tracking completed downloads, file sizes, timestamps, and thumbnail metadata.
  - **Android OS Integration**: System "Share via Glypdl" and "Open with" intent handlers, clipboard auto-detection banner, and scoped storage / SAF download folder selection.
  - **Theming**: Full support for Material 3 Dynamic Colors (Android 12+), Dark mode, and Light mode.

### Fixed

- **History Search Input & IME Composition**: Decoupled immediate search field state from asynchronous Room database queries with a 150ms query debounce, eliminating dropped keystrokes and text scrambling when typing on soft keyboards.
- **Download Completed Notifications**: Fixed notification auto-dismissal on download completion by decoupling completion/failure notification IDs from the terminating foreground service lifecycle.
- **CI/CD & Automated Release Workflow**: Added `build-android` automated APK compilation to `.github/workflows/release.yml` to automatically build and attach `Glypdl-1.2.0.apk` alongside Linux and Windows release assets.

## [1.1.0] - 2026-09-04

> **Notice**: The Firefox & LibreWolf Companion Extension currently operates exclusively on **Linux** (communicating with the native desktop application via Native Messaging and Unix domain sockets).

### Added

- **Firefox & LibreWolf Companion Extension (`glypdl-firefox-extension.xpi`)**:
  - Automatically detects media streams across the web including HTML5 `<video>` / `<audio>`, HLS (`.m3u8`), DASH (`.mpd`), YouTube, Instagram, and TikTok.
  - Interactive browser popup showing real-time thumbnail preview, title, quality/format badge, and stream duration.
  - Dual action triggers:
    - **Download**: Directly queues the URL into Glypdl's native download flow.
    - **🍪 Using Cookie**: One-click active session cookie extraction sent securely with the download request for authenticated media.
  - Native Messaging Host (`io.github.sureshsoudararajan.glypdl`) bridging the browser extension with Glypdl desktop over Unix domain sockets.
  - Zero permanent cookie disk storage: Session cookies sent via the extension are handled strictly in-memory as ephemeral temporary files that automatically self-delete upon completion or dismissal.

### Improved & Fixed

- **Dynamic First-Party Isolation (dFPI) / Total Cookie Protection Support**:
  - Full compatibility with LibreWolf, hardened Firefox profiles, and Multi-Account Containers by querying both partitioned (`partitionKey: {}`) and unpartitioned cookie storage.
  - Multi-store discovery with client-side domain matching fallback ensuring login cookies (`sessionid`, `ds_user_id`, `csrftoken`) are captured reliably.
- **Flatpak Integration**:
  - Added multi-path IPC discovery probing standard runtime directories, Flatpak sandbox paths (`/run/user/1000/app/...`), and user configuration directories.
  - Added `--filesystem=xdg-run/glypdl:create` permission to Flatpak manifests for direct IPC sharing.
  - Added intelligent multi-runtime auto-launcher supporting native `$PATH` binaries, Flatpak packages (`flatpak run io.github.sureshsoudararajan.Glypdl`), local development builds, and `gtk-launch`.
- **Closed Application State Launching**:
  - Fixed an issue where downloading with cookies while Glypdl was closed caused yt-dlp to fail with an unauthenticated fetch error. Glypdl is now launched cleanly without bare CLI URL arguments, polling for IPC readiness and delivering the full cookie payload over IPC.
- **Instagram Stories & Generic Title Handling**:
  - Enforced `--no-playlist` for story downloads so yt-dlp only downloads the targeted individual story instead of the entire user album from story #1.
  - Automatic `[%(id)s]` title deduplication preventing filename collisions when downloading multiple stories or reels from the same user.
- **Single Page Application (SPA) Live Navigation**:
  - Dynamic content script updates when browsing through consecutive Instagram stories or YouTube videos without requiring a manual page refresh.

## [1.0.0] - 2026-09-02

### Added

- **Native Windows 11 Desktop Client**:
  - Built with **WinUI 3**, **C#**, and **.NET 8** utilizing the **Windows App SDK**.
  - Translucent **Mica material backdrop** and automatic Windows Dark / Light system theming.
  - Custom frameless title bar with theme-accented caption controls (`[-]`, `[□]`, `[✕]`).
  - Persistent window state and geometry preservation across launches.
  - Standalone Inno Setup installer (`Glypdl-1.0.0-Setup-x64.exe`) with clean install, update, and complete uninstaller support.

- **Native Linux Desktop Client**:
  - Built with **GTK4**, **libadwaita**, and **Python 3.10+** conforming to GNOME Human Interface Guidelines (HIG).
  - Pure native desktop performance with near-instant launch times and minimal memory footprint.
  - Full desktop theming support across GNOME, KDE Plasma, Cinnamon, XFCE, COSMIC, Hyprland, Sway, and i3.
  - Multi-distribution package distribution: Universal AppImage, Flatpak, Debian/Ubuntu (`.deb`), Fedora/RHEL (`.rpm`), and Arch Linux (`.pkg.tar.zst`).

- **Direct Browser Cookie Authentication (`--cookies-from-browser`)**:
  - Built-in integration with `yt-dlp`'s `--cookies-from-browser` engine.
  - Automatic detection of installed desktop browsers: Microsoft Edge, Google Chrome, Mozilla Firefox, LibreWolf, Brave Browser, Chromium, Opera, and Vivaldi.
  - Multi-profile scanning (e.g., `Default`, `Profile 1`, `default-release`, `default-default`).
  - Keyring configuration support: Windows DPAPI, GNOME Keyring, KWallet, and Basic text.
  - Per-download authentication dropdown on the format selector to choose browser or cookie file on the fly.
  - In-app browser cookie verification test with live diagnostic feedback in Settings.

- **Advanced Windows Enterprise Policy Guide for Chromium**:
  - Dynamic registry command generator for Chromium browsers (Edge, Chrome, Brave, Chromium, Vivaldi, Opera) with a 1-click copy button.
  - Step-by-step technical guidance on clearing previous cookies, re-logging into accounts under standard DPAPI protection, and ending background browser processes in Task Manager.
  - 1-click restore command to re-enable strict App-Bound Encryption at any time.

- **Netscape Cookie Profile Manager (`--cookies`)**:
  - Import and store multiple named Netscape `cookies.txt` and `.cookies` profiles (e.g., *YouTube*, *Instagram*, *Patreon*, *Vimeo*).
  - One-click active profile selection, rename, delete, and validation status badges.
  - Interactive Authentication Recovery modal when private videos, login walls, or bot checkpoints are encountered.

- **Automated Engine Provisioning with Visual Progress**:
  - Automatic first-run provisioning of `yt-dlp`, `ffmpeg`, and `ffprobe` binaries in user-isolated directories.
  - Real-time startup download dialog showing live percentage and component-level progress.
  - In-app engine updater and component version inspector in Settings and About views.

- **Smart URL Input & Metadata Preview Card**:
  - Clipboard auto-detection (`Ctrl+V`) for instant metadata fetching.
  - Rich metadata card displaying high-resolution thumbnails, title, uploader/channel, duration badge, source platform, and previous download history reminders.
  - Static content detector identifying photo-only posts (e.g., Instagram static photos) with clear video/audio format guidance.

- **Visual Playlist Downloader**:
  - Interactive multi-track playlist view displaying 16:9 thumbnails, track numbers, titles, artists, and durations.
  - Batch selection controls: **Select All (`✓`)**, **Deselect All (`✗`)**, and individual track checkboxes.
  - Real-time selection counter (e.g., *14 of 20 items selected*) and dynamic batch download button.

- **Multi-Format Audio & Video Extraction**:
  - **Video + Audio Mode**: Quality selections from 144p up to 4K (2160p) with automatic stream merging via FFmpeg.
  - **Video Only Mode**: Stream extraction without audio tracks.
  - **Audio Only Mode**: Audio extraction with direct conversion into **MP3**, **AAC**, **M4A**, **FLAC**, **WAV**, or **OPUS**.
  - Multiple audio bitrate profiles: `320 kbps (Best)`, `256 kbps (High)`, `192 kbps (Medium)`, `128 kbps (Standard)`, `96 kbps (Low)`.
  - Safe overwrite protection (`--no-overwrites`).

- **Real-Time Download Queue & Explorer Integration**:
  - Multi-threaded download queue with live progress bar, transfer speed (MB/s), ETA countdown, and file sizes.
  - Direct media player launch (`▶`) to play downloaded media immediately in the default media player.
  - Open Containing Folder action (`📁`) that highlights and selects the exact file on disk.
  - Accurate on-disk file size measurement post-conversion.

- **Desktop Toast & System Notifications**:
  - Native toast notifications with audio chimes on Windows and desktop notifications on Linux upon download completion or failure.

- **Persistent SQLite Download History**:
  - Local SQLite database recording download history, timestamps, formats, URLs, and thumbnails.
  - Search-as-you-type filtering by title or URL.
  - Re-download picker to download previously saved media in different formats or qualities.
