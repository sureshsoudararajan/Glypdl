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

import com.glypdl.android.data.model.MediaFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatNormalizerTest {

    @Test
    fun testNormalizeVideoQualities() {
        val formats = listOf(
            // 4K video-only
            MediaFormat(
                formatId = "313",
                ext = "webm",
                resolution = "3840x2160",
                width = 3840,
                height = 2160,
                vcodec = "vp9",
                acodec = "none",
                filesize = 500_000_000L,
                isVideoOnly = true
            ),
            // 1080p video-only
            MediaFormat(
                formatId = "137",
                ext = "mp4",
                resolution = "1920x1080",
                width = 1920,
                height = 1080,
                vcodec = "avc1.640028",
                acodec = "none",
                filesize = 120_000_000L,
                isVideoOnly = true
            ),
            // 720p with audio
            MediaFormat(
                formatId = "22",
                ext = "mp4",
                resolution = "1280x720",
                width = 1280,
                height = 720,
                vcodec = "avc1.64001F",
                acodec = "mp4a.40.2",
                filesize = 60_000_000L,
                isVideoOnly = false
            ),
            // Audio stream
            MediaFormat(
                formatId = "140",
                ext = "m4a",
                resolution = "audio only",
                vcodec = "none",
                acodec = "mp4a.40.2",
                abr = 128f,
                filesize = 10_000_000L,
                isAudioOnly = true
            )
        )

        val normalized = FormatNormalizer.normalizeVideoQualities(formats)

        assertEquals(3, normalized.size)
        // Clean labels as specified
        assertEquals("4K", normalized[0].label)
        assertEquals("1080p", normalized[1].label)
        assertEquals("720p", normalized[2].label)

        // 4K and 1080p are video-only, so their formatSelector requests audio merge
        assertTrue(normalized[0].formatSelector.contains("+bestaudio"))
        assertTrue(normalized[1].formatSelector.contains("+bestaudio"))

        // 720p has audio, formatSelector is its formatId
        assertEquals("22", normalized[2].formatSelector)
    }

    @Test
    fun testPortraitAndReelNormalization() {
        val formats = listOf(
            // 1080x1920 portrait reel
            MediaFormat(
                formatId = "reel_1080",
                ext = "mp4",
                resolution = "1080x1920",
                width = 1080,
                height = 1920,
                vcodec = "avc1",
                acodec = "none",
                isVideoOnly = true
            ),
            // 144x256 preview
            MediaFormat(
                formatId = "preview_144",
                ext = "mp4",
                resolution = "144x256",
                width = 144,
                height = 256,
                vcodec = "avc1",
                acodec = "none",
                isVideoOnly = true
            )
        )

        val normalized = FormatNormalizer.normalizeVideoQualities(formats)

        assertEquals(2, normalized.size)
        assertEquals("1080p", normalized[0].label)
        assertEquals("144p", normalized[1].label)
    }

    @Test
    fun testOnlyAvailableQualitiesShown() {
        // Source only has 720p and 360p
        val formats = listOf(
            MediaFormat(
                formatId = "22",
                ext = "mp4",
                resolution = "1280x720",
                width = 1280,
                height = 720,
                isVideoOnly = false
            ),
            MediaFormat(
                formatId = "18",
                ext = "mp4",
                resolution = "640x360",
                width = 640,
                height = 360,
                isVideoOnly = false
            )
        )

        val normalized = FormatNormalizer.normalizeVideoQualities(formats)

        assertEquals(2, normalized.size)
        assertEquals("720p", normalized[0].label)
        assertEquals("360p", normalized[1].label)

        // Ensure 4K and 1080p are NOT generated
        assertFalse(normalized.any { it.label == "4K" })
        assertFalse(normalized.any { it.label == "1080p" })
        assertFalse(normalized.any { it.label == "1440p" })
    }

    @Test
    fun testNormalizeAudioQualities() {
        val formats = listOf(
            MediaFormat(
                formatId = "251",
                ext = "webm",
                resolution = "audio only",
                vcodec = "none",
                acodec = "opus",
                abr = 160f,
                filesize = 8_000_000L,
                isAudioOnly = true
            ),
            MediaFormat(
                formatId = "140",
                ext = "m4a",
                resolution = "audio only",
                vcodec = "none",
                acodec = "mp4a.40.2",
                abr = 128f,
                filesize = 6_000_000L,
                isAudioOnly = true
            ),
            MediaFormat(
                formatId = "250",
                ext = "webm",
                resolution = "audio only",
                vcodec = "none",
                acodec = "opus",
                abr = 70f,
                filesize = 3_000_000L,
                isAudioOnly = true
            )
        )

        val audioQualities = FormatNormalizer.normalizeAudioQualities(formats)

        assertEquals(3, audioQualities.size)
        assertEquals(160, audioQualities[0].bitrateKbps)
        assertEquals("webm", audioQualities[0].extension)

        assertEquals(128, audioQualities[1].bitrateKbps)
        assertEquals("m4a", audioQualities[1].extension)

        assertEquals(64, audioQualities[2].bitrateKbps)
    }
}
