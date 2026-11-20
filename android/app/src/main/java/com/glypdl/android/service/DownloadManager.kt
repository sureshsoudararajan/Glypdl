/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service

import android.content.Context
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.local.entity.HistoryEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.DownloadStatus
import com.glypdl.android.data.model.SubtitleDownloadMode
import com.glypdl.android.data.model.SubtitleDownloadOptions
import com.glypdl.android.data.model.SubtitleFile
import com.glypdl.android.data.model.SubtitleFormatPreference
import com.glypdl.android.data.repository.DownloadRepository
import com.glypdl.android.data.repository.HistoryRepository
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.data.repository.SubtitleRepository
import com.glypdl.android.service.engine.SubtitleParser
import com.glypdl.android.util.StorageHelper
import com.glypdl.android.util.DiagnosticLogger
import com.glypdl.android.util.LogLevel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val ytDlpService: YtDlpService,
    private val downloadRepository: DownloadRepository,
    private val historyRepository: HistoryRepository,
    private val subtitleRepository: SubtitleRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadNotificationManager: DownloadNotificationManager,
    private val networkMonitor: com.glypdl.android.util.NetworkMonitor,
    @ApplicationContext private val context: Context
) {
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val requestMap = ConcurrentHashMap<String, DownloadRequest>()
    private val queueMutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enqueueDownload(request: DownloadRequest) {
        DiagnosticLogger.log("DownloadManager", "Enqueued task: ${request.title} (ID: ${request.id}, mode: ${request.subtitleOptions.mode})", LogLevel.INFO)
        requestMap[request.id] = request
        val subtitleJson = serializeSubtitleOptions(request.subtitleOptions)
        val entity = DownloadEntity(
            id = request.id,
            url = request.url,
            title = request.title,
            thumbnailUrl = request.thumbnailUrl,
            formatId = request.formatId,
            ext = request.ext,
            resolution = request.resolution,
            isAudioOnly = request.isAudioOnly,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 0L,
            speed = "",
            filePath = request.destinationUri ?: "",
            errorMessage = null,
            technicalLog = null,
            subtitleOptionsJson = subtitleJson,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        downloadRepository.insertDownload(entity)
        startForegroundServiceSafely(request.id)
        processQueue()
    }

    suspend fun processQueue() {
        queueMutex.withLock {
            val isWifiOnly = settingsRepository.wifiOnly.first()
            if (isWifiOnly && !networkMonitor.isWifi.value) {
                // User requested downloads to proceed strictly over unmetered Wi-Fi
                return
            }

            val concurrentLimit = settingsRepository.concurrentDownloads.first().coerceIn(1, 10)
            val inFlightCount = activeJobs.size

            if (inFlightCount < concurrentLimit) {
                val queuedDownloads = try {
                    downloadRepository.getQueuedDownloadsFifo().first()
                } catch (_: Exception) {
                    try {
                        downloadRepository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
                    } catch (_: Exception) {
                        emptyList()
                    }
                }
                val slotsAvailable = (concurrentLimit - inFlightCount).coerceAtLeast(0)

                // IMPORTANT: Filter out items that are already active in activeJobs before taking slotsAvailable.
                // This prevents active tasks from starving queued tasks of slots.
                val eligible = queuedDownloads.filter { !activeJobs.containsKey(it.id) }
                eligible.take(slotsAvailable).forEach { entity ->
                    startDownload(entity)
                }
            }
        }
    }

    private fun startDownload(entity: DownloadEntity) {
        if (activeJobs.containsKey(entity.id)) return
        DiagnosticLogger.log("DownloadManager", "Starting download: ${entity.title} (${entity.id})", LogLevel.INFO)

        val job = scope.launch {
            try {
                downloadRepository.updateStatus(entity.id, DownloadStatus.PREPARING)
                downloadRepository.updateStatus(entity.id, DownloadStatus.DOWNLOADING)
                downloadNotificationManager.onDownloadStarted(entity.id, entity.title)

                val inMemoryRequest = requestMap[entity.id]
                val subOptions = inMemoryRequest?.subtitleOptions ?: deserializeSubtitleOptions(entity.subtitleOptionsJson)
                val request = inMemoryRequest ?: DownloadRequest(
                    id = entity.id,
                    url = entity.url,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    formatId = entity.formatId,
                    ext = entity.ext,
                    resolution = entity.resolution,
                    isAudioOnly = entity.isAudioOnly,
                    destinationUri = entity.filePath,
                    subtitleOptions = subOptions
                )

                val stagingDir = com.glypdl.android.util.StorageHelper.getDefaultStagingDir(context)
                var lastDbUpdateTime = 0L

                var statusSetToProcessing = false
                val result = ytDlpService.download(request, stagingDir.absolutePath) { progress, downloaded, total, speed, eta ->
                    val isMerging = speed.contains("Merging", ignoreCase = true)
                    if (isMerging && !statusSetToProcessing) {
                        statusSetToProcessing = true
                        launch {
                            downloadRepository.updateStatus(entity.id, DownloadStatus.PROCESSING)
                        }
                    }

                    val currentProgress = DownloadProgress(
                        downloadId = entity.id,
                        percent = progress,
                        downloadedBytes = downloaded,
                        totalBytes = total,
                        speed = speed,
                        eta = eta,
                        title = entity.title
                    )
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                        put(entity.id, currentProgress)
                    }
                    downloadNotificationManager.onDownloadProgress(entity.id, entity.title, progress, speed, eta)

                    // Throttle Room DB updates to at most once every 1.5 seconds
                    val now = System.currentTimeMillis()
                    if (now - lastDbUpdateTime > 1500L) {
                        lastDbUpdateTime = now
                        launch {
                            downloadRepository.updateProgress(entity.id, progress, downloaded, total, speed)
                        }
                    }
                }

                result.onSuccess { path ->
                    DiagnosticLogger.log("DownloadManager", "Media processing complete: $path", LogLevel.INFO)
                    val file = File(path)
                    if (!file.exists() || file.length() <= 0L) {
                        val errorMsg = "Download completed but output file was not created or is empty (0 B)."
                        downloadRepository.updateDownload(
                            entity.copy(
                                status = DownloadStatus.FAILED,
                                errorMessage = errorMsg,
                                technicalLog = "Output file: $path does not exist or has 0 bytes.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        downloadNotificationManager.onDownloadFailed(entity.id, entity.title, errorMsg)
                    } else {
                        val fileLength = file.length()
                        val actualExt = file.extension.ifBlank { entity.ext }
                        val customTreeUri = settingsRepository.downloadDirUri.first()
                        val subtitleExtensions = setOf("srt", "vtt", "ass", "ssa", "lrc", "ttml", "sbv", "sub")

                        if (request.subtitleOptions.isSubtitlesOnly) {
                            // Collect ALL subtitle files from the staging directory (not just the one
                            // returned by yt-dlp as the "primary" output). yt-dlp writes one file per
                            // language, e.g. title_id.en.srt, title_id.es.srt, title_id.ta.srt.
                            val allSubFiles = stagingDir.listFiles()?.filter { f ->
                                f.isFile && f.length() > 0 &&
                                f.extension.lowercase() in subtitleExtensions &&
                                (System.currentTimeMillis() - f.lastModified() < 600_000)
                            }?.sortedByDescending { it.lastModified() } ?: listOf(file)

                            // The primary file (returned by yt-dlp) is used for the download record
                            val primaryFile = allSubFiles.firstOrNull() ?: file
                            val primaryExt = primaryFile.extension.ifBlank { actualExt }
                            val primaryMime = StorageHelper.getMimeType(primaryExt)
                            val primaryExport = StorageHelper.exportToPermanentStorage(
                                context = context,
                                stagingFile = primaryFile,
                                displayName = primaryFile.name,
                                mimeType = primaryMime,
                                isAudio = false,
                                customTreeUriString = customTreeUri,
                                overrideCategory = StorageHelper.MediaCategory.VIDEOS
                            )
                            primaryExport.onSuccess { permanentUri ->
                                val finalUriString = permanentUri.toString()
                                DiagnosticLogger.log("DownloadManager", "Subtitle exported to storage: ${primaryFile.name} ($finalUriString)", LogLevel.INFO)
                                downloadRepository.updateDownload(
                                    entity.copy(
                                        status = DownloadStatus.COMPLETED,
                                        filePath = finalUriString,
                                        downloadedBytes = fileLength,
                                        totalBytes = fileLength,
                                        progress = 100f,
                                        ext = primaryExt,
                                        errorMessage = null,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                val history = HistoryEntity(
                                    id = 0,
                                    downloadId = entity.id,
                                    url = entity.url,
                                    title = entity.title,
                                    thumbnailUrl = entity.thumbnailUrl,
                                    format = "Subtitle (${primaryExt.uppercase()})",
                                    filePath = finalUriString,
                                    fileUri = finalUriString,
                                    fileSize = primaryFile.length(),
                                    duration = 0L,
                                    completedAt = System.currentTimeMillis()
                                )
                                historyRepository.insertHistory(history)

                                val primaryLangCode = primaryFile.nameWithoutExtension.substringAfterLast('.', "und")
                                subtitleRepository.insertSubtitle(
                                    SubtitleFile(
                                        id = 0,
                                        downloadId = entity.id,
                                        mediaTitle = entity.title,
                                        mediaUrl = entity.url,
                                        languageCode = primaryLangCode,
                                        languageName = SubtitleParser.resolveLanguageName(primaryLangCode),
                                        format = primaryExt,
                                        isAutoGenerated = request.subtitleOptions.includeAutoGenerated && !request.subtitleOptions.includeManual,
                                        contentUri = finalUriString,
                                        filePath = null,
                                        fileName = primaryFile.name,
                                        fileSize = primaryFile.length(),
                                        createdAt = System.currentTimeMillis(),
                                        isEmbedded = false
                                    )
                                )

                                // Export every remaining subtitle file (additional languages)
                                for (extraSubFile in allSubFiles.drop(1)) {
                                    val extraExt = extraSubFile.extension.lowercase()
                                    val extraMime = StorageHelper.getMimeType(extraExt)
                                    StorageHelper.exportToPermanentStorage(
                                        context = context,
                                        stagingFile = extraSubFile,
                                        displayName = extraSubFile.name,
                                        mimeType = extraMime,
                                        isAudio = false,
                                        customTreeUriString = customTreeUri,
                                        overrideCategory = StorageHelper.MediaCategory.VIDEOS
                                    ).onSuccess { extraUri ->
                                        val extraLangCode = extraSubFile.nameWithoutExtension.substringAfterLast('.', "und")
                                        subtitleRepository.insertSubtitle(
                                            SubtitleFile(
                                                id = 0,
                                                downloadId = entity.id,
                                                mediaTitle = entity.title,
                                                mediaUrl = entity.url,
                                                languageCode = extraLangCode,
                                                languageName = SubtitleParser.resolveLanguageName(extraLangCode),
                                                format = extraExt,
                                                isAutoGenerated = extraSubFile.name.contains(".auto.", ignoreCase = true),
                                                contentUri = extraUri.toString(),
                                                filePath = null,
                                                fileName = extraSubFile.name,
                                                fileSize = extraSubFile.length(),
                                                createdAt = System.currentTimeMillis(),
                                                isEmbedded = false
                                            )
                                        )
                                    }
                                }

                                downloadNotificationManager.onDownloadCompleted(entity.id, entity.title, finalUriString)
                            }.onFailure { exportError ->
                                val errorMsg = "Unable to save subtitle to device storage: ${exportError.message ?: "Unknown storage error"}"
                                downloadRepository.updateDownload(
                                    entity.copy(
                                        status = DownloadStatus.FAILED,
                                        errorMessage = errorMsg,
                                        technicalLog = exportError.stackTraceToString(),
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                downloadNotificationManager.onDownloadFailed(entity.id, entity.title, errorMsg)
                            }
                        } else {
                            val mimeType = StorageHelper.getMimeType(actualExt, entity.isAudioOnly)
                            val displayName = file.name

                            val exportResult = StorageHelper.exportToPermanentStorage(
                                context = context,
                                stagingFile = file,
                                displayName = displayName,
                                mimeType = mimeType,
                                isAudio = entity.isAudioOnly,
                                customTreeUriString = customTreeUri
                            )

                            exportResult.onSuccess { permanentUri ->
                                val finalUriString = permanentUri.toString()
                                DiagnosticLogger.log("DownloadManager", "Exported to storage: $displayName ($finalUriString)", LogLevel.INFO)
                                downloadRepository.updateDownload(
                                    entity.copy(
                                        status = DownloadStatus.COMPLETED,
                                        filePath = finalUriString,
                                        downloadedBytes = fileLength,
                                        totalBytes = fileLength,
                                        progress = 100f,
                                        ext = actualExt,
                                        errorMessage = null,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                val history = HistoryEntity(
                                    id = 0,
                                    downloadId = entity.id,
                                    url = entity.url,
                                    title = entity.title,
                                    thumbnailUrl = entity.thumbnailUrl,
                                    format = request.resolution ?: request.ext,
                                    filePath = finalUriString,
                                    fileUri = finalUriString,
                                    fileSize = fileLength,
                                    duration = 0L,
                                    completedAt = System.currentTimeMillis()
                                )
                                historyRepository.insertHistory(history)

                                // Handle downloaded subtitle files in staging directory.
                                val downloadedSubs = stagingDir.listFiles()?.filter { f ->
                                    f.isFile && f.length() > 0 && f.extension.lowercase() in subtitleExtensions &&
                                    (System.currentTimeMillis() - f.lastModified() < 600_000)
                                } ?: emptyList()

                                if (request.subtitleOptions.shouldEmbed) {
                                    // When subtitles are embedded in the video container, do NOT export
                                    // separate subtitle files to public storage (the user expects a single
                                    // combined video file, not separate video + subtitle files).
                                    // We register each embedded track in Room DB referencing the video's URI,
                                    // and clean up the temporary subtitle files from staging.
                                    for (subFile in downloadedSubs) {
                                        val subDisplayName = subFile.name
                                        val subExt = subFile.extension.lowercase()
                                        val subLength = subFile.length()
                                        val subNameWithoutExt = subFile.nameWithoutExtension
                                        val subLangCode = subNameWithoutExt.substringAfterLast('.', "und")
                                        val subModel = SubtitleFile(
                                            id = 0,
                                            downloadId = entity.id,
                                            mediaTitle = entity.title,
                                            mediaUrl = entity.url,
                                            languageCode = subLangCode,
                                            languageName = SubtitleParser.resolveLanguageName(subLangCode),
                                            format = subExt,
                                            isAutoGenerated = subDisplayName.contains(".auto.", ignoreCase = true),
                                            contentUri = finalUriString,
                                            filePath = null,
                                            fileName = subDisplayName,
                                            fileSize = subLength,
                                            createdAt = System.currentTimeMillis(),
                                            isEmbedded = true
                                        )
                                        subtitleRepository.insertSubtitle(subModel)
                                        try { subFile.delete() } catch (_: Exception) {}
                                    }
                                } else {
                                    // Subtitle mode is MEDIA_WITH_SUBS: export subtitles as separate files alongside the video
                                    for (subFile in downloadedSubs) {
                                        val subDisplayName = subFile.name
                                        val subExt = subFile.extension.lowercase()
                                        val subLength = subFile.length()
                                        val subMime = StorageHelper.getMimeType(subExt)
                                        val subExport = StorageHelper.exportToPermanentStorage(
                                            context = context,
                                            stagingFile = subFile,
                                            displayName = subDisplayName,
                                            mimeType = subMime,
                                            isAudio = false,
                                            customTreeUriString = customTreeUri,
                                            overrideCategory = StorageHelper.MediaCategory.VIDEOS
                                        )
                                        subExport.onSuccess { subUri ->
                                            val subNameWithoutExt = subFile.nameWithoutExtension
                                            val subLangCode = subNameWithoutExt.substringAfterLast('.', "und")
                                            val subModel = SubtitleFile(
                                                id = 0,
                                                downloadId = entity.id,
                                                mediaTitle = entity.title,
                                                mediaUrl = entity.url,
                                                languageCode = subLangCode,
                                                languageName = SubtitleParser.resolveLanguageName(subLangCode),
                                                format = subExt,
                                                isAutoGenerated = subDisplayName.contains(".auto.", ignoreCase = true),
                                                contentUri = subUri.toString(),
                                                filePath = null,
                                                fileName = subDisplayName,
                                                fileSize = subLength,
                                                createdAt = System.currentTimeMillis(),
                                                isEmbedded = false
                                            )
                                            subtitleRepository.insertSubtitle(subModel)
                                        }
                                    }
                                }

                                downloadNotificationManager.onDownloadCompleted(entity.id, entity.title, finalUriString)
                            }.onFailure { exportError ->
                                val mediaLabel = if (entity.isAudioOnly) "audio" else "video"
                                val errorMsg = "Unable to save $mediaLabel to device storage: ${exportError.message ?: "Unknown storage error"}"
                                val technicalDetails = buildString {
                                    appendLine("Storage Export Failure:")
                                    appendLine("Display Name: $displayName")
                                    appendLine("Actual Extension: $actualExt")
                                    appendLine("MIME Type: $mimeType")
                                    appendLine("Is Audio: ${entity.isAudioOnly}")
                                    appendLine("Staging File: ${file.absolutePath} (${file.length()} bytes)")
                                    appendLine("Target Storage: ${customTreeUri ?: "Default Public MediaStore"}")
                                    appendLine("Error: ${exportError.message}")
                                    appendLine(exportError.stackTraceToString())
                                }
                                val redactedLog = com.glypdl.android.service.engine.LogRedactor.redact(technicalDetails)
                                downloadRepository.updateDownload(
                                    entity.copy(
                                        status = DownloadStatus.FAILED,
                                        errorMessage = errorMsg,
                                        technicalLog = redactedLog,
                                        updatedAt = System.currentTimeMillis()
                                    )
                                )
                                downloadNotificationManager.onDownloadFailed(entity.id, entity.title, errorMsg)
                            }
                        }
                    }
                }.onFailure { error ->
                    DiagnosticLogger.logError("DownloadManager", "Task failed: ${entity.title} - ${error.message}", error)
                    if (error !is CancellationException) {
                        val userFriendlyMessage = when (error) {
                            is com.glypdl.android.data.model.GlypdlException -> error.error.userMessage
                            else -> com.glypdl.android.service.engine.YtDlpErrorParser.parse(error.message).userMessage
                        }
                        val technicalLog = when (error) {
                            is com.glypdl.android.data.model.GlypdlException ->
                                error.error.technicalDetails ?: com.glypdl.android.service.engine.LogRedactor.redact(error.stackTraceToString())
                            else -> com.glypdl.android.service.engine.LogRedactor.redact(error.stackTraceToString())
                        }
                        downloadRepository.updateDownload(
                            entity.copy(
                                status = DownloadStatus.FAILED,
                                errorMessage = userFriendlyMessage,
                                technicalLog = technicalLog,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        downloadNotificationManager.onDownloadFailed(entity.id, entity.title, userFriendlyMessage)
                    }
                }
            } catch (t: Throwable) {
                if (t !is CancellationException) {
                    val userFriendly = com.glypdl.android.service.engine.YtDlpErrorParser.parse(t.message).userMessage
                    downloadRepository.updateDownload(
                        entity.copy(
                            status = DownloadStatus.FAILED,
                            errorMessage = userFriendly,
                            technicalLog = t.stackTraceToString(),
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                    downloadNotificationManager.onDownloadFailed(entity.id, entity.title, userFriendly)
                }
            } finally {
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(entity.id) }
                activeJobs.remove(entity.id)
                processQueue()
            }
        }

        activeJobs[entity.id] = job
    }

    suspend fun pauseDownload(id: String) {
        ytDlpService.cancelProcess(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        downloadNotificationManager.onDownloadPaused(id)
        downloadRepository.updateStatus(id, DownloadStatus.PAUSED)
        processQueue()
    }

    suspend fun resumeDownload(id: String) {
        downloadRepository.updateStatus(id, DownloadStatus.QUEUED)
        startForegroundServiceSafely(id)
        processQueue()
    }

    suspend fun cancelDownload(id: String) {
        ytDlpService.cancelProcess(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        downloadRepository.updateStatus(id, DownloadStatus.CANCELLED)
        downloadNotificationManager.onDownloadCancelled(id)
        
        // Clean up partial files
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null && !entity.filePath.isNullOrBlank()) {
            val file = File(entity.filePath)
            if (file.exists()) {
                file.delete()
            }
        }
        try {
            val stagingDir = com.glypdl.android.util.StorageHelper.getDefaultStagingDir(context)
            stagingDir.listFiles()?.filter { f ->
                f.name.contains(id) || (entity != null && entity.title.isNotBlank() && f.name.contains(entity.title.take(20)))
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        processQueue()
    }

    suspend fun deleteDownload(id: String, deleteFile: Boolean = true) {
        ytDlpService.cancelProcess(id)
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        requestMap.remove(id)
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        downloadNotificationManager.onDownloadCancelled(id)

        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null) {
            if (deleteFile) {
                if (!entity.filePath.isNullOrBlank()) {
                    try {
                        val uri = android.net.Uri.parse(entity.filePath)
                        if (uri.scheme == "content") {
                            context.contentResolver.delete(uri, null, null)
                        } else {
                            val file = File(entity.filePath)
                            if (file.exists()) {
                                file.delete()
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
                subtitleRepository.deleteSubtitlesForDownload(id, deleteFileFromDisk = true)
            }
            downloadRepository.deleteDownload(id)
        }
        try {
            val stagingDir = com.glypdl.android.util.StorageHelper.getDefaultStagingDir(context)
            stagingDir.listFiles()?.filter { f ->
                f.name.contains(id) || (entity != null && entity.title.isNotBlank() && f.name.contains(entity.title.take(20)))
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        processQueue()
    }

    suspend fun retryDownload(id: String) {
        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null) {
            var updatedFormatId = entity.formatId
            val isFormatError = entity.errorMessage?.contains("format", ignoreCase = true) == true ||
                    entity.errorMessage?.contains("unavailable", ignoreCase = true) == true
            if (isFormatError) {
                val analyzeResult = ytDlpService.extractInfo(entity.url)
                analyzeResult.onSuccess { mediaInfo ->
                    val fallbackFormat = if (entity.isAudioOnly) {
                        mediaInfo.audioQualities.firstOrNull()?.formatSelector
                            ?: mediaInfo.formats.firstOrNull { it.isAudioOnly }?.formatId
                            ?: "bestaudio/best"
                    } else {
                        mediaInfo.videoQualities.firstOrNull()?.formatSelector
                            ?: mediaInfo.formats.firstOrNull { !it.isAudioOnly }?.formatId
                            ?: "bestvideo+bestaudio/best"
                    }
                    updatedFormatId = fallbackFormat
                }
            }

            downloadRepository.updateDownload(
                entity.copy(
                    formatId = updatedFormatId,
                    status = DownloadStatus.QUEUED,
                    errorMessage = null,
                    technicalLog = null,
                    progress = 0f,
                    downloadedBytes = 0L,
                    updatedAt = System.currentTimeMillis()
                )
            )
            startForegroundServiceSafely(id)
            processQueue()
        }
    }

    private fun startForegroundServiceSafely(downloadId: String) {
        try {
            DownloadForegroundService.start(context, downloadId)
        } catch (e: Throwable) {
            // Safe fallback in unit tests or background restrictions
        }
    }

    suspend fun recoverStaleJobs() {
        val downloading = downloadRepository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
        val preparing = downloadRepository.getDownloadsByStatus(DownloadStatus.PREPARING).first()
        val processing = downloadRepository.getDownloadsByStatus(DownloadStatus.PROCESSING).first()
        val staleJobs = downloading + preparing + processing
        staleJobs.forEach { stale ->
            downloadRepository.updateDownload(
                stale.copy(
                    status = DownloadStatus.QUEUED,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        if (staleJobs.isNotEmpty()) {
            processQueue()
        }
    }

    suspend fun hasActiveOrQueuedDownloads(): Boolean {
        if (activeJobs.isNotEmpty()) return true
        val queued = downloadRepository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
        val preparing = downloadRepository.getDownloadsByStatus(DownloadStatus.PREPARING).first()
        val downloading = downloadRepository.getDownloadsByStatus(DownloadStatus.DOWNLOADING).first()
        val processing = downloadRepository.getDownloadsByStatus(DownloadStatus.PROCESSING).first()
        return queued.isNotEmpty() || preparing.isNotEmpty() || downloading.isNotEmpty() || processing.isNotEmpty()
    }

    fun getProgress(downloadId: String): Flow<DownloadProgress?> {
        return _downloadProgress.map { it[downloadId] }
    }

    private fun serializeSubtitleOptions(opts: SubtitleDownloadOptions): String {
        return JSONObject().apply {
            put("mode", opts.mode.name)
            put("selectedLanguages", JSONArray(opts.selectedLanguages))
            put("downloadAllLanguages", opts.downloadAllLanguages)
            put("includeManual", opts.includeManual)
            put("includeAutoGenerated", opts.includeAutoGenerated)
            put("preferredFormat", opts.preferredFormat.name)
            put("embedInVideo", opts.embedInVideo)
        }.toString()
    }

    private fun deserializeSubtitleOptions(jsonStr: String?): SubtitleDownloadOptions {
        if (jsonStr.isNullOrBlank()) return SubtitleDownloadOptions()
        return try {
            val json = JSONObject(jsonStr)
            val modeStr = json.optString("mode", SubtitleDownloadMode.MEDIA_ONLY.name)
            val mode = try { SubtitleDownloadMode.valueOf(modeStr) } catch (e: Exception) { SubtitleDownloadMode.MEDIA_ONLY }
            val langsArray = json.optJSONArray("selectedLanguages")
            val langs = mutableSetOf<String>()
            if (langsArray != null) {
                for (i in 0 until langsArray.length()) {
                    langs.add(langsArray.getString(i))
                }
            }
            val formatStr = json.optString("preferredFormat", SubtitleFormatPreference.BEST.name)
            val format = SubtitleFormatPreference.fromFormat(formatStr)
            SubtitleDownloadOptions(
                mode = mode,
                selectedLanguages = langs,
                downloadAllLanguages = json.optBoolean("downloadAllLanguages", false),
                includeManual = json.optBoolean("includeManual", true),
                includeAutoGenerated = json.optBoolean("includeAutoGenerated", false),
                preferredFormat = format,
                embedInVideo = json.optBoolean("embedInVideo", false)
            )
        } catch (e: Exception) {
            SubtitleDownloadOptions()
        }
    }
}
