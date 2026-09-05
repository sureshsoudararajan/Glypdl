/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.engine

import android.content.Context
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.GlypdlError
import com.glypdl.android.data.model.GlypdlException
import com.glypdl.android.data.model.MediaFormat
import com.glypdl.android.data.model.MediaInfo
import com.glypdl.android.data.model.PlaylistInfo
import com.glypdl.android.data.model.PlaylistItem
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.util.UrlValidator
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

data class EngineStatus(
    val installedVersion: String,
    val latestVersion: String?,
    val isOutdated: Boolean,
    val isUpdateAvailable: Boolean,
    val isFfmpegAvailable: Boolean,
    val lastCheckedMillis: Long
)

@Singleton
class YtDlpManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: SettingsRepository,
    private val authCookieManager: com.glypdl.android.service.auth.AuthCookieManager
) {

    private var ffmpegInitialized = false

    suspend fun init(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().init(context)
            try {
                com.yausername.ffmpeg.FFmpeg.getInstance().init(context)
                ffmpegInitialized = true
            } catch (e: Exception) {
                // FFmpeg may be unsupported on some test environments or ABIs
                e.printStackTrace()
            }

            // Auto-update check if enabled
            val autoUpdate = settingsRepository.autoUpdateYtDlp.first()
            val lastCheck = settingsRepository.lastUpdateCheck.first()
            val now = System.currentTimeMillis()
            val oneDayMillis = 24L * 60 * 60 * 1000

            if (autoUpdate && (now - lastCheck > oneDayMillis)) {
                try {
                    val status = checkEngineStatus(forceOnlineCheck = true)
                    if (status.isUpdateAvailable || status.isOutdated) {
                        updateEngine()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            val parsedError = YtDlpErrorParser.parse(e.message, getInstalledVersion())
            Result.failure(GlypdlException(parsedError))
        }
    }

    fun getInstalledVersion(): String {
        return try {
            YoutubeDL.getInstance().version(context) ?: "Unknown"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun isFfmpegAvailable(): Boolean {
        return ffmpegInitialized
    }

    suspend fun getLatestOnlineVersion(forceRefresh: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (!forceRefresh) {
                val cached = settingsRepository.cachedLatestVersion.first()
                val lastCheck = settingsRepository.lastUpdateCheck.first()
                val cacheAge = System.currentTimeMillis() - lastCheck
                // Cache valid for 12 hours
                if (!cached.isNullOrBlank() && cacheAge < 12L * 60 * 60 * 1000) {
                    return@withContext Result.success(cached)
                }
            }

            val url = URL("https://api.github.com/repos/yt-dlp/yt-dlp/releases/latest")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Glypdl-Android")
            conn.connectTimeout = 7000
            conn.readTimeout = 7000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val tagMatch = Regex("\"tag_name\"\\s*:\\s*\"([^\"]+)\"").find(response)
                val tagName = tagMatch?.groupValues?.get(1)?.removePrefix("v")?.trim()

                if (!tagName.isNullOrBlank()) {
                    settingsRepository.setCachedLatestVersion(tagName)
                    settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
                    return@withContext Result.success(tagName)
                }
            }
            conn.disconnect()

            // Fallback to cached if online fetch failed
            val fallbackCached = settingsRepository.cachedLatestVersion.first()
            if (!fallbackCached.isNullOrBlank()) {
                Result.success(fallbackCached)
            } else {
                Result.failure(Exception("Unable to fetch latest release from GitHub (HTTP $responseCode)"))
            }
        } catch (e: Exception) {
            val fallbackCached = settingsRepository.cachedLatestVersion.first()
            if (!fallbackCached.isNullOrBlank()) {
                Result.success(fallbackCached)
            } else {
                Result.failure(e)
            }
        }
    }

    suspend fun checkEngineStatus(forceOnlineCheck: Boolean = false): EngineStatus = withContext(Dispatchers.IO) {
        val installed = getInstalledVersion()
        val latestResult = getLatestOnlineVersion(forceOnlineCheck)
        val latest = latestResult.getOrNull()
        val isOutdated = isVersionOutdated(installed)
        val isUpdateAvailable = latest != null && isNewerVersion(latest, installed)
        val lastCheck = settingsRepository.lastUpdateCheck.first()

        EngineStatus(
            installedVersion = installed,
            latestVersion = latest,
            isOutdated = isOutdated,
            isUpdateAvailable = isUpdateAvailable,
            isFfmpegAvailable = isFfmpegAvailable(),
            lastCheckedMillis = lastCheck
        )
    }

    fun isVersionOutdated(version: String, thresholdDays: Long = 90): Boolean {
        if (version.isBlank() || version.equals("Unknown", ignoreCase = true)) {
            return false
        }
        return try {
            // yt-dlp versions are formatted as YYYY.MM.DD or YYYY.MM.DD.*
            val parts = version.split(".")
            if (parts.size >= 3) {
                val dateStr = "${parts[0]}.${parts[1]}.${parts[2]}"
                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.US)
                val releaseDate = sdf.parse(dateStr)
                if (releaseDate != null) {
                    val ageMillis = System.currentTimeMillis() - releaseDate.time
                    val ageDays = ageMillis / (1000L * 60 * 60 * 24)
                    return ageDays > thresholdDays
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    fun isNewerVersion(candidate: String, current: String): Boolean {
        if (candidate.isBlank()) return false
        if (current.isBlank() || current.equals("Unknown", ignoreCase = true)) return true

        return try {
            val candParts = candidate.split(".").mapNotNull { it.toIntOrNull() }
            val currParts = current.split(".").mapNotNull { it.toIntOrNull() }

            val minLength = minOf(candParts.size, currParts.size)
            for (i in 0 until minLength) {
                if (candParts[i] > currParts[i]) return true
                if (candParts[i] < currParts[i]) return false
            }
            candParts.size > currParts.size
        } catch (e: Exception) {
            candidate != current
        }
    }

    suspend fun updateEngine(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, YoutubeDL.UpdateChannel.STABLE)
            val newVersion = getInstalledVersion()

            settingsRepository.setLastUpdateCheck(System.currentTimeMillis())
            settingsRepository.setCachedLatestVersion(newVersion)

            Result.success(newVersion)
        } catch (e: Exception) {
            e.printStackTrace()
            val parsedError = YtDlpErrorParser.parse(e.message, getInstalledVersion())
            Result.failure(GlypdlException(parsedError))
        }
    }

    fun isPlaylist(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains("list=") ||
                lower.contains("/playlist") ||
                lower.contains("/sets/")) &&
                !lower.contains("watch?v=")
    }

    fun isMixedUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains("watch?v=") && lower.contains("list=")
    }

    fun cancelProcess(processId: String): Boolean {
        return try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun extractPlaylistInfo(
        url: String,
        processId: String? = null,
        timeoutMs: Long = 120_000L
    ): Result<PlaylistInfo> = withContext(Dispatchers.IO) {
        val installed = getInstalledVersion()
        val pId = processId ?: ("playlist_" + System.currentTimeMillis())
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("-J")
            request.addOption("--flat-playlist")
            request.addOption("--no-warnings")

            val cookieFile = authCookieManager.getCookieFile(url)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

            val response = withTimeoutOrNull(timeoutMs) {
                YoutubeDL.getInstance().execute(request, pId)
            }

            if (response == null) {
                cancelProcess(pId)
                return@withContext Result.failure(
                    GlypdlException(
                        GlypdlError.TimedOut(
                            userMessage = "Playlist analysis timed out after ${timeoutMs / 1000}s. The playlist may be large, private, or the website may be rate-limiting."
                        )
                    )
                )
            }

            val json = JSONObject(response.out)
            val playlistTitle = json.optString("title", "Playlist")
            val playlistId = json.optString("id", "")
            val uploader = json.optString("uploader").takeIf { it.isNotBlank() }
            val entriesArray = json.optJSONArray("entries") ?: JSONArray()

            val entries = mutableListOf<PlaylistItem>()
            for (i in 0 until entriesArray.length()) {
                val entryObj = entriesArray.optJSONObject(i) ?: continue
                val entryId = entryObj.optString("id", "")
                val entryTitle = entryObj.optString("title", "Untitled Video")
                val duration = entryObj.optLong("duration", 0L)
                val entryUrl = entryObj.optString("url").let { u ->
                    if (u.isBlank() && entryId.isNotBlank()) {
                        "https://www.youtube.com/watch?v=$entryId"
                    } else u
                }
                val entryUploader = entryObj.optString("uploader").takeIf { it.isNotBlank() }
                var entryThumbnail = entryObj.optString("thumbnail").takeIf { it.isNotBlank() }
                if (entryThumbnail == null) {
                    val thumbnailsArray = entryObj.optJSONArray("thumbnails")
                    if (thumbnailsArray != null && thumbnailsArray.length() > 0) {
                        val lastThumb = thumbnailsArray.optJSONObject(thumbnailsArray.length() - 1)
                        entryThumbnail = lastThumb?.optString("url")?.takeIf { it.isNotBlank() }
                    }
                }
                if (entryThumbnail == null && entryId.isNotBlank() && (entryUrl.contains("youtube.com") || entryUrl.contains("youtu.be") || entryId.length == 11)) {
                    entryThumbnail = "https://i.ytimg.com/vi/$entryId/hqdefault.jpg"
                }

                val isDeleted = entryTitle.contains("[Deleted video]", ignoreCase = true)
                val isPrivate = entryTitle.contains("[Private video]", ignoreCase = true) ||
                        entryObj.optBoolean("_is_unavailable", false) ||
                        entryObj.optString("availability") == "private"
                val isAvailable = !isDeleted && !isPrivate
                val statusMessage = when {
                    isDeleted -> "Deleted"
                    isPrivate -> "Private"
                    else -> "Available"
                }

                entries.add(
                    PlaylistItem(
                        id = entryId.ifBlank { "item_$i" },
                        url = entryUrl,
                        title = entryTitle,
                        duration = duration,
                        thumbnailUrl = entryThumbnail,
                        uploader = entryUploader,
                        isAvailable = isAvailable,
                        statusMessage = statusMessage
                    )
                )
            }

            val playlistThumbnail = json.optString("thumbnail").takeIf { it.isNotBlank() }
                ?: entries.firstOrNull { !it.thumbnailUrl.isNullOrBlank() }?.thumbnailUrl

            val playlistInfo = PlaylistInfo(
                id = playlistId,
                url = url,
                title = playlistTitle,
                uploader = uploader,
                thumbnailUrl = playlistThumbnail,
                entries = entries
            )

            Result.success(playlistInfo)
        } catch (e: Exception) {
            cancelProcess(pId)
            e.printStackTrace()
            val parsedError = YtDlpErrorParser.parse(e.message, installed)
            Result.failure(GlypdlException(parsedError))
        }
    }

    suspend fun extractInfo(
        url: String,
        timeoutMs: Long = 90_000L
    ): Result<MediaInfo> = withContext(Dispatchers.IO) {
        val installed = getInstalledVersion()
        try {
            val request = YoutubeDLRequest(url)
            request.addOption("-J")
            request.addOption("--no-playlist")

            val cookieFile = authCookieManager.getCookieFile(url)
            if (cookieFile != null) {
                request.addOption("--cookies", cookieFile.absolutePath)
            }

            val videoInfo = withTimeoutOrNull(timeoutMs) {
                YoutubeDL.getInstance().getInfo(request)
            }

            if (videoInfo == null) {
                return@withContext Result.failure(
                    GlypdlException(
                        GlypdlError.TimedOut(
                            userMessage = "Media analysis timed out after ${timeoutMs / 1000}s. The website may be slow, rate-limiting, or requiring authentication."
                        )
                    )
                )
            }

            val formats = videoInfo.formats?.map { format ->
                MediaFormat(
                    formatId = format.formatId ?: "",
                    ext = format.ext ?: "",
                    resolution = format.formatNote ?: format.format ?: "",
                    width = format.width.takeIf { it > 0 },
                    height = format.height.takeIf { it > 0 },
                    vcodec = format.vcodec ?: "",
                    acodec = format.acodec ?: "",
                    filesize = format.fileSize.takeIf { it > 0 },
                    filesizeApprox = format.fileSizeApproximate.takeIf { it > 0 },
                    abr = format.abr.takeIf { it > 0 }?.toFloat(),
                    tbr = format.tbr.takeIf { it > 0 }?.toFloat(),
                    isVideoOnly = format.acodec == "none" && format.vcodec != "none",
                    isAudioOnly = format.vcodec == "none" && format.acodec != "none"
                )
            } ?: emptyList()

            val normalizedVideo = FormatNormalizer.normalizeVideoQualities(formats)
            val normalizedAudio = FormatNormalizer.normalizeAudioQualities(formats)

            val mediaInfo = MediaInfo(
                id = videoInfo.id ?: "",
                url = url,
                title = videoInfo.title ?: "Unknown Title",
                thumbnail = videoInfo.thumbnail,
                duration = videoInfo.duration?.toLong() ?: 0L,
                uploader = videoInfo.uploader,
                extractor = videoInfo.extractor,
                formats = formats,
                videoQualities = normalizedVideo,
                audioQualities = normalizedAudio
            )

            Result.success(mediaInfo)
        } catch (e: Exception) {
            e.printStackTrace()
            val parsedError = YtDlpErrorParser.parse(e.message, installed)
            Result.failure(GlypdlException(parsedError))
        }
    }

    data class ParsedProgress(
        val percent: Float,
        val downloadedBytes: Long,
        val totalBytes: Long,
        val speed: String,
        val eta: String
    )

    fun parseProgressLine(line: String, fallbackPercent: Float, fallbackEtaSecs: Long): ParsedProgress {
        var percent = fallbackPercent
        var downloadedBytes = 0L
        var totalBytes = 0L
        var speed = ""
        var eta = if (fallbackEtaSecs > 0) "${fallbackEtaSecs}s" else ""

        val percentMatch = Regex("""(\d+(?:\.\d+)?)%""").find(line)
        if (percentMatch != null) {
            percent = percentMatch.groupValues[1].toFloatOrNull() ?: fallbackPercent
        }

        val totalMatch = Regex("""of\s+~?(\d+(?:\.\d+)?)\s*([KMGT]?i?B)""", RegexOption.IGNORE_CASE).find(line)
        if (totalMatch != null) {
            val value = totalMatch.groupValues[1].toDoubleOrNull() ?: 0.0
            val unit = totalMatch.groupValues[2]
            totalBytes = parseBytes(value, unit)
            if (totalBytes > 0 && percent > 0f) {
                downloadedBytes = (totalBytes * (percent / 100.0)).toLong()
            }
        }

        val speedMatch = Regex("""at\s+(\d+(?:\.\d+)?\s*[KMGT]?i?B/s)""", RegexOption.IGNORE_CASE).find(line)
        if (speedMatch != null) {
            speed = speedMatch.groupValues[1].trim()
        }

        val etaMatch = Regex("""ETA\s+(\d+:\d+(?::\d+)?)""", RegexOption.IGNORE_CASE).find(line)
        if (etaMatch != null) {
            eta = etaMatch.groupValues[1].trim()
        }

        if (downloadedBytes == 0L) {
            val downloadedMatch = Regex("""\[download\]\s+(\d+(?:\.\d+)?)\s*([KMGT]?i?B)""", RegexOption.IGNORE_CASE).find(line)
            if (downloadedMatch != null) {
                val value = downloadedMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                val unit = downloadedMatch.groupValues[2]
                downloadedBytes = parseBytes(value, unit)
            }
        }

        return ParsedProgress(percent, downloadedBytes, totalBytes, speed, eta)
    }

    private fun parseBytes(value: Double, unit: String): Long {
        val upper = unit.uppercase()
        val multiplier: Double = when {
            upper.startsWith("KIB") -> 1024.0
            upper.startsWith("KB") -> 1000.0
            upper.startsWith("MIB") -> 1024.0 * 1024.0
            upper.startsWith("MB") -> 1000.0 * 1000.0
            upper.startsWith("GIB") -> 1024.0 * 1024.0 * 1024.0
            upper.startsWith("GB") -> 1000.0 * 1000.0 * 1000.0
            upper.startsWith("TIB") -> 1024.0 * 1024.0 * 1024.0 * 1024.0
            upper.startsWith("TB") -> 1000.0 * 1000.0 * 1000.0 * 1000.0
            upper.startsWith("B") -> 1.0
            else -> 1024.0 * 1024.0
        }
        return (value * multiplier).toLong()
    }

    suspend fun executeDownload(
        request: DownloadRequest,
        downloadDir: String,
        progressCallback: (Float, Long, Long, String, String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val installed = getInstalledVersion()
        try {
            val isInstagram = UrlValidator.isInstagramUrl(request.url)
            val isFacebook = UrlValidator.isFacebookUrl(request.url)
            val isStory = UrlValidator.isInstagramStory(request.url)
            val requestedStoryId = if (isStory) UrlValidator.extractInstagramStoryId(request.url) else null

            val targetUrl = if (isStory) {
                UrlValidator.canonicalizeInstagramStoryUrl(request.url)
            } else {
                request.url
            }

            val dlRequest = YoutubeDLRequest(targetUrl)
            dlRequest.addOption("--no-playlist")

            val cookieFile = authCookieManager.getCookieFile(targetUrl)
            if (cookieFile != null) {
                dlRequest.addOption("--cookies", cookieFile.absolutePath)
            }

            val effectiveFormat = if (isInstagram || isFacebook) {
                when {
                    request.isAudioOnly -> "bestaudio/best"
                    request.formatId.contains("bestaudio") || request.formatId.contains("+") || request.formatId.isBlank() -> "bestvideo+bestaudio/best"
                    else -> request.formatId.ifBlank { "bestvideo+bestaudio/best" }
                }
            } else if (request.formatId.isNotBlank()) {
                request.formatId
            } else {
                "bestvideo+bestaudio/best"
            }
            dlRequest.addOption("-f", effectiveFormat)

            // Preserve non-ASCII / Unicode titles (Tamil, Hindi, Japanese, emoji)
            dlRequest.addOption("--no-restrict-filenames")

            // Safe Unicode truncation to <= 80 bytes in UTF-8 to prevent [Errno 36] File name too long
            val safeTitle = com.glypdl.android.util.FileNameSanitizer.safeFsTitle(request.title, 80)
            val filenameTemplate = if (safeTitle.isNotBlank()) {
                "${safeTitle}_%(id)s.%(ext)s"
            } else {
                "%(id)s.%(ext)s"
            }

            val outputPath = File(downloadDir, filenameTemplate).absolutePath
            dlRequest.addOption("-o", outputPath)

            if (!request.isAudioOnly && (effectiveFormat.contains("+") || effectiveFormat.contains("bestvideo"))) {
                dlRequest.addOption("--merge-output-format", request.ext.ifEmpty { "mp4" })
            }

            var finalOutputFile = ""
            var detectedMediaId: String? = null

            YoutubeDL.getInstance().execute(dlRequest, null) { progress, etaInSeconds, line ->
                val parsed = parseProgressLine(line, progress, etaInSeconds)
                progressCallback(parsed.percent, parsed.downloadedBytes, parsed.totalBytes, parsed.speed, parsed.eta)

                val trimmed = line.trim()
                if (trimmed.contains("[Instagram:story]") || trimmed.contains("[Instagram]")) {
                    val idMatch = Regex("""\[Instagram(?::story)?\]\s+([0-9A-Za-z_-]+):""").find(trimmed)
                    if (idMatch != null) {
                        detectedMediaId = idMatch.groupValues[1]
                    }
                }

                if (trimmed.contains("[Merger] Merging formats into")) {
                    val dest = trimmed.substringAfter("Merging formats into").trim().trim('"', '\'')
                    if (dest.isNotBlank()) {
                        finalOutputFile = dest
                    }
                } else if (trimmed.contains("[ExtractAudio] Destination:")) {
                    val dest = trimmed.substringAfter("Destination:").trim().trim('"', '\'')
                    if (dest.isNotBlank()) {
                        finalOutputFile = dest
                    }
                } else if (trimmed.contains("Destination:")) {
                    val dest = trimmed.substringAfter("Destination:").trim().trim('"', '\'')
                    if (dest.isNotBlank()) {
                        finalOutputFile = dest
                    }
                } else if (trimmed.contains("has already been downloaded")) {
                    val dest = trimmed.substringBefore("has already been").removePrefix("[download]").trim().trim('"', '\'')
                    if (dest.isNotBlank()) {
                        finalOutputFile = dest
                    }
                }
            }

            finalOutputFile = finalOutputFile.trim('"', '\'')

            // If path is missing, unexpanded template, or non-existent, find the output file in downloadDir
            if (finalOutputFile.isBlank() || finalOutputFile.contains("%(") || !File(finalOutputFile).exists()) {
                val dir = File(downloadDir)
                if (dir.exists() && dir.isDirectory) {
                    val candidate = dir.listFiles()
                        ?.filter { it.isFile && it.length() > 0 && (System.currentTimeMillis() - it.lastModified() < 300_000) }
                        ?.maxByOrNull { it.lastModified() }
                    if (candidate != null) {
                        finalOutputFile = candidate.absolutePath
                    }
                }
            }

            if (finalOutputFile.isBlank()) {
                finalOutputFile = outputPath
            }

            // CRITICAL VALIDATION: For Instagram Story, verify downloaded story ID matches requested story ID
            if (requestedStoryId != null) {
                val fileObj = File(finalOutputFile)
                val fileName = fileObj.name
                val matchesFileName = fileName.contains(requestedStoryId)
                val matchesDetectedId = detectedMediaId == requestedStoryId

                if (!matchesFileName && detectedMediaId != null && !matchesDetectedId) {
                    // Mismatched story was downloaded! Clean up the incorrect file
                    if (fileObj.exists()) {
                        fileObj.delete()
                    }
                    return@withContext Result.failure(
                        GlypdlException(
                            GlypdlError.StoryMismatch(
                                requestedId = requestedStoryId,
                                downloadedId = detectedMediaId,
                                technicalDetails = "Requested story ID $requestedStoryId but yt-dlp extracted media $detectedMediaId"
                            )
                        )
                    )
                }
            }

            Result.success(finalOutputFile)
        } catch (e: Exception) {
            e.printStackTrace()
            val parsedError = YtDlpErrorParser.parse(e.message, installed)
            Result.failure(GlypdlException(parsedError))
        }
    }
}
