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
@file:Suppress("DEPRECATION")
package com.glypdl.android.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.glypdl.android.data.model.FileCategory

/**
 * A reusable icon component that renders the appropriate Material Icon for a given [FileCategory].
 */
@Composable
fun FileTypeIcon(
    category: FileCategory,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary
) {
    val icon = when (category) {
        FileCategory.ARCHIVE -> Icons.Default.FolderZip
        FileCategory.DISK_IMAGE -> Icons.Default.Album
        FileCategory.APPLICATION -> Icons.Default.Android
        FileCategory.DOCUMENT -> Icons.Default.Description
        FileCategory.IMAGE -> Icons.Default.Image
        FileCategory.AUDIO -> Icons.Default.AudioFile
        FileCategory.VIDEO -> Icons.Default.VideoFile
        FileCategory.CODE -> Icons.Default.Code
        FileCategory.DATA -> Icons.Default.DataObject
        FileCategory.FONT -> Icons.Default.FontDownload
        FileCategory.UNKNOWN -> Icons.Default.InsertDriveFile
    }

    Icon(
        imageVector = icon,
        contentDescription = category.name,
        modifier = modifier,
        tint = tint
    )
}
