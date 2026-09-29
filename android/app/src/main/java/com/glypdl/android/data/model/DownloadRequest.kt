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

import com.glypdl.android.data.model.DownloadType
import com.glypdl.android.data.model.EngineType
import com.glypdl.android.data.model.FileCategory

/**
 * Immutable command request created by the UI or share intent handlers
 * to enqueue a new download task in [DownloadManager].
 *
 * @property id Unique UUID identifying this download execution.
 * @property url Webpage or direct media stream URL to download from.
 * @property title Title of the media used for file naming and notifications.
 * @property thumbnailUrl Remote or local thumbnail image URI for display in queues.
 * @property formatId Format selector string passed to yt-dlp `-f` argument (e.g., "137+140/best").
 * @property ext Target media file extension (e.g., "mp4", "m4a", "mkv").
 * @property resolution Display resolution or quality preset (e.g., "1080p", "Best").
 * @property isAudioOnly When true, signals that only audio should be extracted/kept.
 * @property destinationUri Optional custom target destination content tree URI (e.g. SAF folder).
 * @property downloadType Type of download.
 * @property engineType Engine to use.
 * @property fileCategory Category of the file.
 * @property fileName Custom file name to use.
 * @property contentLength Expected file size if known.
 * @property detectedMimeType MIME type if known beforehand.
 */
data class DownloadRequest(
    val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val isAudioOnly: Boolean,
    val destinationUri: String?,
    val downloadType: DownloadType = DownloadType.MEDIA,
    val engineType: EngineType = EngineType.YT_DLP,
    val fileCategory: FileCategory = FileCategory.UNKNOWN,
    val fileName: String? = null,
    val contentLength: Long? = null,
    val detectedMimeType: String? = null,
    val selectedFileIndices: List<Int>? = null
)
