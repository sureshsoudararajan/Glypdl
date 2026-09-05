/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.repository

import com.glypdl.android.data.local.dao.DownloadDao
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>> = downloadDao.getDownloadsByStatus(status)

    fun getDownloadById(id: String): Flow<DownloadEntity?> = downloadDao.getDownloadById(id)

    fun getActiveDownloads(): Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    suspend fun insertDownload(download: DownloadEntity) {
        downloadDao.insert(download)
    }

    suspend fun updateDownload(download: DownloadEntity) {
        downloadDao.update(download)
    }

    suspend fun updateStatus(id: String, status: DownloadStatus) {
        val download = downloadDao.getDownloadByIdOnce(id)
        if (download != null) {
            downloadDao.update(download.copy(status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    suspend fun updateProgress(id: String, progress: Float, downloadedBytes: Long, totalBytes: Long, speed: String?) {
        val download = downloadDao.getDownloadByIdOnce(id)
        if (download != null) {
            downloadDao.update(
                download.copy(
                    progress = progress,
                    downloadedBytes = downloadedBytes,
                    totalBytes = totalBytes,
                    speed = speed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    suspend fun getDownloadByIdOnce(id: String): DownloadEntity? = downloadDao.getDownloadByIdOnce(id)

    suspend fun deleteDownload(id: String) {
        downloadDao.deleteById(id)
    }

    suspend fun getActiveDownloadCount(): Int = downloadDao.getActiveDownloadCount()

    suspend fun clearCompleted() {
        downloadDao.clearCompleted()
    }
}
