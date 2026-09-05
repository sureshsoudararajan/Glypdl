/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.engine

import com.glypdl.android.data.model.GlypdlError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YtDlpErrorParserTest {

    @Test
    fun testInstagramAuthenticationRequired() {
        val raw = "ERROR: [instagram] 12345: Sign in to confirm you’re not a bot. This helps protect our community."
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.AuthenticationRequired)
        assertEquals("Authentication Required", parsed.userTitle)
        assertTrue(parsed.canRetry)
        assertFalse(parsed.requiresUpdate)
        assertNotNull(parsed.technicalDetails)
    }

    @Test
    fun testInstagramLoginCheckpoint() {
        val raw = "ERROR: [instagram] checkpoint_required: Please log in to view this profile."
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.AuthenticationRequired)
    }

    @Test
    fun testRateLimited() {
        val raw = "ERROR: [youtube] Unable to download webpage: HTTP Error 429: Too Many Requests"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.RateLimited)
        assertEquals("Temporarily Rate Limited", parsed.userTitle)
        assertTrue(parsed.canRetry)
    }

    @Test
    fun testEngineOutdatedOlderThan90Days() {
        val raw = "WARNING: Your yt-dlp version is older than 90 days! Some extractors may not work. Please update yt-dlp."
        val parsed = YtDlpErrorParser.parse(raw, installedVersion = "2025.11.12")

        assertTrue(parsed is GlypdlError.EngineOutdated)
        val outdated = parsed as GlypdlError.EngineOutdated
        assertEquals("2025.11.12", outdated.installedVersion)
        assertTrue(parsed.requiresUpdate)
    }

    @Test
    fun testDRMProtected() {
        val raw = "ERROR: [netflix] This video is DRM protected and cannot be processed by yt-dlp."
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.DRMProtected)
        assertFalse(parsed.canRetry)
    }

    @Test
    fun testGeoRestricted() {
        val raw = "ERROR: [youtube] dQw4w9WgXcQ: The uploader has not made this video available in your country"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.GeoRestricted)
        assertFalse(parsed.canRetry)
    }

    @Test
    fun testContentUnavailable() {
        val raw = "ERROR: [youtube] Video unavailable. This video has been removed by the user"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.ContentUnavailable)
        assertFalse(parsed.canRetry)
    }

    @Test
    fun testUnsupportedSite() {
        val raw = "ERROR: Unsupported URL: https://invalid-domain.xyz/video"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.UnsupportedSite)
        assertFalse(parsed.canRetry)
    }

    @Test
    fun testNetworkError() {
        val raw = "ERROR: Unable to download webpage: <urlopen error [Errno -2] Name or service not known>"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.NetworkError)
        assertTrue(parsed.canRetry)
    }

    @Test
    fun testFFmpegError() {
        val raw = "ERROR: ffmpeg postprocessing failed: Conversion failed"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.FFmpegError)
        assertTrue(parsed is GlypdlError.FFmpegError.ConversionError)
    }

    @Test
    fun testFFmpegNoSpaceError() {
        val raw = "ERROR: ffmpeg failed: No space left on device"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.FFmpegError.NoSpace)
        assertTrue(parsed.userMessage.contains("Not enough storage space"))
    }

    @Test
    fun testStoryMismatchError() {
        val raw = "ERROR: requested instagram story 12345 did not match downloaded 67890"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.StoryMismatch)
    }

    @Test
    fun testUnknownErrorFallback() {
        val raw = "ERROR: Something very strange happened on line 42"
        val parsed = YtDlpErrorParser.parse(raw)

        assertTrue(parsed is GlypdlError.UnknownError)
        assertEquals("Something very strange happened on line 42", parsed.userMessage)
    }
}
