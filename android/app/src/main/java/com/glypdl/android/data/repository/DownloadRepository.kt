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

/**
 * Repository mediating access to download tasks stored in the Room database.
 *
 * Acts as the single source of truth for the download queue, decoupling business logic
 * and ViewModels from direct SQLite DAO operations.
 *
 * @property downloadDao Injected Room DAO for the `downloads` table.
 */
@Singleton
class DownloadRepository @Inject constructor(
    private val downloadDao: DownloadDao
) {
    /**
     * Observes all download tasks, newest first.
     */
    fun getAllDownloads(): Flow<List<DownloadEntity>> = downloadDao.getAllDownloads()

    /**
     * Observes downloads filtered by [DownloadStatus].
     */
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>> = downloadDao.getDownloadsByStatus(status)

    /**
     * Observes a specific download task by its unique UUID.
     */
    fun getDownloadById(id: String): Flow<DownloadEntity?> = downloadDao.getDownloadById(id)

    /**
     * Observes active and queued downloads in execution order (FIFO).
     */
    fun getActiveDownloads(): Flow<List<DownloadEntity>> = downloadDao.getActiveDownloads()

    /**
     * Inserts a new download task into the database.
     */
    suspend fun insertDownload(download: DownloadEntity) {
        downloadDao.insert(download)
    }

    /**
     * Updates an existing download record.
     */
    suspend fun updateDownload(download: DownloadEntity) {
        downloadDao.update(download)
    }

    /**
     * Atomically transitions the status of a download task and updates its modification timestamp.
     *
     * @param id The download task ID.
     * @param status The new [DownloadStatus] to apply.
     */
    suspend fun updateStatus(id: String, status: DownloadStatus) {
        val download = downloadDao.getDownloadByIdOnce(id)
        if (download != null) {
            downloadDao.update(download.copy(status = status, updatedAt = System.currentTimeMillis()))
        }
    }

    /**
     * Updates progress metrics for an ongoing download task.
     *
     * @param id The download task ID.
     * @param progress Percentage complete (0-100).
     * @param downloadedBytes Bytes transferred so far.
     * @param totalBytes Expected total bytes.
     * @param speed Formatted bandwidth rate string.
     */
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

    /**
     * Synchronously queries a download entity by ID without observing future changes.
     */
    suspend fun getDownloadByIdOnce(id: String): DownloadEntity? = downloadDao.getDownloadByIdOnce(id)

    /**
     * Deletes a download task by ID.
     */
    suspend fun deleteDownload(id: String) {
        downloadDao.deleteById(id)
    }

    /**
     * Gets the count of currently running downloads.
     */
    suspend fun getActiveDownloadCount(): Int = downloadDao.getActiveDownloadCount()

    /**
     * Clears all completed, failed, and cancelled downloads from the queue view.
     */
    suspend fun clearCompleted() {
        downloadDao.clearCompleted()
    }
}
