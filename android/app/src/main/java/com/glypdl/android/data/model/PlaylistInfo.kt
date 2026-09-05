/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.model

data class PlaylistItem(
    val id: String,
    val url: String,
    val title: String,
    val duration: Long = 0L,
    val thumbnailUrl: String? = null,
    val uploader: String? = null,
    val isAvailable: Boolean = true,
    val statusMessage: String? = null
) {
    val displayDuration: String
        get() {
            if (duration <= 0L) return ""
            val minutes = duration / 60
            val seconds = duration % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}

data class PlaylistInfo(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val entries: List<PlaylistItem> = emptyList()
) {
    val totalCount: Int get() = entries.size
    val availableCount: Int get() = entries.count { it.isAvailable }
    val unavailableCount: Int get() = entries.count { !it.isAvailable }
}
