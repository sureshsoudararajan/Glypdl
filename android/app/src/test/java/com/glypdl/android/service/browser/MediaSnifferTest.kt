/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
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

package com.glypdl.android.service.browser

import com.glypdl.android.data.model.DetectionSource
import com.glypdl.android.data.model.MediaKind
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MediaSnifferTest {

    private lateinit var mediaProber: MediaProber
    private lateinit var mediaSniffer: MediaSniffer

    @Before
    fun setup() {
        mediaProber = mockk(relaxed = true)
        mediaSniffer = MediaSniffer(mediaProber)
    }

    @Test
    fun addCandidate_deduplicatesSameMedia() {
        mediaSniffer.addCandidate(
            url = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            pageTitle = "Test Page",
            kind = MediaKind.PROGRESSIVE_VIDEO,
            source = DetectionSource.NETWORK
        )

        // Add same candidate again via DOM
        mediaSniffer.addCandidate(
            url = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            pageTitle = "Test Page",
            kind = MediaKind.PROGRESSIVE_VIDEO,
            source = DetectionSource.DOM
        )

        val list = mediaSniffer.detectedMediaFlow.value
        assertEquals(1, list.size)
        assertEquals("https://example.com/video.mp4", list[0].url)
    }

    @Test
    fun addCandidate_masterPlaylistPriority() {
        // Master playlist added
        mediaSniffer.addCandidate(
            url = "https://example.com/hls/master.m3u8",
            pageUrl = "https://example.com",
            pageTitle = "HLS Stream",
            kind = MediaKind.HLS,
            source = DetectionSource.NETWORK
        )

        // Variant sub-playlist added
        mediaSniffer.addCandidate(
            url = "https://example.com/hls/variant_chunk_720p.m3u8",
            pageUrl = "https://example.com",
            pageTitle = "HLS 720p",
            kind = MediaKind.HLS,
            source = DetectionSource.NETWORK
        )

        val list = mediaSniffer.detectedMediaFlow.value
        assertEquals(1, list.size)
        assertEquals("https://example.com/hls/master.m3u8", list[0].url)
    }

    @Test
    fun resetForPage_clearsDetectedMedia() {
        mediaSniffer.addCandidate(
            url = "https://example.com/video.mp4",
            pageUrl = "https://example.com",
            pageTitle = "Test Page",
            kind = MediaKind.PROGRESSIVE_VIDEO
        )
        assertEquals(1, mediaSniffer.detectedMediaFlow.value.size)

        mediaSniffer.resetForPage("https://another-page.com")
        assertTrue(mediaSniffer.detectedMediaFlow.value.isEmpty())
    }
}
