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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaSiteDetectorTest {

    @Test
    fun detectsYouTubeUrls() {
        assertTrue(MediaSiteDetector.isMediaSite("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(MediaSiteDetector.isMediaSite("https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(MediaSiteDetector.isMediaSite("https://music.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertTrue(MediaSiteDetector.isMediaSite("https://m.youtube.com/watch?v=dQw4w9WgXcQ"))
    }

    @Test
    fun detectsSocialMediaUrls() {
        assertTrue(MediaSiteDetector.isMediaSite("https://www.instagram.com/reel/C12345/"))
        assertTrue(MediaSiteDetector.isMediaSite("https://facebook.com/watch/?v=12345"))
        assertTrue(MediaSiteDetector.isMediaSite("https://fb.watch/abcdef/"))
        assertTrue(MediaSiteDetector.isMediaSite("https://www.tiktok.com/@user/video/123456789"))
        assertTrue(MediaSiteDetector.isMediaSite("https://x.com/user/status/123456"))
        assertTrue(MediaSiteDetector.isMediaSite("https://twitter.com/user/status/123456"))
        assertTrue(MediaSiteDetector.isMediaSite("https://reddit.com/r/videos/comments/123456/title/"))
        assertTrue(MediaSiteDetector.isMediaSite("https://v.redd.it/123456"))
    }

    @Test
    fun detectsStreamingAndMusicUrls() {
        assertTrue(MediaSiteDetector.isMediaSite("https://soundcloud.com/artist/track"))
        assertTrue(MediaSiteDetector.isMediaSite("https://vimeo.com/12345678"))
        assertTrue(MediaSiteDetector.isMediaSite("https://www.twitch.tv/videos/123456"))
        assertTrue(MediaSiteDetector.isMediaSite("https://artist.bandcamp.com/track/song"))
        assertTrue(MediaSiteDetector.isMediaSite("https://www.dailymotion.com/video/x12345"))
        assertTrue(MediaSiteDetector.isMediaSite("https://bilibili.com/video/BV123456"))
    }

    @Test
    fun directFilesAreNotMediaSites() {
        assertFalse(MediaSiteDetector.isMediaSite("https://releases.ubuntu.com/24.04/ubuntu.iso"))
        assertFalse(MediaSiteDetector.isMediaSite("https://example.com/downloads/package.zip"))
        assertFalse(MediaSiteDetector.isMediaSite("https://cdn.example.org/files/app.apk"))
        assertFalse(MediaSiteDetector.isMediaSite("https://mywebsite.org/document.pdf"))
    }

    @Test
    fun handlesBlankAndMalformedInputs() {
        assertFalse(MediaSiteDetector.isMediaSite(""))
        assertFalse(MediaSiteDetector.isMediaSite("not-a-url"))
        assertFalse(MediaSiteDetector.isMediaSite("ftp://random.site.com/file"))
    }
}
