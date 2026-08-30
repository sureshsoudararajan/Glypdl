# Build Glypdl for Windows 11 — Completely Isolated Native Application

## Project Context

The existing **Glypdl** repository is a fully developed and working Linux desktop application.

The Linux application is already production-ready and supports:

* GTK4
* libadwaita
* yt-dlp
* video downloads
* audio downloads
* video + audio downloads
* thumbnails
* download queue
* real-time progress
* download speed
* downloaded/total size
* ETA
* cookies
* cookie profiles
* history
* settings
* dark/light mode
* DEB
* RPM
* Pacman
* AppImage
* Flatpak
* GitHub Actions automated builds/releases

**Do not redesign, rewrite, migrate, or replace the Linux application.**

The next goal is to create a **native Windows 11 edition of Glypdl**.

---

# 1. ABSOLUTE ISOLATION REQUIREMENT

The Windows application must be completely isolated from the existing Linux application.

Create a new directory at the repository root:

```text
windows/
```

ALL Windows-specific source code, resources, configuration, tests and build files must live inside this directory unless a file absolutely must be shared at the repository level.

The architecture must be:

```text
Glypdl/
│
├── src/                       # EXISTING LINUX APPLICATION
│
├── tests/                     # EXISTING LINUX TESTS
│
├── packaging/                 # EXISTING LINUX PACKAGING
│   ├── deb/
│   ├── rpm/
│   ├── arch/
│   ├── flatpak/
│   └── appimage/
│
├── data/                      # EXISTING LINUX RESOURCES
│
├── windows/                   # NEW WINDOWS APPLICATION
│   ├── src/
│   ├── tests/
│   ├── resources/
│   ├── packaging/
│   ├── installer/
│   ├── README.md
│   ├── Glypdl.Windows.sln
│   └── ...
│
├── .github/
│   └── workflows/
│       ├── linux.yml          # EXISTING
│       └── windows.yml        # NEW
│
├── README.md
└── LICENSE
```

---

# 2. NO CODE COLLISION

This is a strict requirement.

### Linux → Windows

Changing:

```text
src/
tests/
packaging/
data/
```

must NOT require modifying Windows source code.

### Windows → Linux

Changing:

```text
windows/
```

must NOT require modifying Linux source code.

The two applications must be independently buildable.

For example:

```text
Linux developer changes:

src/glypdl/downloader.py
        ↓
Linux application changes
        ↓
Windows application remains unaffected
```

And:

```text
Windows developer changes:

windows/src/...
        ↓
Windows application changes
        ↓
Linux application remains unaffected
```

Do not create cross-platform imports between the two implementations.

---

# 3. DO NOT DO THIS

Never create:

```text
windows/src/
    import ../../src/...
```

Do not make Windows execute Linux Python modules.

Do not make Linux import Windows code.

Do not place Windows code inside:

```text
src/
```

Do not place Windows-specific dependencies in the Linux `pyproject.toml`.

Do not modify the Linux GTK4 application to accommodate Windows.

Do not create:

```text
src/windows/
```

The Windows application belongs in:

```text
windows/
```

---

# 4. WINDOWS TECHNOLOGY

Build a genuinely native Windows 11 application.

Recommended stack:

* C#
* modern .NET
* WinUI 3
* Windows App SDK
* XAML
* MVVM
* CommunityToolkit.Mvvm where useful

Do NOT use:

* Electron
* Chromium
* WebView-based application UI
* Flutter
* React Native
* GTK4 for the Windows UI
* a browser-based frontend

The application should look and behave like a modern Windows 11 application.

---

# 5. Windows Project Structure

Use a structure similar to:

