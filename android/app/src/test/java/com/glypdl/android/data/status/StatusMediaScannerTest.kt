/*
 * Copyright (c) 2026. Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
package com.glypdl.android.data.status

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.glypdl.android.data.status.model.StatusMediaType
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

class StatusMediaScannerTest {

    private lateinit var context: Context
    private lateinit var scanner: StatusMediaScanner

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        scanner = StatusMediaScanner(context)
    }

    @Test
    fun `resolveMediaType identifies image MIME types correctly`() {
        assertEquals(StatusMediaType.IMAGE, scanner.resolveMediaType("test.jpg", "image/jpeg"))
        assertEquals(StatusMediaType.IMAGE, scanner.resolveMediaType("photo.png", "image/png"))
        assertEquals(StatusMediaType.IMAGE, scanner.resolveMediaType("status.webp", "image/webp"))
    }

    @Test
    fun `resolveMediaType identifies video MIME types correctly`() {
        assertEquals(StatusMediaType.VIDEO, scanner.resolveMediaType("clip.mp4", "video/mp4"))
        assertEquals(StatusMediaType.VIDEO, scanner.resolveMediaType("video.3gp", "video/3gpp"))
        assertEquals(StatusMediaType.VIDEO, scanner.resolveMediaType("movie.mkv", "video/x-matroska"))
    }

    @Test
    fun `resolveMediaType falls back to file extension when MIME type is generic`() {
        assertEquals(StatusMediaType.IMAGE, scanner.resolveMediaType("status_img.jpg", "application/octet-stream"))
        assertEquals(StatusMediaType.IMAGE, scanner.resolveMediaType("status_img.png", ""))
        assertEquals(StatusMediaType.VIDEO, scanner.resolveMediaType("status_vid.mp4", "application/octet-stream"))
        assertEquals(StatusMediaType.VIDEO, scanner.resolveMediaType("status_vid.mkv", ""))
    }

    @Test
    fun `resolveMediaType returns null for unsupported files`() {
        assertNull(scanner.resolveMediaType(".nomedia", "application/octet-stream"))
        assertNull(scanner.resolveMediaType("status.txt", "text/plain"))
        assertNull(scanner.resolveMediaType("document.pdf", "application/pdf"))
    }

    @Test
    fun `resolveMimeType resolves common extensions accurately`() {
        val jpgDoc = mockk<DocumentFile>()
        every { jpgDoc.type } returns null
        every { jpgDoc.name } returns "status_123.jpg"
        assertEquals("image/jpeg", scanner.resolveMimeType(jpgDoc))

        val mp4Doc = mockk<DocumentFile>()
        every { mp4Doc.type } returns "application/octet-stream"
        every { mp4Doc.name } returns "status_456.mp4"
        assertEquals("video/mp4", scanner.resolveMimeType(mp4Doc))

        val webpDoc = mockk<DocumentFile>()
        every { webpDoc.type } returns null
        every { webpDoc.name } returns "status_789.webp"
        assertEquals("image/webp", scanner.resolveMimeType(webpDoc))
    }

    @Test
    fun `resolveStatusesDirectory returns direct directory when named DotStatuses`() {
        val root = mockk<DocumentFile>()
        every { root.name } returns ".Statuses"
        val resolved = scanner.resolveStatusesDirectory(root)
        assertEquals(root, resolved)
    }

    @Test
    fun `resolveStatusesDirectory finds child DotStatuses when Media is root`() {
        val root = mockk<DocumentFile>()
        val child = mockk<DocumentFile>()

        every { root.name } returns "Media"
        every { root.findFile(".Statuses") } returns child
        every { child.isDirectory } returns true

        val resolved = scanner.resolveStatusesDirectory(root)
        assertEquals(child, resolved)
    }
}
