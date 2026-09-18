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

import com.glypdl.android.data.local.dao.HistoryDao
import com.glypdl.android.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository providing clean abstraction over completed download history records.
 *
 * Exposes reactive query flows and management methods consumed by HistoryViewModel.
 *
 * @property historyDao Injected Room DAO for the `history` table.
 */
@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    /**
     * Observes all history records, newest completion first.
     */
    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    /**
     * Searches history by video or audio title.
     *
     * @param query Search query substring.
     */
    fun searchHistory(query: String): Flow<List<HistoryEntity>> = historyDao.searchHistory(query)

    /**
     * Retrieves a history entry by primary key ID.
     */
    suspend fun getHistoryById(id: Long): HistoryEntity? = historyDao.getHistoryById(id)

    /**
     * Inserts a newly completed download into history.
     */
    suspend fun insertHistory(history: HistoryEntity) {
        historyDao.insert(history)
    }

    /**
     * Deletes a specific history record.
     */
    suspend fun deleteHistory(history: HistoryEntity) {
        historyDao.delete(history)
    }

    /**
     * Deletes a history record by primary key ID.
     */
    suspend fun deleteById(id: Long) {
        historyDao.deleteById(id)
    }

    /**
     * Clears all history entries.
     */
    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
