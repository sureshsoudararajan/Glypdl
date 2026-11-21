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

class DashParserTest {

    @Test
    fun parse_mpdRepresentations_videoAndAudio() {
        val mpdXml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011"
                 type="static"
                 mediaPresentationDuration="PT1H30M0S">
              <BaseURL>https://cdn.example.com/dash/</BaseURL>
              <Period id="1">
                <AdaptationSet contentType="video" mimeType="video/mp4" codecs="avc1.640028">
                  <Representation id="1080p" bandwidth="4500000" width="1920" height="1080" frameRate="30">
                    <BaseURL>video_1080p.mp4</BaseURL>
                  </Representation>
                  <Representation id="720p" bandwidth="2200000" width="1280" height="720" frameRate="30">
                    <BaseURL>video_720p.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
                <AdaptationSet contentType="audio" mimeType="audio/mp4" lang="en">
                  <Representation id="audio_en" bandwidth="128000" codecs="mp4a.40.2">
                    <BaseURL>audio_en.mp4</BaseURL>
                  </Representation>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val result = DashParser.parse(mpdXml, "https://example.com/manifest.mpd")

        assertFalse(result.isLive)
        assertFalse(result.isDrmProtected)
        assertNotNull(result.durationSeconds)
        assertEquals(5400.0, result.durationSeconds!!, 0.1)

        assertEquals(2, result.videoVariants.size)
        val v1080 = result.videoVariants[0]
        assertEquals("1080p", v1080.label)
        assertEquals(1920, v1080.width)
        assertEquals(1080, v1080.height)
        assertEquals("https://cdn.example.com/dash/video_1080p.mp4", v1080.url)
        assertNotNull(v1080.estimatedSizeBytes)

        val v720 = result.videoVariants[1]
        assertEquals("720p", v720.label)
        assertEquals(1280, v720.width)

        assertEquals(1, result.audioVariants.size)
        val a1 = result.audioVariants[0]
        assertTrue(a1.label.contains("Audio 128 kbps"))
        assertEquals("en", a1.language)
        assertEquals("https://cdn.example.com/dash/audio_en.mp4", a1.url)
    }

    @Test
    fun parse_drmProtectionInAdaptationSet() {
        val drmMpd = """
            <?xml version="1.0" encoding="UTF-8"?>
            <MPD xmlns="urn:mpeg:dash:schema:mpd:2011"
                 type="static"
                 mediaPresentationDuration="PT10M">
              <Period id="1">
                <AdaptationSet contentType="video" mimeType="video/mp4">
                  <ContentProtection schemeIdUri="urn:uuid:edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"/>
                  <Representation id="video_1" bandwidth="2000000" width="1280" height="720"/>
                </AdaptationSet>
              </Period>
            </MPD>
        """.trimIndent()

        val result = DashParser.parse(drmMpd, "https://example.com/drm.mpd")
        assertTrue(result.isDrmProtected)
    }

    @Test
    fun parseIsoDuration_variousFormats() {
        assertEquals(3600.0, DashParser.parseIsoDuration("PT1H")!!, 0.01)
        assertEquals(60.0, DashParser.parseIsoDuration("PT1M")!!, 0.01)
        assertEquals(45.5, DashParser.parseIsoDuration("PT45.5S")!!, 0.01)
        assertEquals(3665.0, DashParser.parseIsoDuration("PT1H1M5S")!!, 0.01)
    }
}
