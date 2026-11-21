/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.domain.usecase

import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.service.DownloadManager
import javax.inject.Inject

class DownloadMediaUseCase @Inject constructor(
    private val downloadManager: DownloadManager
) {
    suspend operator fun invoke(request: DownloadRequest) {
        downloadManager.enqueueDownload(request)
    }

    /**
     * Hands a batch (e.g. playlist selection) to [DownloadManager], which enqueues it on its own
     * app-lifetime scope. Non-suspending: safe to call right before navigating away.
     */
    fun enqueueAll(requests: List<DownloadRequest>) {
        downloadManager.enqueueDownloads(requests)
    }
}
