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

/**
 * Snapshot model emitted continuously during an ongoing media download.
 *
 * Broadcast to UI components (cards, progress bars, notifications) via reactive StateFlow
 * in DownloadManager to reflect real-time network throughput and progress.
 *
 * @property downloadId UUID identifier of the associated download task.
 * @property percent Completion percentage in range 0.0f to 100.0f.
 * @property downloadedBytes Amount of data received and written to disk so far.
 * @property totalBytes Expected total file size from server headers, or 0 if unknown/streaming.
 * @property speed Human-readable download rate formatted by yt-dlp (e.g., "5.4MiB/s", "Merging media...").
 * @property eta Estimated time remaining formatted as a duration string (e.g., "00:45").
 * @property title Title of the downloading item for display in notification banners.
 */
data class DownloadProgress(
    val downloadId: String,
    val percent: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: String,
    val eta: String,
    val title: String = ""
)
