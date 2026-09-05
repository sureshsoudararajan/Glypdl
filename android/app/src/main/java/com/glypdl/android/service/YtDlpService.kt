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

import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.MediaInfo
import com.glypdl.android.service.engine.EngineStatus
import com.glypdl.android.service.engine.YtDlpManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpService @Inject constructor(
    val ytDlpManager: YtDlpManager
) {

    suspend fun init(): Result<Unit> = ytDlpManager.init()

    suspend fun updateYtDlp(): Result<String> = ytDlpManager.updateEngine()

    fun getInstalledVersion(): String = ytDlpManager.getInstalledVersion()

    suspend fun checkEngineStatus(forceOnlineCheck: Boolean = false): EngineStatus =
        ytDlpManager.checkEngineStatus(forceOnlineCheck)

    fun isPlaylist(url: String): Boolean = ytDlpManager.isPlaylist(url)

    fun cancelProcess(processId: String): Boolean = ytDlpManager.cancelProcess(processId)

    suspend fun extractInfo(url: String): Result<MediaInfo> = ytDlpManager.extractInfo(url)

    suspend fun extractPlaylistInfo(
        url: String,
        processId: String? = null,
        timeoutMs: Long = 120_000L
    ): Result<com.glypdl.android.data.model.PlaylistInfo> =
        ytDlpManager.extractPlaylistInfo(url, processId, timeoutMs)

    suspend fun download(
        request: DownloadRequest,
        downloadDir: String,
        progressCallback: (Float, Long, Long, String, String) -> Unit
    ): Result<String> = ytDlpManager.executeDownload(request, downloadDir, progressCallback)
}
