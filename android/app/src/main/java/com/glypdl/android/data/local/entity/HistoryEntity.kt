/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room database entity representing a completed download log entry in the `history` table.
 *
 * Stored permanently even if the active task is cleared from the download queue,
 * enabling users to search, open, or re-verify past downloaded media.
 *
 * @property id Auto-incremented primary key.
 * @property downloadId The original task UUID associated with the download.
 * @property url Original media URL that was downloaded.
 * @property title Title of the media file.
 * @property thumbnailUrl Cached or remote URL of the media thumbnail image.
 * @property format Selected format and quality description (e.g. "1080p • MP4").
 * @property filePath Local absolute filesystem path (if accessible).
 * @property fileUri Android content URI string (e.g., `content://media/external/...`) for Scoped Storage.
 * @property fileSize Final verified file size on disk in bytes.
 * @property duration Media duration in seconds (if known).
 * @property completedAt Epoch timestamp in milliseconds when the download completed.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val format: String,
    val filePath: String?,
    val fileUri: String?,
    val fileSize: Long,
    val duration: Long?,
    val completedAt: Long
)
