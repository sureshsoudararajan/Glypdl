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

import com.glypdl.android.data.model.MediaKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {

    @Test
    fun classify_videoExtensions() {
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, MediaClassifier.classify("https://example.com/video.mp4"))
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, MediaClassifier.classify("https://example.com/path/clip.webm?key=123"))
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, MediaClassifier.classify("https://example.com/film.mkv"))
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, MediaClassifier.classify("https://example.com/movie.mov"))
    }

    @Test
    fun classify_audioExtensions() {
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, MediaClassifier.classify("https://example.com/song.mp3"))
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, MediaClassifier.classify("https://example.com/track.m4a?auth=abc"))
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, MediaClassifier.classify("https://example.com/audio.aac"))
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, MediaClassifier.classify("https://example.com/podcast.opus"))
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, MediaClassifier.classify("https://example.com/music.flac"))
    }

    @Test
    fun classify_streamingManifests() {
        assertEquals(MediaKind.HLS, MediaClassifier.classify("https://example.com/live/master.m3u8"))
        assertEquals(MediaKind.HLS, MediaClassifier.classify("https://example.com/stream/index.m3u"))
        assertEquals(MediaKind.DASH, MediaClassifier.classify("https://example.com/manifest.mpd"))
    }

    @Test
    fun classify_mimeTypes() {
        assertEquals(MediaKind.HLS, MediaClassifier.classify("https://example.com/stream?id=1", "application/x-mpegurl"))
        assertEquals(MediaKind.HLS, MediaClassifier.classify("https://example.com/stream?id=2", "application/vnd.apple.mpegurl"))
        assertEquals(MediaKind.DASH, MediaClassifier.classify("https://example.com/stream?id=3", "application/dash+xml"))
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, MediaClassifier.classify("https://example.com/media/file", "video/mp4; codecs=\"avc1.4d401f\""))
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, MediaClassifier.classify("https://example.com/media/sound", "audio/mpeg"))
    }

    @Test
    fun classify_queryHints() {
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, MediaClassifier.classify("https://example.com/videoplayback?mime=video%2Fmp4&id=xyz"))
        assertEquals(MediaKind.HLS, MediaClassifier.classify("https://example.com/get_stream?format=m3u8"))
        assertEquals(MediaKind.DASH, MediaClassifier.classify("https://example.com/get_stream?format=mpd"))
    }

    @Test
    fun classify_rejectsSegmentsAndChunks() {
        // .ts segments
        assertNull(MediaClassifier.classify("https://example.com/hls/segment1.ts"))
        // .m4s fragments
        assertNull(MediaClassifier.classify("https://example.com/dash/chunk_1.m4s"))
        // Chunk naming conventions
        assertNull(MediaClassifier.classify("https://example.com/video/seg-10.mp4"))
        assertNull(MediaClassifier.classify("https://example.com/video/chunk_5.m4a"))
        assertNull(MediaClassifier.classify("https://example.com/video/init.mp4"))
        assertTrue(MediaClassifier.isSegmentOrChunk("https://example.com/stream/segment-1.ts"))
        assertTrue(MediaClassifier.isSegmentOrChunk("https://example.com/stream/init-v1.mp4"))
    }

    @Test
    fun classify_rejectsStaticAssetsAndAds() {
        assertNull(MediaClassifier.classify("https://example.com/image.png"))
        assertNull(MediaClassifier.classify("https://example.com/style.css"))
        assertNull(MediaClassifier.classify("https://example.com/script.js"))
        assertNull(MediaClassifier.classify("https://google-analytics.com/analytics.js"))
        assertNull(MediaClassifier.classify("https://stats.doubleclick.net/video.mp4"))
        assertTrue(MediaClassifier.isAdOrTrackingUrl("https://pagead2.googlesyndication.com/pagead/show_ads.js"))
    }
}
