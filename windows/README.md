# Glypdl for Windows 11

A modern, blazing-fast, native Windows 11 desktop media downloader powered by **yt-dlp**, built with **C#**, **.NET 8**, **WinUI 3**, and the **Windows App SDK**.

---

## 🌟 Overview

**Glypdl for Windows** is an ultra-fast, modern desktop client designed specifically for Windows 11 and Windows 10. It delivers a fluid Fluent 2 user interface with native Mica material backdrops, automatic system theme adaptation, automated engine provisioning, rich playlist management, advanced cookie profile authentication, and high-quality audio/video extraction powered by **yt-dlp** and **FFmpeg**.

---

## 🚀 Key Features

### 🎨 1. Native Windows 11 Fluent 2 Design & Mica
* **Mica System Backdrop**: Translucent Mica material integrated seamlessly into the Windows 11 desktop.
* **Automatic System Theming**: Automatically synchronizes with your Windows Dark or Light theme.
* **Custom Frameless Title Bar**: Integrated caption controls (`[-]`, `[□]`, `[✕]`) matching active theme accents.
* **Persistent Window State**: Retains maximized, minimized, and custom window bounds across launches.

### 📋 2. Smart URL Input & Instant Metadata Fetch
* **Clipboard Auto-Detection**: Pressing `Ctrl+V` or pasting any supported URL automatically inputs the link and fetches metadata.
* **Metadata Preview Card**: Displays high-resolution thumbnail preview, title, channel/uploader name, duration badge, platform source, and previous download history reminders.
* **Static Content Detection**: Identifies photo/image-only posts (e.g. Instagram static posts) and provides clear guidance that Glypdl downloads video and audio streams.

### 🍪 3. Authentication Cookies & Profile Management
* **Netscape Cookie Import**: Import `cookies.txt` or `.cookies` files exported from your browser.
* **Saved Cookie Profiles**: Save and name multiple cookie profiles (e.g., *YouTube Premium*, *Instagram*, *Vimeo*).
* **Per-Download Cookie Selector**: Choose which cookie profile to use directly on the *Add Download* page.
* **Interactive Authentication Recovery**: If a private video, login wall, or bot checkpoint is encountered:
  - An informative warning banner displays on the download page with one-click **"Go to Settings"** and **"Select / Import Cookies"** actions.
  - An interactive recovery dialog allows selecting or importing a cookie profile to immediately enable cookies and retry the download.

### 📑 4. Visual Playlist Downloader
* **Interactive Track List**: Displays 16:9 thumbnails, track numbers (`#1`, `#2`...), titles, artists, and durations for all playlist items.
* **Flexible Batch Selection**:
  - **Select All (`✓`)** & **Deselect All (`✗`)**
  - Individual track selection checkboxes
* **Dynamic Status Counter**: Real-time summary (e.g. `14 of 20 items selected`).
* **Adaptive Action Button**: Updates dynamically (e.g. *"Download 14 Selected Videos"*).

### 🎵 5. Multi-Format Audio & Video Extraction
* **Video + Audio Mode**: Quality options up to 4K / 1440p / 1080p / 720p / 480p / Best.
* **Video Only Mode**: Stream extraction without audio track.
* **Audio Only Mode**: High-quality audio extraction with direct conversion into **MP3**, **AAC**, **M4A**, **FLAC**, **WAV**, or **OPUS**.
* **Audio Bitrates**: `320 kbps (Best)`, `256 kbps (High)`, `192 kbps (Medium)`, `128 kbps (Standard)`, `96 kbps (Low)`.
* **Safe Overwrite Protection**: Uses `--no-overwrites` to prevent accidental file loss.

### ⚡ 6. Real-Time Download Queue & Explorer Integration
* **Auto-Navigation**: Enqueuing a download automatically switches view to the **Downloads Queue**.
* **Live Telemetry**: Real-time progress bar, transfer speed (MB/s), ETA countdown, downloaded/total size.
* **Direct Player Launch (`▶`)**: Plays the completed audio or video file instantly in your default media player.
* **Open Folder & Select (`📁`)**: Opens Windows Explorer with the **exact downloaded file highlighted and selected** (`explorer.exe /select,...`).
* **Accurate Disk Size**: Measures exact post-conversion file size directly from disk.

### 🔔 7. Native Windows Toast Notifications
* Desktop toast notifications with audio chimes when downloads complete or fail, displaying media title, format, and final size.

### 📜 8. SQLite History & Instant Search
* **Search-as-you-Type**: Filter download history by title or URL instantly.
* **Redownload Format Picker**: Re-download previously saved media in a different format or quality.
* **Local Database**: All history stored locally in `%LOCALAPPDATA%\Glypdl\glypdl.db` for privacy.

