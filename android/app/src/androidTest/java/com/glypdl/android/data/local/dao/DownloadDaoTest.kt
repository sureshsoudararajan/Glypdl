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
import com.glypdl.android.data.model.DownloadEntity
import com.glypdl.android.data.model.DownloadStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DownloadDaoTest {

    private lateinit var db: GlypdlDatabase
    private lateinit var downloadDao: DownloadDao

    @Before
    fun createDb() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            GlypdlDatabase::class.java
        ).allowMainThreadQueries().build()
        downloadDao = db.downloadDao()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun insertAndRetrieveDownload() = runTest {
        val entity = DownloadEntity(
            url = "https://example.com",
            title = "Video",
            formatId = "1",
            ext = "mp4",
            status = DownloadStatus.QUEUED
        )
        
        val id = downloadDao.insert(entity)
        val loaded = downloadDao.getById(id)
        
        assertNotNull(loaded)
        assertEquals(entity.url, loaded?.url)
        assertEquals(entity.title, loaded?.title)
    }

    @Test
    fun updateDownloadStatus() = runTest {
        val entity = DownloadEntity(
            url = "https://example.com",
            title = "Video",
            formatId = "1",
            ext = "mp4",
            status = DownloadStatus.QUEUED
        )
        
        val id = downloadDao.insert(entity)
        downloadDao.updateStatus(id, DownloadStatus.DOWNLOADING)
        
        val loaded = downloadDao.getById(id)
        assertEquals(DownloadStatus.DOWNLOADING, loaded?.status)
    }

    @Test
    fun deleteDownloadById() = runTest {
        val entity = DownloadEntity(
            url = "https://example.com",
            title = "Video",
            formatId = "1",
            ext = "mp4",
            status = DownloadStatus.QUEUED
        )
        
        val id = downloadDao.insert(entity)
        downloadDao.deleteById(id)
        
        val loaded = downloadDao.getById(id)
        assertNull(loaded)
    }

    @Test
    fun getDownloadsByStatusFiltersCorrectly() = runTest {
        downloadDao.insert(DownloadEntity(url = "url1", title = "Video1", formatId = "1", ext = "mp4", status = DownloadStatus.QUEUED))
        downloadDao.insert(DownloadEntity(url = "url2", title = "Video2", formatId = "1", ext = "mp4", status = DownloadStatus.COMPLETED))
        
        val queued = downloadDao.getByStatus(DownloadStatus.QUEUED).first()
        assertEquals(1, queued.size)
        assertEquals("Video1", queued[0].title)
    }

    @Test
    fun getActiveDownloadsReturnsCorrectItems() = runTest {
        downloadDao.insert(DownloadEntity(url = "url1", title = "Video1", formatId = "1", ext = "mp4", status = DownloadStatus.QUEUED))
        downloadDao.insert(DownloadEntity(url = "url2", title = "Video2", formatId = "1", ext = "mp4", status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(DownloadEntity(url = "url3", title = "Video3", formatId = "1", ext = "mp4", status = DownloadStatus.COMPLETED))
        
        val active = downloadDao.getAllActive().first()
        assertEquals(2, active.size)
    }

    @Test
    fun getActiveDownloadCountIsAccurate() = runTest {
        downloadDao.insert(DownloadEntity(url = "url1", title = "Video1", formatId = "1", ext = "mp4", status = DownloadStatus.QUEUED))
        downloadDao.insert(DownloadEntity(url = "url2", title = "Video2", formatId = "1", ext = "mp4", status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(DownloadEntity(url = "url3", title = "Video3", formatId = "1", ext = "mp4", status = DownloadStatus.COMPLETED))
        
        val count = downloadDao.getActiveCount().first()
        assertEquals(2, count)
    }

    @Test
    fun clearCompletedRemovesOnlyCompletedFailedCancelled() = runTest {
        downloadDao.insert(DownloadEntity(url = "url1", title = "Video1", formatId = "1", ext = "mp4", status = DownloadStatus.QUEUED))
        downloadDao.insert(DownloadEntity(url = "url2", title = "Video2", formatId = "1", ext = "mp4", status = DownloadStatus.COMPLETED))
        downloadDao.insert(DownloadEntity(url = "url3", title = "Video3", formatId = "1", ext = "mp4", status = DownloadStatus.FAILED))
        downloadDao.insert(DownloadEntity(url = "url4", title = "Video4", formatId = "1", ext = "mp4", status = DownloadStatus.CANCELLED))
        
        downloadDao.clearCompleted()
        
        val remaining = downloadDao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals(DownloadStatus.QUEUED, remaining[0].status)
    }
}
