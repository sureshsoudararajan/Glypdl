/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
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

package com.glypdl.android.data.model

enum class MediaKind {
    PROGRESSIVE_VIDEO,
    PROGRESSIVE_AUDIO,
    HLS,
    DASH
}

enum class DetectionSource {
    NETWORK,
    DOM,
    PROBE
}

data class DetectedMedia(
    val id: String,
    val url: String,
    val pageUrl: String,
    val pageTitle: String?,
    val kind: MediaKind,
    val mime: String? = null,
    val ext: String? = null,
    val sizeBytes: Long? = null,
    val durationMs: Long? = null,
    val isLive: Boolean = false,
    val isDrmProtected: Boolean = false,
    val posterUrl: String? = null,
    val variants: List<MediaVariant> = emptyList(),
    val headers: Map<String, String> = emptyMap(),
    val source: DetectionSource = DetectionSource.NETWORK,
    val detectedAt: Long = System.currentTimeMillis()
)

data class MediaVariant(
    val label: String,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Int? = null,
    val bandwidthBps: Long? = null,
    val codecs: String? = null,
    val estimatedSizeBytes: Long? = null,
    val url: String? = null,
    val audioGroup: String? = null,
    val language: String? = null,
    val isAudioOnly: Boolean = false
)
