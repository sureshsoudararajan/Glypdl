/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {

    @Inject
    lateinit var downloadManager: DownloadManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val downloadId = intent.getStringExtra(EXTRA_DOWNLOAD_ID) ?: return

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    ACTION_RETRY -> {
                        downloadManager.retryDownload(downloadId)
                    }
                    ACTION_PAUSE -> {
                        downloadManager.pauseDownload(downloadId)
                    }
                    ACTION_CANCEL -> {
                        downloadManager.cancelDownload(downloadId)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_RETRY = "com.glypdl.android.action.RECEIVER_RETRY"
        const val ACTION_PAUSE = "com.glypdl.android.action.RECEIVER_PAUSE"
        const val ACTION_CANCEL = "com.glypdl.android.action.RECEIVER_CANCEL"
        const val EXTRA_DOWNLOAD_ID = "download_id"
    }
}