```text
windows/
│
├── src/
│   └── Glypdl.Windows/
│       │
│       ├── App.xaml
│       ├── App.xaml.cs
│       ├── MainWindow.xaml
│       ├── MainWindow.xaml.cs
│       │
│       ├── Views/
│       │   ├── HomePage.xaml
│       │   ├── DownloadsPage.xaml
│       │   ├── HistoryPage.xaml
│       │   ├── SettingsPage.xaml
│       │   └── AboutPage.xaml
│       │
│       ├── ViewModels/
│       │   ├── HomeViewModel.cs
│       │   ├── DownloadsViewModel.cs
│       │   ├── HistoryViewModel.cs
│       │   ├── SettingsViewModel.cs
│       │   └── AboutViewModel.cs
│       │
│       ├── Services/
│       │   ├── YtDlpService.cs
│       │   ├── DownloadService.cs
│       │   ├── QueueService.cs
│       │   ├── MetadataService.cs
│       │   ├── CookieService.cs
│       │   ├── HistoryService.cs
│       │   ├── SettingsService.cs
│       │   ├── UpdateService.cs
│       │   └── NotificationService.cs
│       │
│       ├── Models/
│       │   ├── DownloadItem.cs
│       │   ├── MediaMetadata.cs
│       │   ├── MediaFormat.cs
│       │   └── CookieProfile.cs
│       │
│       ├── Data/
│       │   ├── Database/
│       │   └── Preferences/
│       │
│       ├── Controls/
│       │   ├── DownloadCard.xaml
│       │   ├── ProgressCard.xaml
│       │   └── UrlInput.xaml
│       │
│       └── Utilities/
│
├── tests/
│
├── resources/
│   ├── Icons/
│   ├── Images/
│   └── AppIcon/
│
├── packaging/
│   ├── msix/
│   └── installer/
│
├── .editorconfig
├── Directory.Build.props
├── Glypdl.Windows.sln
└── README.md
```

Adapt the exact structure when necessary, but maintain strict Windows isolation.

---

# 6. Native Windows 11 UI

Use WinUI 3 and Windows 11 design principles.

Use:

* NavigationView
* NavigationViewItem
* CommandBar
* InfoBar
* ContentDialog
* ProgressBar
* TeachingTip where useful
* ComboBox
* ListView
* GridView
* Expander
* SettingsCards
* Acrylic/Mica where appropriate
* Windows 11 typography and spacing

The application must feel like a real Windows 11 application.

Do not simply copy the GTK4 interface.

---

# 7. Main Navigation

Use a Windows-style NavigationView.

Example:

```text
┌──────────────────────────────────────────────┐
│ Glypdl                                      │
├───────────────┬──────────────────────────────┤
│               │                              │
│ 🏠 Home       │                              │
│               │       Application            │
│ 📥 Downloads  │          Content             │
│               │                              │
│ 🕘 History    │                              │
│               │                              │
│ ⚙ Settings   │                              │
│               │                              │
│ ℹ About       │                              │
│               │                              │
└───────────────┴──────────────────────────────┘
```

Allow the navigation pane to collapse automatically on smaller windows.

---

# 8. Home Page

Create a prominent URL input.

Example:

```text
┌──────────────────────────────────────────────┐
│ Paste video URL...                       📋 │
└──────────────────────────────────────────────┘

                 [ Download ]
```

Support:

* paste
* Ctrl+V
* keyboard input
* Enter
* multiple URLs
* playlist URLs
* browser share/open-with behavior where appropriate

Do not automatically download clipboard URLs.

Require user confirmation.

---

# 9. Metadata Retrieval

When the user enters a URL:

```text
URL
 ↓
Validate
 ↓
yt-dlp metadata
 ↓
Thumbnail
 ↓
Available formats
 ↓
Download configuration
```

Retrieve metadata asynchronously.

Use yt-dlp JSON output.

For example:

```text
yt-dlp -J --no-warnings URL
```

Do not freeze the UI.

Display:

* title
* uploader
* duration
* thumbnail
* site
* available resolutions
* video/audio codecs
* file size
* format information

---

# 10. Download Modes

Support:

### Video

Video only.

### Audio

