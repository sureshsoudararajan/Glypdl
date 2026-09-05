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

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glypdl.android.data.local.GlypdlDatabase
import com.glypdl.android.data.model.HistoryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HistoryDaoTest {

    private lateinit var db: GlypdlDatabase
    private lateinit var historyDao: HistoryDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GlypdlDatabase::class.java
        ).allowMainThreadQueries().build()
        historyDao = db.historyDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveHistoryItem() = runTest {
        val entity = HistoryEntity(
            url = "https://example.com",
            title = "Test Video",
            filePath = "/path/to/file.mp4",
            fileSize = 1024L,
            completedAt = 1000L
        )
        
        historyDao.insert(entity)
        val loaded = historyDao.getAll().first()
        
        assertEquals(1, loaded.size)
        assertEquals(entity.title, loaded[0].title)
    }

    @Test
    fun searchByTitleMatchesCorrectly() = runTest {
        historyDao.insert(HistoryEntity(url = "url1", title = "First Video", filePath = "path1", fileSize = 1024L, completedAt = 1000L))
        historyDao.insert(HistoryEntity(url = "url2", title = "Second Clip", filePath = "path2", fileSize = 1024L, completedAt = 2000L))
        
        val results = historyDao.search("Video").first()
        
        assertEquals(1, results.size)
        assertEquals("First Video", results[0].title)
    }

    @Test
    fun searchByTitleReturnsEmptyForNoMatch() = runTest {
        historyDao.insert(HistoryEntity(url = "url1", title = "First Video", filePath = "path1", fileSize = 1024L, completedAt = 1000L))
        
        val results = historyDao.search("Nonexistent").first()
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun deleteByIdRemovesItem() = runTest {
        val entity = HistoryEntity(
            url = "https://example.com",
            title = "Test Video",
            filePath = "/path/to/file.mp4",
            fileSize = 1024L,
            completedAt = 1000L
        )
        
        val id = historyDao.insert(entity)
        historyDao.deleteById(id)
        
        val loaded = historyDao.getAll().first()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun clearAllRemovesEverything() = runTest {
        historyDao.insert(HistoryEntity(url = "url1", title = "First Video", filePath = "path1", fileSize = 1024L, completedAt = 1000L))
        historyDao.insert(HistoryEntity(url = "url2", title = "Second Clip", filePath = "path2", fileSize = 1024L, completedAt = 2000L))
        
        historyDao.clearAll()
        
        val loaded = historyDao.getAll().first()
        assertTrue(loaded.isEmpty())
    }

    @Test
    fun itemsAreOrderedByCompletedAtDesc() = runTest {
        historyDao.insert(HistoryEntity(url = "url1", title = "Old", filePath = "path1", fileSize = 1024L, completedAt = 1000L))
        historyDao.insert(HistoryEntity(url = "url2", title = "New", filePath = "path2", fileSize = 1024L, completedAt = 2000L))
        
        val loaded = historyDao.getAll().first()
        
        assertEquals(2, loaded.size)
        assertEquals("New", loaded[0].title)
        assertEquals("Old", loaded[1].title)
    }
}
