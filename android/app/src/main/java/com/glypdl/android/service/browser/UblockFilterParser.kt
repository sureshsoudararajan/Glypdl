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

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

data class UblockFilterRules(
    val blockedDomains: Set<String>,
    val whitelistedDomains: Set<String>,
    val pathKeywords: List<String>
)

/**
 * Parser for uBlock Origin & Adblock Plus network filter lists.
 * Extracts domain rules, whitelist/exception rules, and path keywords.
 */
object UblockFilterParser {

    private fun isEssentialPlatformDomain(domain: String): Boolean {
        val lower = domain.lowercase()
        for (p in AdBlocker.ESSENTIAL_PLATFORM_DOMAINS) {
            if (lower == p || lower.endsWith(".$p")) {
                if (lower.startsWith("adservice.") ||
                    lower.startsWith("pagead") ||
                    lower.startsWith("googleadservices") ||
                    lower.startsWith("ads.") ||
                    lower.startsWith("mail-ads.") ||
                    lower.startsWith("partnerad.")
                ) {
                    return false
                }
                return true
            }
        }
        return false
    }

    fun parse(inputStream: InputStream): UblockFilterRules {
        val blocked = hashSetOf<String>()
        val whitelisted = hashSetOf<String>()
        val pathKeywords = arrayListOf<String>()

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val trimmed = line?.trim() ?: continue
                if (trimmed.isEmpty() || trimmed.startsWith('!') || trimmed.startsWith('[')) {
                    continue
                }

                // Exception / Whitelist rule (e.g., @@||youtube.com^)
                if (trimmed.startsWith("@@||")) {
                    val raw = trimmed.removePrefix("@@||")
                    val domain = raw.substringBefore('^').substringBefore('$').substringBefore('/').trim().lowercase()
                    if (domain.isNotEmpty()) {
                        whitelisted.add(domain)
                    }
                    continue
                }

                // Standard domain block rule (e.g., ||doubleclick.net^ or ||adservice.google.com^$third-party)
                if (trimmed.startsWith("||")) {
                    val raw = trimmed.removePrefix("||")
                    val domain = raw.substringBefore('^').substringBefore('$').substringBefore('/').trim().lowercase()
                    if (domain.isNotEmpty() && !isEssentialPlatformDomain(domain)) {
                        blocked.add(domain)
                    }
                    continue
                }

                // Hosts format rule (0.0.0.0 or 127.0.0.1)
                if (trimmed.startsWith("0.0.0.0 ") || trimmed.startsWith("127.0.0.1 ")) {
                    val domain = trimmed.split("\\s+".toRegex()).getOrNull(1)?.trim()?.lowercase() ?: ""
                    if (domain.isNotEmpty() && domain != "localhost") {
                        blocked.add(domain)
                    }
                    continue
                }

                // Path keyword rule (e.g., /ads/ or /pagead/)
                if (trimmed.startsWith("/")) {
                    pathKeywords.add(trimmed.lowercase())
                }
            }
        }

        return UblockFilterRules(
            blockedDomains = blocked - whitelisted,
            whitelistedDomains = whitelisted,
            pathKeywords = pathKeywords
        )
    }
}
