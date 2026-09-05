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

@Singleton
class HistoryRepository @Inject constructor(
    private val historyDao: HistoryDao
) {
    fun getAllHistory(): Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    fun searchHistory(query: String): Flow<List<HistoryEntity>> = historyDao.searchHistory(query)

    suspend fun getHistoryById(id: Long): HistoryEntity? = historyDao.getHistoryById(id)

    suspend fun insertHistory(history: HistoryEntity) {
        historyDao.insert(history)
    }

    suspend fun deleteHistory(history: HistoryEntity) {
        historyDao.delete(history)
    }

    suspend fun deleteById(id: Long) {
        historyDao.deleteById(id)
    }

    suspend fun clearAll() {
        historyDao.clearAll()
    }
}
