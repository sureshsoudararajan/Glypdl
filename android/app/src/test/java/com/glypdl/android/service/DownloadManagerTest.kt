/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service

import android.content.Context
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.DownloadStatus
import com.glypdl.android.data.repository.DownloadRepository
import com.glypdl.android.data.repository.HistoryRepository
import com.glypdl.android.data.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class DownloadManagerTest {

    private lateinit var downloadRepository: DownloadRepository
    private lateinit var historyRepository: HistoryRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var ytDlpService: YtDlpService
    private lateinit var context: Context
    private lateinit var downloadManager: DownloadManager

    @Before
    fun setup() {
        downloadRepository = mockk(relaxed = true)
        historyRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        ytDlpService = mockk(relaxed = true)
        context = mockk(relaxed = true)

        every { settingsRepository.concurrentDownloads } returns flowOf(2)
        every { settingsRepository.downloadDirUri } returns flowOf(null)
        every { downloadRepository.getActiveDownloads() } returns flowOf(emptyList())
        every { downloadRepository.getDownloadsByStatus(any()) } returns flowOf(emptyList())

        val downloadNotificationManager: DownloadNotificationManager = mockk(relaxed = true)
        val aria2DownloadEngine: com.glypdl.android.service.engine.Aria2DownloadEngine = mockk(relaxed = true)

        downloadManager = DownloadManager(
            ytDlpService = ytDlpService,
            aria2DownloadEngine = aria2DownloadEngine,
            downloadRepository = downloadRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            downloadNotificationManager = downloadNotificationManager,
            context = context
        )
    }

    @Test
    fun `enqueueDownload inserts entity with QUEUED status`() = runTest {
        val request = DownloadRequest(
            id = "test-id-1",
            url = "https://example.com/video",
            title = "Test Video",
            thumbnailUrl = null,
            formatId = "137",
            ext = "mp4",
            resolution = "1080p",
            isAudioOnly = false,
            destinationUri = null
        )

        downloadManager.enqueueDownload(request)

        coVerify {
            downloadRepository.insertDownload(match {
                it.id == "test-id-1" && it.status == DownloadStatus.QUEUED && it.title == "Test Video"
            })
        }
    }

    @Test
    fun `pauseDownload updates status to PAUSED`() = runTest {
        val downloadId = "test-id-2"

        downloadManager.pauseDownload(downloadId)

        coVerify { downloadRepository.updateStatus(downloadId, DownloadStatus.PAUSED) }
    }

    @Test
    fun `resumeDownload updates status to QUEUED`() = runTest {
        val downloadId = "test-id-3"

        downloadManager.resumeDownload(downloadId)

        coVerify { downloadRepository.updateStatus(downloadId, DownloadStatus.QUEUED) }
    }

    @Test
    fun `cancelDownload updates status to CANCELLED`() = runTest {
        val downloadId = "test-id-4"
        val entity = DownloadEntity(
            id = downloadId,
            url = "https://example.com/video",
            title = "Test",
            thumbnailUrl = null,
            formatId = "137",
            ext = "mp4",
            resolution = "1080p",
            isAudioOnly = false,
            status = DownloadStatus.DOWNLOADING,
            progress = 50f,
            downloadedBytes = 500,
            totalBytes = 1000,
            speed = "1 MB/s",
            filePath = null,
            errorMessage = null,
            createdAt = 0L,
            updatedAt = 0L
        )

        coEvery { downloadRepository.getDownloadByIdOnce(downloadId) } returns entity

        downloadManager.cancelDownload(downloadId)

        coVerify { downloadRepository.updateStatus(downloadId, DownloadStatus.CANCELLED) }
    }

    @Test
    fun `retryDownload updates entity status to QUEUED`() = runTest {
        val downloadId = "test-id-5"
        val entity = DownloadEntity(
            id = downloadId,
            url = "https://example.com/video",
            title = "Test",
            thumbnailUrl = null,
            formatId = "137",
            ext = "mp4",
            resolution = "1080p",
            isAudioOnly = false,
            status = DownloadStatus.FAILED,
            progress = 0f,
            downloadedBytes = 0,
            totalBytes = 0,
            speed = null,
            filePath = null,
            errorMessage = "Error",
            createdAt = 0L,
            updatedAt = 0L
        )

        coEvery { downloadRepository.getDownloadByIdOnce(downloadId) } returns entity

        downloadManager.retryDownload(downloadId)

        coVerify {
            downloadRepository.updateDownload(match {
                it.id == downloadId && it.status == DownloadStatus.QUEUED && it.errorMessage == null
            })
        }
    }

    @Test
    fun `aria2 download failure updates status to FAILED and does not crash`() = runTest {
        val downloadId = "test-aria2-fail"
        val aria2Engine: com.glypdl.android.service.engine.Aria2DownloadEngine = mockk(relaxed = true)
        coEvery { aria2Engine.startDownload(any(), any(), any()) } throws java.io.IOException("Cannot run program aria2c: error=2")

        val notifManager: DownloadNotificationManager = mockk(relaxed = true)
        val testDownloadManager = DownloadManager(
            ytDlpService = ytDlpService,
            aria2DownloadEngine = aria2Engine,
            downloadRepository = downloadRepository,
            historyRepository = historyRepository,
            settingsRepository = settingsRepository,
            downloadNotificationManager = notifManager,
            context = context
        )

        coEvery { downloadRepository.getDownloadsByStatus(DownloadStatus.QUEUED) } returns flowOf(
            listOf(
                DownloadEntity(
                    id = downloadId,
                    url = "https://github.com/test/archive.zip",
                    title = "archive.zip",
                    thumbnailUrl = null,
                    formatId = "direct",
                    ext = "zip",
                    resolution = null,
                    isAudioOnly = false,
                    status = DownloadStatus.QUEUED,
                    progress = 0f,
                    downloadedBytes = 0,
                    totalBytes = 0,
                    speed = null,
                    filePath = null,
                    errorMessage = null,
                    createdAt = 0L,
                    updatedAt = 0L,
                    downloadType = com.glypdl.android.data.model.DownloadType.DIRECT_FILE,
                    engineType = com.glypdl.android.data.model.EngineType.ARIA2C,
                    fileName = "archive.zip"
                )
            )
        )

        testDownloadManager.processQueue()
        kotlinx.coroutines.delay(200)

        coVerify {
            downloadRepository.updateDownload(match {
                it.id == downloadId && it.status == DownloadStatus.FAILED && it.errorMessage?.contains("Cannot run program") == true
            })
            notifManager.onDownloadFailed(downloadId, "archive.zip", any())
        }
    }
}