### ⚙️ 9. Automated Dependency Management
* Auto-provisions and maintains standalone `yt-dlp.exe`, `ffmpeg.exe`, and `ffprobe.exe` binaries inside `%LOCALAPPDATA%\Glypdl\bin\`.
* One-click engine updates and version checking in Settings.

---

## 💻 System Requirements

* **Operating System**: Windows 11 (all versions) or Windows 10 (version 1809+, build 17763+)
* **Architecture**: x64 (64-bit Intel/AMD) or ARM64
* **Prerequisites**: **None!** The application is 100% self-contained with bundled .NET 8 runtime and Windows App SDK.

---

## 📦 Installation & Uninstallation

### Installing Glypdl
1. Download the setup executable (`Glypdl-1.0.0-Setup-x64.exe`).
2. Run the installer.
3. If Glypdl is already installed on your system, the installer will prompt:
   > *"Glypdl (version 1.0.0) is already installed on your computer. Do you want to reinstall or update it?"*
4. Follow the setup wizard to complete the installation.

### Complete Uninstallation
When uninstalled through **Windows Settings > Apps > Installed apps** or **Control Panel**:
* Removes the application installation directory (`%LOCALAPPDATA%\Programs\Glypdl`).
* Removes all auto-downloaded dependencies and data (`%LOCALAPPDATA%\Glypdl`), including `bin/yt-dlp.exe`, `bin/ffmpeg.exe`, `glypdl.db`, thumbnails, cookies, settings, and logs.

---

## 🛠️ Building from Source

### Prerequisites
1. Install [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).
2. Install [Visual Studio 2022](https://visualstudio.microsoft.com/) (Community, Professional, or Enterprise) with:
   * **.NET Desktop Development**
   * **Windows App SDK C# Templates**
3. Install [Inno Setup 6](https://jrsoftware.org/isdl.php) (for building the setup installer).

### 1. Build and Run via Visual Studio
1. Open `windows\Glypdl.Windows.sln` in Visual Studio 2022.
2. Set configuration to `Debug` or `Release` and platform to `x64`.
3. Set `Glypdl.Windows` as the startup project.
4. Press `F5` to build and run.

### 2. Run Automated Unit Tests
```powershell
dotnet test windows\tests\Glypdl.Windows.Tests\Glypdl.Windows.Tests.csproj -c Release
```

### 3. Publish 100% Standalone Self-Contained Package
To publish a self-contained release build with bundled .NET 8 runtime and Windows App SDK:
```powershell
& "C:\Program Files\Microsoft Visual Studio\2022\Community\MSBuild\Current\Bin\MSBuild.exe" windows\src\Glypdl.Windows\Glypdl.Windows.csproj -t:Publish -p:Configuration=Release -p:Platform=x64 -p:RuntimeIdentifier=win-x64 -p:SelfContained=true -p:PublishDir=..\..\publish\
```

### 4. Build Inno Setup Installer Executable (`.exe`)
Compile the standalone setup installer (`Glypdl-1.0.0-Setup-x64.exe`):
```powershell
& "$env:LOCALAPPDATA\Programs\Inno Setup 6\ISCC.exe" windows\packaging\inno\setup.iss
```
The compiled installer will be saved to:
`windows\dist\Glypdl-1.0.0-Setup-x64.exe`

---

## 📂 Project Structure

```
windows/
├── Glypdl.Windows.sln              # Visual Studio 2022 Solution
├── README.md                       # Windows documentation & guide
├── dist/                           # Compiled setup installer output (.exe)
│   └── Glypdl-1.0.0-Setup-x64.exe
├── publish/                        # Self-contained published application files
├── packaging/
│   └── inno/
│       ├── setup.iss               # Inno Setup compilation script
│       └── app.ico                 # Installer executable icon
├── src/
│   └── Glypdl.Windows/
│       ├── App.xaml / .cs          # Application startup, DI container, theme engine
│       ├── MainWindow.xaml / .cs   # Navigation window, Mica material, title bar
│       ├── app.manifest            # Windows 11 compatibility & DPI manifest
│       ├── Assets/                 # Application icons and assets
│       ├── Converters/             # XAML binding converters (Visibility, State, Icons)
│       ├── Models/                 # App entities (DownloadItem, PlaylistItem, CookieProfile, etc.)
│       ├── Services/               # Core services:
│       │   ├── CookieService.cs    # Netscape cookies parser & profile manager
│       │   ├── DownloadService.cs  # yt-dlp execution & progress parser
│       │   ├── HistoryService.cs   # SQLite database download history repository
│       │   ├── MetadataService.cs  # Media metadata extraction & thumbnail cache
│       │   ├── NotificationService.cs # Windows toast notification dispatcher
│       │   ├── QueueService.cs     # Download queue concurrency & state machine
│       │   ├── SettingsService.cs  # User preferences persistence
│       │   ├── UpdateService.cs    # GitHub release updater
│       │   └── YtDlpService.cs     # yt-dlp & FFmpeg provisioning and execution
│       ├── Utilities/              # Dispatcher helper, path utilities
│       ├── ViewModels/             # MVVM ViewModels:
│       │   ├── AboutViewModel.cs   # About page & engine updater
│       │   ├── DownloadsViewModel.cs # Download queue management
│       │   ├── HistoryViewModel.cs # History search and re-download
│       │   ├── HomeViewModel.cs    # URL fetch, quality selector, auth recovery
│       │   ├── MainViewModel.cs    # Navigation & shell state
│       │   └── SettingsViewModel.cs# Preferences, cookie profiles, engine settings
│       └── Views/                  # WinUI 3 Views:
│           ├── AboutPage.xaml      # About & component version view
│           ├── DownloadsPage.xaml  # Active & completed download queue view
│           ├── HistoryPage.xaml    # SQLite download history view
│           ├── HomePage.xaml       # URL input, preview, playlist selector
│           └── SettingsPage.xaml   # Preferences & cookie profile management
└── tests/
    └── Glypdl.Windows.Tests/       # xUnit automated test suite
        ├── ArgumentBuilderTests.cs # CLI arguments & cookie validation tests
        └── QueueManagerTests.cs    # Concurrency & queue state tests
```

---

## 📄 License

This project is licensed under the [MIT License](../LICENSE).
