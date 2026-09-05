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

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.glypdl.android.data.repository.DownloadRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadForegroundService : Service() {

    @Inject
    lateinit var downloadManager: DownloadManager

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var downloadNotificationManager: DownloadNotificationManager

    @Inject
    lateinit var downloadRepository: DownloadRepository

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var currentForegroundNotifId: Int? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper.createNotificationChannels()
        val (initId, initNotif) = downloadNotificationManager.getForegroundNotification(null, null)
        currentForegroundNotifId = initId
        startForeground(initId, initNotif)

        serviceScope.launch {
            downloadManager.downloadProgress.collectLatest { progressMap ->
                if (progressMap.isNotEmpty()) {
                    // If the current foreground notification was generic, bind foreground to the first active download
                    val firstActive = progressMap.values.firstOrNull()
                    if (firstActive != null) {
                        val activeNotifId = downloadNotificationManager.getNotificationId(firstActive.downloadId)
                        if (currentForegroundNotifId != activeNotifId) {
                            currentForegroundNotifId = activeNotifId
                            val notif = notificationHelper.buildProgressNotification(
                                downloadId = firstActive.downloadId,
                                title = firstActive.title.ifBlank { "Glypdl - Downloading" },
                                progress = firstActive.percent.toInt().coerceIn(0, 100),
                                speed = firstActive.speed,
                                eta = firstActive.eta,
                                isIndeterminate = firstActive.percent <= 0f
                            )
                            startForeground(activeNotifId, notif)
                        }
                    }
                } else {
                    delay(1500L)
                    if (!downloadManager.hasActiveOrQueuedDownloads()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val downloadId = intent?.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return START_NOT_STICKY
        
        when (intent.action) {
            ACTION_START -> {
                serviceScope.launch {
                    val entity = downloadRepository.getDownloadByIdOnce(downloadId)
                    if (entity != null) {
                        val notifId = downloadNotificationManager.getNotificationId(entity.id)
                        currentForegroundNotifId = notifId
                        val (id, notif) = downloadNotificationManager.getForegroundNotification(entity.id, entity.title)
                        startForeground(id, notif)
                    }
                }
            }
            ACTION_PAUSE -> serviceScope.launch { downloadManager.pauseDownload(downloadId) }
            ACTION_RESUME -> serviceScope.launch { downloadManager.resumeDownload(downloadId) }
            ACTION_CANCEL -> serviceScope.launch { downloadManager.cancelDownload(downloadId) }
            ACTION_RETRY -> serviceScope.launch { downloadManager.retryDownload(downloadId) }
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        currentForegroundNotifId?.let { id ->
            notificationHelper.cancelNotification(id)
        }
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
        
        const val ACTION_START = "com.glypdl.android.action.START_DOWNLOAD"
        const val ACTION_PAUSE = "com.glypdl.android.action.PAUSE_DOWNLOAD"
        const val ACTION_RESUME = "com.glypdl.android.action.RESUME_DOWNLOAD"
        const val ACTION_CANCEL = "com.glypdl.android.action.CANCEL_DOWNLOAD"
        const val ACTION_RETRY = "com.glypdl.android.action.RETRY_DOWNLOAD"
        
        const val EXTRA_DOWNLOAD_ID = "download_id"

        fun start(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startForegroundService(intent)
        }

        fun pause(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_PAUSE
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun resume(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun cancel(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }

        fun retry(context: Context, downloadId: String) {
            val intent = Intent(context, DownloadForegroundService::class.java).apply {
                action = ACTION_RETRY
                putExtra(EXTRA_DOWNLOAD_ID, downloadId)
            }
            context.startService(intent)
        }
    }
}
