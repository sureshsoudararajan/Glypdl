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

    /**
     * Generates a descriptive media title including account handle, platform context, and story/reel IDs.
     */
    fun generateSmartTitle(
        pageUrl: String?,
        pageTitle: String?,
        candidateUrl: String? = null,
        accountName: String? = null
    ): String {
        val safeUrl = pageUrl ?: ""
        val safeTitle = pageTitle?.trim() ?: ""

        // 1. Instagram Stories: e.g. https://www.instagram.com/stories/username/334455/
        val instaStoryRegex = Regex("(?i)instagram\\.com/stories/([a-zA-Z0-9._]+)(?:/([0-9]+))?")
        val storyMatch = instaStoryRegex.find(safeUrl)
        if (storyMatch != null) {
            val user = storyMatch.groupValues.getOrNull(1) ?: accountName ?: "User"
            val storyId = storyMatch.groupValues.getOrNull(2)
            return if (!storyId.isNullOrBlank()) "@$user • Story #$storyId" else "@$user • Story"
        }

        // 2. Instagram Reels / Posts: e.g. https://www.instagram.com/reel/C7xyz123/
        val instaReelRegex = Regex("(?i)instagram\\.com/(?:reel|p)/([a-zA-Z0-9_-]+)")
        val reelMatch = instaReelRegex.find(safeUrl)
        if (reelMatch != null) {
            val reelId = reelMatch.groupValues[1]
            if (safeTitle.contains(" on Instagram:", ignoreCase = true)) {
                val author = safeTitle.substringBefore(" on Instagram:", "").trim()
                val desc = safeTitle.substringAfter(" on Instagram:", "").trim('"', ' ', '\'')
                if (desc.isNotBlank()) {
                    val preview = if (desc.length > 40) desc.take(40) + "..." else desc
                    return "@$author: $preview"
                }
                if (author.isNotBlank()) return "@$author • Reel"
            }
            if (!accountName.isNullOrBlank()) {
                return "@$accountName • Reel"
            }
            return "Instagram Reel • $reelId"
        }

        // 3. YouTube Shorts: e.g. https://m.youtube.com/shorts/12345
        val ytShortsRegex = Regex("(?i)youtube\\.com/shorts/([a-zA-Z0-9_-]+)")
        val shortsMatch = ytShortsRegex.find(safeUrl)
        if (shortsMatch != null) {
            val shortId = shortsMatch.groupValues[1]
            val clean = safeTitle.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim()
            if (clean.isNotBlank() && !clean.equals("YouTube", ignoreCase = true)) {
                return "YouTube Shorts • $clean"
            }
            return "YouTube Shorts • $shortId"
        }

        // 4. YouTube Watch: e.g. https://m.youtube.com/watch?v=12345
        if (safeUrl.contains("youtube.com/watch", ignoreCase = true) || safeUrl.contains("youtu.be/", ignoreCase = true)) {
            val clean = safeTitle.removeSuffix(" - YouTube").removeSuffix("- YouTube").trim()
            if (clean.isNotBlank() && !clean.equals("YouTube", ignoreCase = true)) {
                return clean
            }
        }

        // 5. TikTok: e.g. https://www.tiktok.com/@username/video/12345
        val tiktokRegex = Regex("(?i)tiktok\\.com/@([a-zA-Z0-9._]+)")
        val tiktokMatch = tiktokRegex.find(safeUrl)
        if (tiktokMatch != null) {
            val user = tiktokMatch.groupValues[1]
            return "@$user • TikTok"
        }

        // 6. Twitter / X: e.g. https://x.com/username/status/12345
        val xRegex = Regex("(?i)(?:twitter|x)\\.com/([a-zA-Z0-9_]+)/status/")
        val xMatch = xRegex.find(safeUrl)
        if (xMatch != null) {
            val user = xMatch.groupValues[1]
            return "@$user • Post"
        }

        // 7. Reddit: e.g. https://www.reddit.com/r/videos/comments/...
        val redditRegex = Regex("(?i)reddit\\.com/r/([a-zA-Z0-9_]+)")
        val redditMatch = redditRegex.find(safeUrl)
        if (redditMatch != null) {
            val sub = redditMatch.groupValues[1]
            val clean = safeTitle.removeSuffix(" : r/$sub").removeSuffix(" - Reddit").trim()
            return if (clean.isNotBlank()) "r/$sub • $clean" else "r/$sub • Reddit Video"
        }

        // Fallback: If title is meaningful and not a generic site title
        val genericTitles = setOf("instagram", "youtube", "stories • instagram", "browser", "new tab", "video", "")
        if (safeTitle.lowercase() !in genericTitles) {
            return safeTitle
        }

        if (!accountName.isNullOrBlank()) {
            return "@$accountName Video"
        }

        // Fallback: clean media filename from candidateUrl
        if (!candidateUrl.isNullOrBlank()) {
            val name = candidateUrl.substringBefore('?').substringAfterLast('/')
            if (name.isNotBlank() && name.length < 50 && !name.startsWith("videoplayback")) {
                return name
            }
        }

        return if (safeTitle.isNotBlank()) safeTitle else "Detected Media"
    }

    /**
     * Resolves high-resolution video thumbnail from platform patterns if raw poster is missing.
     */
    fun resolvePosterUrl(pageUrl: String?, rawPoster: String?): String? {
        if (!rawPoster.isNullOrBlank() && !rawPoster.startsWith("data:")) {
            return rawPoster
        }
        val safeUrl = pageUrl ?: return null
        val ytRegex = Regex("(?i)(?:watch\\?.*v=|/shorts/|youtu\\.be/)([a-zA-Z0-9_-]{11})")
        val match = ytRegex.find(safeUrl)
        if (match != null) {
            val vid = match.groupValues[1]
            return "https://i.ytimg.com/vi/$vid/hqdefault.jpg"
        }
        return null
    }
}
