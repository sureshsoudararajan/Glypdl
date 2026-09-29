/*
 * Copyright (c) 2024 Glypdl
 *
 * This file is part of Glypdl.
 *
 * Glypdl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Glypdl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android.util

import java.net.URI

object UrlValidator {
    fun isMagnetLink(url: String): Boolean {
        val trimmed = url.trim()
        return trimmed.startsWith("magnet:?", ignoreCase = true) && trimmed.length > 8
    }

    fun isTorrentUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.startsWith("torrent-base64:", ignoreCase = true)) return true
        val path = trimmed.substringBefore('?').substringBefore('#')
        if (path.endsWith(".torrent", ignoreCase = true)) return true
        if (path.contains("/torrent/download/", ignoreCase = true) ||
            path.contains("/torrents/download/", ignoreCase = true) ||
            path.contains("/download/torrent/", ignoreCase = true)) return true
        return false
    }

    fun isTorrentUri(uriString: String): Boolean {
        val trimmed = uriString.trim()
        if (isMagnetLink(trimmed)) return true
        if (trimmed.startsWith("torrent-base64:", ignoreCase = true)) return true
        val lower = trimmed.lowercase()
        if (lower.startsWith("content://") || lower.startsWith("file://")) {
            return lower.contains(".torrent") || lower.contains("bittorrent") || lower.contains("torrent")
        }
        return isTorrentUrl(trimmed)
    }

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        if (isMagnetLink(url)) return true
        if (url.trim().startsWith("torrent-base64:", ignoreCase = true)) return true
        if (url.trim().startsWith("content://", ignoreCase = true) || url.trim().startsWith("file://", ignoreCase = true)) return true
        return try {
            val uri = URI(url)
            val scheme = uri.scheme?.lowercase()
            (scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()
        } catch (e: Exception) {
            false
        }
    }

    fun isInstagramUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("instagram.com") || lower.contains("instagr.am")
    }

    fun isInstagramStory(url: String): Boolean {
        if (!isInstagramUrl(url)) return false
        val lower = url.lowercase()
        return lower.contains("/stories/")
    }

    fun isFacebookUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.com")
    }

    fun extractInstagramStoryId(url: String): String? {
        if (!isInstagramStory(url)) return null
        val match = Regex("""/stories/[^/?#]+/(\d+)""").find(url)
        return match?.groupValues?.get(1)
    }

    fun extractInstagramStoryUsername(url: String): String? {
        if (!isInstagramStory(url)) return null
        val match = Regex("""/stories/([^/?#]+)/""").find(url)
        return match?.groupValues?.get(1)
    }

    fun canonicalizeInstagramStoryUrl(url: String): String {
        val storyId = extractInstagramStoryId(url)
        val username = extractInstagramStoryUsername(url)
        return if (storyId != null && username != null) {
            "https://www.instagram.com/stories/$username/$storyId/"
        } else {
            sanitizeUrl(url)
        }
    }

    fun sanitizeUrl(url: String): String {
        var cleanUrl = url.trim()
        // Strip trailing punctuation often appended by chat / share apps
        cleanUrl = cleanUrl.trimEnd('.', ',', ';', ')', ']', '}')
        return try {
            val uri = URI(cleanUrl)
            val query = uri.query
            if (query != null) {
                val cleanedQuery = query.split("&")
                    .filter { param ->
                        val key = param.substringBefore("=")
                        // Only strip pure tracking parameters; preserve video IDs, story IDs, and auth hints
                        !key.startsWith("utm_") &&
                        key != "fbclid" &&
                        key != "igshid" &&
                        key != "igsh" &&
                        key != "si" &&
                        key != "ref" &&
                        key != "mibextid"
                    }.joinToString("&")
                URI(
                    uri.scheme,
                    uri.authority,
                    uri.path,
                    if (cleanedQuery.isNotEmpty()) cleanedQuery else null,
                    uri.fragment
                ).toString()
            } else {
                cleanUrl
            }
        } catch (e: Exception) {
            cleanUrl
        }
    }

    fun extractUrlFromText(text: String): String? {
        val magnetRegex = "(?i)magnet:\\?[^\\s\"'<>\\]\\[\\}\\{]+".toRegex()
        val magnetMatch = magnetRegex.find(text)
        if (magnetMatch != null) return magnetMatch.value

        val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
        val matchResult = urlRegex.find(text)
        return matchResult?.value
    }
}
