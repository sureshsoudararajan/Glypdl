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
}
