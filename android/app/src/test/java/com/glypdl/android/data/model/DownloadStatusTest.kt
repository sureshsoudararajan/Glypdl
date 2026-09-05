/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DownloadStatusTest {

    @Test
    fun `verify expected values exist`() {
        val expectedStatuses = listOf(
            "QUEUED", "PREPARING", "DOWNLOADING", "PAUSED", "PROCESSING", "COMPLETED", "FAILED", "CANCELLED"
        )
        
        val actualStatuses = DownloadStatus.values().map { it.name }
        
        expectedStatuses.forEach { expected ->
            assertNotNull("Status $expected should exist", actualStatuses.find { it == expected })
        }
    }

    @Test
    fun `valueOf works for all values`() {
        DownloadStatus.values().forEach { status ->
            assertEquals(status, DownloadStatus.valueOf(status.name))
        }
    }
}
