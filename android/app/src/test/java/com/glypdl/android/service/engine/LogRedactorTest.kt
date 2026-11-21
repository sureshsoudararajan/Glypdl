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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LogRedactorTest {

    @Test
    fun testRedactCookies() {
        val input = "Header cookie: sessionid=abc123xyz456; csrftoken=def789;"
        val output = LogRedactor.redact(input)

        assertFalse(output.contains("abc123xyz456"))
        assertTrue(output.contains("[REDACTED]"))
    }

    @Test
    fun testRedactCookieArg() {
        val input = "yt-dlp --cookies /data/user/0/cookies.txt https://instagram.com/reel/123"
        val output = LogRedactor.redact(input)

        assertFalse(output.contains("/data/user/0/cookies.txt"))
        assertTrue(output.contains("--cookies [REDACTED]"))
        assertTrue(output.contains("https://instagram.com/reel/123"))
    }

    @Test
    fun testRedactAuthorizationHeader() {
        val input = "Authorization: Bearer ya29.a0AfH6SMD_secret_token_12345"
        val output = LogRedactor.redact(input)

        assertFalse(output.contains("ya29.a0AfH6SMD_secret_token_12345"))
        assertTrue(output.contains("Bearer [REDACTED]"))
    }

    @Test
    fun testRedactPasswords() {
        val input = "Connecting with password=SuperSecretPassword123! to account"
        val output = LogRedactor.redact(input)

        assertFalse(output.contains("SuperSecretPassword123!"))
        assertTrue(output.contains("password=[REDACTED]"))
    }

    @Test
    fun testPreservesSafeContent() {
        val safe = "[youtube] Downloading video format 137 (1080p) + 140 (m4a)"
        val output = LogRedactor.redact(safe)

        assertEquals(safe, output)
    }

    @Test
    fun testRedactSignedUrlAndQueryTokens() {
        val signedUrl = "https://example.com/video.mp4?token=secretToken123&expires=1700000000&Signature=abcd9876xyz&AWSAccessKeyId=AKIAIOSFODNN7EXAMPLE"
        val output = LogRedactor.redact(signedUrl)

        assertFalse(output.contains("secretToken123"))
        assertFalse(output.contains("abcd9876xyz"))
        assertFalse(output.contains("AKIAIOSFODNN7EXAMPLE"))
        assertTrue(output.contains("token=[REDACTED]"))
        assertTrue(output.contains("Signature=[REDACTED]"))
        assertTrue(output.contains("AWSAccessKeyId=[REDACTED]"))
    }

    @Test
    fun testRedactAddHeaderOptions() {
        val cmd = "yt-dlp --add-header 'Cookie: sessionid=secretSession123' --add-header 'Authorization: Bearer secretBearer123'"
        val output = LogRedactor.redact(cmd)

        assertFalse(output.contains("secretSession123"))
        assertFalse(output.contains("secretBearer123"))
        assertTrue(output.contains("[REDACTED]"))
    }
}
