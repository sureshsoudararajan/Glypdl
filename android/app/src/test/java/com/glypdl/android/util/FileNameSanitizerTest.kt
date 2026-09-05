/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FileNameSanitizerTest {

    @Test
    fun `sanitize removes illegal characters`() {
        val input = "Video\\/:*?\"<>|Name"
        val result = FileNameSanitizer.sanitize(input)
        assertEquals("Video_________Name", result)
    }

    @Test
    fun `sanitize truncates long filenames to limit`() {
        val longName = "A".repeat(300) + ".mp4"
        val result = FileNameSanitizer.sanitize(longName)
        assertTrue(result.length <= 200)
    }

    @Test
    fun `sanitize handles empty title`() {
        val result = FileNameSanitizer.sanitize("")
        assertEquals("", result)
    }

    @Test
    fun `sanitize handles Unicode titles`() {
        val input = "こんにちは世界"
        val result = FileNameSanitizer.sanitize(input)
        assertEquals("こんにちは世界", result)
    }

    @Test
    fun `sanitize trims trailing dots and spaces`() {
        val input = "My Video ...   "
        val result = FileNameSanitizer.sanitize(input)
        assertEquals("My Video", result)
    }

    @Test
    fun `generateFilename adds ID for generic titles`() {
        val result = FileNameSanitizer.generateFilename("story", "mp4", "12345")
        assertEquals("story_12345.mp4", result)
        
        val result2 = FileNameSanitizer.generateFilename("Reel", "mp4", "67890")
        assertEquals("Reel_67890.mp4", result2)
    }

    @Test
    fun `generateFilename uses title and id`() {
        val result = FileNameSanitizer.generateFilename("My Cool Video", "mp4", "12345")
        assertEquals("My Cool Video_12345.mp4", result)
    }

    @Test
    fun `safeFsTitle bounds UTF-8 byte length to prevent Errno 36`() {
        // Multi-byte Tamil characters (3 bytes each)
        val tamilTitle = "தமிழ் பாடல் காணொளி ".repeat(20)
        val safe = FileNameSanitizer.safeFsTitle(tamilTitle, 80)
        val byteLength = safe.toByteArray(Charsets.UTF_8).size
        assertTrue(byteLength <= 80)
        assertTrue(safe.isNotBlank())
    }

    @Test
    fun `handleDuplicate returns filename if no duplicate exists`() {
        val tempDir = System.getProperty("java.io.tmpdir") ?: "/tmp"
        val filename = "non_existent_unique_file_12345.mp4"
        val result = FileNameSanitizer.handleDuplicate(tempDir, filename)
        assertEquals(filename, result)
    }
}
