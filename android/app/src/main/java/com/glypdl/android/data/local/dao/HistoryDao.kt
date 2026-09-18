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
import androidx.room.Query
import com.glypdl.android.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object (DAO) for the `history` table.
 * Maintains permanent history records of completed media downloads.
 */
@Dao
interface HistoryDao {

    /**
     * Inserts a completed download history record into the database.
     *
     * @param history The [HistoryEntity] record to insert.
     */
    @Insert
    suspend fun insert(history: HistoryEntity)

    /**
     * Deletes a specific history record from the database.
     *
     * @param history The [HistoryEntity] to remove.
     */
    @Delete
    suspend fun delete(history: HistoryEntity)

    /**
     * Observes all historical download entries, ordered chronologically
     * with the most recently completed downloads appearing first.
     *
     * @return A reactive [Flow] emitting the updated list of history records.
     */
    @Query("SELECT * FROM history ORDER BY completedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    /**
     * Searches history entries by matching the query against video/audio titles.
     *
     * @param query Search query substring to match.
     * @return A reactive [Flow] emitting matching history entries.
     */
    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' ORDER BY completedAt DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    /**
     * Retrieves a single history record by its auto-generated database primary key.
     *
     * @param id The auto-incremented primary key.
     * @return The [HistoryEntity] if found, or null otherwise.
     */
    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getHistoryById(id: Long): HistoryEntity?

    /**
     * Deletes all records from the history table.
     */
    @Query("DELETE FROM history")
    suspend fun clearAll()

    /**
     * Deletes a history entry by its auto-generated database primary key.
     *
     * @param id The primary key of the record to delete.
     */
    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
