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
package com.glypdl.android.data.status.model

import android.net.Uri

data class StatusMedia(
    val id: String,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val mediaType: StatusMediaType,
    val sizeBytes: Long,
    val modifiedAt: Long,
    val isSaved: Boolean = false,
    val sourcePackage: String = "com.whatsapp"
)
