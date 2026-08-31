# Glypdl for Windows 11

A modern, blazing-fast, native Windows 11 desktop media downloader powered by **yt-dlp**, built with **C#**, **.NET 8**, **WinUI 3**, and the **Windows App SDK**.

---

## 🌟 Overview

**Glypdl for Windows** is an ultra-fast, modern desktop client designed specifically for Windows 11. It delivers a fluid Fluent 2 user interface with native Mica backdrops, real-time Dark and Light theme adaptation, automated engine provisioning, rich playlist downloading, and seamless audio/video format conversion powered by **yt-dlp** and **FFmpeg**.

---

## 🚀 Key Features

### 🎨 1. Native Windows 11 Fluent 2 Design & Mica
* **Mica System Backdrop**: Beautiful transluscent Mica material that integrates seamlessly with Windows 11.
* **Instant Dark & Light Themes**: Dynamic theme switching with matching custom title bar buttons (`[-]`, `[□]`, `[✕]`) and background contrast.
* **Persistent Window State**: Retains fullscreen and maximized states across app switching, alt-tabbing, and restarts.

### 📋 2. Smart URL Input & Instant Fetch
* **Clipboard Auto-Detection**: Pressing `Ctrl+V` or pasting any link automatically populates the input bar and initiates metadata fetch immediately.
* **Metadata Preview Card**: Displays thumbnail preview, title, channel/uploader, duration, platform source, and history download warnings.

### 📑 3. Premium Playlist Downloader
* **Interactive Visual Track List**: Displays individual 16:9 thumbnails, track numbers (`#1`, `#2`...), duration badges, titles, and artist details for every video in the playlist.
* **Flexible Batch Selection**:
  * **Select All (`✓`)** & **Deselect All (`✗`)**
  * **Invert Selection (`⇄`)**
  * Individual check/uncheck per track
* **Live Selection Counter**: Displays real-time status (e.g. `18 of 25 items selected`).
* **Dynamic Action Button**: Adapts to your selection (e.g., *"Download 18 Selected Videos"*).

### 🎵 4. Multi-Format Audio & Video Engine
* **Separate Video & Audio Controls**:
  * **Video + Audio Mode**: Quality selection up to 4K / 1080p / 720p / 480p / Best.
  * **Video Only Mode**: Video stream extraction without audio track.
  * **Audio Only Mode**: Converts directly into **MP3**, **AAC**, **M4A**, **FLAC**, **WAV**, or **OPUS**.
  * **Audio Bitrates**: `320 kbps (Best)`, `256 kbps (High)`, `192 kbps (Medium)`, `128 kbps (Standard)`, `96 kbps (Low)`.
* **Zero Missing Dependencies**: Bundles and auto-provisions `yt-dlp.exe`, `ffmpeg.exe`, and `ffprobe.exe` directly inside `%LOCALAPPDATA%\Glypdl\bin\`.
* **Safe Overwrite Protection**: Uses `--no-overwrites` to prevent accidental file deletion or replacement.

### ⚡ 5. Real-Time Download Queue & Direct Explorer Integration
* **Auto-Redirect on Start**: Enqueuing a single track or an entire playlist automatically switches view to the **Downloads Queue**.
* **Live Telemetry**: Real-time progress bar, transfer speed (MB/s), ETA countdown, downloaded/total size.
* **Direct Player Launch (`▶`)**: Plays the completed audio or video file instantly in your default media player.
* **Open Folder & Select (`📁`)**: Opens Windows Explorer with the **exact downloaded file highlighted and selected** (`explorer.exe /select,...`).
* **Accurate Merged File Size**: Measures exact post-conversion file size from disk.

### 🔔 6. Native Windows Toast Notifications
* Desktop toast notification banners with sound chimes (`Notification.Default`) when downloads complete or fail, displaying media title, format, and final size.

### 📜 7. History & Database Search
* **Search-as-you-Type**: Filter download history by title or URL instantly without pressing enter.
* **Redownload Format Picker**: Easily choose a new format or quality to re-download previously saved media.
* **Local SQLite Database**: Stores history and metadata locally for privacy and offline review.

### 🍪 8. Cookie Profiles & Authenticated Downloads
* Extract cookies from Chrome, Edge, Firefox, Brave, and Vivaldi for member-only, private, or age-restricted videos.

---

## 💻 System Requirements

* **Operating System**: Windows 11 (all versions) or Windows 10 (version 1809+, build 17763+)
* **Architecture**: x64 (64-bit Intel/AMD) or ARM64
* **Runtime**: .NET 8.0 Desktop Runtime (included in self-contained builds)

---

## 🛠️ Building from Source

### Prerequisites
1. Install [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).
2. Install [Visual Studio 2022](https://visualstudio.microsoft.com/) (Community, Professional, or Enterprise) with the following workloads:
   * **.NET Desktop Development**
   * **Windows App SDK C# Templates** (or install the *Windows App SDK* component)

### Clone & Build via Command Line
```powershell
# 1. Clone repository
git clone https://github.com/sureshsoudararajan/Glypdl.git
cd Glypdl\windows

