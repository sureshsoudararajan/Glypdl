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

package com.glypdl.android.data.repository

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import com.glypdl.android.data.local.dao.BookmarkDao
import com.glypdl.android.data.local.dao.BrowserHistoryDao
import com.glypdl.android.data.local.entity.BookmarkEntity
import com.glypdl.android.data.local.entity.BrowserHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BrowserRepository @Inject constructor(
    private val bookmarkDao: BookmarkDao,
    private val browserHistoryDao: BrowserHistoryDao
) {

    fun getAllBookmarks(): Flow<List<BookmarkEntity>> {
        return bookmarkDao.getAllBookmarks()
    }

    suspend fun addBookmark(url: String, title: String) = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext
        bookmarkDao.insert(
            BookmarkEntity(
                url = url,
                title = title.ifBlank { url }
            )
        )
    }

    suspend fun removeBookmark(url: String) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteByUrl(url)
    }

    suspend fun removeBookmarkById(id: Long) = withContext(Dispatchers.IO) {
        bookmarkDao.deleteById(id)
    }

    suspend fun isBookmarked(url: String): Boolean = withContext(Dispatchers.IO) {
        if (url.isBlank()) false else bookmarkDao.isBookmarked(url)
    }

    fun getAllHistory(): Flow<List<BrowserHistoryEntity>> {
        return browserHistoryDao.getAllHistory()
    }

    fun searchHistory(query: String): Flow<List<BrowserHistoryEntity>> {
        return browserHistoryDao.searchHistory(query)
    }

    suspend fun recordVisit(url: String, title: String) = withContext(Dispatchers.IO) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("chrome:")) return@withContext
        browserHistoryDao.insert(
            BrowserHistoryEntity(
                url = url,
                title = title.ifBlank { url },
                visitedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteHistoryById(id: Long) = withContext(Dispatchers.IO) {
        browserHistoryDao.deleteById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        browserHistoryDao.clearAll()
    }

    suspend fun clearAllBrowsingData(context: Context) = withContext(Dispatchers.Main) {
        // Clear history from DB
        withContext(Dispatchers.IO) {
            browserHistoryDao.clearAll()
        }

        // Clear web cache, storage, and cookies
        try {
            WebView(context).apply {
                clearCache(true)
                clearHistory()
                clearFormData()
            }
            WebStorage.getInstance().deleteAllData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {}
    }
}
