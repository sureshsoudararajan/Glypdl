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

import android.app.Notification
import com.glypdl.android.data.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
open class DownloadNotificationManager @Inject constructor(
    private val notificationHelper: NotificationHelper,
    private val settingsRepository: SettingsRepository
) {
    // Map of downloadId to notificationId
    private val activeNotifications = ConcurrentHashMap<String, Int>()

    fun getNotificationId(downloadId: String): Int {
        val hash = downloadId.hashCode() and 0x7FFFFFFF
        return if (hash == 0) 1001 else hash
    }

    fun getCompletionNotificationId(downloadId: String): Int {
        val hash = ("completed_" + downloadId).hashCode() and 0x7FFFFFFF
        return if (hash == 0) 2001 else hash
    }

    fun getFailureNotificationId(downloadId: String): Int {
        val hash = ("failed_" + downloadId).hashCode() and 0x7FFFFFFF
        return if (hash == 0) 3001 else hash
    }

    open fun getForegroundNotification(downloadId: String?, title: String?): Pair<Int, Notification> {
        val targetId = downloadId ?: "glypdl_foreground_service"
        val notifId = getNotificationId(targetId)
        val displayTitle = title?.takeIf { it.isNotBlank() } ?: "Glypdl Downloader"
        val notification = notificationHelper.buildProgressNotification(
            downloadId = targetId,
            title = displayTitle,
            progress = 0,
            speed = "",
            eta = "",
            isIndeterminate = true
        )
        activeNotifications[targetId] = notifId
        return Pair(notifId, notification)
    }

    open fun onDownloadStarted(downloadId: String, title: String): Int {
        val notifId = getNotificationId(downloadId)
        activeNotifications[downloadId] = notifId

        val notification = notificationHelper.buildProgressNotification(
            downloadId = downloadId,
            title = title,
            progress = 0,
            speed = "",
            eta = "",
            isIndeterminate = true
        )
        notificationHelper.notify(notifId, notification)
        return notifId
    }

    open fun onDownloadProgress(
        downloadId: String,
        title: String,
        percent: Float,
        speed: String,
        eta: String
    ): Int {
        val notifId = getNotificationId(downloadId)
        activeNotifications[downloadId] = notifId

        val notification = notificationHelper.buildProgressNotification(
            downloadId = downloadId,
            title = title,
            progress = percent.toInt().coerceIn(0, 100),
            speed = speed,
            eta = eta,
            isIndeterminate = percent <= 0f
        )
        notificationHelper.notify(notifId, notification)
        return notifId
    }

    open fun onDownloadCompleted(downloadId: String, title: String, filePath: String?): Int {
        val progressNotifId = getNotificationId(downloadId)
        activeNotifications.remove(downloadId)
        notificationHelper.cancelNotification(progressNotifId)

        val completionNotifId = getCompletionNotificationId(downloadId)

        val showNotification = try {
            runBlocking { settingsRepository.completionNotifications.first() }
        } catch (e: Exception) {
            true
        }

        if (showNotification) {
            notificationHelper.showCompletionNotification(
                id = completionNotifId,
                downloadId = downloadId,
                title = title,
                filePath = filePath
            )
        }
        return completionNotifId
    }

    open fun onDownloadFailed(downloadId: String, title: String, errorMessage: String): Int {
        val progressNotifId = getNotificationId(downloadId)
        activeNotifications.remove(downloadId)
        notificationHelper.cancelNotification(progressNotifId)

        val failureNotifId = getFailureNotificationId(downloadId)

        val showNotification = try {
            runBlocking { settingsRepository.errorNotifications.first() }
        } catch (e: Exception) {
            true
        }

        if (showNotification) {
            notificationHelper.showErrorNotification(
                id = failureNotifId,
                downloadId = downloadId,
                title = title,
                error = errorMessage
            )
        }
        return failureNotifId
    }

    open fun onDownloadCancelled(downloadId: String): Int {
        val notifId = getNotificationId(downloadId)
        activeNotifications.remove(downloadId)
        notificationHelper.cancelNotification(notifId)
        return notifId
    }

    open fun onDownloadPaused(downloadId: String): Int {
        val notifId = getNotificationId(downloadId)
        activeNotifications.remove(downloadId)
        notificationHelper.cancelNotification(notifId)
        return notifId
    }

    fun isNotificationActive(downloadId: String): Boolean {
        return activeNotifications.containsKey(downloadId)
    }

    fun getActiveNotificationCount(): Int {
        return activeNotifications.size
    }

    fun getActiveNotificationIds(): Set<Int> {
        return activeNotifications.values.toSet()
    }
}
