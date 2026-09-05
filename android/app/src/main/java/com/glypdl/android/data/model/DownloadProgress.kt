/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.model

data class DownloadProgress(
    val downloadId: String,
    val percent: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: String,
    val eta: String,
    val title: String = ""
)