Audio only.

Formats:

```text
Best
MP3
M4A
Opus
FLAC
WAV
```

### Video + Audio

Download appropriate video and audio streams and merge them using FFmpeg when required.

Do not hard-code format IDs.

Use actual available formats from yt-dlp.

---

# 11. Format Selection

Normal UI:

```text
Download type
[ Video + Audio ▼ ]

Quality
[ 1080p ▼ ]
```

Advanced UI:

```text
Format ID
Container
Resolution
FPS
Codec
Bitrate
HDR
File size
```

Keep technical information hidden by default.

---

# 12. Download Card

Each download should have a dedicated card.

Example:

```text
┌──────────────────────────────────────────────┐
│ [Thumbnail] Linux Tutorial                   │
│              1080p • Video + Audio           │
│                                              │
│ █████████████████░░░░  78%                   │
│                                              │
│ 1.42 GB / 1.82 GB                            │
│ 9.7 MB/s                                     │
│ ETA: 41 seconds                              │
│                                              │
│ [Pause] [Cancel]                         ⋯ │
└──────────────────────────────────────────────┘
```

---

# 13. Accurate Progress

Never fake progress.

Use actual yt-dlp progress information.

Display:

* percentage
* downloaded bytes
* total bytes
* current speed
* average speed
* ETA
* elapsed time
* fragment progress
* current status

Correctly distinguish:

```text
Downloading
Processing
Merging
Converting
Completed
```

Example:

```text
100%

Merging video and audio...
```

then:

```text
✓ Completed
```

---

# 14. Speed Calculation

Use actual yt-dlp speed when available.

If required, calculate speed from:

```text
downloaded bytes
elapsed time
```

Use smoothing to prevent unstable display.

Example:

```text
9.8 MB/s
10.1 MB/s
9.9 MB/s
```

Do not claim this is total Windows network traffic.

It represents Glypdl's download throughput.

---

# 15. Download Queue

Support multiple downloads.

Example:

```text
Downloading
────────────────────

Linux Tutorial
78% • 9.7 MB/s

Queued
────────────────────

Python Tutorial
Docker Tutorial
Windows Tutorial
```

Settings:

```text
Maximum simultaneous downloads

1
2
3
4
5
```

Default to 2.

---

# 16. Background Downloads

Downloads should continue when the main window is minimized or not focused.

Do not depend on the UI remaining open.

Use an appropriate Windows background/task architecture.

For long-running downloads, ensure that:

* the process continues safely
* the UI can reconnect to download state
* progress persists
* completion is detected
* failures are recorded

If the user closes the application, clearly define whether downloads continue or are cancelled.

Prefer allowing downloads to continue when practical.

---

# 17. Windows Notifications

Use native Windows notifications.

Completion:

```text
Glypdl

Download complete

Linux Tutorial.mp4

[Open File]
```

Failure:

```text
Glypdl

Download failed

[Retry]
```

Do not spam notifications.

---

# 18. System yt-dlp

For the Windows application, design a Windows-specific yt-dlp integration.

Do NOT reuse the Linux executable path.

Do not assume:

```text
/usr/bin/yt-dlp
```

exists.

For Windows, detect yt-dlp appropriately.

Preferred architecture:

```text
Glypdl Windows
      ↓
YtDlpService
      ↓
yt-dlp.exe
```

Provide a configurable yt-dlp executable path.

If yt-dlp is not found, show:

```text
yt-dlp was not found

Glypdl requires yt-dlp.

[Locate yt-dlp.exe]
```

Do not silently download or replace the user's yt-dlp unless the user explicitly enables such functionality.

---

# 19. FFmpeg

Detect FFmpeg on Windows.

Allow:

```text
FFmpeg executable
```

to be configured.

If FFmpeg is missing when merging/conversion requires it:

```text
FFmpeg is required for this operation.

[Locate ffmpeg.exe]
```

Do not crash.

---

