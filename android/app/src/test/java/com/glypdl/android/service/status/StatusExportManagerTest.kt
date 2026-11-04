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
package com.glypdl.android.service.status

import android.content.Context
import com.glypdl.android.data.local.dao.SavedStatusDao
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class StatusExportManagerTest {

    private lateinit var context: Context
    private lateinit var savedStatusDao: SavedStatusDao
    private lateinit var exportManager: StatusExportManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        savedStatusDao = mockk(relaxed = true)
        exportManager = StatusExportManager(context, savedStatusDao)
    }

    @Test
    fun `sanitizeFilename removes illegal characters`() {
        val result = exportManager.sanitizeFilename("status:photo/name*?.jpg")
        assertEquals("status_photo_name__.jpg", result)
    }

    @Test
    fun `sanitizeFilename handles whitespace cleanly`() {
        val result = exportManager.sanitizeFilename("  my status photo.png  ")
        assertEquals("my status photo.png", result)
    }

    @Test
    fun `sanitizeFilename generates fallback name when string is blank`() {
        val result = exportManager.sanitizeFilename("   ")
        assertTrue(result.startsWith("status_"))
    }
}
