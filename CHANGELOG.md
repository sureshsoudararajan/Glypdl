# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.0.0] - 2026-08-30

### Added
- Initial release of **Glypdl**, native GTK4/libadwaita download manager frontend for `yt-dlp`.
- Distribution-independent application architecture.
- Real-time download progress, throughput rate metrics, and ETA computation.
- Video + Audio, Video Only, and Audio Only download modes.
- Format analysis and advanced stream details inspector.
- Local thumbnail fetching and caching in XDG cache directories.
- Concurrent download queue with configurable maximum simultaneous downloads.
- Full persistent history tracking backed by SQLite.
- Netscape `cookies.txt` authentication and profile management.
- Desktop notifications on download completion and failure.
- File manager integration for opening downloaded files and containing folders.
- Libadwaita dark, light, and system color scheme support.
- Packaging configurations for Debian/Ubuntu (.deb), Fedora/RHEL (.rpm), Arch Linux (PKGBUILD), Flatpak, and AppImage.
- GitHub Actions automated CI and multi-distribution release workflows.
