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

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.net.URI

/**
 * High-performance ad and tracker blocker powered by uBlock Origin & community filter rules.
 *
 * Supports domain/subdomain matching, whitelist/exception rules, and path keyword filters.
 */
object AdBlocker {

    private const val ASSET_FILE = "adblock/ublock_filters.txt"
    private const val CACHE_DIR = "adblock"
    private const val CACHE_FILE = "ublock_filters.txt"

    @Volatile
    private var isInitialized = false

    private val blockedDomains = hashSetOf<String>()
    private val whitelistedDomains = hashSetOf<String>()
    private val pathKeywords = arrayListOf<String>()

    private val DEFAULT_PATH_KEYWORDS = listOf(
        "/ads/", "/adserver/", "/ad_stream/", "/pagead/", "/advert/", "/ads.js", "/prebid"
    )

    val ESSENTIAL_PLATFORM_DOMAINS = setOf(
        // Google & YouTube & CDNs
        "google.com", "google.co.in", "google.co.uk", "google.ca", "google.de", "google.fr", "google.co.jp",
        "youtube.com", "youtu.be", "googlevideo.com", "googleusercontent.com",
        "googleapis.com", "gstatic.com", "ytimg.com", "ggpht.com",
        // Meta (Facebook, Instagram, WhatsApp)
        "facebook.com", "fbcdn.net", "fbsbx.com", "instagram.com", "cdninstagram.com",
        "threads.net", "whatsapp.com", "meta.com",
        // X / Twitter
        "twitter.com", "x.com", "twimg.com", "t.co",
        // Reddit
        "reddit.com", "redditmedia.com", "redditstatic.com", "redd.it",
        // TikTok
        "tiktok.com", "tiktokv.com", "byteoversea.com", "ibytedtos.com",
        // Media platforms & search engines
        "duckduckgo.com", "bing.com", "yahoo.com", "startpage.com", "ecosia.org", "qwant.com",
        "wikipedia.org", "wikimedia.org", "twitch.tv", "ttvnw.net", "jtvnw.net",
        "vimeo.com", "vimeocdn.com", "soundcloud.com", "sndcdn.com", "dailymotion.com", "dmcdn.net",
        "github.com", "githubassets.com", "githubusercontent.com", "amazon.com", "ebay.com"
    )

    // For backwards compatibility
    val CORE_SAFE_DOMAINS = ESSENTIAL_PLATFORM_DOMAINS

    // Fallback domains if asset loading is unavailable
    private val FALLBACK_DOMAINS = setOf(
        "doubleclick.net", "googlesyndication.com", "googleadservices.com",
        "adservice.google.com", "pagead2.googlesyndication.com", "adnxs.com",
        "scorecardresearch.com", "taboola.com", "outbrain.com", "popads.net",
        "popcash.net", "propellerads.com", "exoclick.com", "adsterra.com",
        "trafficjunky.net", "moatads.com", "amazon-adsystem.com", "criteo.com",
        "criteo.net", "rubiconproject.com", "openx.net", "pubmatic.com",
        "casalemedia.com", "smartadserver.com", "yieldmo.com", "applovin.com",
        "inmobi.com", "chartbeat.net", "hotjar.com", "clarity.ms",
        "adcolony.com", "vungle.com", "unityads.unity3d.com", "ironsrc.com"
    )

    init {
        // Pre-populate with fallback domains so static checks work immediately even before init(context)
        blockedDomains.addAll(FALLBACK_DOMAINS)
        pathKeywords.addAll(DEFAULT_PATH_KEYWORDS)
    }

    /**
     * Initializes the filter rules. Loads cached updated rules from filesDir if available;
     * otherwise loads the bundled community asset. Safe to call multiple times.
     */
    fun init(context: Context) {
        if (isInitialized) return
        synchronized(this) {
            if (isInitialized) return
            loadRules(context)
            isInitialized = true
        }
    }

    fun getRuleCount(): Int = blockedDomains.size

