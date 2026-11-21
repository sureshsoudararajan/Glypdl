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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsParserTest {

    @Test
    fun parse_masterPlaylist_renditionsAndMediaTracks() {
        val masterManifest = """
            #EXTM3U
            #EXT-X-VERSION:4
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aacl",NAME="English",DEFAULT=YES,LANGUAGE="en",URI="audio/en/prog_index.m3u8"
            #EXT-X-MEDIA:TYPE=AUDIO,GROUP-ID="audio-aacl",NAME="Tamil",DEFAULT=NO,LANGUAGE="ta",URI="audio/ta/prog_index.m3u8"
            #EXT-X-MEDIA:TYPE=SUBTITLES,GROUP-ID="subs",NAME="English",DEFAULT=YES,LANGUAGE="en",URI="subs/en.vtt"
            #EXT-X-STREAM-INF:BANDWIDTH=5000000,AVERAGE-BANDWIDTH=4500000,RESOLUTION=1920x1080,FRAME-RATE=60.000,CODECS="avc1.64002a,mp4a.40.2",AUDIO="audio-aacl"
            1080p/prog_index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=2500000,RESOLUTION=1280x720,FRAME-RATE=30.000,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="audio-aacl"
            720p/prog_index.m3u8
            #EXT-X-STREAM-INF:BANDWIDTH=1000000,RESOLUTION=854x480,FRAME-RATE=30.000,CODECS="avc1.4d401f,mp4a.40.2",AUDIO="audio-aacl"
            480p/prog_index.m3u8
        """.trimIndent()

        val result = HlsParser.parse(masterManifest, "https://cdn.example.com/hls/master.m3u8")

        assertTrue(result.isMaster)
        assertFalse(result.isLive)
        assertFalse(result.isDrmProtected)
        assertEquals(3, result.variants.size)

        // 1080p rendition
        val v1080 = result.variants[0]
        assertEquals("1080p 60 fps", v1080.label)
        assertEquals(1920, v1080.width)
        assertEquals(1080, v1080.height)
        assertEquals(60, v1080.fps)
        assertEquals("https://cdn.example.com/hls/1080p/prog_index.m3u8", v1080.url)

        // 720p rendition
        val v720 = result.variants[1]
        assertEquals("720p", v720.label)
        assertEquals("https://cdn.example.com/hls/720p/prog_index.m3u8", v720.url)

        // 480p rendition
        val v480 = result.variants[2]
        assertEquals("480p", v480.label)
        assertEquals("https://cdn.example.com/hls/480p/prog_index.m3u8", v480.url)

        // Audio & Subtitle tracks
        assertEquals(2, result.audioTracks.size)
        assertEquals("English", result.audioTracks[0].name)
        assertEquals("en", result.audioTracks[0].language)
        assertEquals("Tamil", result.audioTracks[1].name)
        assertEquals("ta", result.audioTracks[1].language)
        assertEquals("https://cdn.example.com/hls/audio/ta/prog_index.m3u8", result.audioTracks[1].uri)

        assertEquals(1, result.subtitleTracks.size)
        assertEquals("subs/en.vtt", result.subtitleTracks[0].uri?.substringAfter("cdn.example.com/hls/"))
    }

    @Test
    fun parse_mediaPlaylist_durationAndLiveDetection() {
        // VOD media playlist with #EXT-X-ENDLIST
        val vodManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:9.009,
            segment1.ts
            #EXTINF:8.500,
            segment2.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val vodResult = HlsParser.parse(vodManifest, "https://example.com/stream.m3u8")
        assertFalse(vodResult.isMaster)
        assertFalse(vodResult.isLive)
        assertNotNull(vodResult.durationSeconds)
        assertEquals(17.509, vodResult.durationSeconds!!, 0.001)

        // Live media playlist lacking #EXT-X-ENDLIST
        val liveManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:10
            #EXTINF:10.0,
            live_100.ts
            #EXTINF:10.0,
            live_101.ts
        """.trimIndent()

        val liveResult = HlsParser.parse(liveManifest, "https://example.com/live.m3u8")
        assertFalse(liveResult.isMaster)
        assertTrue(liveResult.isLive)
    }

    @Test
    fun parse_drmProtectionDetection() {
        val widevineManifest = """
            #EXTM3U
            #EXT-X-VERSION:5
            #EXT-X-KEY:METHOD=SAMPLE-AES,URI="data:text/plain;base64,AAA",KEYFORMAT="com.widevine",KEYFORMATVERSIONS="1"
            #EXT-X-STREAM-INF:BANDWIDTH=2000000,RESOLUTION=1280x720
            720p.m3u8
        """.trimIndent()

        val widevineResult = HlsParser.parse(widevineManifest, "https://example.com/drm.m3u8")
        assertTrue(widevineResult.isDrmProtected)

        val plainAesManifest = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-KEY:METHOD=AES-128,URI="https://example.com/key.bin"
            #EXTINF:10.0,
            seg1.ts
            #EXT-X-ENDLIST
        """.trimIndent()

        val plainResult = HlsParser.parse(plainAesManifest, "https://example.com/plain_aes.m3u8")
        assertFalse(plainResult.isDrmProtected)
    }
}
