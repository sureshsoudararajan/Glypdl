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
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaFormatTest {

    @Test
    fun `displaySize returns KB for small files`() {
        val format = MediaFormat(
            formatId = "1",
            ext = "mp4",
            resolution = "1080p",
            filesize = 512_000L
        )
        assertTrue(format.displaySize.contains("KB"))
    }

    @Test
    fun `displaySize returns MB for medium files`() {
        val format = MediaFormat(
            formatId = "2",
            ext = "mp4",
            resolution = "1080p",
            filesize = 5_242_880L
        )
        assertTrue(format.displaySize.contains("MB"))
    }

    @Test
    fun `displaySize returns GB for large files`() {
        val format = MediaFormat(
            formatId = "3",
            ext = "mp4",
            resolution = "4K",
            filesize = 1_610_612_736L
        )
        assertTrue(format.displaySize.contains("GB"))
    }

    @Test
    fun `displaySize returns Unknown size for null filesize`() {
        val format = MediaFormat(
            formatId = "4",
            ext = "mp4",
            resolution = "720p",
            filesize = null
        )
        assertEquals("Unknown size", format.displaySize)
    }

    @Test
    fun `displayResolution returns resolution for video`() {
        val format = MediaFormat(
            formatId = "5",
            ext = "mp4",
            resolution = "1080p",
            isAudioOnly = false
        )
        assertEquals("1080p", format.displayResolution)
    }

    @Test
    fun `displayResolution returns Audio for audio-only`() {
        val format = MediaFormat(
            formatId = "6",
            ext = "m4a",
            resolution = null,
            isAudioOnly = true
        )
        assertEquals("Audio", format.displayResolution)
    }
}
