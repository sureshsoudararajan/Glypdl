/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.util

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class ClipboardHelperTest {

    private lateinit var context: Context
    private lateinit var clipboardManager: ClipboardManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        clipboardManager = mockk(relaxed = true)
        every { context.getSystemService(Context.CLIPBOARD_SERVICE) } returns clipboardManager
    }

    @Test
    fun testGetUrlFromClipboardWithValidHttpUrl() {
        val clipItem = ClipData.Item("Check out this video https://www.instagram.com/reel/C-12345/ now!")
        val clipData = ClipData("label", arrayOf("text/plain"), clipItem)
        
        every { clipboardManager.hasPrimaryClip() } returns true
        every { clipboardManager.primaryClip } returns clipData

        val result = ClipboardHelper.getUrlFromClipboard(context)
        assertEquals("https://www.instagram.com/reel/C-12345/", result)
    }

    @Test
    fun testGetUrlFromClipboardWithNoClipReturnsNull() {
        every { clipboardManager.hasPrimaryClip() } returns false

        val result = ClipboardHelper.getUrlFromClipboard(context)
        assertNull(result)
    }

    @Test
    fun testGetUrlFromClipboardWithNonUrlTextReturnsNull() {
        val clipItem = ClipData.Item("Just regular text with no links")
        val clipData = ClipData("label", arrayOf("text/plain"), clipItem)
        
        every { clipboardManager.hasPrimaryClip() } returns true
        every { clipboardManager.primaryClip } returns clipData

        val result = ClipboardHelper.getUrlFromClipboard(context)
        assertNull(result)
    }
}
