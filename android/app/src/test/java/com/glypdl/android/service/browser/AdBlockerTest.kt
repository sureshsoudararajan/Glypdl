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

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdBlockerTest {

    @Test
    fun `isAd returns true for known ad domains`() {
        assertTrue(AdBlocker.isAd("https://pagead2.googlesyndication.com/pagead/js/adsbygoogle.js"))
        assertTrue(AdBlocker.isAd("https://adservice.google.com/adsid/google/ui"))
        assertTrue(AdBlocker.isAd("https://securepubads.g.doubleclick.net/gampad/ads"))
        assertTrue(AdBlocker.isAd("https://c.amazon-adsystem.com/aax2/apstag.js"))
        assertTrue(AdBlocker.isAd("https://sb.scorecardresearch.com/beacon.js"))
        assertTrue(AdBlocker.isAd("https://static.criteo.net/js/ld/ld.js"))
        assertTrue(AdBlocker.isAd("https://delivery.popads.net/serve.js"))
        assertTrue(AdBlocker.isAd("https://tr.outbrain.com/traffic/script.js"))
    }

    @Test
    fun `isAd returns true for known ad path keywords`() {
        assertTrue(AdBlocker.isAd("https://example.com/ads/banner.gif"))
        assertTrue(AdBlocker.isAd("https://media.server.org/adserver/vast.xml"))
        assertTrue(AdBlocker.isAd("https://cdn.site.com/static/prebid.js"))
    }

    @Test
    fun `isAd returns false for normal content URLs`() {
        assertFalse(AdBlocker.isAd("https://duckduckgo.com"))
        assertFalse(AdBlocker.isAd("https://google.com"))
        assertFalse(AdBlocker.isAd("https://www.google.com/search?q=test"))
        assertFalse(AdBlocker.isAd("https://bing.com"))
        assertFalse(AdBlocker.isAd("https://m.youtube.com/watch?v=123"))
        assertFalse(AdBlocker.isAd("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertFalse(AdBlocker.isAd("https://yt3.ggpht.com/ytc/abc=s68"))
        assertFalse(AdBlocker.isAd("https://rr1---sn-4g5edn6r.googlevideo.com/videoplayback?expire=123"))
        assertFalse(AdBlocker.isAd("https://youtubei.googleapis.com/youtubei/v1/browse"))
        assertFalse(AdBlocker.isAd("https://static.xx.fbcdn.net/rsrc.php/v3/yP/r/abc.js"))
        assertFalse(AdBlocker.isAd("https://scontent.cdninstagram.com/v/t51.2885-15/photo.jpg"))
        assertFalse(AdBlocker.isAd("https://pbs.twimg.com/media/abc.jpg"))
        assertFalse(AdBlocker.isAd("https://preview.redd.it/post.jpg"))
        assertFalse(AdBlocker.isAd("https://v16-webapp-prime.tiktok.com/video.mp4"))
        assertFalse(AdBlocker.isAd("https://en.wikipedia.org/wiki/Main_Page"))
        assertFalse(AdBlocker.isAd("https://github.com/sureshsoudararajan/Glypdl"))
        assertFalse(AdBlocker.isAd("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"))
        assertFalse(AdBlocker.isAd("https://video.example.com/stream/master.m3u8"))
    }

    @Test
    fun `isAd handles empty or invalid URLs gracefully`() {
        assertFalse(AdBlocker.isAd(""))
        assertFalse(AdBlocker.isAd("   "))
        assertFalse(AdBlocker.isAd("not a valid url"))
    }
}
