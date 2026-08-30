# 🚀 Glypdl: Windows 11 Native Development & Context Handover

> **For Antigravity AI & Developers on Windows 11**:
> This document contains the complete context, architectural breakdown, directory boundaries, and execution instructions for developing and running **Glypdl for Windows 11**.

---

## 1. 🛡️ Absolute Platform Isolation (Core Rule)

Glypdl is designed as a dual-platform project with **100% strict isolation**:

```text
                                  GLYPDL ROOT
                                       │
                ┌──────────────────────┴──────────────────────┐
                │                                             │
      🐧 LINUX ENVIRONMENT                         🪟 WINDOWS ENVIRONMENT
  (Directory: root / src / packaging)               (Directory: /windows/)
  ───────────────────────────────────               ───────────────────────
  • UI: GTK4 + libadwaita                           • UI: WinUI 3 + Windows App SDK
  • Language: Python 3.10+                          • Language: C# (.NET 8.0)
  • Packaging: deb, rpm, appimage, aur, flatpak     • Packaging: Inno Setup (.exe)
  • History: ~/.local/share/glypdl/history.db       • History: %LOCALAPPDATA%\Glypdl\history.db
  • Engine: Bundled /usr/share/glypdl/bin/yt-dlp   • Engine: Bundled %LOCALAPPDATA%\Glypdl\bin\yt-dlp.exe
  • State: PRODUCTION READY (All 24 Tests Pass)     • State: Full C# Codebase Complete
```

> [!IMPORTANT]
> **Isolation Guarantee**: Code under `windows/` MUST NEVER import or touch Linux files (`src/`, `packaging/`, `PKGBUILD`, `setup.py`, etc.). Linux packaging and code must remain completely untouched when working on Windows.

---

## 2. 📁 Windows Project Architecture (`windows/`)

The Windows application is structured following the **MVVM (Model-View-ViewModel)** architectural pattern with **CommunityToolkit.Mvvm** and **Microsoft.WindowsAppSDK**:

```text
windows/
├── Glypdl.Windows.sln                  # Visual Studio 2022 Solution
├── Directory.Build.props               # Global build properties (x64, UseRidGraph, net8.0)
├── global.json                         # Pin .NET SDK 8.0
│
├── src/Glypdl.Windows/                 # WinUI 3 Desktop Application (.NET 8)
│   ├── Glypdl.Windows.csproj           # Project configuration (Unpackaged desktop WinExe)
│   ├── app.manifest                    # Windows 10/11 Per-Monitor DPI V2 manifest
│   ├── App.xaml / App.xaml.cs          # Application entry point & DI container
│   ├── MainWindow.xaml / .cs           # NavigationView, Mica backdrop, custom titlebar
│   │
│   ├── Models/                         # Data Models & Enums
│   │   ├── Enums.cs                    # DownloadState, DownloadMode, AppTheme
│   │   ├── MediaFormat.cs              # Video/Audio stream format representation
│   │   ├── MediaMetadata.cs            # Single video & playlist metadata model
│   │   ├── DownloadItem.cs             # Observable download queue item (speed, ETA, progress)
│   │   ├── HistoryEntry.cs             # SQLite history persistence model
│   │   ├── CookieProfile.cs            # Netscape cookies profile model
│   │   └── AppSettings.cs              # JSON application configuration model
│   │
│   ├── Services/                       # Core Business & Engine Services (Interfaces + Classes)
│   │   ├── IYtDlpService.cs / YtDlpService.cs         # yt-dlp & ffmpeg discovery and CLI argument builder
│   │   ├── IMetadataService.cs / MetadataService.cs   # JSON metadata parser & format spec resolver
│   │   ├── IDownloadService.cs / DownloadService.cs   # Streaming process execution & progress parser
│   │   ├── IQueueService.cs / QueueService.cs         # Concurrency semaphore & active/queued lists
│   │   ├── IHistoryService.cs / HistoryService.cs     # SQLite CRUD database manager
│   │   ├── ICookieService.cs / CookieService.cs       # Secure cookie profiles manager
│   │   ├── ISettingsService.cs / SettingsService.cs   # %LOCALAPPDATA% JSON settings manager
│   │   ├── INotificationService.cs / ...              # Windows Toast notifications
│   │   └── IUpdateService.cs / UpdateService.cs       # GitHub Releases update checker
│   │
│   ├── ViewModels/                     # MVVM ViewModels
│   │   ├── MainViewModel.cs            # Root ViewModel coordinating child pages
│   │   ├── HomeViewModel.cs            # URL fetch, quality/mode selection, download trigger
│   │   ├── DownloadsViewModel.cs       # Live queue cards, pause/cancel/retry/open folder
│   │   ├── HistoryViewModel.cs         # SQLite search, clear all, open folder, download again
│   │   ├── SettingsViewModel.cs        # Directory picker, theme, concurrency, cookie profiles
│   │   └── AboutViewModel.cs           # Version info, author details, GitHub update checker
│   │
│   ├── Views/                          # WinUI 3 XAML Pages
│   │   ├── HomePage.xaml / .cs         # URL input bar, live metadata preview, download button
│   │   ├── DownloadsPage.xaml / .cs    # Active downloads list with live progress bars & speed
│   │   ├── HistoryPage.xaml / .cs      # Searchable SQLite history list with context actions
│   │   ├── SettingsPage.xaml / .cs     # Windows 11 style settings cards
│   │   └── AboutPage.xaml / .cs        # Fluent About page with update check button
│   │
│   └── Utilities/                      # Reusable Helpers
│       ├── PathUtils.cs                # %LOCALAPPDATA%\Glypdl directory resolver
│       ├── FormattingUtils.cs          # Human-readable sizes (GB/MB), speed, ETA, and progress parser
│       └── ProcessRunner.cs            # Asynchronous and streaming Process execution
│
├── tests/Glypdl.Windows.Tests/         # xUnit Unit Testing Project
│   ├── Glypdl.Windows.Tests.csproj     # Headless unit test configuration
│   ├── FormattingTests.cs              # Tests for size, speed, ETA, and yt-dlp line parsing
│   └── ArgumentBuilderTests.cs         # Tests for yt-dlp command-line generation
│
└── packaging/inno/
    └── setup.iss                       # Inno Setup 6 script -> Glypdl-1.0.0-Setup-x64.exe
```

