/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.engine

import android.content.Context
import com.glypdl.android.data.model.GlypdlError
import com.glypdl.android.service.auth.AuthCookieManager
import com.glypdl.android.util.UrlValidator
import com.yausername.youtubedl_android.YoutubeDLRequest
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class InstagramFacebookExtractionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var settingsRepository: com.glypdl.android.data.repository.SettingsRepository
    private lateinit var authCookieManager: AuthCookieManager
    private lateinit var ytDlpManager: YtDlpManager
    private lateinit var filesDir: File

    @Before
    fun setup() {
        filesDir = tempFolder.newFolder("files")
        context = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        every { context.filesDir } returns filesDir
        authCookieManager = AuthCookieManager(context)
        ytDlpManager = YtDlpManager(context, settingsRepository, authCookieManager)
    }

    @Test
    fun testFacebookShareUrlIsNotMangled() {
        val shareUrl = "https://www.facebook.com/share/r/1TLQtRtVQW/"
        val canonical = UrlValidator.canonicalizeFacebookUrl(shareUrl)

        // Must NOT be rewritten to /reel/1TLQtRtVQW because 1TLQtRtVQW is an opaque share token
        assertEquals("https://www.facebook.com/share/r/1TLQtRtVQW/", canonical)
    }

    @Test
    fun testInstagramReelUrlPreservesXtok() {
        val igUrl = "https://www.instagram.com/reel/DfgXse5Icb7/?xtok=MW8zeWcwY3ZnbmJneg=="
        assertTrue(UrlValidator.isInstagramUrl(igUrl))
        val sanitized = UrlValidator.sanitizeUrl(igUrl)
        assertTrue(sanitized.contains("xtok=MW8zeWcwY3ZnbmJneg=="))
    }

    @Test
    fun testApplyPlatformOptionsForInstagramUsesDesktopUserAgent() {
        val request = YoutubeDLRequest("https://www.instagram.com/reel/DfgXse5Icb7/")
        ytDlpManager.applyPlatformOptions(request, "https://www.instagram.com/reel/DfgXse5Icb7/", cookieFile = null)

        val args = request.buildCommand()
        assertTrue(args.contains("--user-agent"))
        val uaIndex = args.indexOf("--user-agent")
        val uaValue = args[uaIndex + 1]
        // Must use desktop Chrome on Windows/Linux so Instagram serves the full desktop web JSON / Relay cache
        assertEquals(YtDlpManager.DESKTOP_USER_AGENT, uaValue)

        assertTrue(args.contains("--referer"))
        val refIndex = args.indexOf("--referer")
        assertEquals("https://www.instagram.com/", args[refIndex + 1])

        // Anonymous by default: no --cookies flag when cookieFile is null
        assertFalse(args.contains("--cookies"))
    }

    @Test
    fun testApplyPlatformOptionsForFacebookUsesDesktopUserAgentAndSecFetchHeaders() {
        val request = YoutubeDLRequest("https://www.facebook.com/share/r/1TLQtRtVQW/")
        ytDlpManager.applyPlatformOptions(request, "https://www.facebook.com/share/r/1TLQtRtVQW/", cookieFile = null)

        val args = request.buildCommand()
        assertTrue(args.contains("--user-agent"))
        val uaIndex = args.indexOf("--user-agent")
        assertEquals(YtDlpManager.DESKTOP_USER_AGENT, args[uaIndex + 1])

        assertTrue(args.contains("--referer"))
        val refIndex = args.indexOf("--referer")
        assertEquals("https://www.facebook.com/", args[refIndex + 1])

        assertTrue(args.contains("Sec-Fetch-Mode:navigate"))
        assertTrue(args.contains("Sec-Fetch-Site:none"))
        assertTrue(args.contains("Sec-Fetch-Dest:document"))
        assertFalse(args.contains("--cookies"))
    }

    @Test
    fun testApplyPlatformOptionsAttachesValidCookiesWhenProvided() {
        authCookieManager.saveCookies("instagram.com", "sessionid=test_session_id; csrftoken=test_csrf")
        val cookieFile = authCookieManager.getCookieFile("instagram.com")

        val request = YoutubeDLRequest("https://www.instagram.com/reel/DfgXse5Icb7/")
        ytDlpManager.applyPlatformOptions(request, "https://www.instagram.com/reel/DfgXse5Icb7/", cookieFile = cookieFile)

        val args = request.buildCommand()
        assertTrue(args.contains("--cookies"))
        val cookieIndex = args.indexOf("--cookies")
        assertEquals(cookieFile!!.absolutePath, args[cookieIndex + 1])
    }

    @Test
    fun testInstagramEmptyMediaResponseErrorClassification() {
        val errorMsg = "ERROR: [Instagram] DfgXse5Icb7: Instagram sent an empty media response. Check if this post is accessible in your browser without being logged-in. If it is not, then use --cookies-from-browser or --cookies for the authentication."
        val error = YtDlpErrorParser.parse(errorMsg)

        assertTrue(error is GlypdlError.EmptyMediaResponse)
        assertEquals("Empty Media Response", error.userTitle)
        assertTrue(error.canRetry)
    }

    @Test
    fun testFacebookCannotParseDataErrorClassification() {
        val errorMsg = "ERROR: [facebook] 1: Cannot parse data; please report this issue on https://github.com/yt-dlp/yt-dlp/issues?q="
        val error = YtDlpErrorParser.parse(errorMsg)

        assertTrue(error is GlypdlError.ExtractorError)
        assertEquals("Extractor Limitation", error.userTitle)
        assertTrue(error.requiresUpdate)
    }
}
