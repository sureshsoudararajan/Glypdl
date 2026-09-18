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
 * Discriminated result representing the outcome of URL extraction and analysis.
 * Differentiates between standalone individual media items and multi-track playlists/albums.
 */
sealed interface MediaAnalysisResult {

    /**
     * Analysis produced an individual video or audio track.
     *
     * @property mediaInfo Extracted metadata and available resolution/quality tiers.
     */
    data class SingleMedia(val mediaInfo: MediaInfo) : MediaAnalysisResult

    /**
     * Analysis produced a multi-item playlist, album, or series.
     *
     * @property playlistInfo Extracted metadata and list of constituent media entries.
     */
    data class Playlist(val playlistInfo: PlaylistInfo) : MediaAnalysisResult
}
