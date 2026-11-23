package com.glypdl.android.service.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateVerifierTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `trusts only https github release hosts`() {
        assertTrue(UpdateVerifier.isTrustedDownloadUrl("https://github.com/o/r/releases/download/v1/a.apk"))
        assertTrue(UpdateVerifier.isTrustedDownloadUrl("https://objects.githubusercontent.com/x"))
        assertTrue(UpdateVerifier.isTrustedDownloadUrl("https://release-assets.githubusercontent.com/x"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl("http://github.com/a.apk"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl("https://evil.com/a.apk"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl("https://github.com.evil.com/a.apk"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl("https://github.com@evil.com/a.apk"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl("https://github.com:8443/a.apk"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl("file:///sdcard/a.apk"))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl(""))
        assertFalse(UpdateVerifier.isTrustedDownloadUrl(null))
    }

    @Test fun `parses github digest field`() {
        val hex = "a".repeat(64)
        assertEquals(hex, UpdateVerifier.parseSha256Digest("sha256:$hex"))
        assertEquals(hex, UpdateVerifier.parseSha256Digest("SHA256:${hex.uppercase()}"))
        assertNull(UpdateVerifier.parseSha256Digest("sha256:short"))
        assertNull(UpdateVerifier.parseSha256Digest("sha256:" + "g".repeat(64)))
        assertNull(UpdateVerifier.parseSha256Digest(""))
        assertNull(UpdateVerifier.parseSha256Digest(null))
    }

    @Test fun `verifies file hash`() {
        val f = tmp.newFile("a.apk").apply { writeText("hello") }
        // sha256("hello")
        val good = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertTrue(UpdateVerifier.matches(f, good))
        assertTrue(UpdateVerifier.matches(f, good.uppercase()))
        assertFalse(UpdateVerifier.matches(f, "0".repeat(64)))
    }

    @Test fun `strips path components from file names`() {
        assertEquals("a.apk", UpdateVerifier.safeFileName("../../files/a.apk"))
        assertEquals("a.apk", UpdateVerifier.safeFileName("..\\x\\a.apk"))
        assertEquals("glypdl-update.apk", UpdateVerifier.safeFileName(".."))
        assertEquals("glypdl-update.apk", UpdateVerifier.safeFileName(""))
    }
}
