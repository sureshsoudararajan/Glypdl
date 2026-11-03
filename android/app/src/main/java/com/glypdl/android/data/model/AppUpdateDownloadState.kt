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

import java.io.File

/**
 * Progress and state model for the in-app application updater.
 */
data class AppUpdateDownloadState(
    val isDownloading: Boolean = false,
    val progress: Float = 0f,
    val downloadedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val speed: String = "",
    val error: String? = null,
    val downloadedApkFile: File? = null,
    val isReadyToInstall: Boolean = false
)
