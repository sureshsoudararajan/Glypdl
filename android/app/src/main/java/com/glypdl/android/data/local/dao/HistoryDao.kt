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

@Dao
interface HistoryDao {
    @Insert
    suspend fun insert(history: HistoryEntity)

    @Delete
    suspend fun delete(history: HistoryEntity)

    @Query("SELECT * FROM history ORDER BY completedAt DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' ORDER BY completedAt DESC")
    fun searchHistory(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE id = :id")
    suspend fun getHistoryById(id: Long): HistoryEntity?

    @Query("DELETE FROM history")
    suspend fun clearAll()

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
