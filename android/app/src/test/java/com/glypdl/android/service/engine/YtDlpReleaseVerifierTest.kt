package com.glypdl.android.service.engine

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class YtDlpReleaseVerifierTest {
    @get:Rule val tmp = TemporaryFolder()
    private val h1 = "a".repeat(64)
    private val h2 = "b".repeat(64)

    @Test fun `accepts only well formed release tags`() {
        assertTrue(YtDlpReleaseVerifier.isValidTag("2026.10.26"))
        assertTrue(YtDlpReleaseVerifier.isValidTag("2026.10.26.1"))
        assertFalse(YtDlpReleaseVerifier.isValidTag("2026.10.26/../x"))
        assertFalse(YtDlpReleaseVerifier.isValidTag("latest"))
        assertFalse(YtDlpReleaseVerifier.isValidTag(null))
    }

    @Test fun `finds exactly one checksum entry`() {
        val sums = "$h1  yt-dlp\n$h2  yt-dlp.exe\n"
        assertEquals(h1, YtDlpReleaseVerifier.checksumFor(sums, "yt-dlp"))
        assertEquals(h2, YtDlpReleaseVerifier.checksumFor(sums, "yt-dlp.exe"))
        assertNull(YtDlpReleaseVerifier.checksumFor(sums, "yt-dlp_macos"))
        assertNull(YtDlpReleaseVerifier.checksumFor("$h1  yt-dlp\n$h2  yt-dlp\n", "yt-dlp"))
        assertNull(YtDlpReleaseVerifier.checksumFor("nothex  yt-dlp\n", "yt-dlp"))
    }

    @Test fun `rejects wrong hash, tiny files and wrong header`() {
        val body = "#!/usr/bin/env python3\n" + "x".repeat(1_100_000)
        val good = tmp.newFile("good").apply { writeText(body) }
        val hash = com.glypdl.android.service.update.UpdateVerifier.sha256Of(good)
        assertTrue(YtDlpReleaseVerifier.isAcceptable(good, hash))
        assertFalse(YtDlpReleaseVerifier.isAcceptable(good, "0".repeat(64)))
        val tiny = tmp.newFile("tiny").apply { writeText("#!x") }
        assertFalse(YtDlpReleaseVerifier.isAcceptable(tiny, com.glypdl.android.service.update.UpdateVerifier.sha256Of(tiny)))
        val noHeader = tmp.newFile("nh").apply { writeText("MZ" + "x".repeat(1_100_000)) }
        assertFalse(YtDlpReleaseVerifier.isAcceptable(noHeader, com.glypdl.android.service.update.UpdateVerifier.sha256Of(noHeader)))
    }
}
