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
}
