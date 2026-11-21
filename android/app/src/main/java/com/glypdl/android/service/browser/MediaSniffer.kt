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

import com.glypdl.android.data.model.DetectedMedia
import com.glypdl.android.data.model.DetectionSource
import com.glypdl.android.data.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaSniffer @Inject constructor(
    private val mediaProber: MediaProber
) {
    private val _detectedMediaFlow = MutableStateFlow<List<DetectedMedia>>(emptyList())
    val detectedMediaFlow: StateFlow<List<DetectedMedia>> = _detectedMediaFlow.asStateFlow()

    private val mediaItems = ConcurrentHashMap<String, DetectedMedia>()
    private var currentPageUrl: String = ""

    /**
     * Resets sniffer state for a newly navigated page.
     */
    fun resetForPage(pageUrl: String) {
        currentPageUrl = pageUrl
        mediaItems.clear()
        mediaProber.clearPage(pageUrl)
        _detectedMediaFlow.value = emptyList()
    }

    /**
     * Adds or updates a detected media candidate.
     */
    fun addCandidate(
        url: String,
        pageUrl: String,
        pageTitle: String?,
        kind: MediaKind,
        mime: String? = null,
        sizeBytes: Long? = null,
        durationMs: Long? = null,
        posterUrl: String? = null,
        headers: Map<String, String> = emptyMap(),
        source: DetectionSource = DetectionSource.NETWORK,
        isDrmProtected: Boolean = false
    ) {
        if (url.isBlank() || url.startsWith("blob:") || url.startsWith("data:")) return
        if (MediaClassifier.isAdOrTrackingUrl(url) || MediaClassifier.isSegmentOrChunk(url)) return

        val normalizedUrl = normalizeMediaUrl(url)
        val id = generateMediaId(normalizedUrl, kind)

        val item = DetectedMedia(
            id = id,
            url = url,
            pageUrl = pageUrl.ifBlank { currentPageUrl },
            pageTitle = pageTitle,
            kind = kind,
            mime = mime,
            ext = MediaClassifier.extractExtension(url),
            sizeBytes = sizeBytes,
            durationMs = durationMs,
            isLive = false,
            isDrmProtected = isDrmProtected,
            posterUrl = posterUrl,
            headers = headers,
            source = source,
            detectedAt = System.currentTimeMillis()
        )

        mediaItems[id] = item
        updateFilteredList()
    }

    /**
     * Deduplicates and applies master-playlist priority:
     * When master playlists are present (e.g. .m3u8 or .mpd), avoid duplicate variant streams.
     */
    private fun updateFilteredList() {
        val all = mediaItems.values.toList()

        val hasHlsMaster = all.any { it.kind == MediaKind.HLS && isMasterPlaylist(it.url) }
        val hasDash = all.any { it.kind == MediaKind.DASH }

        val filtered = all.filter { media ->
            if (hasHlsMaster && media.kind == MediaKind.HLS && !isMasterPlaylist(media.url)) {
                // If we already captured the master HLS, suppress individual variant playlists
                return@filter false
            }
            true
        }.sortedByDescending { it.detectedAt }

        _detectedMediaFlow.value = filtered
    }

    private fun isMasterPlaylist(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("master") || lower.contains("playlist") || !lower.contains("chunk")
    }

    fun normalizeMediaUrl(url: String): String {
        return url.substringBefore("#")
    }

    private fun generateMediaId(url: String, kind: MediaKind): String {
        val key = "${kind.name}:$url"
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(key.toByteArray(Charsets.UTF_8))
            digest.take(12).joinToString("") { "%02x".format(it) }
        } catch (_: Exception) {
            key.hashCode().toString()
        }
    }
}
