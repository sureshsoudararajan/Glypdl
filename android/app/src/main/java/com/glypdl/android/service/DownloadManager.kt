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
import com.glypdl.android.data.repository.DownloadRepository
import com.glypdl.android.data.repository.HistoryRepository
import com.glypdl.android.data.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val ytDlpService: YtDlpService,
    private val aria2DownloadEngine: com.glypdl.android.service.engine.Aria2DownloadEngine,
    private val downloadRepository: DownloadRepository,
    private val historyRepository: HistoryRepository,
    private val settingsRepository: SettingsRepository,
    private val downloadNotificationManager: DownloadNotificationManager,
    @ApplicationContext private val context: Context
) {
    private val _downloadProgress = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, DownloadProgress>> = _downloadProgress.asStateFlow()

    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun enqueueDownload(request: DownloadRequest) {
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
            totalBytes = request.contentLength ?: 0L,
            speed = "",
            filePath = request.destinationUri ?: "",
            errorMessage = null,
            technicalLog = null,
            downloadType = request.downloadType,
            engineType = request.engineType,
            fileCategory = request.fileCategory,
            fileName = request.fileName,
            contentLength = request.contentLength ?: 0L,
            mimeType = request.detectedMimeType,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        downloadRepository.insertDownload(entity)
        startForegroundServiceSafely(request.id)
        processQueue()
    }

    suspend fun processQueue() {
        val concurrentLimit = settingsRepository.concurrentDownloads.first().coerceIn(1, 10)
        val inFlightCount = activeJobs.size
        
        if (inFlightCount < concurrentLimit) {
            val queuedDownloads = downloadRepository.getDownloadsByStatus(DownloadStatus.QUEUED).first()
            val slotsAvailable = (concurrentLimit - inFlightCount).coerceAtLeast(0)
            
            queuedDownloads.take(slotsAvailable).forEach { entity ->
                startDownload(entity)
            }
        }
    }

    private fun startDownload(entity: DownloadEntity) {
        if (activeJobs.containsKey(entity.id)) return
        
        val job = scope.launch {
            try {
                if (entity.engineType == com.glypdl.android.data.model.EngineType.ARIA2C) {
                    downloadRepository.updateStatus(entity.id, DownloadStatus.PREPARING)

                    val selectedIndices = if (!entity.formatId.isNullOrBlank() && entity.formatId.all { it.isDigit() || it == ',' }) {
                        entity.formatId.split(",").mapNotNull { it.toIntOrNull() }
                    } else null

                    val existingGid = entity.aria2Gid
                    val existingValid = if (!existingGid.isNullOrBlank()) {
                        try {
                            val st = aria2DownloadEngine.getStatus(existingGid)
                            if (!st.isError && !st.isRemoved && !st.isComplete) {
                                if (st.isPaused) {
                                    aria2DownloadEngine.resumeDownload(existingGid)
                                }
                                true
                            } else false
                        } catch (e: Exception) {
                            false
                        }
                    } else false

                    val gid = if (existingValid && !existingGid.isNullOrBlank()) {
                        existingGid
                    } else if (entity.url.startsWith("torrent-base64:", ignoreCase = true)) {
                        val base64 = entity.url.removePrefix("torrent-base64:")
                        aria2DownloadEngine.addTorrentDownload(base64, selectedIndices)
                    } else if (entity.downloadType == com.glypdl.android.data.model.DownloadType.TORRENT && entity.url.startsWith("content://", ignoreCase = true)) {
                        val uri = android.net.Uri.parse(entity.url)
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalArgumentException("Could not read content URI: ${entity.url}")
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        aria2DownloadEngine.addTorrentDownload(base64, selectedIndices)
                    } else if (entity.downloadType == com.glypdl.android.data.model.DownloadType.TORRENT && entity.url.startsWith("file://", ignoreCase = true)) {
                        val path = entity.url.removePrefix("file://")
                        val file = File(path)
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        aria2DownloadEngine.addTorrentDownload(base64, selectedIndices)
                    } else if (entity.downloadType == com.glypdl.android.data.model.DownloadType.TORRENT && File(entity.url).exists()) {
                        val file = File(entity.url)
                        val bytes = file.readBytes()
                        val base64 = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        aria2DownloadEngine.addTorrentDownload(base64, selectedIndices)
                    } else {
                        val options = mutableMapOf<String, String>()
                        if (selectedIndices != null && selectedIndices.isNotEmpty()) {
                            options["select-file"] = selectedIndices.joinToString(",")
                        }
                        aria2DownloadEngine.startDownload(entity.url, entity.fileName, options)
                    }

                    downloadRepository.updateAria2Gid(entity.id, gid)
                    downloadRepository.updateStatus(entity.id, DownloadStatus.DOWNLOADING)
                    downloadNotificationManager.onDownloadStarted(entity.id, entity.title)

                var lastDbUpdateTime = 0L
                aria2DownloadEngine.pollProgress(gid).collect { status ->
                    if (status.gid.isNotBlank() && status.gid != entity.aria2Gid) {
                        downloadRepository.updateAria2Gid(entity.id, status.gid)
                    }

                    val percent = status.progressPercent
                    val speedStr = android.text.format.Formatter.formatShortFileSize(context, status.downloadSpeed) + "/s"
                    val etaStr = if (status.downloadSpeed > 0 && status.totalLength > status.completedLength) {
                        val remainingSec = (status.totalLength - status.completedLength) / status.downloadSpeed
                        if (remainingSec > 60) "${remainingSec / 60}m ${remainingSec % 60}s" else "${remainingSec}s"
                    } else ""

                    val uploadSpeedStr = if (status.uploadSpeed > 0) android.text.format.Formatter.formatShortFileSize(context, status.uploadSpeed) + "/s" else ""
                    val currentProgress = DownloadProgress(
                        downloadId = entity.id,
                        percent = percent,
                        downloadedBytes = status.completedLength,
                        totalBytes = status.totalLength,
                        speed = speedStr,
                        eta = etaStr,
                        title = entity.title,
                        seeds = status.numSeeders,
                        peers = status.connections,
                        uploadSpeed = uploadSpeedStr,
                        bitfield = status.bitfield,
                        totalPieces = status.numPieces
                    )
                    _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { put(entity.id, currentProgress) }
                    downloadNotificationManager.onDownloadProgress(
                        downloadId = entity.id,
                        title = entity.title,
                        percent = percent,
                        speed = speedStr,
                        eta = etaStr,
                        downloadedBytes = status.completedLength,
                        totalBytes = status.totalLength
                    )

                    val now = System.currentTimeMillis()
                    if (now - lastDbUpdateTime > 1500L) {
                        lastDbUpdateTime = now
                        launch { downloadRepository.updateProgress(entity.id, percent, status.completedLength, status.totalLength, speedStr) }
                    }

                    val isFinished = !status.isPaused &&
                        !status.isError &&
                        !status.isRemoved &&
                        status.followedBy.isNullOrEmpty() &&
                        (
                            status.status == "complete" ||
                            (status.totalLength > 0L && status.completedLength >= status.totalLength && status.completedLength > 0L) ||
                            (
                                status.files.isNotEmpty() &&
                                status.files.any { it.selected && it.length > 0L } &&
                                status.files.filter { it.selected }.all { it.length > 0L && it.completedLength >= it.length } &&
                                status.completedLength > 0L
                            )
                        )

                    if (isFinished) {
                        val customTreeUri = settingsRepository.downloadDirUri.first()
                        val completedFiles = status.files.filter { it.selected && it.length > 0L && it.completedLength == it.length }

                        val primaryFile = if (completedFiles.size > 1) {
                            var firstUri: android.net.Uri? = null
                            val torrentSub = com.glypdl.android.util.StorageHelper.getCategorySubfolder(
                                downloadType = entity.downloadType,
                                fileCategory = entity.fileCategory,
                                mimeType = null,
                                isAudio = false,
                                torrentTitle = entity.title
                            )
                            for (cFile in completedFiles) {
                                val f = File(cFile.path)
                                if (f.exists() && f.length() > 0L) {
                                    val exportRes = com.glypdl.android.util.StorageHelper.exportToPermanentStorage(
                                        context = context,
                                        stagingFile = f,
                                        displayName = f.name,
                                        mimeType = com.glypdl.android.util.StorageHelper.getMimeType(f.extension, false),
                                        isAudio = false,
                                        customTreeUriString = customTreeUri,
                                        subfolder = torrentSub
                                    )
                                    if (firstUri == null) {
                                        firstUri = exportRes.getOrNull()
                                    }
                                }
                            }
                            firstUri
                        } else {
                            val singleFile = completedFiles.firstOrNull()?.let { File(it.path) }
                                ?: status.files.firstOrNull()?.path?.let { File(it) }
                                ?: File(com.glypdl.android.util.StorageHelper.getDefaultStagingDir(context), entity.fileName ?: "download")
                            if (singleFile.exists() && singleFile.length() > 0L) {
                                val sub = com.glypdl.android.util.StorageHelper.getCategorySubfolder(
                                    downloadType = entity.downloadType,
                                    fileCategory = entity.fileCategory,
                                    mimeType = entity.mimeType,
                                    isAudio = entity.fileCategory == com.glypdl.android.data.model.FileCategory.AUDIO
                                )
                                val exportResult = com.glypdl.android.util.StorageHelper.exportToPermanentStorage(
                                    context = context,
                                    stagingFile = singleFile,
                                    displayName = entity.fileName ?: singleFile.name,
                                    mimeType = entity.mimeType ?: "*/*",
                                    isAudio = entity.fileCategory == com.glypdl.android.data.model.FileCategory.AUDIO,
                                    customTreeUriString = customTreeUri,
                                    subfolder = sub
                                )
                                exportResult.getOrNull()
                            } else null
                        }

                        if (primaryFile != null) {
                            val finalUriString = primaryFile.toString()
                            downloadRepository.updateDownload(
                                entity.copy(
                                    status = DownloadStatus.COMPLETED,
                                    filePath = finalUriString,
                                    downloadedBytes = status.totalLength.takeIf { it > 0 } ?: status.completedLength,
                                    totalBytes = status.totalLength.takeIf { it > 0 } ?: status.completedLength,
                                    progress = 100f,
                                    speed = null,
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply {
                                put(entity.id, currentProgress.copy(percent = 100f, speed = "", eta = ""))
                            }
                            historyRepository.insertHistory(
                                HistoryEntity(
                                    id = 0,
                                    downloadId = entity.id,
                                    url = entity.url,
                                    title = entity.title,
                                    thumbnailUrl = entity.thumbnailUrl,
                                    format = entity.resolution ?: entity.ext,
                                    filePath = finalUriString,
                                    fileUri = finalUriString,
                                    fileSize = status.totalLength.takeIf { it > 0 } ?: status.completedLength,
                                    duration = 0L,
                                    completedAt = System.currentTimeMillis(),
                                    downloadType = entity.downloadType,
                                    engineType = entity.engineType,
                                    fileCategory = entity.fileCategory
                                )
                            )
                            try {
                                aria2DownloadEngine.cancelDownload(status.gid)
                                if (gid != status.gid) {
                                    aria2DownloadEngine.cancelDownload(gid)
                                }
                            } catch (e: Exception) {}
                            downloadNotificationManager.onDownloadCompleted(entity.id, entity.title, finalUriString)
                        } else {
                            val errorMsg = "Download completed but file could not be exported to storage"
                            downloadRepository.updateDownload(
                                entity.copy(
                                    status = DownloadStatus.FAILED,
                                    errorMessage = errorMsg,
                                    technicalLog = "Files: ${status.files.map { it.path }}, dir=${status.dir}",
                                    updatedAt = System.currentTimeMillis()
                                )
                            )
                            downloadNotificationManager.onDownloadFailed(entity.id, entity.title, errorMsg)
                        }
                    } else if (status.isError) {
                        val errorMsg = "Aria2 download failed: ${status.errorMessage ?: "Error code ${status.errorCode}"}"
                        downloadRepository.updateDownload(
                            entity.copy(
                                status = DownloadStatus.FAILED,
                                errorMessage = errorMsg,
                                technicalLog = "Error code: ${status.errorCode}, message: ${status.errorMessage}",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        downloadNotificationManager.onDownloadFailed(entity.id, entity.title, errorMsg)
                    }
                }
            } else {
                downloadRepository.updateStatus(entity.id, DownloadStatus.PREPARING)
                downloadRepository.updateStatus(entity.id, DownloadStatus.DOWNLOADING)
                downloadNotificationManager.onDownloadStarted(entity.id, entity.title)
                
                val request = DownloadRequest(
                    id = entity.id,
                    url = entity.url,
                    title = entity.title,
                    thumbnailUrl = entity.thumbnailUrl,
                    formatId = entity.formatId,
                    ext = entity.ext,
                    resolution = entity.resolution,
                    isAudioOnly = entity.isAudioOnly,
                    destinationUri = entity.filePath
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
                    downloadNotificationManager.onDownloadProgress(
                        downloadId = entity.id,
                        title = entity.title,
                        percent = progress,
                        speed = speed,
                        eta = eta,
                        downloadedBytes = downloaded,
                        totalBytes = total
                    )
                    
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
                        val mimeType = com.glypdl.android.util.StorageHelper.getMimeType(actualExt, entity.isAudioOnly)
                        val displayName = file.name

                        val mediaSub = com.glypdl.android.util.StorageHelper.getCategorySubfolder(
                            downloadType = entity.downloadType,
                            fileCategory = entity.fileCategory,
                            mimeType = mimeType,
                            isAudio = entity.isAudioOnly
                        )
                        val exportResult = com.glypdl.android.util.StorageHelper.exportToPermanentStorage(
                            context = context,
                            stagingFile = file,
                            displayName = displayName,
                            mimeType = mimeType,
                            isAudio = entity.isAudioOnly,
                            customTreeUriString = customTreeUri,
                            subfolder = mediaSub
                        )

                        exportResult.onSuccess { permanentUri ->
                            val finalUriString = permanentUri.toString()
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
                }.onFailure { error ->
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
            }
        } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                val userFriendlyMessage = "Download failed: ${e.message ?: "Unknown error"}"
                val technicalLog = com.glypdl.android.service.engine.LogRedactor.redact(e.stackTraceToString())
                downloadRepository.updateDownload(
                    entity.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = userFriendlyMessage,
                        technicalLog = technicalLog,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                downloadNotificationManager.onDownloadFailed(entity.id, entity.title, userFriendlyMessage)
            } finally {
                _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(entity.id) }
                activeJobs.remove(entity.id)
                processQueue()
            }
        }
        
        activeJobs[entity.id] = job
    }

    suspend fun pauseDownload(id: String) {
        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null && entity.engineType == com.glypdl.android.data.model.EngineType.ARIA2C && entity.aria2Gid != null) {
            aria2DownloadEngine.pauseDownload(entity.aria2Gid)
        }
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        downloadNotificationManager.onDownloadPaused(id)
        downloadRepository.updateStatus(id, DownloadStatus.PAUSED)
        processQueue()
    }

    suspend fun resumeDownload(id: String) {
        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null && entity.engineType == com.glypdl.android.data.model.EngineType.ARIA2C && entity.aria2Gid != null) {
            try {
                aria2DownloadEngine.resumeDownload(entity.aria2Gid)
            } catch (e: Exception) {}
        }
        downloadRepository.updateStatus(id, DownloadStatus.QUEUED)
        startForegroundServiceSafely(id)
        processQueue()
    }

    suspend fun cancelDownload(id: String) {
        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null && entity.engineType == com.glypdl.android.data.model.EngineType.ARIA2C && entity.aria2Gid != null) {
            aria2DownloadEngine.cancelDownload(entity.aria2Gid)
        }
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        downloadRepository.updateStatus(id, DownloadStatus.CANCELLED)
        downloadNotificationManager.onDownloadCancelled(id)
        
        // Clean up partial files & thumbnails
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        if (entity != null) {
            deleteThumbnails(entity)
            if (!entity.filePath.isNullOrBlank()) {
                val file = File(entity.filePath)
                if (file.exists()) {
                    file.delete()
                }
            }
        }
        processQueue()
    }

    suspend fun deleteDownload(id: String, deleteFile: Boolean = true) {
        val dEntity = downloadRepository.getDownloadByIdOnce(id)
        if (dEntity != null && dEntity.engineType == com.glypdl.android.data.model.EngineType.ARIA2C && dEntity.aria2Gid != null) {
            aria2DownloadEngine.cancelDownload(dEntity.aria2Gid)
        }
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        downloadNotificationManager.onDownloadCancelled(id)

        val entity = dEntity ?: downloadRepository.getDownloadByIdOnce(id)
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
                deleteThumbnails(entity)
            }
            downloadRepository.deleteDownload(id)
        }
        processQueue()
    }

    @OptIn(coil.annotation.ExperimentalCoilApi::class)
    private fun deleteThumbnails(entity: DownloadEntity) {
        try {
            // 1. Evict from Coil image cache (memory & disk)
            val thumbUrl = entity.thumbnailUrl
            if (!thumbUrl.isNullOrBlank()) {
                try {
                    val imageLoader = coil.Coil.imageLoader(context)
                    imageLoader.diskCache?.remove(thumbUrl)
                    imageLoader.memoryCache?.remove(coil.memory.MemoryCache.Key(thumbUrl))
                } catch (e: Exception) {
                    // Ignore cache eviction failure
                }

                if (thumbUrl.startsWith("file://") || thumbUrl.startsWith("/")) {
                    val path = thumbUrl.removePrefix("file://")
                    val f = File(path)
                    if (f.exists()) f.delete()
                } else if (thumbUrl.startsWith("content://")) {
                    try {
                        context.contentResolver.delete(android.net.Uri.parse(thumbUrl), null, null)
                    } catch (e: Exception) {}
                }
            }

            // 2. Remove companion thumbnail/info files from staging directory
            val stagingDir = com.glypdl.android.util.StorageHelper.getDefaultStagingDir(context)
            if (stagingDir.exists() && stagingDir.isDirectory) {
                val safeTitle = com.glypdl.android.util.FileNameSanitizer.safeFsTitle(entity.title, 80)
                stagingDir.listFiles { file ->
                    val name = file.name
                    name.startsWith(entity.id) ||
                    (safeTitle.isNotBlank() && name.startsWith(safeTitle)) ||
                    ((name.endsWith(".jpg") || name.endsWith(".webp") || name.endsWith(".png")) &&
                     (name.contains(entity.id) || (safeTitle.isNotBlank() && name.contains(safeTitle))))
                }?.forEach { it.delete() }
            }

            // 3. Remove cached torrent metadata file
            val torrentCacheDir = File(context.cacheDir, "torrents")
            if (torrentCacheDir.exists() && torrentCacheDir.isDirectory) {
                torrentCacheDir.listFiles { file ->
                    file.name.contains(entity.id)
                }?.forEach { it.delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
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
}
