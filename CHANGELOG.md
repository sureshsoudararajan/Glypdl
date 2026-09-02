# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

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
