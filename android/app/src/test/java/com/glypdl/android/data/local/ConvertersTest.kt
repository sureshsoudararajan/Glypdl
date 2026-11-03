/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.local

import com.glypdl.android.data.model.DownloadStatus
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class ConvertersTest {

    private lateinit var converters: Converters

    @Before
    fun setup() {
        converters = Converters()
    }

    @Test
    fun testFromDownloadStatusValid() {
        assertEquals("DOWNLOADING", converters.fromDownloadStatus(DownloadStatus.DOWNLOADING))
        assertEquals("COMPLETED", converters.fromDownloadStatus(DownloadStatus.COMPLETED))
        assertEquals("QUEUED", converters.fromDownloadStatus(DownloadStatus.QUEUED))
    }

    @Test
    fun testFromDownloadStatusNullReturnsQueued() {
        assertEquals("QUEUED", converters.fromDownloadStatus(null))
    }

    @Test
    fun testToDownloadStatusValid() {
        assertEquals(DownloadStatus.DOWNLOADING, converters.toDownloadStatus("DOWNLOADING"))
        assertEquals(DownloadStatus.COMPLETED, converters.toDownloadStatus("COMPLETED"))
        assertEquals(DownloadStatus.QUEUED, converters.toDownloadStatus("QUEUED"))
    }

    @Test
    fun testToDownloadStatusNullOrBlankReturnsQueued() {
        assertEquals(DownloadStatus.QUEUED, converters.toDownloadStatus(null))
        assertEquals(DownloadStatus.QUEUED, converters.toDownloadStatus(""))
        assertEquals(DownloadStatus.QUEUED, converters.toDownloadStatus("   "))
    }

    @Test
    fun testToDownloadStatusInvalidReturnsQueued() {
        assertEquals(DownloadStatus.QUEUED, converters.toDownloadStatus("UNKNOWN_CORRUPT_STATUS"))
    }
}
