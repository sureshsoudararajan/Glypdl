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
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.glypdl.android.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val CHANNEL_DOWNLOADS = "glypdl_downloads"
        const val CHANNEL_COMPLETED = "glypdl_completed"
    }

    private val notificationManager = NotificationManagerCompat.from(context)

    fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val downloadsChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                "Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Active downloads progress"
            }

            val completedChannel = NotificationChannel(
                CHANNEL_COMPLETED,
                "Completed Downloads",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Completed and failed downloads"
            }

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannels(listOf(downloadsChannel, completedChannel))
        }
    }

    fun buildProgressNotification(
        downloadId: String,
        title: String,
        progress: Int,
        speed: String,
        eta: String = "",
        isIndeterminate: Boolean = false
    ): Notification {
        val pauseIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_PAUSE
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val pausePendingIntent = PendingIntent.getBroadcast(
            context,
            (downloadId + "_pause").hashCode(),
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val cancelIntent = Intent(context, DownloadActionReceiver::class.java).apply {
            action = DownloadActionReceiver.ACTION_CANCEL
            putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            (downloadId + "_cancel").hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val contentText = buildString {
            if (speed.isNotBlank()) append("$speed ")
            if (eta.isNotBlank()) append("• ETA: $eta")
            if (isEmpty()) append("Downloading...")
        }

        val displayTitle = title.ifBlank { "Glypdl - Downloading" }

        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setContentTitle(displayTitle)
            .setContentText(contentText)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(displayTitle)
                    .bigText(contentText)
            )
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, progress, isIndeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pausePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelPendingIntent)

        if (downloadId.isNotBlank()) {
            val contentIntent = PendingIntent.getActivity(
                context,
                (downloadId + "_content").hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO_DOWNLOAD_ID, downloadId)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(contentIntent)
        }

        return builder.build()
    }

    fun buildProgressNotification(title: String, progress: Int, speed: String): Notification {
        return buildProgressNotification("", title, progress, speed)
    }

    fun showCompletionNotification(id: Int, downloadId: String, title: String, filePath: String?) {
        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle("Download Completed")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)

        if (downloadId.isNotBlank()) {
            val detailIntent = PendingIntent.getActivity(
                context,
                (downloadId + "_completed").hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO_DOWNLOAD_ID, downloadId)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(detailIntent)
        }

        if (!filePath.isNullOrBlank()) {
            val openIntent = com.glypdl.android.util.StorageHelper.createOpenIntent(context, filePath, isAudio = false)
            if (openIntent != null) {
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    id,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                builder.addAction(android.R.drawable.ic_menu_view, "Open", openPendingIntent)
            }
        }

        try {
            notificationManager.notify(id, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showCompletionNotification(id: Int, title: String, filePath: String?) {
        showCompletionNotification(id, "", title, filePath)
    }

    fun showErrorNotification(id: Int, downloadId: String, title: String, error: String) {
        val builder = NotificationCompat.Builder(context, CHANNEL_COMPLETED)
            .setContentTitle("Download Failed: $title")
            .setContentText(error)
            .setStyle(NotificationCompat.BigTextStyle().bigText(error))
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setAutoCancel(true)

        if (downloadId.isNotBlank()) {
            val detailIntent = PendingIntent.getActivity(
                context,
                (downloadId + "_failed_detail").hashCode(),
                Intent(context, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra(MainActivity.EXTRA_NAVIGATE_TO_DOWNLOAD_ID, downloadId)
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(detailIntent)

            val retryIntent = Intent(context, DownloadActionReceiver::class.java).apply {
                action = DownloadActionReceiver.ACTION_RETRY
                putExtra(DownloadActionReceiver.EXTRA_DOWNLOAD_ID, downloadId)
            }
            val retryPendingIntent = PendingIntent.getBroadcast(
                context,
                (downloadId + "_retry").hashCode(),
                retryIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(android.R.drawable.ic_menu_rotate, "Retry", retryPendingIntent)
        }

        try {
            notificationManager.notify(id, builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun showErrorNotification(id: Int, title: String, error: String) {
        showErrorNotification(id, "", title, error)
    }

    fun notify(id: Int, notification: Notification) {
        try {
            notificationManager.notify(id, notification)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}
