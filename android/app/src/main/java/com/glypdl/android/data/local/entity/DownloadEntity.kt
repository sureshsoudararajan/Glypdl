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
