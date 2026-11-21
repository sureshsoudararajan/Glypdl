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

import com.glypdl.android.data.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

data class ProbeResult(
    val url: String,
    val kind: MediaKind?,
    val mimeType: String?,
    val contentLength: Long?,
    val isMedia: Boolean
)

@Singleton
class MediaProber @Inject constructor() {

    private val probedCache = ConcurrentHashMap<String, ProbeResult>()
    private val pageProbeCount = ConcurrentHashMap<String, Int>()

    suspend fun probeUrl(
        candidateUrl: String,
        pageUrl: String,
        headers: Map<String, String> = emptyMap()
    ): ProbeResult = withContext(Dispatchers.IO) {
        probedCache[candidateUrl]?.let { return@withContext it }

        // Rate limit probes per page
        val count = pageProbeCount.getOrDefault(pageUrl, 0)
        if (count >= 20) {
            val empty = ProbeResult(candidateUrl, null, null, null, false)
            probedCache[candidateUrl] = empty
            return@withContext empty
        }
        pageProbeCount[pageUrl] = count + 1

        val result = tryProbe(candidateUrl, headers, method = "HEAD")
            ?: tryProbe(candidateUrl, headers, method = "GET", isRanged = true)
            ?: ProbeResult(candidateUrl, null, null, null, false)

        probedCache[candidateUrl] = result
        result
    }

    private fun tryProbe(
        urlStr: String,
        headers: Map<String, String>,
        method: String,
        isRanged: Boolean = false
    ): ProbeResult? {
        var conn: HttpURLConnection? = null
        try {
            val url = URL(urlStr)
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.instanceFollowRedirects = true

            // Set headers
            headers["User-Agent"]?.let { conn.setRequestProperty("User-Agent", it) }
            headers["Referer"]?.let { conn.setRequestProperty("Referer", it) }
            headers["Cookie"]?.let { conn.setRequestProperty("Cookie", it) }
            if (isRanged) {
                conn.setRequestProperty("Range", "bytes=0-0")
            }

            val code = conn.responseCode
            if (code in 200..299 || code == 206) {
                val contentType = conn.contentType?.substringBefore(';')?.trim()
                val lengthHeader = conn.getHeaderField("Content-Length")
                val contentRange = conn.getHeaderField("Content-Range")
                val size = parseSize(lengthHeader, contentRange)

                val kind = MediaClassifier.classify(urlStr, contentType)
                if (kind != null) {
                    return ProbeResult(
                        url = urlStr,
                        kind = kind,
                        mimeType = contentType,
                        contentLength = size,
                        isMedia = true
                    )
                }
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            try {
                conn?.disconnect()
            } catch (_: Exception) {}
        }
    }

    private fun parseSize(contentLength: String?, contentRange: String?): Long? {
        if (!contentRange.isNullOrBlank()) {
            val total = contentRange.substringAfterLast('/', "").trim()
            total.toLongOrNull()?.let { return it }
        }
        return contentLength?.toLongOrNull()
    }

    fun clearPage(pageUrl: String) {
        pageProbeCount.remove(pageUrl)
    }
}
