# Glypdl

<div align="center">
  <img src="data/icons/hicolor/scalable/apps/io.github.suresh.Glypdl.svg" width="128" height="128" alt="Glypdl Logo" />
  <h3>A lightweight, native Linux desktop download manager for yt-dlp</h3>
  <p>Built with <b>GTK4</b> and <b>libadwaita</b> conforming to modern GNOME Human Interface Guidelines.</p>
</div>

---

## Features

* **Native GTK4 & Libadwaita Interface**:
  * Ultra-fast startup and minimal RAM usage — zero Electron, Chromium, or web wrappers.
  * Seamless theme integration supporting **System**, **Light**, and **Dark** color schemes.
  * Cross-desktop compatibility: Runs natively on **GNOME**, **KDE Plasma**, **Cinnamon**, **XFCE**, **MATE**, **COSMIC**, and tiling window managers (**Hyprland**, **i3**, **sway**).

* **Flexible Download Modes**:
  * **Video + Audio**: Automatically downloads and merges high-quality video and audio streams using `ffmpeg`.
  * **Video Only**: Download video streams without audio across all standard resolutions (**4K 2160p**, **1440p**, **1080p**, **720p**, **480p**, **360p**, **240p**, **144p**).
  * **Audio Only**: Extract and convert audio directly to **MP3**, **M4A**, **Opus**, **FLAC**, or **WAV** with maximum audio quality.

* **Interactive YouTube Playlist Support**:
  * Automatically detects playlists and fetches video titles, thumbnails, durations, and channels in a scrollable preview card.
  * Individual per-video selection checkboxes to download only chosen videos.
  * One-click **Select All** / **Deselect All** toggle with a live selection counter (*e.g., 8 of 12 selected*).
  * Batch format selector applying quality settings across all selected playlist items.

* **Real-Time Network & Progress Metrics**:
  * Accurate live download speed (*e.g., 9.7 MB/s*), estimated time remaining (ETA), downloaded bytes, and total file size.
  * Visual progress bar with status badges: `Queued`, `Downloading`, `Processing`, `Merging`, `Completed`, `Failed`, and `Cancelled`.
  * Non-blocking multithreaded architecture keeping the GTK UI smooth and responsive at all times.

* **Queue & Concurrency Management**:
  * Configurable simultaneous downloads (1 to 10 concurrent items) with automatic queue processing.
  * Individual item controls: **Pause**, **Resume**, **Cancel**, and **Retry on Failure**.

* **Persistent SQLite History & File Size Tracking**:
  * Local SQLite database recording every download, timestamp, media format, URL, cached thumbnail, and exact disk file size.
  * Instant search filtering by video title or URL.
  * Context actions: **Open File**, **Open Containing Folder**, **Download Again**, **Copy URL**, and **Delete**.
  * One-click **Clear All History** with confirmation.

* **Cookie Profiles & Authentication Handling**:
  * **Saved Cookie Profiles**: Save and name multiple Netscape `cookies.txt` files (*e.g., YouTube, Patreon, Vimeo*) in Preferences with one-click **Use Profile** switching and active status badges.
  * **Interactive Authentication Recovery**: If a private, age-restricted, or bot-protected link fails, Glypdl automatically displays an **Authentication / Cookies Required** dialog allowing immediate cookie profile selection and retry.
  * **Automatic Pre-Selection**: When media metadata is fetched using a cookie profile, that exact profile is automatically pre-selected in the format selector.

* **Desktop Integration**:
  * Desktop notifications on download completion or failure.
  * Native file & directory selection via `xdg-desktop-portal` (opens native Dolphin on KDE, Nautilus on GNOME, Nemo on Cinnamon).
  * Standard desktop menu launcher with scalable vector SVG and 512×512 application icons.

---

## Dependencies & Requirements

* **Python**: `>= 3.10`
* **GTK**: `GTK4 (>= 4.0)` & `libadwaita (>= 1.0)`
* **PyGObject**: `python3-gi / python-gobject`
* **yt-dlp**: Required command-line media downloader
* **ffmpeg**: Required for stream merging and audio extraction

### Installing System Dependencies

#### Arch Linux / Manjaro / EndeavourOS
```bash
sudo pacman -S python python-gobject gtk4 libadwaita yt-dlp ffmpeg
```

#### Debian / Ubuntu / Linux Mint / Pop!_OS
```bash
sudo apt update
sudo apt install python3 python3-gi gir1.2-gtk-4.0 gir1.2-adw-1 yt-dlp ffmpeg
```

