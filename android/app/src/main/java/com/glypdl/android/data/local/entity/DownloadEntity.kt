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
import com.glypdl.android.data.model.DownloadStatus

/**
 * Room database entity representing a media download task in the `downloads` table.
 *
 * Tracks the complete lifecycle of a download including request metadata, live progress metrics,
 * temporary and final storage paths, error messages, and technical diagnostic logs.
 *
 * @property id Unique UUID identifier for the download task.
 * @property url Webpage or direct media URL being downloaded.
 * @property title Sanitized title of the video or audio clip.
 * @property thumbnailUrl Remote HTTP URL or local URI of the media thumbnail image.
 * @property formatId yt-dlp format identifier or selector string (e.g., "137+140", "best").
 * @property ext File extension of the output container (e.g., "mp4", "m4a", "mkv").
 * @property resolution User-facing resolution or bitrate label (e.g., "1080p", "320 kbps").
 * @property isAudioOnly True if the download is strictly audio extraction/transcoding.
 * @property status Current execution state ([DownloadStatus]).
 * @property progress Percentage of download completed (0.0f to 100.0f).
 * @property downloadedBytes Number of bytes downloaded so far.
 * @property totalBytes Expected total bytes from HTTP Content-Length, or 0 if unknown/chunked.
 * @property speed Current download bandwidth speed string formatted by yt-dlp (e.g., "4.2MiB/s").
 * @property filePath Absolute staging file path or Scoped Storage content URI when finished.
 * @property errorMessage User-friendly summary message if the download encounters a failure.
 * @property technicalLog Redacted diagnostic log / stack trace for technical error inspection.
 * @property createdAt Epoch timestamp in milliseconds when the download was enqueued.
 * @property updatedAt Epoch timestamp in milliseconds when the download was last updated.
 */
@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val formatId: String,
    val ext: String,
    val resolution: String?,
    val isAudioOnly: Boolean,
    val status: DownloadStatus,
    val progress: Float,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val speed: String?,
    val filePath: String?,
    val errorMessage: String?,
    val technicalLog: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
