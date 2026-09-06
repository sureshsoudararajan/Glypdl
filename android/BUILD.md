# Glypdl Android — Build, Packaging & Architecture Guide

This document describes the Android architecture, build configurations, APK size breakdown, and repository distribution considerations (including IzzyOnDroid / F-Droid and GitHub Releases).

---

## 1. Supported Architectures & ABIs

Glypdl Android produces per-architecture split APKs using standard Android Gradle ABI splits:

| ABI | Architecture | Target Devices | APK Name |
|---|---|---|---|
| `arm64-v8a` | 64-bit ARM | 90%+ modern Android phones & tablets (Android 8.0+) | `app-arm64-v8a-release.apk` |
| `armeabi-v7a` | 32-bit ARM | Legacy Android devices (32-bit ARMv7) | `app-armeabi-v7a-release.apk` |
| `x86_64` | 64-bit Intel/AMD | Android Studio Emulators, ChromeOS, x86 tablets | `app-x86_64-release.apk` |

Universal fat APKs are disabled (`isUniversalApk = false`) to prevent bundling 4 copies of Python and FFmpeg native runtimes, which previously inflated the APK to ~239 MB.

---

## 2. APK Size Audit & Breakdown

### Baseline (Original Universal Debug APK: 239.6 MB / 228.5 MiB)
* **All 4 ABIs bundled together**: `x86`, `x86_64`, `armeabi-v7a`, `arm64-v8a`
* **Unused `aria2c` dependency included**: +19.68 MB
* **Debug Multidex (16 unminified `classes*.dex` files)**: +18.06 MB compressed (+59.7 MB uncompressed)
* **Unused Termux libraries in FFmpeg**: +132.68 MB across all 4 ABIs

### Optimized Architecture-Specific Release APKs (`v1.2.1`)

| Artifact | ABI | File Size (Bytes) | Size (MB) | Status |
|---|---|---|---|---|
| `app-arm64-v8a-release.apk` | `arm64-v8a` | **66,788,922 B** | **63.69 MB** | Signed & 100% Stable |
| `app-armeabi-v7a-release.apk` | `armeabi-v7a` | **60,135,512 B** | **57.35 MB** | Signed & 100% Stable |
| `app-x86_64-release.apk` | `x86_64` | **69,780,380 B** | **66.54 MB** | Signed & 100% Stable |

### Exact Component Breakdown (`arm64-v8a` Release APK: 55.97 MB)

```text
app-arm64-v8a-release.apk (55,967,927 bytes)
├── lib/arm64-v8a/libffmpeg.zip.so       35,438,741 B (33.80 MiB)  [63.3%]  FFmpeg 6.1 + audio/video codecs
├── lib/arm64-v8a/libpython.zip.so       14,052,725 B (13.40 MiB)  [25.1%]  Python 3.11 runtime + stdlib
├── res/raw/ytdlp (obfuscated)            3,170,726 B ( 3.02 MiB)  [ 5.7%]  Complete yt-dlp wheel package
├── classes.dex (R8 Minified)             2,086,447 B ( 1.99 MiB)  [ 3.7%]  Entire UI, DB, Hilt, Services
├── lib/arm64-v8a/libqjs.so                 434,446 B ( 0.41 MiB)  [ 0.8%]  QuickJS JavaScript engine
├── resources.arsc                          280,864 B ( 0.27 MiB)  [ 0.5%]  Shrunk Android resources
├── lib/arm64-v8a/libffmpeg.so              140,601 B ( 0.13 MiB)  [ 0.2%]  FFmpeg launcher ELF executable
├── lib/arm64-v8a/libffprobe.so              79,152 B ( 0.08 MiB)  [ 0.1%]  FFprobe launcher ELF executable
└── Manifest, assets & icons                284,950 B ( 0.27 MiB)  [ 0.5%]  Metadata & drawables
```

> **Key Architectural Takeaway**:
> The entire Glypdl Android application — including the Python 3 runtime, complete yt-dlp engine, QuickJS extractor helper, Room database, Jetpack Compose UI, and resources — weighs **only 19.52 MiB**.
> The remaining **33.80 MiB** is the bundled FFmpeg environment (`libffmpeg.zip.so`) required for muxing separate audio/video streams (such as YouTube 1080p/4K DASH formats).

---

## 3. How to Build

From the root of the project or inside `android/`:

### Prerequisites
* JDK 17+
* Android SDK with platform-tools and build-tools (compileSdk 34)

### Build Release APKs (All Architectures)
```bash
cd android
./gradlew assembleRelease
```
The output APKs will be located at:
* `android/app/build/outputs/apk/release/app-arm64-v8a-release.apk`
* `android/app/build/outputs/apk/release/app-armeabi-v7a-release.apk`
* `android/app/build/outputs/apk/release/app-x86_64-release.apk`

### Run Release Unit Tests
```bash
./gradlew testReleaseUnitTest
```

---

## 4. Packaging Optimizations Implemented

1. **ABI Splits**: Removed `ndk.abiFilters` that forced fat universal APK compilation. Configured `splits.abi` for `arm64-v8a`, `armeabi-v7a`, and `x86_64`.
2. **Removed Unused `aria2c`**: Purged `com.github.yausername.youtubedl-android:aria2c` from dependencies, saving ~5 MB per ABI (~20 MB total).
3. **R8 Code & Resource Shrinking**:
   * Enabled `isMinifyEnabled = true` and `isShrinkResources = true` in `buildTypes.release`.
   * Shrunk DEX bytecode from 16 files / 18.06 MiB down to a single minified `classes.dex` of 1.99 MiB.
   * Stripped unused resources and drawables.
4. **Targeted ProGuard Keep Rules**: Configured keep rules for `youtubedl-android`, `com.yausername.ffmpeg`, Room database entities, and Jackson deserializers.
5. **Fixed Lint & Backup Configurations**: Corrected invalid `<exclude domain="no_backup" />` entries in `backup_rules.xml` and `data_extraction_rules.xml`.
6. **V2 Scheme Signing**: Configured release builds to produce validly signed APKs.

---

## 5. IzzyOnDroid & F-Droid Submission Considerations

* **Initial Decline Reason**: The original `Glypdl-1.2.0.apk` was 239 MB because it was an uncompressed debug fat universal APK bundling 4 architectures and unused aria2c binaries.
* **IzzyOnDroid Policy**: IzzyOnDroid maintains a 30 MB guideline due to server bandwidth limits. However, per repository documentation, IzzyOnDroid regularly grants exceptions or supports **per-architecture splits** for yt-dlp/FFmpeg clients (such as Seal at ~41 MB).
* **F-Droid vs. IzzyOnDroid**:
  * For IzzyOnDroid: Submitting `app-arm64-v8a-release.apk` (53 MB) with an explanation that it packages self-contained, GPL-compliant native FFmpeg + Python for complete offline yt-dlp functionality aligns with how other yt-dlp clients (e.g. Seal) are hosted.
  * In the GitHub Release, naming the files `Glypdl-1.2.0-arm64-v8a.apk`, `Glypdl-1.2.0-armeabi-v7a.apk`, and `Glypdl-1.2.0-x86_64.apk` allows repository aggregators to pick the architecture-specific APK directly.
