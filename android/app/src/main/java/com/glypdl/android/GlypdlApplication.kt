/*
 * Copyright (c) 2024 Glypdl
 *
 * This file is part of Glypdl.
 *
 * Glypdl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Glypdl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Glypdl.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.glypdl.android.service.YtDlpService
import com.glypdl.android.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class GlypdlApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var ytDlpService: YtDlpService

    @Inject
    lateinit var notificationHelper: NotificationHelper

    @Inject
    lateinit var downloadManager: com.glypdl.android.service.DownloadManager

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        
        notificationHelper.createNotificationChannels()
        
        applicationScope.launch {
            try {
                ytDlpService.init()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            try {
                downloadManager.recoverStaleJobs()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache"))
                    .maxSizePercent(0.02)
                    .maxSizeBytes(50L * 1024 * 1024) // 50MB
                    .build()
            }
            .crossfade(true)
            .build()
    }
}
