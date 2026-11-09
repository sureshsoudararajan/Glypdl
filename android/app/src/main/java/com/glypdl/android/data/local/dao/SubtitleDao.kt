/*
 * Copyright (C) 2026 The Glypdl Authors
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

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.glypdl.android.data.local.entity.SubtitleFileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Room Data Access Object for subtitle tracks saved in the `subtitle_files` table.
 */
@Dao
interface SubtitleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(subtitle: SubtitleFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(subtitles: List<SubtitleFileEntity>): List<Long>

    @Update
    suspend fun update(subtitle: SubtitleFileEntity)

    @Delete
    suspend fun delete(subtitle: SubtitleFileEntity)

    @Query("DELETE FROM subtitle_files WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM subtitle_files WHERE downloadId = :downloadId")
    suspend fun deleteByDownloadId(downloadId: String)

    @Query("SELECT * FROM subtitle_files ORDER BY createdAt DESC")
    fun getAllSubtitles(): Flow<List<SubtitleFileEntity>>

    @Query("SELECT * FROM subtitle_files WHERE id = :id")
    suspend fun getSubtitleById(id: Long): SubtitleFileEntity?

    @Query("SELECT * FROM subtitle_files WHERE downloadId = :downloadId ORDER BY languageName ASC")
    fun getSubtitlesForDownload(downloadId: String): Flow<List<SubtitleFileEntity>>

    @Query("SELECT * FROM subtitle_files WHERE downloadId = :downloadId ORDER BY languageName ASC")
    suspend fun getSubtitlesForDownloadSync(downloadId: String): List<SubtitleFileEntity>

    @Query("SELECT * FROM subtitle_files WHERE mediaTitle LIKE '%' || :query || '%' OR languageName LIKE '%' || :query || '%' OR languageCode LIKE '%' || :query || '%' ORDER BY createdAt DESC")
    fun searchSubtitles(query: String): Flow<List<SubtitleFileEntity>>

    @Query("SELECT COUNT(*) FROM subtitle_files")
    fun getSubtitleCount(): Flow<Int>
}
