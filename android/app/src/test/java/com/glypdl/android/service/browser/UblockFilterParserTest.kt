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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream

class UblockFilterParserTest {

    @Test
    fun `parse extracts domain rules, whitelist rules, and hosts format`() {
        val testFilterData = """
            ! Title: Test uBlock List
            ! Comments should be ignored
            [Adblock Plus 2.0]
            
            ||doubleclick.net^
            ||adservice.google.com^${'$'}third-party
            ||popads.net/
            
            ! Whitelist exceptions
            @@||youtube.com^
            @@||github.com^
            
            ! Hosts format
            0.0.0.0 malware-tracker.com
            127.0.0.1 adserver.org
            
            ! Path keywords
            /banner_ad/
            /pagead/
        """.trimIndent()

        val rules = UblockFilterParser.parse(ByteArrayInputStream(testFilterData.toByteArray(Charsets.UTF_8)))

        assertTrue(rules.blockedDomains.contains("doubleclick.net"))
        assertTrue(rules.blockedDomains.contains("adservice.google.com"))
        assertTrue(rules.blockedDomains.contains("popads.net"))
        assertTrue(rules.blockedDomains.contains("malware-tracker.com"))
        assertTrue(rules.blockedDomains.contains("adserver.org"))

        assertTrue(rules.whitelistedDomains.contains("youtube.com"))
        assertTrue(rules.whitelistedDomains.contains("github.com"))

        assertEquals(2, rules.pathKeywords.size)
        assertTrue(rules.pathKeywords.contains("/banner_ad/"))
        assertTrue(rules.pathKeywords.contains("/pagead/"))
    }
}
