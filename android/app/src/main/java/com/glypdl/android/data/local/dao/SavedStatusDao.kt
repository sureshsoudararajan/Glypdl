/*
 * Copyright (c) 2026. Glypdl Contributors
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
 */
package com.glypdl.android.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.glypdl.android.data.local.entity.SavedStatusEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SavedStatusDao {

    @Query("SELECT * FROM saved_statuses ORDER BY savedAt DESC")
    fun getAllSavedStatuses(): Flow<List<SavedStatusEntity>>

    @Query("SELECT * FROM saved_statuses WHERE originalUri = :originalUri LIMIT 1")
    suspend fun getByOriginalUri(originalUri: String): SavedStatusEntity?

    @Query("SELECT * FROM saved_statuses WHERE contentHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SavedStatusEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM saved_statuses WHERE originalUri = :originalUri)")
    suspend fun isOriginalUriSaved(originalUri: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(status: SavedStatusEntity): Long

    @Query("DELETE FROM saved_statuses WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM saved_statuses")
    suspend fun deleteAll()
}
