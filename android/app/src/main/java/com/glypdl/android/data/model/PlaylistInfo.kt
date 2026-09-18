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

/**
 * Metadata model representing an individual track/entry within a multi-video playlist or channel feed.
 *
 * @property id Unique media ID of the item within the playlist.
 * @property url Direct playable or webpage URL of the item.
 * @property title Title of the video or track.
 * @property duration Duration in seconds (0 if live stream or unknown).
 * @property thumbnailUrl Thumbnail image URL.
 * @property uploader Channel or artist name.
 * @property isAvailable False if the video was deleted, geo-restricted, or private.
 * @property statusMessage Optional explanatory status for unavailable videos.
 */
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
    /** Formats [duration] into MM:SS display time. */
    val displayDuration: String
        get() {
            if (duration <= 0L) return ""
            val minutes = duration / 60
            val seconds = duration % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}

/**
 * Metadata model representing a parsed playlist, album, or multi-video collection.
 *
 * @property id Playlist identifier from the provider.
 * @property url Webpage URL of the playlist.
 * @property title Playlist title.
 * @property uploader Author or curator of the playlist.
 * @property thumbnailUrl Cover image URL of the playlist.
 * @property entries Ordered list of [PlaylistItem] entries.
 */
data class PlaylistInfo(
    val id: String,
    val url: String,
    val title: String,
    val uploader: String? = null,
    val thumbnailUrl: String? = null,
    val entries: List<PlaylistItem> = emptyList()
) {
    /** Total number of items in the playlist. */
    val totalCount: Int get() = entries.size

    /** Number of playable/accessible items. */
    val availableCount: Int get() = entries.count { it.isAvailable }

    /** Number of items that cannot be downloaded due to restrictions or deletion. */
    val unavailableCount: Int get() = entries.count { !it.isAvailable }
}
