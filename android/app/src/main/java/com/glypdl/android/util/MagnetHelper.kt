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

package com.glypdl.android.util

import android.net.Uri
import java.net.URLDecoder
import java.net.URLEncoder

object MagnetHelper {

    /**
     * Tier-1 resilient public BitTorrent trackers with high peer density.
     */
    val BEST_TRACKERS = listOf(
        "udp://tracker.opentrackr.org:1337/announce",
        "udp://open.stealth.si:80/announce",
        "udp://tracker.torrent.eu.org:451/announce",
        "udp://tracker.bittor.pw:1337/announce",
        "udp://public.popcorn-tracker.org:6969/announce",
        "udp://explodie.org:6969/announce",
        "http://tracker.openbittorrent.com:80/announce"
    )

    /**
     * Enriches a magnet link by appending popular reliable public trackers
     * if they are not already included.
     */
    fun injectTrackers(magnetUri: String): String {
        val trimmed = magnetUri.trim()
        if (!trimmed.startsWith("magnet:?", ignoreCase = true)) return trimmed

        val existingLower = trimmed.lowercase()
        val sb = StringBuilder(trimmed)

        for (tracker in BEST_TRACKERS) {
            val encodedTracker = URLEncoder.encode(tracker, "UTF-8")
            if (!existingLower.contains(tracker.lowercase()) && !existingLower.contains(encodedTracker.lowercase())) {
                sb.append("&tr=").append(encodedTracker)
            }
        }

        return sb.toString()
    }

    /**
     * Extracts display name (dn parameter) from magnet URI.
     */
    fun extractDisplayName(magnetUri: String): String? {
        return try {
            val uri = Uri.parse(magnetUri)
            val dn = uri.getQueryParameter("dn")
            if (!dn.isNullOrBlank()) {
                URLDecoder.decode(dn, "UTF-8")
            } else null
        } catch (e: Exception) {
            null
        }
    }
}