    fun loadRules(context: Context) {
        try {
            val localCache = File(context.filesDir, "$CACHE_DIR/$CACHE_FILE")
            // Invalidate stale local cache if it contains broken platform or CDN blocking
            if (localCache.exists()) {
                val cachedContent = try { localCache.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
                if (cachedContent.contains("googlevideo.com") ||
                    cachedContent.contains("static.xx.fbcdn.net") ||
                    cachedContent.contains("google.com^") ||
                    cachedContent.contains("googleusercontent.com")
                ) {
                    localCache.delete()
                }
            }
            val stream = if (localCache.exists() && localCache.length() > 0) {
                FileInputStream(localCache)
            } else {
                context.assets.open(ASSET_FILE)
            }

            val rules = UblockFilterParser.parse(stream)
            synchronized(this) {
                blockedDomains.clear()
                blockedDomains.addAll(rules.blockedDomains)
                whitelistedDomains.clear()
                whitelistedDomains.addAll(rules.whitelistedDomains)
                pathKeywords.clear()
                pathKeywords.addAll(rules.pathKeywords)
            }
        } catch (_: Exception) {
            synchronized(this) {
                blockedDomains.addAll(FALLBACK_DOMAINS)
                pathKeywords.addAll(DEFAULT_PATH_KEYWORDS)
            }
        }
    }

    /**
     * Reloads rules from a newly downloaded file.
     */
    fun reloadRules(file: File) {
        if (!file.exists()) return
        try {
            val rules = UblockFilterParser.parse(FileInputStream(file))
            synchronized(this) {
                blockedDomains.clear()
                blockedDomains.addAll(rules.blockedDomains)
                whitelistedDomains.clear()
                whitelistedDomains.addAll(rules.whitelistedDomains)
                pathKeywords.clear()
                pathKeywords.addAll(rules.pathKeywords)
            }
        } catch (_: Exception) {}
    }

    /**
     * Checks if the given URL is an ad or tracker according to uBlock Origin community rules.
     */
    fun isAd(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false

        // 1. Core safe domains should never be blocked unless explicit ad subdomain
        if (isSafeDomain(host)) {
            if (!isExplicitAdSubdomain(host)) {
                return false
            }
        }

        // 2. Check whitelist / exception rules first
        if (isHostWhitelisted(host)) return false

        // 3. Check domain / subdomain blocklist
        if (isHostBlocked(host)) return true

        // 4. Check path keyword rules
        val path = uri.path?.lowercase() ?: ""
        if (path.isNotEmpty()) {
            for (kw in pathKeywords) {
                if (path.contains(kw)) return true
            }
        }

        return false
    }

    private fun isSafeDomain(host: String): Boolean {
        val lower = host.lowercase()
        for (domain in ESSENTIAL_PLATFORM_DOMAINS) {
            if (lower == domain || lower.endsWith(".$domain")) {
                return true
            }
        }
        return false
    }

    private fun isExplicitAdSubdomain(host: String): Boolean {
        val lower = host.lowercase()
        return lower.startsWith("adservice.") ||
                lower.startsWith("pagead") ||
                lower.startsWith("googleadservices") ||
                lower.startsWith("ads.") ||
                lower.startsWith("ad.") ||
                lower.startsWith("mail-ads.") ||
                lower.startsWith("ogads-") ||
                lower.startsWith("partnerad.") ||
                lower.startsWith("doubleclick")
    }

    private fun isHostWhitelisted(host: String): Boolean {
        if (whitelistedDomains.contains(host)) return true
        var dotIdx = host.indexOf('.')
        while (dotIdx != -1) {
            val parent = host.substring(dotIdx + 1)
            if (whitelistedDomains.contains(parent)) return true
            dotIdx = host.indexOf('.', dotIdx + 1)
        }
        return false
    }

    private fun isHostBlocked(host: String): Boolean {
        if (blockedDomains.contains(host)) return true
        var dotIdx = host.indexOf('.')
        while (dotIdx != -1) {
            val parent = host.substring(dotIdx + 1)
            if (blockedDomains.contains(parent)) return true
            dotIdx = host.indexOf('.', dotIdx + 1)
        }
        return false
    }
}