# 20. Security

Never execute:

```text
yt-dlp " + userUrl
```

through a shell.

Do not use:

```text
cmd.exe /c ...
```

with untrusted string concatenation.

Use process argument APIs safely.

Treat:

* URLs
* filenames
* cookie paths
* custom arguments

as untrusted input.

Never log credentials or cookies.

---

# 21. Cookies

Support standard `cookies.txt`.

UI:

```text
Cookies

[ ] Use cookies

Cookie profile
[ None ▼ ]

Cookie file
[ Select File ]
```

Pass the cookie file safely to yt-dlp.

Never:

* upload cookies
* send cookies to a server
* log cookies
* store cookie contents in the database
* display cookie contents
* commit cookie files
* include cookies in crash reports

---

# 22. Cookie Profiles

Support:

```text
Cookie Profiles

YouTube
cookies-youtube.txt

Other Site
cookies-other.txt

[+ Add Profile]
```

Store only:

```text
profile name
local file path
```

Never store cookie values.

---

# 23. Download Location

Allow the user to choose the default Windows download folder.

Use the appropriate Windows folder selection APIs.

Example:

```text
Download location

C:\Users\User\Downloads

[Choose Folder]
```

Do not hard-code a username.

---

# 24. Filename Template

Default:

```text
%(title)s.%(ext)s
```

Allow advanced customization.

Example:

```text
%(uploader)s - %(title)s.%(ext)s
```

---

# 25. History

Use a local database.

SQLite is acceptable.

Store:

```text
URL
Title
Uploader
Thumbnail
Status
Download path
Format
Resolution
File size
Duration
Created time
Completed time
```

Never store cookie contents.

History actions:

```text
Open File
Open Folder
Download Again
Copy URL
Share
Remove
Clear History
```

---

# 26. Settings

Create a native Windows Settings experience.

## Downloads

```text
Download location
Maximum simultaneous downloads
Start downloads automatically
Resume downloads
Filename template
Playlist folder behavior
```

## Appearance

```text
System
Light
Dark
```

Use Windows system theme where appropriate.

## Cookies

```text
Cookie profiles
Cookie file management
```

## Notifications

```text
Download completed
Download failed
```

## Advanced

```text
yt-dlp executable
FFmpeg executable
Additional yt-dlp arguments
Debug logging
```

Do not provide arbitrary shell execution.

---

# 27. Windows Dark/Light Mode

Support:

```text
System
Light
Dark
```

Default:

```text
System
```

Use Windows 11 theme resources and WinUI 3 theme support.

Preserve Glypdl branding:

```text
Purple → Blue
```

for the application icon and brand elements.

---

# 28. Thumbnail Cache

Cache thumbnails locally.

Use an appropriate Windows cache/application-data directory.

Do not repeatedly download the same thumbnail.

Clean old cache entries when appropriate.

---

# 29. Open File / Folder

After downloading:

```text
[Open File]
[Open Folder]
```

Use standard Windows APIs.

Do not assume a particular file manager.

---

# 30. Share / Copy URL

Provide:

```text
Copy URL
```

and appropriate Windows sharing support where practical.

---

# 31. Update Checker

Implement an update checker for GitHub Releases.

Architecture:

```text
Glypdl
   ↓
UpdateService
   ↓
GitHub Releases
   ↓
Latest version
   ↓
Version comparison
   ↓
Update available
```

Check periodically, such as once every 24 hours.

Also provide:

```text
Settings → About → Check for Updates
```

Example:

```text
Glypdl

Version 1.2.0

✓ You're using the latest version
```

or:

```text
Update available

Glypdl 1.3.0

What's new:
• Improved downloads
• Faster startup
• Bug fixes

[Later] [Update]
```

Do not blindly execute downloaded installers.

Verify that the update originates from the official release source.

---

# 32. Windows Installer

Support modern Windows packaging.

Primary goal:

