/*
 * Copyright (C) 2026 The Glypdl Authors
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
package com.glypdl.android.service.resolver

import com.glypdl.android.data.model.DownloadType
import com.glypdl.android.data.model.EngineType
import com.glypdl.android.data.model.FileCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class ResolvedDownload(
    val downloadType: DownloadType,
    val engineType: EngineType,
    val fileCategory: FileCategory,
    val detectedFileName: String?,
    val detectedFileSize: Long?,
    val detectedMimeType: String?,
    val originalInput: String
)

@Singleton
class DownloadResolver @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    
    /**
     * Synchronously resolves the download type based on the input string.
     * Uses URL pattern matching, domain detection, and file extension analysis.
     * Does NOT make network requests — use [resolveWithProbe] for HEAD request probing.
     */
    fun resolve(input: String): ResolvedDownload {
        val trimmedInput = input.trim()
        
        // 1. Magnet link
        if (trimmedInput.startsWith("magnet:?", ignoreCase = true)) {
            return ResolvedDownload(
                downloadType = DownloadType.MAGNET,
                engineType = EngineType.ARIA2C,
                fileCategory = FileCategory.UNKNOWN,
                detectedFileName = null,
                detectedFileSize = null,
                detectedMimeType = null,
                originalInput = input
            )
        }

        // 2. Torrent base64 payload
        if (trimmedInput.startsWith("torrent-base64:", ignoreCase = true)) {
            return ResolvedDownload(
                downloadType = DownloadType.TORRENT,
                engineType = EngineType.ARIA2C,
                fileCategory = FileCategory.UNKNOWN,
                detectedFileName = "download.torrent",
                detectedFileSize = null,
                detectedMimeType = "application/x-bittorrent",
                originalInput = input
            )
        }

        // 3. Local content:// or file:// URI (e.g. tapped in browser or file manager)
        if (trimmedInput.startsWith("content://", ignoreCase = true) || trimmedInput.startsWith("file://", ignoreCase = true)) {
            var detectedFileName: String? = null
            var detectedMime: String? = null

            if (trimmedInput.startsWith("content://", ignoreCase = true)) {
                try {
                    val uri = android.net.Uri.parse(trimmedInput)
                    detectedMime = context.contentResolver.getType(uri)
                    context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (nameIndex != -1) {
                                detectedFileName = cursor.getString(nameIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Ignore error querying content resolver
                }
            }

            if (detectedFileName == null) {
                val lastSegment = trimmedInput.substringAfterLast('/').substringBefore('?').substringBefore('#')
                if (lastSegment.isNotBlank()) {
                    detectedFileName = try { java.net.URLDecoder.decode(lastSegment, "UTF-8") } catch (e: Exception) { lastSegment }
                }
            }

            val isTorrent = (detectedMime == "application/x-bittorrent" || detectedMime == "application/x-torrent") ||
                    (detectedFileName?.endsWith(".torrent", ignoreCase = true) == true) ||
                    trimmedInput.contains(".torrent", ignoreCase = true)

            if (isTorrent) {
                return ResolvedDownload(
                    downloadType = DownloadType.TORRENT,
                    engineType = EngineType.ARIA2C,
                    fileCategory = FileCategory.UNKNOWN,
                    detectedFileName = detectedFileName ?: "download.torrent",
                    detectedFileSize = null,
                    detectedMimeType = detectedMime ?: "application/x-bittorrent",
                    originalInput = input
                )
            }
        }
        
        // Try parsing URL path for next checks
        val path = try { URI(trimmedInput).path ?: "" } catch (e: Exception) { "" }
        
        // 4. Torrent URL (HTTP/HTTPS)
        val isTorrentHttp = path.endsWith(".torrent", ignoreCase = true) ||
                trimmedInput.substringBefore('?').substringBefore('#').endsWith(".torrent", ignoreCase = true) ||
                path.contains("/torrent/download/", ignoreCase = true) ||
                path.contains("/torrents/download/", ignoreCase = true) ||
                path.contains("/download/torrent/", ignoreCase = true)
        if (isTorrentHttp) {
            return ResolvedDownload(
                downloadType = DownloadType.TORRENT,
                engineType = EngineType.ARIA2C,
                fileCategory = FileCategory.UNKNOWN,
                detectedFileName = FileCategoryClassifier.extractFileNameFromUrl(trimmedInput),
                detectedFileSize = null,
                detectedMimeType = "application/x-bittorrent",
                originalInput = input
            )
        }
        
        // 3. Known media site
        if (MediaSiteDetector.isMediaSite(trimmedInput)) {
            return ResolvedDownload(
                downloadType = DownloadType.MEDIA,
                engineType = EngineType.YT_DLP,
                fileCategory = FileCategory.UNKNOWN,
                detectedFileName = null,
                detectedFileSize = null,
                detectedMimeType = null,
                originalInput = input
            )
        }
        
        // 4. Direct file extension
        val extension = FileCategoryClassifier.extractExtensionFromUrl(trimmedInput)
        if (extension != null) {
            val category = FileCategoryClassifier.fromExtension(extension)
            if (category != FileCategory.UNKNOWN) {
                return ResolvedDownload(
                    downloadType = DownloadType.DIRECT_FILE,
                    engineType = EngineType.ARIA2C,
                    fileCategory = category,
                    detectedFileName = FileCategoryClassifier.extractFileNameFromUrl(trimmedInput),
                    detectedFileSize = null,
                    detectedMimeType = null,
                    originalInput = input
                )
            }
        }
        
        // 5. Fallback
        return ResolvedDownload(
            downloadType = DownloadType.MEDIA,
            engineType = EngineType.YT_DLP,
            fileCategory = FileCategory.UNKNOWN,
            detectedFileName = null,
            detectedFileSize = null,
            detectedMimeType = null,
            originalInput = input
        )
    }

    /**
     * Asynchronously resolves the download type, performing an HTTP HEAD
     * request to inspect Content-Type, Content-Length, and Content-Disposition.
     * Probes both ambiguous (fallback) URLs and DIRECT_FILE URLs that are
     * missing file size / MIME type information.
     */
    suspend fun resolveWithProbe(input: String): ResolvedDownload = withContext(Dispatchers.IO) {
        val initialResult = resolve(input)

        // Determine whether we should probe via HTTP HEAD
        val isHttp = input.startsWith("http://", ignoreCase = true) || input.startsWith("https://", ignoreCase = true)
        if (!isHttp) return@withContext initialResult

        val isFallback = initialResult.downloadType == DownloadType.MEDIA &&
            initialResult.engineType == EngineType.YT_DLP &&
            initialResult.fileCategory == FileCategory.UNKNOWN

        val isDirectFileMissingInfo = initialResult.downloadType == DownloadType.DIRECT_FILE &&
            (initialResult.detectedFileSize == null || initialResult.detectedMimeType == null)

        if (!isFallback && !isDirectFileMissingInfo) return@withContext initialResult

        try {
            val url = URL(input)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 16) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0 Mobile Safari/537.36 Glypdl/1.0")
            connection.setRequestProperty("Accept", "*/*")

            val responseCode = connection.responseCode
            if (responseCode in 200..399) {
                val contentType = connection.contentType?.substringBefore(";")?.lowercase()?.trim()
                val contentLength = connection.contentLengthLong.takeIf { it >= 0 }
                val contentDisposition = connection.getHeaderField("Content-Disposition")

                var detectedFileName = initialResult.detectedFileName
                    ?: FileCategoryClassifier.extractFileNameFromUrl(input)
                if (!contentDisposition.isNullOrBlank()) {
                    FileCategoryClassifier.extractFileNameFromContentDisposition(contentDisposition)?.let {
                        detectedFileName = it
                    }
                }

                // For DIRECT_FILE that already has a category, just merge in size/mime
                if (isDirectFileMissingInfo) {
                    val mergedMime = contentType?.takeIf { it != "application/octet-stream" }
                        ?: initialResult.detectedMimeType
                    val mergedCategory = if (initialResult.fileCategory != FileCategory.UNKNOWN) {
                        initialResult.fileCategory
                    } else if (mergedMime != null) {
                        FileCategoryClassifier.fromMimeType(mergedMime)
                    } else {
                        FileCategory.UNKNOWN
                    }
                    return@withContext initialResult.copy(
                        detectedFileName = detectedFileName ?: initialResult.detectedFileName,
                        detectedFileSize = contentLength ?: initialResult.detectedFileSize,
                        detectedMimeType = mergedMime ?: contentType,
                        fileCategory = mergedCategory
                    )
                }

                // Fallback path — full classification from HEAD response
                if (contentType == "text/html" || contentType == "application/xhtml+xml") {
                    return@withContext initialResult.copy(
                        detectedFileName = detectedFileName,
                        detectedFileSize = contentLength,
                        detectedMimeType = contentType
                    )
                } else if (contentType == "application/x-bittorrent" || contentType == "application/x-torrent" || detectedFileName?.endsWith(".torrent", ignoreCase = true) == true) {
                    return@withContext ResolvedDownload(
                        downloadType = DownloadType.TORRENT,
                        engineType = EngineType.ARIA2C,
                        fileCategory = FileCategory.UNKNOWN,
                        detectedFileName = detectedFileName ?: "download.torrent",
                        detectedFileSize = contentLength,
                        detectedMimeType = contentType ?: "application/x-bittorrent",
                        originalInput = input
                    )
                } else if (!contentType.isNullOrBlank()) {
                    var category = FileCategoryClassifier.fromMimeType(contentType)
                    if (category == FileCategory.UNKNOWN && detectedFileName != null) {
                        val ext = detectedFileName!!.substringAfterLast('.', "")
                        if (ext.isNotEmpty()) {
                            category = FileCategoryClassifier.fromExtension(ext)
                        }
                    }

                    return@withContext ResolvedDownload(
                        downloadType = DownloadType.DIRECT_FILE,
                        engineType = EngineType.ARIA2C,
                        fileCategory = category,
                        detectedFileName = detectedFileName,
                        detectedFileSize = contentLength,
                        detectedMimeType = contentType,
                        originalInput = input
                    )
                }
            }
        } catch (e: Exception) {
            // Network error, fall back to initial result
        }

        return@withContext initialResult
    }
}
