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
import com.glypdl.android.data.local.entity.DownloadEntity
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

    private fun createTestDownload(
        id: String = "test-1",
        url: String = "https://example.com",
        title: String = "Video",
        formatId: String = "1",
        ext: String = "mp4",
        status: DownloadStatus = DownloadStatus.QUEUED
    ) = DownloadEntity(
        id = id,
        url = url,
        title = title,
        thumbnailUrl = null,
        formatId = formatId,
        ext = ext,
        resolution = "1080p",
        isAudioOnly = false,
        status = status,
        progress = 0f,
        downloadedBytes = 0L,
        totalBytes = 1000L,
        speed = null,
        filePath = null,
        errorMessage = null,
        technicalLog = null,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

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
        val entity = createTestDownload(id = "dl-1", url = "https://example.com", title = "Video")
        
        downloadDao.insert(entity)
        val loaded = downloadDao.getById("dl-1")
        
        assertNotNull(loaded)
        assertEquals(entity.url, loaded?.url)
        assertEquals(entity.title, loaded?.title)
    }

    @Test
    fun updateDownloadStatus() = runTest {
        val entity = createTestDownload(id = "dl-2")
        
        downloadDao.insert(entity)
        downloadDao.updateStatus("dl-2", DownloadStatus.DOWNLOADING)
        
        val loaded = downloadDao.getById("dl-2")
        assertEquals(DownloadStatus.DOWNLOADING, loaded?.status)
    }

    @Test
    fun deleteDownloadById() = runTest {
        val entity = createTestDownload(id = "dl-3")
        
        downloadDao.insert(entity)
        downloadDao.deleteById("dl-3")
        
        val loaded = downloadDao.getById("dl-3")
        assertNull(loaded)
    }

    @Test
    fun getDownloadsByStatusFiltersCorrectly() = runTest {
        downloadDao.insert(createTestDownload(id = "q-1", title = "Video1", status = DownloadStatus.QUEUED))
        downloadDao.insert(createTestDownload(id = "c-1", title = "Video2", status = DownloadStatus.COMPLETED))
        
        val queued = downloadDao.getByStatus(DownloadStatus.QUEUED).first()
        assertEquals(1, queued.size)
        assertEquals("Video1", queued[0].title)
    }

    @Test
    fun getActiveDownloadsReturnsCorrectItems() = runTest {
        downloadDao.insert(createTestDownload(id = "1", title = "Video1", status = DownloadStatus.QUEUED))
        downloadDao.insert(createTestDownload(id = "2", title = "Video2", status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(createTestDownload(id = "3", title = "Video3", status = DownloadStatus.COMPLETED))
        
        val active = downloadDao.getAllActive().first()
        assertEquals(2, active.size)
    }

    @Test
    fun getActiveDownloadCountIsAccurate() = runTest {
        downloadDao.insert(createTestDownload(id = "1", title = "Video1", status = DownloadStatus.QUEUED))
        downloadDao.insert(createTestDownload(id = "2", title = "Video2", status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(createTestDownload(id = "3", title = "Video3", status = DownloadStatus.DOWNLOADING))
        downloadDao.insert(createTestDownload(id = "4", title = "Video4", status = DownloadStatus.COMPLETED))
        
        val count = downloadDao.getActiveDownloadCount()
        assertEquals(2, count)
    }

    @Test
    fun clearCompletedRemovesOnlyCompletedFailedCancelled() = runTest {
        downloadDao.insert(createTestDownload(id = "1", status = DownloadStatus.QUEUED))
        downloadDao.insert(createTestDownload(id = "2", status = DownloadStatus.COMPLETED))
        downloadDao.insert(createTestDownload(id = "3", status = DownloadStatus.FAILED))
        downloadDao.insert(createTestDownload(id = "4", status = DownloadStatus.CANCELLED))
        
        downloadDao.clearCompleted()
        
        val remaining = downloadDao.getAll().first()
        assertEquals(1, remaining.size)
        assertEquals(DownloadStatus.QUEUED, remaining[0].status)
    }
}
}