```text
Glypdl-Setup-x64.exe
```

Provide a proper installer.

Also consider:

```text
MSIX
```

for Microsoft Store/modern Windows distribution.

The installer should:

* install Glypdl
* create Start Menu shortcut
* optionally create desktop shortcut
* register application metadata
* register uninstall information
* preserve user settings during updates
* support upgrades
* support uninstall
* not delete user downloads
* not delete user-created cookie files
* not delete important user data without confirmation

---

# 33. Architecture Support

Initially target:

```text
Windows 11 x64
```

Structure the code so ARM64 can potentially be added later.

Do not add ARM64 unless the build pipeline and dependencies actually support it.

---

# 34. Application Data

Use proper Windows application-data directories.

Separate:

```text
Configuration
Cache
Database
Logs
Downloads
```

Do not put application data randomly beside the executable.

Do not store:

```text
cookies.txt
```

inside the application installation directory.

---

# 35. About / Author Page

Create:

```text
About Glypdl
```

Display:

```text
[Glypdl Logo]

Glypdl

A lightweight native Windows media downloader

Version 1.0.0

Powered by yt-dlp

Built with:
C#
WinUI 3
Windows App SDK

Author

Suresh S

Developer / Project Author

[GitHub]
[Website]
[Report an Issue]

Open Source Licenses
Acknowledgements
```

Do not invent URLs.

Use official project URLs when available.

---

# 36. Keyboard Shortcuts

Support:

```text
Ctrl+V
Paste URL

Ctrl+L
Focus URL

Ctrl+H
History

Ctrl+,
Settings

Ctrl+Q
Quit
```

Use standard Windows conventions.

---

# 37. Accessibility

Support:

* Windows Narrator
* keyboard navigation
* accessible names
* proper focus behavior
* scalable text
* sufficient contrast
* meaningful progress information
* appropriate touch targets

---

# 38. Performance

Glypdl Windows must launch quickly.

Avoid:

* Electron
* browser engines
* unnecessary services
* heavy startup tasks
* unnecessary network requests

Do not fetch metadata during application startup.

The main window should appear immediately.

---

# 39. Windows Application Architecture

Use MVVM.

Recommended:

```text
View
 ↓
ViewModel
 ↓
Service
 ↓
Repository
 ↓
yt-dlp / FFmpeg / Database
```

Example:

```text
HomePage
   ↓
HomeViewModel
   ↓
MetadataService
   ↓
YtDlpService
   ↓
yt-dlp.exe
```

Download:

```text
DownloadsPage
   ↓
DownloadsViewModel
   ↓
DownloadService
   ↓
QueueService
   ↓
YtDlpService
   ↓
yt-dlp.exe
```

---

# 40. Project Isolation Rules

The following rules are mandatory.

### Rule 1

Windows source code must live inside:

```text
windows/
```

### Rule 2

Linux source code must remain inside its existing directories.

### Rule 3

Windows must not import Linux source modules.

### Rule 4

Linux must not import Windows source modules.

### Rule 5

Windows dependencies must not be added to Linux dependency files.

### Rule 6

Linux dependencies must not be added to Windows project files unless independently required.

### Rule 7

Windows CI must operate from:

```text
windows/
```

### Rule 8

Linux CI must continue operating from the existing Linux project.

### Rule 9

A Linux-only feature change must not require Windows code changes.

### Rule 10

A Windows-only feature change must not require Linux code changes.

### Rule 11

Do not refactor working Linux code simply to make Windows easier.

### Rule 12

If functionality needs to exist on both platforms, implement it independently using the native platform's architecture.

---

# 41. GitHub Actions

Create:

```text
.github/workflows/windows.yml
```

The workflow must build only the Windows application.

Example:

```text
Windows repository change
        ↓
GitHub Actions
        ↓
cd windows
        ↓
dotnet restore
        ↓
dotnet build
        ↓
dotnet test
        ↓
package
        ↓
Windows installer
```

