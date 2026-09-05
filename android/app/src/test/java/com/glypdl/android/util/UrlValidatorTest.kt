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
package com.glypdl.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlValidatorTest {

    @Test
    fun `isValidUrl returns true for valid http URL`() {
        assertTrue(UrlValidator.isValidUrl("http://example.com"))
    }

    @Test
    fun `isValidUrl returns true for valid https URL`() {
        assertTrue(UrlValidator.isValidUrl("https://example.com"))
    }

    @Test
    fun `isValidUrl returns false for empty string`() {
        assertFalse(UrlValidator.isValidUrl(""))
    }

    @Test
    fun `isValidUrl returns false for no scheme`() {
        assertFalse(UrlValidator.isValidUrl("example.com"))
    }

    @Test
    fun `isValidUrl returns false for ftp scheme`() {
        assertFalse(UrlValidator.isValidUrl("ftp://example.com"))
    }

    @Test
    fun `isValidUrl returns false for javascript scheme`() {
        assertFalse(UrlValidator.isValidUrl("javascript:alert(1)"))
    }

    @Test
    fun `isValidUrl returns false for malformed URL`() {
        assertFalse(UrlValidator.isValidUrl("https://[malformed]"))
    }

    @Test
    fun `extractUrlFromText finds URL in text`() {
        val text = "Check out this video: https://youtube.com/watch?v=12345 It's great!"
        assertEquals("https://youtube.com/watch?v=12345", UrlValidator.extractUrlFromText(text))
    }

    @Test
    fun `extractUrlFromText returns null for no URL`() {
        val text = "There is no url here, just text."
        assertNull(UrlValidator.extractUrlFromText(text))
    }

    @Test
    fun `sanitizeUrl trims whitespace`() {
        val url = "  https://example.com/  "
        assertEquals("https://example.com/", UrlValidator.sanitizeUrl(url))
    }

    @Test
    fun `isFacebookUrl correctly identifies Facebook URLs`() {
        assertTrue(UrlValidator.isFacebookUrl("https://www.facebook.com/watch/?v=123456789"))
        assertTrue(UrlValidator.isFacebookUrl("https://fb.watch/abcd1234/"))
        assertTrue(UrlValidator.isFacebookUrl("https://facebook.com/reel/987654321"))
        assertFalse(UrlValidator.isFacebookUrl("https://instagram.com/p/123"))
        assertFalse(UrlValidator.isFacebookUrl("https://youtube.com/watch?v=123"))
    }

    @Test
    fun `extractInstagramStoryId extracts correct ID`() {
        val url = "https://www.instagram.com/stories/some_user/3312345678901234567/?utm_source=ig_story_item_share"
        assertEquals("3312345678901234567", UrlValidator.extractInstagramStoryId(url))
        assertEquals("some_user", UrlValidator.extractInstagramStoryUsername(url))
    }

    @Test
    fun `canonicalizeInstagramStoryUrl preserves username and exact story ID`() {
        val url = "https://www.instagram.com/stories/testcreator/123456789012345/?utm_source=ig_story_item_share&igsh=abcdef123456"
        val canonical = UrlValidator.canonicalizeInstagramStoryUrl(url)
        assertEquals("https://www.instagram.com/stories/testcreator/123456789012345/", canonical)
    }
}
