# Glypdl for Windows 11

A modern, fast, and native Windows 11 desktop media downloader for **yt-dlp** built with **C#**, **.NET 8**, **WinUI 3**, and **Windows App SDK**.

---

## Architecture & Principles

* **100% Native Windows 11**: WinUI 3, Mica material, Windows 11 Fluent design system, and toast notifications.
* **MVVM Design**: Clean separation of UI, business logic, and services powered by `CommunityToolkit.Mvvm`.
* **Zero Dependencies Required**: Bundles `yt-dlp.exe` and auto-provisions `ffmpeg.exe` safely.
* **Isolated Architecture**: Operates exclusively within `windows/` with zero impact on the Linux GTK4 application.

---

## Building from Source

### Prerequisites
* Windows 10 (version 1809+) or Windows 11
* [.NET 8.0 SDK](https://dotnet.microsoft.com/download/dotnet/8.0)
* [Visual Studio 2022](https://visualstudio.microsoft.com/) with **.NET Desktop Development** and **Windows App SDK C# Templates**

### Build Commands
```cmd
# 1. Restore dependencies
dotnet restore windows/Glypdl.Windows.sln

# 2. Run unit tests
dotnet test windows/tests/Glypdl.Windows.Tests/Glypdl.Windows.Tests.csproj

# 3. Publish standalone 64-bit binary
dotnet publish windows/src/Glypdl.Windows/Glypdl.Windows.csproj -c Release -r win-x64 --self-contained true -o windows/publish
```

---

## Installer Generation (Inno Setup)

Generate `Glypdl-1.0.0-Setup-x64.exe`:
```cmd
iscc windows/packaging/inno/setup.iss
```
Output installer will be placed in `windows/dist/`.