Use path filtering where appropriate.

For example:

```text
windows/**
```

should trigger Windows CI.

Linux-only changes should not unnecessarily trigger the Windows build.

---

# 42. GitHub Release

The existing Linux release pipeline already produces:

```text
.deb
.rpm
Pacman
AppImage
source archives
```

Do not break it.

Add Windows artifacts independently.

Example:

```text
Glypdl v1.0.0

Linux
├── glypdl-1.0.0.deb
├── glypdl-1.0.0.rpm
├── glypdl-1.0.0.pkg.tar.zst
└── Glypdl-1.0.0-x86_64.AppImage

Windows
├── Glypdl-1.0.0-Setup-x64.exe
└── Glypdl-1.0.0-x64.msix
```

Use consistent versioning.

---

# 43. Testing

Create Windows-specific tests under:

```text
windows/tests/
```

Test:

```text
URL validation
yt-dlp detection
FFmpeg detection
metadata parsing
format parsing
progress parsing
speed calculation
ETA calculation
download queue
history
settings
cookie profiles
update checking
version comparison
```

Do not use live websites for normal unit tests.

Mock yt-dlp output.

---

# 44. Windows-Specific Testing

Test:

```text
Windows 11 x64
```

Verify:

* installer
* upgrade
* uninstall
* Start Menu integration
* application launch
* dark mode
* light mode
* system theme
* downloads
* background downloads
* notifications
* file opening
* folder opening
* cookies
* history
* settings persistence
* update checker

---

# 45. Development Phases

Build incrementally.

## Phase 1 — Project Setup

Create:

```text
windows/
```

and the WinUI 3 application.

Do not modify Linux application code.

---

## Phase 2 — Native UI Shell

Implement:

* MainWindow
* NavigationView
* Home
* Downloads
* History
* Settings
* About

Use mock data initially.

---

## Phase 3 — yt-dlp Integration

Implement Windows-specific `YtDlpService`.

Verify:

```text
yt-dlp.exe
 ↓
metadata
```

Then:

```text
yt-dlp.exe
 ↓
download
```

---

## Phase 4 — Metadata UI

Implement:

```text
URL
 ↓
metadata
 ↓
thumbnail
 ↓
format selection
```

---

## Phase 5 — Download Engine

Implement:

* video
* audio
* video + audio
* FFmpeg
* progress
* speed
* ETA
* cancellation

---

## Phase 6 — Queue

Implement:

* multiple downloads
* concurrency
* queue persistence
* retry

---

## Phase 7 — Background Downloads

Implement appropriate Windows background execution.

---

## Phase 8 — History

Implement database and history UI.

---

## Phase 9 — Cookies

Implement:

* cookies.txt
* cookie profiles
* secure local handling

---

## Phase 10 — Settings

Implement:

* download path
* appearance
* notifications
* yt-dlp path
* FFmpeg path
* advanced settings

---

## Phase 11 — Notifications

Implement Windows notifications.

---

## Phase 12 — Update Manager

Implement GitHub Release update checking.

---

## Phase 13 — About / Author

Implement final About page.

---

## Phase 14 — Packaging

Implement:

```text
EXE installer
MSIX
```

---

## Phase 15 — CI/CD

Add Windows GitHub Actions.

---

## Phase 16 — Final Testing

Verify that:

```text
Linux application
```

continues to build and work exactly as before.

Verify that:

```text
Windows application
```

builds independently.

---

# 46. Final Repository Architecture

The final repository should look approximately like:

