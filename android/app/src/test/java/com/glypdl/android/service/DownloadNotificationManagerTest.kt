/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service

import android.app.Notification
import com.glypdl.android.data.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DownloadNotificationManagerTest {

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var notificationManager: DownloadNotificationManager
    private val mockNotification: Notification = mockk(relaxed = true)

    @Before
    fun setup() {
        notificationHelper = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)

        every { settingsRepository.completionNotifications } returns flowOf(true)
        every { settingsRepository.errorNotifications } returns flowOf(true)
        every { notificationHelper.buildProgressNotification(any(), any(), any(), any(), any(), any()) } returns mockNotification

        notificationManager = DownloadNotificationManager(
            notificationHelper = notificationHelper,
            settingsRepository = settingsRepository
        )
    }

    @Test
    fun `Test 1 - start download produces one notification`() {
        val downloadId = "test-download-1"
        val title = "Sample Video 1"

        val notifId = notificationManager.onDownloadStarted(downloadId, title)

        assertEquals(1, notificationManager.getActiveNotificationCount())
        assertTrue(notificationManager.isNotificationActive(downloadId))
        verify(exactly = 1) { notificationHelper.notify(notifId, any()) }
    }

    @Test
    fun `Test 2 - progress 10% uses the same notification ID`() {
        val downloadId = "test-download-2"
        val title = "Sample Video 2"

        val initialNotifId = notificationManager.onDownloadStarted(downloadId, title)
        val progressNotifId = notificationManager.onDownloadProgress(downloadId, title, 10f, "1.5 MB/s", "01:20")

        assertEquals(initialNotifId, progressNotifId)
        assertEquals(1, notificationManager.getActiveNotificationCount())
        verify(exactly = 2) { notificationHelper.notify(initialNotifId, any()) }
    }

    @Test
    fun `Test 3 - progress 50% uses the same notification ID`() {
        val downloadId = "test-download-3"
        val title = "Sample Video 3"

        val initialNotifId = notificationManager.onDownloadStarted(downloadId, title)
        val p10Id = notificationManager.onDownloadProgress(downloadId, title, 10f, "1.5 MB/s", "01:20")
        val p50Id = notificationManager.onDownloadProgress(downloadId, title, 50f, "2.0 MB/s", "00:40")

        assertEquals(initialNotifId, p10Id)
        assertEquals(initialNotifId, p50Id)
        assertEquals(1, notificationManager.getActiveNotificationCount())
        verify(exactly = 3) { notificationHelper.notify(initialNotifId, any()) }
    }

    @Test
    fun `Test 4 - complete removes ongoing notification`() {
        val downloadId = "test-download-4"
        val title = "Sample Video 4"

        val progressNotifId = notificationManager.onDownloadStarted(downloadId, title)
        notificationManager.onDownloadProgress(downloadId, title, 99f, "2.0 MB/s", "00:01")
        val completedNotifId = notificationManager.onDownloadCompleted(downloadId, title, "/path/to/video.mp4")

        assertEquals(0, notificationManager.getActiveNotificationCount())
        assertNotEquals(progressNotifId, completedNotifId)
        assertEquals(notificationManager.getCompletionNotificationId(downloadId), completedNotifId)
        verify(exactly = 1) { notificationHelper.cancelNotification(progressNotifId) }
        verify(exactly = 1) { notificationHelper.showCompletionNotification(completedNotifId, downloadId, title, "/path/to/video.mp4") }
    }

    @Test
    fun `Test 5 - fail removes ongoing notification and shows failure notification`() {
        val downloadId = "test-download-5"
        val title = "Sample Video 5"

        val progressNotifId = notificationManager.onDownloadStarted(downloadId, title)
        val failureNotifId = notificationManager.onDownloadFailed(downloadId, title, "Network error occurred")

        assertEquals(0, notificationManager.getActiveNotificationCount())
        assertNotEquals(progressNotifId, failureNotifId)
        assertEquals(notificationManager.getFailureNotificationId(downloadId), failureNotifId)
        verify(exactly = 1) { notificationHelper.cancelNotification(progressNotifId) }
        verify(exactly = 1) { notificationHelper.showErrorNotification(failureNotifId, downloadId, title, "Network error occurred") }
    }

    @Test
    fun `Test 6 - cancel removes ongoing notification`() {
        val downloadId = "test-download-6"
        val title = "Sample Video 6"

        val notifId = notificationManager.onDownloadStarted(downloadId, title)
        notificationManager.onDownloadCancelled(downloadId)

        assertEquals(0, notificationManager.getActiveNotificationCount())
        verify(exactly = 1) { notificationHelper.cancelNotification(notifId) }
    }

    @Test
    fun `Test 7 - two downloads produce two unique notification IDs`() {
        val downloadIdA = "download-A-unique"
        val downloadIdB = "download-B-unique"

        val notifIdA = notificationManager.onDownloadStarted(downloadIdA, "Video A")
        val notifIdB = notificationManager.onDownloadStarted(downloadIdB, "Video B")

        assertNotEquals(notifIdA, notifIdB)
        assertEquals(2, notificationManager.getActiveNotificationCount())
        assertEquals(setOf(notifIdA, notifIdB), notificationManager.getActiveNotificationIds())
    }

    @Test
    fun `Test 8 - same download receives 100 progress updates and still only one notification active`() {
        val downloadId = "test-download-8"
        val title = "Stress Progress Video"

        val expectedNotifId = notificationManager.onDownloadStarted(downloadId, title)

        for (percent in 1..100) {
            val currentNotifId = notificationManager.onDownloadProgress(
                downloadId = downloadId,
                title = title,
                percent = percent.toFloat(),
                speed = "${percent * 50} KB/s",
                eta = "${100 - percent}s"
            )
            assertEquals(expectedNotifId, currentNotifId)
        }

        // 1 from started + 100 from progress updates = 101 notify calls with the same ID
        assertEquals(1, notificationManager.getActiveNotificationCount())
        verify(exactly = 101) { notificationHelper.notify(expectedNotifId, any()) }
    }
}