#### Fedora / RHEL / Rocky Linux / AlmaLinux
```bash
sudo dnf install python3 python3-gobject gtk4 libadwaita yt-dlp ffmpeg
```

---

## Installation & Packaging Guide

### 1. Arch Linux / Manjaro (`pacman`)

Build and install the native `.pkg.tar.zst` package:

```bash
cd Glypdl

# Build the package
makepkg -f --nodeps

# Install the package locally
sudo pacman -U ./glypdl-1.0.0-1-any.pkg.tar.zst
```

To run Glypdl:
```bash
glypdl
```

---

### 2. Debian / Ubuntu / Linux Mint / Pop!_OS (`.deb`)

#### Method A: Universal Python Builder (Works on any distro)
```bash
cd Glypdl
python3 packaging/deb/build_deb.py
```
> Generates `glypdl_1.0.0_all.deb` in the project root.

#### Method B: Using `dpkg-deb` (On Debian/Ubuntu)
```bash
cd Glypdl
bash packaging/deb/build_deb.sh
```

#### Install the `.deb` package:
```bash
sudo apt install ./glypdl_1.0.0_all.deb
```

---

### 3. Fedora / RHEL / CentOS / openSUSE (`.rpm`)

#### Prerequisites:
```bash
sudo dnf install rpm-build rpmdevtools python3-devel
```

#### Build Steps:
```bash
# 1. Create rpmbuild directory tree
mkdir -p ~/rpmbuild/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

# 2. Create the source archive
tar --exclude-vcs --exclude='*.deb' --exclude='*.pkg.tar.zst' \
    -czvf ~/rpmbuild/SOURCES/glypdl-1.0.0.tar.gz \
    --transform 's,^\.,glypdl-1.0.0,' .

# 3. Build the RPM package
cp packaging/rpm/glypdl.spec ~/rpmbuild/SPECS/
rpmbuild -ba ~/rpmbuild/SPECS/glypdl.spec

# 4. Install the built package
sudo dnf install ~/rpmbuild/RPMS/noarch/glypdl-1.0.0-1.*.noarch.rpm
```

---

### 4. Flatpak (Cross-Distribution Sandboxed Container)

The Flatpak build bundles `yt-dlp` directly inside the container so it works out-of-the-box on any Linux distribution.

#### Prerequisites:
```bash
# Install Flatpak & Flatpak Builder
# Arch:   sudo pacman -S flatpak flatpak-builder
# Fedora: sudo dnf install flatpak flatpak-builder
# Ubuntu: sudo apt install flatpak flatpak-builder

# Install GNOME 46 runtime
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install flathub org.gnome.Platform//46 org.gnome.Sdk//46
```

#### Build & Install:
```bash
cd Glypdl
flatpak-builder --user --install --force-clean build-dir packaging/flatpak/io.github.suresh.Glypdl.yaml
```

#### Run Flatpak:
```bash
flatpak run io.github.suresh.Glypdl
```

#### Export as Single `.flatpak` Bundle (Optional):
```bash
flatpak-builder --repo=repo --force-clean build-dir packaging/flatpak/io.github.suresh.Glypdl.yaml
flatpak build-bundle repo glypdl.flatpak io.github.suresh.Glypdl
```

---

### 5. Running Directly from Source (No Installation)

You can run Glypdl directly from the source directory without installing:

```bash
git clone https://github.com/sureshsoundararajan/Glypdl.git
cd Glypdl

PYTHONPATH=src python3 -m glypdl.app
```

---

## Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| <kbd>Ctrl</kbd> + <kbd>L</kbd> | Focus URL input field |
| <kbd>Ctrl</kbd> + <kbd>V</kbd> | Paste URL into input field |
| <kbd>Ctrl</kbd> + <kbd>H</kbd> | Switch to Download History view |
| <kbd>Ctrl</kbd> + <kbd>,</kbd> | Open Preferences dialog |
| <kbd>Ctrl</kbd> + <kbd>Q</kbd> | Quit Glypdl |
| <kbd>Escape</kbd> | Dismiss preview card or close modal dialog |

---

## File & Configuration Paths

Following the **XDG Base Directory Specification**:

* **Configuration**: `~/.config/glypdl/config.ini`
* **Cookie Profiles**: `~/.config/glypdl/profiles.json`
* **History Database**: `~/.local/share/glypdl/history.db`
* **Thumbnail Cache**: `~/.cache/glypdl/thumbnails/`
* **Default Download Folder**: `~/Downloads`

---

## Running Automated Tests

Run the complete 24-test unit suite:

```bash
PYTHONPATH=src python3 -m unittest discover -s tests -v
```

---

## License

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.