```text
Glypdl/
│
├── src/                       ← LINUX ONLY
│
├── tests/                     ← LINUX ONLY
│
├── data/                      ← LINUX ONLY
│
├── packaging/                 ← LINUX ONLY
│   ├── deb/
│   ├── rpm/
│   ├── arch/
│   ├── flatpak/
│   └── appimage/
│
├── windows/                   ← WINDOWS ONLY
│   │
│   ├── src/
│   │   └── Glypdl.Windows/
│   │
│   ├── tests/
│   │
│   ├── resources/
│   │
│   ├── packaging/
│   │   ├── msix/
│   │   └── installer/
│   │
│   ├── Glypdl.Windows.sln
│   ├── README.md
│   └── ...
│
├── .github/
│   └── workflows/
│       ├── linux.yml
│       └── windows.yml
│
├── README.md
└── LICENSE
```

---

# 47. Final Platform Architecture

The final project must conceptually be:

```text
                         GLYPDL
                           │
              ┌────────────┴────────────┐
              │                         │
            Linux                    Windows
              │                         │
       GTK4 + libadwaita             WinUI 3
              │                         │
          Python                       C#
              │                         │
       Linux Services            Windows Services
              │                         │
          yt-dlp                     yt-dlp
              │                         │
          FFmpeg                     FFmpeg
```

These are **two independent applications**.

They share:

* Glypdl name
* Glypdl branding
* logo
* project documentation
* project website
* GitHub repository
* issue tracker
* license
* overall product vision

They do NOT share:

* UI implementation
* platform-specific services
* platform-specific packaging
* platform-specific build configuration
* platform-specific dependencies
* platform-specific filesystem logic
* platform-specific process management

---

# 48. Critical Requirements

These requirements are non-negotiable:

1. **Do not break the existing Linux application.**
2. **Create `windows/` at the repository root.**
3. **All Windows-specific code must remain inside `windows/`.**
4. **Linux code must remain in its existing directories.**
5. **No Linux → Windows imports.**
6. **No Windows → Linux imports.**
7. **No GTK4 UI on Windows.**
8. **Use C# + WinUI 3 + Windows App SDK.**
9. **Use MVVM.**
10. **Use native Windows 11 UI patterns.**
11. **Use yt-dlp as the download engine.**
12. **Use Windows-specific yt-dlp executable handling.**
13. **Support video downloads.**
14. **Support audio downloads.**
15. **Support video + audio downloads.**
16. **Support thumbnails.**
17. **Support playlists where yt-dlp supports them.**
18. **Support cookies.txt.**
19. **Support cookie profiles.**
20. **Keep cookies local and secure.**
21. **Support download queue.**
22. **Support real-time progress.**
23. **Show accurate speed.**
24. **Show downloaded/total size.**
25. **Show ETA.**
26. **Support cancellation.**
27. **Implement pause only if technically reliable.**
28. **Support download history.**
29. **Support configurable download directory.**
30. **Support dark/light/system themes.**
31. **Support Windows notifications.**
32. **Support Open File/Open Folder.**
33. **Support GitHub Release update checking.**
34. **Add About/Author page.**
35. **Provide Windows installer.**
36. **Provide MSIX where practical.**
37. **Create Windows-specific tests.**
38. **Create Windows-specific GitHub Actions.**
39. **Do not modify Linux dependencies for Windows.**
40. **Do not modify Linux architecture to accommodate Windows.**
41. **A Windows-only change must remain Windows-only.**
42. **A Linux-only change must remain Linux-only.**
43. **Keep the two applications independently buildable.**
44. **Keep startup fast and resource usage reasonable.**
45. **Use safe process argument handling and never concatenate untrusted URLs into shell commands.**

---

# 49. Final Objective

The final result must be:

> **Glypdl for Windows 11 — a native, lightweight, fast Windows media downloader powered by yt-dlp.**

The existing Linux Glypdl must continue working exactly as before.

The Windows application must be developed independently under:

```text
windows/
```

so that both applications can evolve independently without code collision.

The final repository should support:

```text
Linux
  → GTK4/libadwaita
  → Python
  → DEB/RPM/Pacman/AppImage/Flatpak

Windows
  → WinUI 3
  → C#
  → EXE/MSIX
```

One Glypdl project.

Two independent native applications.
