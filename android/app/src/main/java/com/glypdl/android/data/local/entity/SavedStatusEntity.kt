/*
 * Copyright (c) 2026. Glypdl Contributors
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
 */
package com.glypdl.android.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_statuses",
    indices = [
        Index(value = ["originalUri"]),
        Index(value = ["savedAt"])
    ]
)
data class SavedStatusEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val originalUri: String,
    val savedUri: String,
    val displayName: String,
    val mimeType: String,
    val mediaType: String, // "IMAGE" or "VIDEO"
    val sizeBytes: Long,
    val savedAt: Long,
    val contentHash: String? = null,
    val sourcePackage: String = "com.whatsapp"
)
