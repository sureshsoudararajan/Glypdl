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
            totalBytes = 0L,
            speed = "",
            filePath = request.destinationUri ?: "",
            errorMessage = null,
            technicalLog = null,
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

            val result = ytDlpService.download(request, stagingDir.absolutePath) { progress, downloaded, total, speed, eta ->
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
                    val customTreeUri = settingsRepository.downloadDirUri.first()
                    val mimeType = com.glypdl.android.util.StorageHelper.getMimeType(entity.ext, entity.isAudioOnly)
                    val displayName = file.name

                    val exportResult = com.glypdl.android.util.StorageHelper.exportToPermanentStorage(
                        context = context,
                        stagingFile = file,
                        displayName = displayName,
                        mimeType = mimeType,
                        isAudio = entity.isAudioOnly,
                        customTreeUriString = customTreeUri
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
                        val errorMsg = "Failed to save file to permanent storage: ${exportError.message}"
                        val redactedLog = com.glypdl.android.service.engine.LogRedactor.redact(exportError.stackTraceToString())
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
            
            _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(entity.id) }
            activeJobs.remove(entity.id)
            processQueue()
        }
        
        activeJobs[entity.id] = job
    }

    suspend fun pauseDownload(id: String) {
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
        processQueue()
    }

    suspend fun deleteDownload(id: String, deleteFile: Boolean = true) {
        activeJobs[id]?.cancel()
        activeJobs.remove(id)
        _downloadProgress.value = _downloadProgress.value.toMutableMap().apply { remove(id) }
        downloadNotificationManager.onDownloadCancelled(id)

        val entity = downloadRepository.getDownloadByIdOnce(id)
        if (entity != null) {
            if (deleteFile && !entity.filePath.isNullOrBlank()) {
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
            downloadRepository.deleteDownload(id)
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
}
