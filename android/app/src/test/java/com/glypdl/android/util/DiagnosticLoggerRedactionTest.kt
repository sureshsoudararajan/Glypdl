package com.glypdl.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticLoggerRedactionTest {

    @Test
    fun `redacts cookie and authorization headers`() {
        val out = DiagnosticLogger.redact("Cookie: sessionid=abc123; csrftoken=zzz\nAuthorization: Bearer secret")
        assertFalse(out.contains("abc123"))
        assertFalse(out.contains("secret"))
        assertTrue(out.contains("[REDACTED]"))
    }

    @Test
    fun `redacts sensitive yt-dlp arguments`() {
        val out = DiagnosticLogger.redact("yt-dlp --cookies /data/cookies.txt --password hunter2 https://example.com/v")
        assertFalse(out.contains("hunter2"))
        assertFalse(out.contains("/data/cookies.txt"))
        assertTrue(out.contains("https://example.com/v"))
    }

    @Test
    fun `redacts token query parameters but keeps the url`() {
        val out = DiagnosticLogger.redact("GET https://cdn.example.com/v.mp4?id=5&token=SECRET&sig=XYZ")
        assertFalse(out.contains("SECRET"))
        assertFalse(out.contains("XYZ"))
        assertTrue(out.contains("id=5"))
    }

    @Test
    fun `leaves ordinary messages untouched`() {
        val msg = "Download complete: Rick Astley - Never Gonna Give You Up (3 of 15)"
        assertEquals(msg, DiagnosticLogger.redact(msg))
    }
}
