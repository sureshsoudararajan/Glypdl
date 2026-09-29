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

import java.net.URI

object MediaSiteDetector {
    
    private val exactDomains = setOf(
        "youtube.com", "youtu.be", "youtube-nocookie.com", "music.youtube.com", "m.youtube.com",
        "instagram.com", "instagr.am",
        "facebook.com", "fb.com", "fb.watch", "fbwat.ch",
        "tiktok.com", "vm.tiktok.com",
        "twitter.com", "x.com", "t.co",
        "reddit.com", "redd.it", "v.redd.it", "i.redd.it",
        "twitch.tv", "clips.twitch.tv",
        "vimeo.com", "player.vimeo.com",
        "dailymotion.com", "dai.ly",
        "soundcloud.com", "on.soundcloud.com",
        "bilibili.com", "b23.tv",
        "nicovideo.jp",
        "open.spotify.com",
        "pinterest.com", "pin.it",
        "threads.net",
        "snapchat.com", "story.snapchat.com",
        "linkedin.com",
        "flickr.com", "flic.kr",
        "streamable.com",
        "rumble.com",
        "bitchute.com",
        "odysee.com", "lbry.tv",
        "mixcloud.com",
        "audiomack.com",
        "vk.com", "vkvideo.ru",
        "ok.ru",
        "coub.com",
        "joinclubhouse.com"
    )

    private val domainSuffixes = setOf(
        ".bandcamp.com",
        ".tumblr.com"
    )

    private val pathPatterns = listOf(
        "/embed/", "/watch", "/video/", "/clip/", "/w/", "/videos/watch/"
    )

    /**
     * Returns true if the given URL belongs to a known media website
     * that should be handled by yt-dlp rather than aria2c.
     */
    fun isMediaSite(url: String): Boolean {
        if (url.isBlank()) return false
        
        try {
            val uri = URI(url)
            val host = uri.host?.lowercase()?.removePrefix("www.") ?: return false
            
            if (exactDomains.contains(host)) return true
            
            if (domainSuffixes.any { host.endsWith(it) }) return true
            
            val path = uri.path ?: ""
            if (pathPatterns.any { path.contains(it, ignoreCase = true) }) return true
            
        } catch (e: Exception) {
            return false
        }
        
        return false
    }
}
