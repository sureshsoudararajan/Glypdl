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
    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
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

    fun isYouTubeUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("youtube.com") || lower.contains("youtu.be")
    }

    fun isInstagramStory(url: String): Boolean {
        if (!isInstagramUrl(url)) return false
        val lower = url.lowercase()
        return lower.contains("/stories/")
    }

    fun isFacebookUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val lower = url.lowercase()
        return lower.contains("facebook.com") ||
               lower.contains("fb.watch") ||
               lower.contains("fb.com") ||
               lower.contains("fb.gg")
    }

    fun resolveRedirect(url: String): String {
        if (!url.contains("fb.watch") && !url.contains("/flx/warn")) return url
        return try {
            var current = url
            for (i in 0 until 5) {
                val conn = (java.net.URL(current).openConnection() as java.net.HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36")
                    setRequestProperty("Sec-Fetch-Mode", "navigate")
                    setRequestProperty("Sec-Fetch-Site", "none")
                    connectTimeout = 4000
                    readTimeout = 4000
                }
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location") ?: break
                    current = if (loc.startsWith("http")) loc else java.net.URL(java.net.URL(current), loc).toString()
                    conn.disconnect()
                } else {
                    conn.disconnect()
                    break
                }
            }
            current
        } catch (e: Exception) {
            url
        }
    }

    fun canonicalizeFacebookUrl(url: String): String {
        var cleanUrl = sanitizeUrl(url)

        if (cleanUrl.contains("fb.watch") || cleanUrl.contains("/flx/warn")) {
            cleanUrl = resolveRedirect(cleanUrl)
        }

        // Normalize mobile subdomains (m.facebook.com, touch.facebook.com, mbasic.facebook.com) to www.facebook.com
        cleanUrl = cleanUrl
            .replace("://m.facebook.com", "://www.facebook.com")
            .replace("://touch.facebook.com", "://www.facebook.com")
            .replace("://mobile.facebook.com", "://www.facebook.com")
            .replace("://mbasic.facebook.com", "://www.facebook.com")
            .replace("://web.facebook.com", "://www.facebook.com")
            .replace("://fb.com", "://www.facebook.com")
            .replace("://www.fb.com", "://www.facebook.com")

        // 1. Match story.php?story_fbid=<id> or fbid=<id>
        val storyPhpMatch = Regex("""story\.php\?.*(?:story_fbid|fbid)=([0-9]+)""").find(cleanUrl)
        if (storyPhpMatch != null) {
            val id = storyPhpMatch.groupValues[1]
            return "https://www.facebook.com/watch/?v=$id"
        }

        // 2. Match /share/r/<id> -> /reel/<id>
        val reelShareMatch = Regex("""facebook\.com/share/r/([^/?#]+)""").find(cleanUrl)
        if (reelShareMatch != null) {
            val id = reelShareMatch.groupValues[1]
            return "https://www.facebook.com/reel/$id"
        }

        // 3. Match /share/v/<id> -> /watch/?v=<id>
        val videoShareMatch = Regex("""facebook\.com/share/v/([^/?#]+)""").find(cleanUrl)
        if (videoShareMatch != null) {
            val id = videoShareMatch.groupValues[1]
            return "https://www.facebook.com/watch/?v=$id"
        }

        // 4. Match /share/p/<id> or /share/<id>
        val postShareMatch = Regex("""facebook\.com/share/(?:p/)?([^/?#]+)""").find(cleanUrl)
        if (postShareMatch != null) {
            val id = postShareMatch.groupValues[1]
            return if (id.all { it.isDigit() }) {
                "https://www.facebook.com/watch/?v=$id"
            } else {
                "https://www.facebook.com/$id"
            }
        }

        // 5. Match /<user>/videos/<id> -> /watch/?v=<id>
        val userVideoMatch = Regex("""facebook\.com/[^/?#]+/videos/([0-9]+)""").find(cleanUrl)
        if (userVideoMatch != null) {
            val id = userVideoMatch.groupValues[1]
            return "https://www.facebook.com/watch/?v=$id"
        }

        // 6. Match /<user>/posts/<id> -> /watch/?v=<id>
        val userPostMatch = Regex("""facebook\.com/[^/?#]+/posts/([0-9]+)""").find(cleanUrl)
        if (userPostMatch != null) {
            val id = userPostMatch.groupValues[1]
            return "https://www.facebook.com/watch/?v=$id"
        }

        // 7. Normalize /watch?v= to /watch/?v=
        if (cleanUrl.contains("/watch?v=") && !cleanUrl.contains("/watch/?v=")) {
            cleanUrl = cleanUrl.replace("/watch?v=", "/watch/?v=")
        }

        return cleanUrl
    }

    fun pkToShortcode(mediaId: String): String? {
        val pkStr = mediaId.substringBefore("_").trim()
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        return try {
            var num = java.math.BigInteger(pkStr)
            if (num <= java.math.BigInteger.ZERO) return null
            val base = java.math.BigInteger.valueOf(64)
            val sb = StringBuilder()
            while (num > java.math.BigInteger.ZERO) {
                val rem = num.mod(base).toInt()
                sb.append(alphabet[rem])
                num = num.divide(base)
            }
            sb.reverse().toString()
        } catch (e: Throwable) {
            null
        }
    }

    fun extractInstagramStoryId(url: String): String? {
        if (!isInstagramStory(url)) return null
        val match = Regex("""/stories/[^/?#]+/(\d+)""").find(url)
        return match?.groupValues?.get(1)
    }

    fun extractInstagramStoryUsername(url: String): String? {
        if (!isInstagramStory(url)) return null
        val match = Regex("""/stories/([^/?#]+)""").find(url)
        return match?.groupValues?.get(1)
    }

    fun canonicalizeInstagramStoryUrl(url: String): String {
        val storyId = extractInstagramStoryId(url)
        val username = extractInstagramStoryUsername(url)
        if (storyId != null && username != "highlights") {
            val shortcode = pkToShortcode(storyId)
            if (!shortcode.isNullOrBlank()) {
                return "https://www.instagram.com/p/$shortcode/"
            }
        }
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
                        key != "mibextid" &&
                        key != "rdid" &&
                        key != "paipv" &&
                        !key.startsWith("__cft__") &&
                        !key.startsWith("__tn__") &&
                        key != "fs" &&
                        key != "focus_composer"
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
        val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
        val matchResult = urlRegex.find(text)
        return matchResult?.value
    }
}
