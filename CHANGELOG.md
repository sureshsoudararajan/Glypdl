# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-09-01

### Added
- **Native GTK4 & Libadwaita Desktop Interface**:
  - Full Adwaita styling, dark/light/system theme support, responsive card layouts.
  - Video + Audio, Video Only, and Audio Only download modes with advanced stream inspector.
  - Real-time download progress metrics, throughput rates, and ETA computation.
  - Persistent SQLite download history with playback and folder management.
- **Direct Browser Cookie Extraction (`--cookies-from-browser`)**:
  - Native integration with yt-dlp's `--cookies-from-browser` engine.
  - Auto-discovery for installed desktop browsers: Google Chrome, Chromium, Mozilla Firefox, LibreWolf, Brave Browser, Microsoft Edge, Opera, Vivaldi, and Naver Whale.
  - Multi-profile scanning (e.g. `Default`, `Profile 1`, `default-release`, `default-default`).
  - Keyring selection support: GNOME Keyring, KWallet, Basic text, and Auto.
  - In-app connection verification test in Preferences with live diagnostic feedback.
  - Flatpak sandbox detection with scoped permissions notice.
  - LibreWolf support resolving nested configuration directories (`~/.config/librewolf/librewolf/`).
- **Interactive Authentication & Retry Dialog**:
  - Auto-prompts with installed browser dropdown and saved cookie profiles when authentication is required.
  - Custom browser/profile picker modal to authenticate on the fly.
- **Authentication Selector in Preview Cards**:
  - Displays the active browser and profile (e.g. `🌐 LibreWolf (default-default)`) directly in the preview card before starting download.
- **YouTube & Instagram Multi-Item / Story Support**:
  - Full multi-video playlist selector with thumbnail previews and individual selection checkboxes.
  - Automated URL normalization for Instagram stories, TikTok, Reddit, and Twitter/X collections.
  - Graceful notifications when stories have expired or when accounts have 0 active stories.
- **Windows 11 Native Client**:
  - Native WinUI 3 / Windows App SDK desktop client with Mica styling and Inno Setup installer.

### Fixed
- **Download Progress Bar Layout Stability**:
  - Fixed GTK4 layout bug where fluctuating speed and ETA text caused progress bars to narrow or resize erratically.
- **Instagram Story Entry Resolution**:
  - Fixed `ERROR: [generic] '' is not a valid URL` by reconstructing individual story URLs when yt-dlp omits entry URLs.
- **Duration Formatting**:
  - Fixed `ValueError: Unknown format code 'd' for object of type 'float'` by robustly handling float and string durations.
- **UI Label Formatting**:
  - Cleaned up dropdown labels so long filesystem paths are never displayed in the UI, preventing row squishing and vertical text wrapping.
