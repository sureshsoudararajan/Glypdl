/*
 * Copyright (C) 2026 The Glypdl Authors
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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android.service.resolver

import com.glypdl.android.data.model.DownloadType
import android.content.Context
import com.glypdl.android.data.model.EngineType
import com.glypdl.android.data.model.FileCategory
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class DownloadResolverTest {

    private lateinit var context: Context
    private lateinit var resolver: DownloadResolver

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        resolver = DownloadResolver(context)
    }

    @Test
    fun resolvesMagnetLinksToAria2Magnet() {
        val magnet = "magnet:?xt=urn:btih:d2474e86c95b19b8bcfdbb72428a2433ec9eecb4&dn=Ubuntu"
        val resolved = resolver.resolve(magnet)

        assertEquals(DownloadType.MAGNET, resolved.downloadType)
        assertEquals(EngineType.ARIA2C, resolved.engineType)
        assertEquals(FileCategory.UNKNOWN, resolved.fileCategory)
    }

    @Test
    fun resolvesTorrentUrlsToAria2Torrent() {
        val torrentUrl = "https://releases.ubuntu.com/24.04/ubuntu-24.04-desktop-amd64.iso.torrent"
        val resolved = resolver.resolve(torrentUrl)

        assertEquals(DownloadType.TORRENT, resolved.downloadType)
        assertEquals(EngineType.ARIA2C, resolved.engineType)
    }

    @Test
    fun resolvesMediaSitesToYtDlpMedia() {
        val youtube = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val ytResolved = resolver.resolve(youtube)
        assertEquals(DownloadType.MEDIA, ytResolved.downloadType)
        assertEquals(EngineType.YT_DLP, ytResolved.engineType)

        val instagram = "https://www.instagram.com/reel/C12345/"
        val igResolved = resolver.resolve(instagram)
        assertEquals(DownloadType.MEDIA, igResolved.downloadType)
        assertEquals(EngineType.YT_DLP, igResolved.engineType)

        val tiktok = "https://www.tiktok.com/@user/video/12345"
        val ttResolved = resolver.resolve(tiktok)
        assertEquals(DownloadType.MEDIA, ttResolved.downloadType)
        assertEquals(EngineType.YT_DLP, ttResolved.engineType)
    }

    @Test
    fun resolvesDirectFilesToAria2DirectFile() {
        val zipUrl = "https://example.com/downloads/archive.zip"
        val zipResolved = resolver.resolve(zipUrl)
        assertEquals(DownloadType.DIRECT_FILE, zipResolved.downloadType)
        assertEquals(EngineType.ARIA2C, zipResolved.engineType)
        assertEquals(FileCategory.ARCHIVE, zipResolved.fileCategory)
        assertEquals("archive.zip", zipResolved.detectedFileName)

        val isoUrl = "https://releases.ubuntu.com/24.04/ubuntu-desktop.iso"
        val isoResolved = resolver.resolve(isoUrl)
        assertEquals(DownloadType.DIRECT_FILE, isoResolved.downloadType)
        assertEquals(EngineType.ARIA2C, isoResolved.engineType)
        assertEquals(FileCategory.DISK_IMAGE, isoResolved.fileCategory)

        val apkUrl = "https://f-droid.org/repo/com.example.app_123.apk"
        val apkResolved = resolver.resolve(apkUrl)
        assertEquals(DownloadType.DIRECT_FILE, apkResolved.downloadType)
        assertEquals(EngineType.ARIA2C, apkResolved.engineType)
        assertEquals(FileCategory.APPLICATION, apkResolved.fileCategory)

        val pdfUrl = "https://arxiv.org/pdf/2301.00001.pdf"
        val pdfResolved = resolver.resolve(pdfUrl)
        assertEquals(DownloadType.DIRECT_FILE, pdfResolved.downloadType)
        assertEquals(EngineType.ARIA2C, pdfResolved.engineType)
        assertEquals(FileCategory.DOCUMENT, pdfResolved.fileCategory)
    }

    @Test
    fun fallsBackToMediaYtDlpForAmbiguousWebsites() {
        val genericUrl = "https://example.com/watch/video123"
        val resolved = resolver.resolve(genericUrl)
        assertEquals(DownloadType.MEDIA, resolved.downloadType)
        assertEquals(EngineType.YT_DLP, resolved.engineType)
    }

    @Test
    fun resolvesTorrentBase64AndContentUri() {
        val base64Input = "torrent-base64:dGVzdHRvcnJlbnQ="
        val base64Resolved = resolver.resolve(base64Input)
        assertEquals(DownloadType.TORRENT, base64Resolved.downloadType)
        assertEquals(EngineType.ARIA2C, base64Resolved.engineType)

        val contentUri = "content://media/external/downloads/test.torrent"
        val contentResolved = resolver.resolve(contentUri)
        assertEquals(DownloadType.TORRENT, contentResolved.downloadType)
        assertEquals(EngineType.ARIA2C, contentResolved.engineType)
    }
}
