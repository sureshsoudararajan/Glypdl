/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) providing CRUD and reactive query interfaces
 * for the `downloads` table in SQLite.
 *
 * All state modifications and queries use Kotlin Coroutines (`suspend` or [Flow]).
 */
@Dao
interface DownloadDao {

    /**
     * Inserts a download record. If a record with the same primary key already exists,
     * it will be replaced ([OnConflictStrategy.REPLACE]).
     *
     * @param download The [DownloadEntity] to insert.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(download: DownloadEntity)

    /**
     * Updates an existing download record matching the primary key.
     *
     * @param download The updated [DownloadEntity].
     */
    @Update
    suspend fun update(download: DownloadEntity)

    /**
     * Atomically updates status and modification timestamp without a read-modify-write cycle.
     */
    @Query("UPDATE downloads SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: DownloadStatus, updatedAt: Long = System.currentTimeMillis())

    /**
     * Atomically updates progress metrics and modification timestamp.
     */
    @Query("UPDATE downloads SET progress = :progress, downloadedBytes = :downloadedBytes, totalBytes = :totalBytes, speed = :speed, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateProgress(id: String, progress: Float, downloadedBytes: Long, totalBytes: Long, speed: String?, updatedAt: Long = System.currentTimeMillis())

    /**
     * Atomically updates final destination file path.
     */
    @Query("UPDATE downloads SET filePath = :filePath, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateFilePath(id: String, filePath: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * Atomically records failure error message and technical log.
     */
    @Query("UPDATE downloads SET errorMessage = :errorMessage, technicalLog = :technicalLog, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateError(id: String, errorMessage: String?, technicalLog: String?, status: DownloadStatus = DownloadStatus.FAILED, updatedAt: Long = System.currentTimeMillis())

    /**
     * Deletes a specific download entity from the database.
     *
     * @param download The [DownloadEntity] to remove.
     */
    @Delete
    suspend fun delete(download: DownloadEntity)

    /**
     * Observes all download tasks in the database, ordered from newest to oldest by creation time.
     *
     * @return A reactive [Flow] emitting the updated list whenever the table changes.
     */
    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAllDownloads(): Flow<List<DownloadEntity>>

    /**
     * Observes download tasks filtered by a specific [DownloadStatus] (e.g., QUEUED, DOWNLOADING, FAILED).
     *
     * @param status The target status filter.
     * @return A reactive [Flow] emitting downloads matching the given status.
     */
    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getDownloadsByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    /**
     * Observes a single download task by its unique UUID.
     *
     * @param id The download task identifier.
     * @return A reactive [Flow] emitting the download entity, or null if deleted.
     */
    @Query("SELECT * FROM downloads WHERE id = :id")
    fun getDownloadById(id: String): Flow<DownloadEntity?>

    /**
     * Retrieves a single download task snapshot synchronously without continuous observation.
     * Useful for one-off checks and atomic status updates in background services.
     *
     * @param id The download task identifier.
     * @return The current [DownloadEntity] or null if not found.
     */
    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getDownloadByIdOnce(id: String): DownloadEntity?

    /**
     * Observes all unfinished download tasks that are currently active or queued for execution.
     * Used by the download queue manager and background foreground service.
     *
     * @return A reactive [Flow] emitting active/pending downloads in FIFO order (createdAt ASC).
     */
    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'PREPARING', 'DOWNLOADING', 'PAUSED', 'PROCESSING') ORDER BY createdAt ASC")
    fun getActiveDownloads(): Flow<List<DownloadEntity>>

    /**
     * Counts how many downloads are currently in the active DOWNLOADING state.
     * Used to enforce concurrency limits before dispatching new tasks from the queue.
     *
     * @return The number of concurrently downloading items.
     */
    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    suspend fun getActiveDownloadCount(): Int

    /**
     * Deletes a download task by its unique ID.
     *
     * @param id The identifier of the download to remove.
     */
    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Clears all finished downloads (COMPLETED, FAILED, or CANCELLED) from the queue view.
     * Does not delete the exported media files from user storage.
     */
    @Query("DELETE FROM downloads WHERE status IN ('COMPLETED', 'FAILED', 'CANCELLED')")
    suspend fun clearCompleted()

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun getById(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status = :status ORDER BY createdAt DESC")
    fun getByStatus(status: DownloadStatus): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE status IN ('QUEUED', 'PREPARING', 'DOWNLOADING', 'PAUSED', 'PROCESSING') ORDER BY createdAt ASC")
    fun getAllActive(): Flow<List<DownloadEntity>>

    @Query("SELECT COUNT(*) FROM downloads WHERE status = 'DOWNLOADING'")
    fun getActiveCount(): Flow<Int>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun getAll(): Flow<List<DownloadEntity>>
}
