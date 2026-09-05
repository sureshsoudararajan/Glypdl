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
