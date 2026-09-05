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

sealed interface MediaAnalysisResult {
    data class SingleMedia(val mediaInfo: MediaInfo) : MediaAnalysisResult
    data class Playlist(val playlistInfo: PlaylistInfo) : MediaAnalysisResult
}
