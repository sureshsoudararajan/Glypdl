/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a downloaded or imported subtitle track in the database.
 *
 * Supports querying by associated download ID, language, or search query.
 */
@Entity(
    tableName = "subtitle_files",
    indices = [
        Index("downloadId"),
        Index("languageCode"),
        Index("createdAt")
    ]
)
data class SubtitleFileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val downloadId: String?,
    val mediaTitle: String,
    val mediaUrl: String?,
    val languageCode: String,
    val languageName: String,
    val format: String,
    val subtitleType: String, // MANUAL, AUTOMATIC
    val fileUri: String,
    val filePath: String?,
    val fileName: String,
    val sizeBytes: Long,
    val createdAt: Long,
    val isEmbedded: Boolean = false
)
