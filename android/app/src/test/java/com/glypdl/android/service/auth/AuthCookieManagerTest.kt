/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.auth

import android.content.Context
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AuthCookieManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var authCookieManager: AuthCookieManager
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        authCookieManager = AuthCookieManager(context)
    }

    @Test
    fun testResolveDomainKey() {
        assertEquals("instagram.com", authCookieManager.resolveDomainKey("https://www.instagram.com/reel/C-123"))
        assertEquals("instagram.com", authCookieManager.resolveDomainKey("instagram.com"))
        assertEquals("facebook.com", authCookieManager.resolveDomainKey("https://www.facebook.com/reel/123456789"))
        assertEquals("facebook.com", authCookieManager.resolveDomainKey("https://fb.watch/xyz789/"))
        assertEquals("facebook.com", authCookieManager.resolveDomainKey("facebook.com"))
        assertEquals("youtube.com", authCookieManager.resolveDomainKey("https://m.youtube.com/watch?v=abc"))
        assertEquals("youtube.com", authCookieManager.resolveDomainKey("https://youtu.be/abc"))
        assertEquals("tiktok.com", authCookieManager.resolveDomainKey("https://www.tiktok.com/@user/video/123"))
        assertEquals("twitter.com", authCookieManager.resolveDomainKey("https://x.com/user/status/123"))
    }

    @Test
    fun testSaveCookiesInNetscapeFormat() {
        val rawCookies = "sessionid=12345abcd; csrftoken=token999; mid=Z123"
        val success = authCookieManager.saveCookies("instagram.com", rawCookies)
        assertTrue(success)

        val file = authCookieManager.getCookieFile("instagram.com")
        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue(file.length() > 0)

        val content = file.readText()
        assertTrue(content.contains("# Netscape HTTP Cookie File"))
        assertTrue(content.contains(".instagram.com\tTRUE\t/\tTRUE\t"))
        assertTrue(content.contains("sessionid\t12345abcd"))
        assertTrue(content.contains("csrftoken\ttoken999"))
    }

    @Test
    fun testSaveFacebookCookies() {
        val fbCookies = "c_user=100012345678; xs=32%3Aabcde%3A2%3A123; fr=0abcdef123"
        val success = authCookieManager.saveCookies("facebook.com", fbCookies)
        assertTrue(success)

        val file = authCookieManager.getCookieFile("https://www.facebook.com/reel/999")
        assertNotNull(file)
        assertTrue(file!!.exists())
        val content = file.readText()
        assertTrue(content.contains(".facebook.com\tTRUE\t/\tTRUE\t"))
        assertTrue(content.contains("c_user\t100012345678"))
        assertTrue(content.contains("xs\t32%3Aabcde%3A2%3A123"))
    }

    @Test
    fun testHasAndClearCookies() {
        assertFalse(authCookieManager.hasCookies("instagram.com"))

        authCookieManager.saveCookies("instagram.com", "sessionid=abc")
        assertTrue(authCookieManager.hasCookies("instagram.com"))

        authCookieManager.clearCookies("instagram.com")
        assertFalse(authCookieManager.hasCookies("instagram.com"))

        assertFalse(authCookieManager.hasCookies("facebook.com"))
        authCookieManager.saveCookies("facebook.com", "c_user=123; xs=abc")
        assertTrue(authCookieManager.hasCookies("facebook.com"))
        authCookieManager.clearCookies("facebook.com")
        assertFalse(authCookieManager.hasCookies("facebook.com"))
    }
}
