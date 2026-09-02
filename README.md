# Glypdl

<div align="center">
  <img src="data/icons/hicolor/scalable/apps/io.github.sureshsoudararajan.Glypdl.svg" width="128" height="128" alt="Glypdl Logo" />
  <h3>A modern, blazing-fast cross-platform desktop media downloader for yt-dlp</h3>
  <p>Native <b>Windows 11 (WinUI 3 / C# / .NET 8)</b> and <b>Linux (GTK4 / libadwaita / Python)</b> desktop client.</p>

  <p>
    <a href="https://github.com/sureshsoudararajan/Glypdl/releases"><img src="https://img.shields.io/github/v/release/sureshsoudararajan/Glypdl?style=flat-square&color=3584e4" alt="Latest Release" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/License-GPL%20v3.0-blue.svg?style=flat-square" alt="License: GPL v3" /></a>
    <img src="https://img.shields.io/badge/Windows-11%20%7C%2010-0078D4.svg?style=flat-square&logo=windows" alt="Windows 11/10" />
    <img src="https://img.shields.io/badge/.NET-8.0-512BD4.svg?style=flat-square&logo=dotnet" alt=".NET 8" />
    <img src="https://img.shields.io/badge/WinUI-3.0-0078D4.svg?style=flat-square" alt="WinUI 3" />
    <img src="https://img.shields.io/badge/Linux-GTK4%20%2F%20libadwaita-2ec27e.svg?style=flat-square&logo=linux" alt="Linux GTK4" />
    <img src="https://img.shields.io/badge/Python-3.10+-yellow.svg?style=flat-square&logo=python" alt="Python 3.10+" />
  </p>
</div>

---

## 🌟 Overview

**Glypdl** is an ultra-fast, modern, cross-platform desktop media downloader powered by **yt-dlp** and **FFmpeg**. Built from the ground up to provide native, high-performance desktop experiences on both **Windows** and **Linux**:

* **Windows Edition**: Native **WinUI 3**, **C#**, and **.NET 8** application featuring Windows 11 Fluent 2 design, translucent Mica material backdrops, automatic system dark/light theming, and an Inno Setup installer.
* **Linux Edition**: Native **GTK4**, **libadwaita**, and **Python** application conforming to the GNOME Human Interface Guidelines (HIG) with standalone AppImage, Flatpak, Deb, RPM, and Arch Linux packages.

---

## 📸 Screenshots

<div align="center">
  <table>
    <tr>
      <td align="center"><b>Active Downloads Queue</b><br/><img src="data/screenshots/active-downloads.png" width="400" alt="Active Downloads" /></td>
      <td align="center"><b>Metadata & Format Selection</b><br/><img src="data/screenshots/metadata-preview.png" width="400" alt="Metadata Preview" /></td>
    </tr>
    <tr>
      <td align="center"><b>Persistent Download History</b><br/><img src="data/screenshots/download-history.png" width="400" alt="Download History" /></td>
      <td align="center"><b>Preferences & Settings</b><br/><img src="data/screenshots/preferences-settings.png" width="400" alt="Preferences" /></td>
    </tr>
  </table>
</div>

---

## 🚀 Key Features

### 🎨 1. Native Desktop Experiences
* **Windows 11 (WinUI 3 & Mica)**:
  - Translucent Mica backdrop integrated seamlessly into the Windows 11 desktop.
  - Automatic system Dark/Light theme synchronization.
  - Custom frameless title bar with integrated caption controls.
  - Persistent window size and maximized state preservation across launches.
* **Linux (GTK4 & libadwaita)**:
  - Responsive GNOME HIG-compliant interface with pure native performance.
  - Seamless desktop theme integration across GNOME, KDE Plasma, Cinnamon, XFCE, COSMIC, and tiling window managers (Hyprland, Sway, i3).

### 📋 2. Smart URL Input & Instant Metadata Fetch
* **Clipboard Auto-Detection**: Pressing `Ctrl+V` or pasting any supported URL automatically inputs the link and fetches metadata.
* **Metadata Preview Card**: Displays high-resolution thumbnail preview, title, channel/uploader name, duration badge, platform source, and previous download history reminders.
* **Static Content Detection**: Identifies photo/image-only posts (e.g., Instagram static posts) and provides clear guidance that Glypdl downloads video and audio streams.

### 🍪 3. Comprehensive Browser Cookies & Authentication
* **Three Authentication Modes**:
  1. **Disabled**: Downloads without session cookies.
  2. **Direct Web Browser (`--cookies-from-browser`)**: Extracts session cookies directly from installed browsers (**Microsoft Edge**, **Google Chrome**, **Mozilla Firefox**, **LibreWolf**, **Brave Browser**, **Opera**, **Vivaldi**, and **Chromium**) with automatic profile and keyring discovery.
  3. **Cookie File (`--cookies`)**: Uses imported Netscape `cookies.txt` files for maximum reliability.
* **Per-Download Cookie Selector**: Choose between direct browser extraction, specific `cookies.txt` profiles, or no cookies directly from the format selector before starting a download.
* **Advanced Chromium Direct Extraction Guide (In Settings)**:
  - Dynamically generates the required Windows Enterprise Policy command matching the currently selected Chromium browser with a **1-Click Copy** button.
  - Step-by-step guidance on clearing previous cookies, re-logging into accounts under standard DPAPI protection, and closing background browser processes in Task Manager.
  - Includes a 1-click restore command to re-enable strict App-Bound Encryption at any time.
* **Intelligent Authentication Recovery**:
  - Automatically detects DPAPI errors (yt-dlp issue #10927), locked database handles (issue #7271), login walls, and private post checkpoints.
  - Replaces raw CLI error traces with user-friendly recovery guidance and displays an interactive modal dialog allowing users to choose an authentication profile or import a `cookies.txt` file and retry immediately.

### 📑 4. Visual Playlist Downloader
* **Interactive Track List**: Displays 16:9 thumbnails, track numbers (`#1`, `#2`...), titles, artists, and durations for all playlist items.
* **Flexible Batch Selection**:
  - **Select All (`✓`)** & **Deselect All (`✗`)**
  - Individual track selection checkboxes
* **Dynamic Status Counter**: Real-time summary (e.g. `14 of 20 items selected`).
* **Adaptive Action Button**: Updates dynamically (e.g. *"Download 14 Selected Videos"*).

### 🎵 5. Multi-Format Audio & Video Extraction
* **Video + Audio Mode**: Quality options up to **4K (2160p)**, **1440p**, **1080p**, **720p**, **480p**, **360p**, **240p**, **144p**, or **Best**.
* **Video Only Mode**: Stream extraction without audio track.
* **Audio Only Mode**: High-quality audio extraction with direct conversion into **MP3**, **AAC**, **M4A**, **FLAC**, **WAV**, or **OPUS**.
* **Audio Bitrates**: `320 kbps (Best)`, `256 kbps (High)`, `192 kbps (Medium)`, `128 kbps (Standard)`, `96 kbps (Low)`.
* **Safe Overwrite Protection**: Uses `--no-overwrites` to prevent accidental file loss.

### ⚡ 6. Real-Time Download Queue & Explorer Integration
* **Auto-Navigation**: Enqueuing a download automatically switches view to the **Downloads Queue**.
* **Live Telemetry**: Real-time progress bar, transfer speed (MB/s), ETA countdown, downloaded/total size.
* **Direct Player Launch (`▶`)**: Plays the completed audio or video file instantly in your default media player.
* **Open Folder & Select (`📁`)**: Opens Windows Explorer / file manager with the **exact downloaded file highlighted and selected**.
* **Accurate Disk Size**: Measures exact post-conversion file size directly from disk.

### 🔔 7. Native Desktop Toast Notifications
* System toast notifications with audio chimes when downloads complete or fail, displaying media title, format, and final size.

### 📜 8. SQLite History & Instant Search
* **Search-as-you-Type**: Filter download history by title or URL instantly.
* **Redownload Format Picker**: Re-download previously saved media in a different format or quality.
* **Local Database**: All history stored locally for complete privacy.

### ⚙️ 9. Automated Dependency Management & First-Run Provisioning
* **Automatic Engine Download**: If dependencies (`yt-dlp`, `ffmpeg`, `ffprobe`) are missing on first run, Glypdl displays an informative progress dialog showing real-time download status, percentages, and component names.
* **Zero System Bloat**: Binaries are installed locally into application data directories without polluting system PATH or modifying global environment variables.
* **One-Click Engine Updates**: Check and update `yt-dlp` and engine components anytime from Settings or the About page.

---

## 📥 Installation

Choose the package format best suited for your operating system from [GitHub Releases](https://github.com/sureshsoudararajan/Glypdl/releases):

### 🪟 Windows 11 / 10 Installer (`.exe`)
1. Download **`Glypdl-1.0.0-Setup-x64.exe`**.
2. Run the installer to set up Start Menu and Desktop shortcuts.
3. Launch **Glypdl** from your Start Menu!

### 🐧 Linux Packages

#### 1. 🚀 Universal Standalone AppImage
```bash
wget -O Glypdl-x86_64.AppImage https://github.com/sureshsoudararajan/Glypdl/releases/download/v1.0.0/Glypdl-x86_64.AppImage
chmod +x Glypdl-x86_64.AppImage
./Glypdl-x86_64.AppImage
```

#### 2. 📦 Debian / Ubuntu / Linux Mint / Pop!_OS (`.deb`)
```bash
wget -O glypdl.deb https://github.com/sureshsoudararajan/Glypdl/releases/download/v1.0.0/glypdl_1.0.0-1_all.deb
sudo apt install -y ./glypdl.deb
glypdl
```

#### 3. 📦 Fedora / Rocky Linux / RHEL / AlmaLinux (`.rpm`)
```bash
wget -O glypdl.rpm https://github.com/sureshsoudararajan/Glypdl/releases/download/v1.0.0/glypdl-1.0.0-1.fc44.noarch.rpm
sudo dnf install -y ./glypdl.rpm
glypdl
```

#### 4. 📦 Arch Linux / Manjaro / EndeavourOS
```bash
wget https://github.com/sureshsoudararajan/Glypdl/releases/download/v1.0.0/glypdl-1.0.0-1-any.pkg.tar.zst
sudo pacman -U ./glypdl-1.0.0-1-any.pkg.tar.zst
glypdl
```

#### 5. 🛍️ Flatpak (Sandboxed Container)
```bash
cd Glypdl
flatpak remote-add --if-not-exists flathub https://dl.flathub.org/repo/flathub.flatpakrepo
flatpak install -y flathub org.gnome.Platform//50 org.gnome.Sdk//50

# Build and run
flatpak-builder --user --install --force-clean build-dir packaging/flatpak/io.github.sureshsoudararajan.Glypdl.local.yaml
flatpak run io.github.sureshsoudararajan.Glypdl
```

---

## 🛠️ Building from Source

### Windows Build (.NET 8 & WinUI 3)

#### Prerequisites
1. [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
2. [Visual Studio 2022](https://visualstudio.microsoft.com/) with **.NET Desktop Development** and **Windows App SDK C# Templates**
3. [Inno Setup 6](https://jrsoftware.org/isdl.php) (for installer creation)

#### Build & Run
```powershell
# Run unit tests
dotnet test windows\tests\Glypdl.Windows.Tests\Glypdl.Windows.Tests.csproj -c Release

# Publish self-contained executable
& "C:\Program Files\Microsoft Visual Studio\2022\Community\MSBuild\Current\Bin\MSBuild.exe" windows\src\Glypdl.Windows\Glypdl.Windows.csproj -t:Publish -p:Configuration=Release -p:Platform=x64 -p:RuntimeIdentifier=win-x64 -p:SelfContained=true -p:PublishDir=..\..\publish\

# Build setup installer (.exe)
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" windows\packaging\inno\setup.iss
```

---

### Linux Build (Python & GTK4 / Libadwaita)

#### Prerequisites
1. Python 3.10+
2. GTK4, libadwaita-1, PyGObject

#### Run Directly from Source
```bash
git clone https://github.com/sureshsoudararajan/Glypdl.git
cd Glypdl

# Run automated tests
PYTHONPATH=src python3 -m tests

# Launch application
PYTHONPATH=src python3 -m glypdl.app
```

---

## ⌨️ Keyboard Shortcuts

| Shortcut | Action |
| :--- | :--- |
| <kbd>Ctrl</kbd> + <kbd>L</kbd> | Focus URL input field |
| <kbd>Ctrl</kbd> + <kbd>V</kbd> | Paste URL into input field |
| <kbd>Ctrl</kbd> + <kbd>H</kbd> | Switch to Download History view |
| <kbd>Ctrl</kbd> + <kbd>,</kbd> | Open Preferences dialog |
| <kbd>Ctrl</kbd> + <kbd>Q</kbd> | Quit Glypdl |
| <kbd>Escape</kbd> | Dismiss preview card or close modal dialog |

---

## 📂 Project Structure

```
Glypdl/
├── data/                           # App icons, metadata, and screenshots
├── packaging/                      # Linux packaging (Deb, RPM, Flatpak, Arch, AppImage)
├── src/glypdl/                     # Linux GTK4 / Python application source
├── tests/                          # Linux test suite
├── windows/                        # Native Windows 11 client
│   ├── Glypdl.Windows.sln          # Visual Studio 2022 solution
│   ├── dist/                       # Output installer (.exe)
│   ├── packaging/inno/             # Inno Setup installer script & assets
│   ├── publish/                    # Published self-contained binaries
│   ├── src/Glypdl.Windows/         # WinUI 3 / C# application source
│   │   ├── Assets/                 # WinUI icons and assets
│   │   ├── Converters/             # XAML state & visibility converters
│   │   ├── Models/                 # Download, playlist, cookie models
│   │   ├── Services/               # Engine, cookie, queue, history services
│   │   ├── ViewModels/             # MVVM ViewModels
│   │   └── Views/                  # WinUI 3 XAML Pages
│   └── tests/Glypdl.Windows.Tests/ # xUnit test suite
├── CHANGELOG.md                    # Release version history
├── LICENSE                         # GNU General Public License v3.0
└── README.md                       # Project documentation
```

---

## 📁 File & Configuration Paths

### Windows
* **Configuration**: `%LOCALAPPDATA%\Glypdl\config.json`
* **Cookie Profiles**: `%LOCALAPPDATA%\Glypdl\profiles.json`
* **History Database**: `%LOCALAPPDATA%\Glypdl\history.db`
* **Bundled Binaries**: `%LOCALAPPDATA%\Glypdl\bin\` (`yt-dlp.exe`, `ffmpeg.exe`, `ffprobe.exe`)
* **Thumbnail Cache**: `%LOCALAPPDATA%\Glypdl\thumbnails\`

### Linux (XDG Base Directory)
* **Configuration**: `~/.config/glypdl/config.ini`
* **Cookie Profiles**: `~/.config/glypdl/profiles.json`
* **History Database**: `~/.local/share/glypdl/history.db`
* **Bundled Binaries**: `~/.local/share/glypdl/bin/`
* **Thumbnail Cache**: `~/.cache/glypdl/thumbnails/`

---

## 📄 License

Glypdl is free and open-source software licensed under the **GNU General Public License v3.0 or later (GPL-3.0-or-later)** &mdash; see the [LICENSE](LICENSE) file for details.

Copyright © 2026 Suresh S.

### Third-Party Licenses & Notices
Glypdl utilizes independent third-party open-source components that retain their respective original licenses:
* **yt-dlp**: [The Unlicense](https://github.com/yt-dlp/yt-dlp/blob/master/LICENSE) (Public Domain dedication) / [MIT License](https://opensource.org/licenses/MIT)
* **FFmpeg**: [GNU Lesser General Public License (LGPL) v2.1+](https://www.ffmpeg.org/legal.html) / [GNU General Public License (GPL) v2+](https://www.gnu.org/licenses/old-licenses/gpl-2.0.html)
* **Windows App SDK & WinUI 3**: [MIT License](https://github.com/microsoft/WindowsAppSDK/blob/main/LICENSE)
* **CommunityToolkit.Mvvm**: [MIT License](https://github.com/CommunityToolkit/dotnet/blob/main/License.md)
