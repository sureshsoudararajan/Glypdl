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

package com.glypdl.android.service.browser

import android.net.Uri
import com.glypdl.android.data.model.MediaKind
import java.util.Locale

object MediaClassifier {

    private val VIDEO_EXTENSIONS = setOf(
        "mp4", "m4v", "webm", "mkv", "mov", "3gp", "flv", "avi", "wmv"
    )

    private val AUDIO_EXTENSIONS = setOf(
        "mp3", "m4a", "aac", "ogg", "opus", "flac", "wav"
    )

    private val HLS_EXTENSIONS = setOf(
        "m3u8", "m3u"
    )

    private val DASH_EXTENSIONS = setOf(
        "mpd"
    )

    private val REJECTED_SEGMENT_EXTENSIONS = setOf(
        "ts", "m4s", "cmfv", "cmfa"
    )

    private val REJECTED_STATIC_EXTENSIONS = setOf(
        "jpg", "jpeg", "png", "webp", "gif", "svg", "ico",
        "woff", "woff2", "ttf", "eot", "css", "js", "json", "html", "htm", "xml"
    )

    private val AD_ANALYTICS_PATTERNS = listOf(
        "google-analytics", "googlesyndication", "doubleclick",
        "adnxs", "adsystem", "scorecardresearch", "clarity.ms",
        "facebook.com/tr/", "ads.", "/ads/", "telemetry", "tracking"
    )

    /**
     * Determines whether the given URL is a known ad or tracking beacon.
     */
    fun isAdOrTrackingUrl(url: String): Boolean {
        val lower = url.lowercase(Locale.ROOT)
        return AD_ANALYTICS_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Determines whether the candidate URL is a segment, fragment, or chunk that should not be listed as standalone media.
     */
    fun isSegmentOrChunk(url: String): Boolean {
        val path = extractPath(url).lowercase(Locale.ROOT)
        val ext = extractExtension(path)

        if (REJECTED_SEGMENT_EXTENSIONS.contains(ext)) {
            return true
        }

        // HLS/DASH chunk naming patterns: e.g., segment-1.mp4, chunk-12.m4a, init.mp4, frag-1.mp4
        val filename = path.substringAfterLast('/')
        val chunkPatterns = listOf(
            Regex("(?i)(?:seg|segment|chunk|frag|part)[-_]?[0-9]+"),
            Regex("(?i)^init[-_.]"),
            Regex("(?i)[-_.]init\\.")
        )
        return chunkPatterns.any { it.containsMatchIn(filename) }
    }

    /**
     * Extracts clean extension from path, ignoring query parameters.
     */
    fun extractExtension(pathOrUrl: String): String {
        val clean = pathOrUrl.substringBefore('?').substringBefore('#')
        val lastDot = clean.lastIndexOf('.')
        val lastSlash = clean.lastIndexOf('/')
        return if (lastDot > lastSlash && lastDot != -1) {
            clean.substring(lastDot + 1).lowercase(Locale.ROOT)
        } else {
            ""
        }
    }

    private fun extractPath(url: String): String {
        return try {
            val uri = Uri.parse(url)
            uri.path ?: url.substringBefore('?')
        } catch (_: Exception) {
            url.substringBefore('?')
        }
    }

    /**
     * Classifies a candidate media request by its URL and optional MIME type.
     * Returns null if the URL is not a media candidate, or if it is a segment/ad.
     */
    fun classify(url: String, mimeType: String? = null): MediaKind? {
        if (url.isBlank()) return null

        val scheme = url.substringBefore("://").lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") {
            return null
        }

        if (isAdOrTrackingUrl(url)) {
            return null
        }

        if (isSegmentOrChunk(url)) {
            return null
        }

        val path = extractPath(url)
        val ext = extractExtension(path)

        if (REJECTED_STATIC_EXTENSIONS.contains(ext)) {
            return null
        }

        // 1. Check extension
        when {
            HLS_EXTENSIONS.contains(ext) -> return MediaKind.HLS
            DASH_EXTENSIONS.contains(ext) -> return MediaKind.DASH
            VIDEO_EXTENSIONS.contains(ext) -> return MediaKind.PROGRESSIVE_VIDEO
            AUDIO_EXTENSIONS.contains(ext) -> return MediaKind.PROGRESSIVE_AUDIO
        }

        // 2. Check MIME type if provided
        if (!mimeType.isNullOrBlank()) {
            val cleanMime = mimeType.lowercase(Locale.ROOT).substringBefore(';').trim()
            when {
                cleanMime == "application/x-mpegurl" || cleanMime == "application/vnd.apple.mpegurl" -> return MediaKind.HLS
                cleanMime == "application/dash+xml" -> return MediaKind.DASH
                cleanMime.startsWith("video/") -> return MediaKind.PROGRESSIVE_VIDEO
                cleanMime.startsWith("audio/") -> return MediaKind.PROGRESSIVE_AUDIO
            }
        }

        // 3. Check query parameters for embedded hints (e.g., ?mime=video%2Fmp4, ?format=m3u8)
        val lowerUrl = url.lowercase(Locale.ROOT)
        when {
            lowerUrl.contains("format=m3u8") || lowerUrl.contains(".m3u8") -> return MediaKind.HLS
            lowerUrl.contains("format=mpd") || lowerUrl.contains(".mpd") -> return MediaKind.DASH
            lowerUrl.contains("mime=video") || lowerUrl.contains("video/mp4") -> return MediaKind.PROGRESSIVE_VIDEO
            lowerUrl.contains("mime=audio") || lowerUrl.contains("audio/mp4") -> return MediaKind.PROGRESSIVE_AUDIO
        }

        return null
    }
}
