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

import java.net.URI

/**
 * Lightweight in-app browser ad and tracker blocker.
 *
 * Inspects request URLs against known ad networks, tracking telemetry, and intrusive
 * pop-up domains. Blocks them from loading and prevents them from poisoning media detection.
 */
object AdBlocker {

    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "pagead2.googlesyndication.com",
        "adnxs.com",
        "scorecardresearch.com",
        "taboola.com",
        "outbrain.com",
        "popads.net",
        "popcash.net",
        "propellerads.com",
        "exoclick.com",
        "adsterra.com",
        "trafficjunky.net",
        "moatads.com",
        "amazon-adsystem.com",
        "criteo.com",
        "criteo.net",
        "rubiconproject.com",
        "openx.net",
        "pubmatic.com",
        "casalemedia.com",
        "smartadserver.com",
        "yieldmo.com",
        "applovin.com",
        "inmobi.com",
        "chartbeat.net",
        "hotjar.com",
        "clarity.ms",
        "adcolony.com",
        "vungle.com",
        "unityads.unity3d.com",
        "ironsrc.com"
    )

    private val AD_PATH_KEYWORDS = listOf(
        "/ads/",
        "/adserver/",
        "/ad_stream/",
        "/pagead/",
        "/advert/",
        "/ads.js",
        "/prebid"
    )

    /**
     * Checks if the given URL belongs to an ad network or tracking service.
     */
    fun isAd(url: String): Boolean {
        if (url.isBlank()) return false
        val uri = try { URI(url) } catch (_: Exception) { return false }
        val host = uri.host?.lowercase() ?: return false

        for (domain in AD_DOMAINS) {
            if (host == domain || host.endsWith(".$domain")) {
                return true
            }
        }

        val path = uri.path?.lowercase() ?: ""
        for (keyword in AD_PATH_KEYWORDS) {
            if (path.contains(keyword)) {
                return true
            }
        }

        return false
    }
}
