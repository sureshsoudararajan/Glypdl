/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.util

import android.content.Intent
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IntentHandlerTest {

    @Test
    fun `ACTION_SEND with valid URL extracts URL`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "text/plain"
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns "https://example.com/video"

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertEquals("https://example.com/video", result)
    }

    @Test
    fun `ACTION_SEND with text containing URL extracts URL`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "text/plain"
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns "Check this out: https://example.com/video"

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertEquals("https://example.com/video", result)
    }

    @Test
    fun `ACTION_SEND with no text returns null`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.type } returns "text/plain"
        every { intent.getStringExtra(Intent.EXTRA_TEXT) } returns null

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertNull(result)
    }

    @Test
    fun `ACTION_VIEW with valid data URI extracts URL`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.dataString } returns "https://example.com/video"

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertEquals("https://example.com/video", result)
    }

    @Test
    fun `ACTION_VIEW with null data returns null`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.dataString } returns null

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertNull(result)
    }

    @Test
    fun `Invalid action returns null`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_MAIN

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertNull(result)
    }

    @Test
    fun `ACTION_VIEW with torrent content URI extracts URI`() {
        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.dataString } returns "content://media/external/downloads/test.torrent"
        every { intent.data } returns null
        every { intent.type } returns "application/x-bittorrent"

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertEquals("content://media/external/downloads/test.torrent", result)
    }

    @Test
    fun `ACTION_SEND with EXTRA_STREAM extracts torrent URI`() {
        val mockUri = mockk<android.net.Uri>()
        every { mockUri.toString() } returns "content://media/external/file/999"

        val intent = mockk<Intent>()
        every { intent.action } returns Intent.ACTION_SEND
        every { intent.getParcelableExtra<android.net.Uri>(Intent.EXTRA_STREAM) } returns mockUri

        val result = IntentHandler.extractUrlFromIntent(intent)
        assertEquals("content://media/external/file/999", result)
    }
}
