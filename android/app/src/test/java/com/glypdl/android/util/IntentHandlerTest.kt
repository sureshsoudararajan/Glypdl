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
}