# 2. Restore NuGet packages
dotnet restore Glypdl.Windows.sln -p:Platform=x64

# 3. Run automated tests
dotnet test tests\Glypdl.Windows.Tests\Glypdl.Windows.Tests.csproj -c Debug -p:Platform=x64

# 4. Build Debug solution
msbuild Glypdl.Windows.sln -p:Configuration=Debug -p:Platform=x64

# 5. Publish Self-Contained Release
dotnet publish src\Glypdl.Windows\Glypdl.Windows.csproj -c Release -r win-x64 -p:Platform=x64 --self-contained true -p:PublishSingleFile=false -o publish
```

---

## 📦 Creating the Installer (Inno Setup)

To build the standalone Windows setup installer (`Glypdl-1.0.0-Setup-x64.exe`):

1. Install [Inno Setup 6](https://jrsoftware.org/isdl.php) (or via Chocolatey: `choco install innosetup -y`).
2. Run the compiler:
```powershell
iscc packaging\inno\setup.iss
```
3. The generated installer will be located in `windows\dist\Glypdl-1.0.0-Setup-x64.exe`.

---

## 📂 Project Structure

```
windows/
├── Glypdl.Windows.sln              # Visual Studio Solution file
├── README.md                       # Windows documentation
├── packaging/
│   └── inno/
│       ├── setup.iss               # Inno Setup compiler script
│       └── app.ico                 # Installer icon
├── src/
│   └── Glypdl.Windows/
│       ├── App.xaml / .cs          # Application entry point & theme engine
│       ├── MainWindow.xaml / .cs   # Shell window with Mica & NavigationView
│       ├── Converters/             # XAML binding value converters
│       ├── Models/                 # Data entities (DownloadItem, PlaylistItem, etc.)
│       ├── Services/               # Core services (YtDlp, Download, Queue, DB, Notifications)
│       ├── Utilities/              # Process runner, path helpers, dispatcher
│       ├── ViewModels/             # MVVM ViewModels (Home, Downloads, History, Settings)
│       └── Views/                  # WinUI 3 Pages (HomePage, DownloadsPage, HistoryPage, etc.)
└── tests/
    └── Glypdl.Windows.Tests/       # xUnit unit test suite
```

---

## ⚙️ CI/CD Automated Pipelines

Glypdl includes continuous integration and automated release workflows via GitHub Actions:
* `.github/workflows/windows.yml`: Restores, compiles, runs tests, publishes self-contained binaries, builds the Inno Setup installer, and uploads build artifacts.
* `.github/workflows/ci.yml`: Runs automated cross-platform test suites on every pull request and push.
* `.github/workflows/release.yml`: Automatically packages and attaches the Windows installer (`Glypdl-*-Setup-x64.exe`) to GitHub Releases upon publishing a git tag (`v*`).

---

## 📄 License

This project is licensed under the [MIT License](../LICENSE).
