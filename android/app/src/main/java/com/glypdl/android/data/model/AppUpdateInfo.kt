/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.model

/**
 * Information model holding GitHub release metadata for the Glypdl Android application.
 *
 * Used by the in-app update checker to prompt users when a newer release of the Glypdl APK
 * is available on GitHub releases.
 *
 * @property currentVersion The currently installed version name (e.g., "1.2.1").
 * @property latestVersion The latest release version tag published on GitHub (e.g., "1.2.2").
 * @property isUpdateAvailable True if [latestVersion] is strictly newer than [currentVersion].
 * @property releaseUrl Web URL to the GitHub release page (for viewing release notes & all APK assets).
 * @property releaseTitle Title of the release from GitHub (e.g., "Glypdl v1.2.2").
 * @property releaseNotes Markdown description or changelog published with the release.
 * @property apkDownloadUrl Direct asset download URL for the architecture-matching APK, if found.
 */
data class AppUpdateInfo(
    val currentVersion: String,
    val latestVersion: String,
    val isUpdateAvailable: Boolean,
    val releaseUrl: String,
    val releaseTitle: String? = null,
    val releaseNotes: String? = null,
    val apkDownloadUrl: String? = null
)
