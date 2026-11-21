/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.glypdl.android.data.local.dao

import androidx.room.*
import com.glypdl.android.data.local.entity.BrowserHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserHistoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: BrowserHistoryEntity): Long

    @Delete
    suspend fun delete(history: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC")
    fun getAllHistory(): Flow<List<BrowserHistoryEntity>>

    @Query("SELECT * FROM browser_history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC")
    fun searchHistory(query: String): Flow<List<BrowserHistoryEntity>>

    @Query("DELETE FROM browser_history")
    suspend fun clearAll()
}
