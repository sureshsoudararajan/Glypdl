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

package com.glypdl.android.data.model

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Supported search engines for the in-app browser.
 */
data class SearchEngine(
    val id: String,
    val name: String,
    val queryUrlTemplate: String,
    val homeUrl: String,
    val badgeLabel: String,
    val badgeBgColor: Long,
    val badgeTextColor: Long = 0xFFFFFFFF
) {
    fun buildSearchUrl(query: String): String {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        return queryUrlTemplate.replace("{query}", encoded)
    }

    companion object {
        val GOOGLE = SearchEngine(
            id = "google",
            name = "Google",
            queryUrlTemplate = "https://www.google.com/search?q={query}",
            homeUrl = "https://www.google.com",
            badgeLabel = "G",
            badgeBgColor = 0xFF4285F4
        )
        val YAHOO = SearchEngine(
            id = "yahoo",
            name = "Yahoo",
            queryUrlTemplate = "https://search.yahoo.com/search?p={query}",
            homeUrl = "https://search.yahoo.com",
            badgeLabel = "Y!",
            badgeBgColor = 0xFF6001D2
        )
        val BING = SearchEngine(
            id = "bing",
            name = "Bing",
            queryUrlTemplate = "https://www.bing.com/search?q={query}",
            homeUrl = "https://www.bing.com",
            badgeLabel = "b",
            badgeBgColor = 0xFF008373
        )
        val DUCKDUCKGO = SearchEngine(
            id = "duckduckgo",
            name = "DuckDuckGo",
            queryUrlTemplate = "https://duckduckgo.com/?q={query}",
            homeUrl = "https://duckduckgo.com",
            badgeLabel = "DDG",
            badgeBgColor = 0xFFDE5833
        )
        val ECOSIA = SearchEngine(
            id = "ecosia",
            name = "Ecosia",
            queryUrlTemplate = "https://www.ecosia.org/search?q={query}",
            homeUrl = "https://www.ecosia.org",
            badgeLabel = "eco",
            badgeBgColor = 0xFF00897B
        )
        val QWANT = SearchEngine(
            id = "qwant",
            name = "Qwant",
            queryUrlTemplate = "https://www.qwant.com/?q={query}",
            homeUrl = "https://www.qwant.com",
            badgeLabel = "Q",
            badgeBgColor = 0xFF5C6BC0
        )
        val STARTPAGE = SearchEngine(
            id = "startpage",
            name = "Startpage private search",
            queryUrlTemplate = "https://www.startpage.com/sp/search?query={query}",
            homeUrl = "https://www.startpage.com",
            badgeLabel = "SP",
            badgeBgColor = 0xFF1B64F2
        )
        val AMAZON = SearchEngine(
            id = "amazon",
            name = "Amazon",
            queryUrlTemplate = "https://www.amazon.com/s?k={query}",
            homeUrl = "https://www.amazon.com",
            badgeLabel = "a",
            badgeBgColor = 0xFFFF9900
        )
        val EBAY = SearchEngine(
            id = "ebay",
            name = "eBay",
            queryUrlTemplate = "https://www.ebay.com/sch/i.html?_nkw={query}",
            homeUrl = "https://www.ebay.com",
            badgeLabel = "ebay",
            badgeBgColor = 0xFFE53238
        )
        val WIKIPEDIA = SearchEngine(
            id = "wikipedia",
            name = "Wikipedia",
            queryUrlTemplate = "https://en.wikipedia.org/wiki/Special:Search?search={query}",
            homeUrl = "https://en.wikipedia.org",
            badgeLabel = "W",
            badgeBgColor = 0xFF333333
        )

        val ALL: List<SearchEngine> = listOf(
            GOOGLE,
            YAHOO,
            BING,
            DUCKDUCKGO,
            ECOSIA,
            QWANT,
            STARTPAGE,
            AMAZON,
            EBAY,
            WIKIPEDIA
        )

        fun fromId(id: String?): SearchEngine {
            if (id.isNullOrBlank()) return DUCKDUCKGO
            val clean = id.trim().lowercase()
            return ALL.find { it.id == clean } ?: DUCKDUCKGO
        }
    }
}