---

## 3. 🏃 How to Run on Native Windows 11

When Antigravity opens this project on Windows 11 tomorrow, follow these steps:

### Prerequisites:
* **Windows 11** (or Windows 10 Build 19041+)
* **Visual Studio 2022** (with **.NET Desktop Development** workload) OR **.NET 8.0 SDK**
* **Inno Setup 6** (for building the installer, `choco install innosetup -y` or installer download)

### Step-by-Step Commands:

```powershell
# 1. Navigate to the Windows directory
cd Glypdl\windows

# 2. Restore NuGet packages
dotnet restore Glypdl.Windows.sln -p:Platform=x64

# 3. Run the Unit Tests
dotnet test tests\Glypdl.Windows.Tests\Glypdl.Windows.Tests.csproj -p:Platform=x64

# 4. Launch the application in Debug / Live mode
dotnet run --project src\Glypdl.Windows\Glypdl.Windows.csproj -p:Platform=x64
```

### Or in Visual Studio 2022 (F5 Workflow):
1. Double-click `windows\Glypdl.Windows.sln`.
2. Ensure the top architecture dropdown is set to **`x64`** (not AnyCPU).
3. Right-click `Glypdl.Windows` &rarr; **Set as Startup Project**.
4. Press **F5** to start with full debugging and **XAML Hot Reload**!

---

## 4. 📦 How to Build the Installer (`Glypdl-1.0.0-Setup-x64.exe`)

```powershell
cd Glypdl\windows

# Step A: Publish self-contained 64-bit binary
dotnet publish src\Glypdl.Windows\Glypdl.Windows.csproj -c Release -r win-x64 -p:Platform=x64 --self-contained true -o publish

# Step B: Compile Inno Setup Installer
iscc packaging\inno\setup.iss

# Result: Output installer will be created at:
# Glypdl\windows\dist\Glypdl-1.0.0-Setup-x64.exe
```

---

## 5. 📝 Summary of Linux State (Stage 1 Complete)

* **AppImage**: Zero-dependency bundled GTK4/Adwaita typelibs and shared objects.
* **Debian / Ubuntu**: `.deb` installs isolated `yt-dlp` to `/usr/share/glypdl/bin/yt-dlp`.
* **Fedora / RPM**: `.rpm` installs with universal Python 3.10–3.14 compatibility.
* **Arch Linux / AUR**: `PKGBUILD` and `.SRCINFO` validated.
* **Linux Unit Tests**: `PYTHONPATH=src python3 -m tests` &rarr; **24/24 passing (100% OK)**.
* **Screenshots**: High-resolution gallery in `data/screenshots/` and AppStream metainfo registered.
