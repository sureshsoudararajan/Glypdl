/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.util

import android.content.Context
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StorageHelperTest {

    @Test
    fun `getMimeType maps media extensions accurately`() {
        assertEquals("audio/mp4", StorageHelper.getMimeType("m4a", isAudio = true))
        assertEquals("audio/mp4", StorageHelper.getMimeType("mp4", isAudio = true))
        assertEquals("video/mp4", StorageHelper.getMimeType("mp4", isAudio = false))
        assertEquals("video/x-matroska", StorageHelper.getMimeType("mkv", isAudio = false))
        assertEquals("audio/webm", StorageHelper.getMimeType("webm", isAudio = true))
        assertEquals("video/webm", StorageHelper.getMimeType("webm", isAudio = false))
        assertEquals("audio/mpeg", StorageHelper.getMimeType("mp3", isAudio = true))
        assertEquals("audio/flac", StorageHelper.getMimeType("flac", isAudio = true))
        assertEquals("audio/wav", StorageHelper.getMimeType("wav", isAudio = true))
        assertEquals("audio/ogg", StorageHelper.getMimeType("ogg", isAudio = true))
        assertEquals("audio/opus", StorageHelper.getMimeType("opus", isAudio = true))
        assertEquals("audio/aac", StorageHelper.getMimeType("aac", isAudio = true))
        assertEquals("video/3gpp", StorageHelper.getMimeType("3gp", isAudio = false))
    }

    @Test
    fun `isSupportedAudioMediaStoreMime correctly identifies allowed audio types`() {
        // Supported types by Android MediaStore.Audio.Media
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/mp4"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/mpeg"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/aac"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/flac"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/ogg"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/wav"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/x-wav"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/3gpp"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/m4a"))
        assertTrue(StorageHelper.isSupportedAudioMediaStoreMime("audio/x-m4a"))

        // Types NOT supported by MediaStore.Audio.Media that must be routed to MediaStore.Downloads
        assertFalse(StorageHelper.isSupportedAudioMediaStoreMime("audio/webm"))
        assertFalse(StorageHelper.isSupportedAudioMediaStoreMime("audio/opus"))
        assertFalse(StorageHelper.isSupportedAudioMediaStoreMime("video/webm"))
        assertFalse(StorageHelper.isSupportedAudioMediaStoreMime("video/mp4"))
        assertFalse(StorageHelper.isSupportedAudioMediaStoreMime("audio/*"))
        assertFalse(StorageHelper.isSupportedAudioMediaStoreMime("application/octet-stream"))
    }

    @Test
    fun `exportToPermanentStorage fails immediately for non-existent staging file`() {
        val nonExistent = File("/tmp/non_existent_file_${System.currentTimeMillis()}.webm")
        val context = mockk<Context>(relaxed = true)

        val result = StorageHelper.exportToPermanentStorage(
            context = context,
            stagingFile = nonExistent,
            displayName = "test.webm",
            mimeType = "audio/webm",
            isAudio = true,
            customTreeUriString = null
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("does not exist") == true)
    }

    @Test
    fun `exportToPermanentStorage fails immediately for empty 0-byte staging file`() {
        val tempFile = File.createTempFile("empty_staging", ".webm")
        try {
            val context = mockk<Context>(relaxed = true)

            val result = StorageHelper.exportToPermanentStorage(
                context = context,
                stagingFile = tempFile,
                displayName = "test.webm",
                mimeType = "audio/webm",
                isAudio = true,
                customTreeUriString = null
            )

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message?.contains("empty") == true)
        } finally {
            tempFile.delete()
        }
    }
}
