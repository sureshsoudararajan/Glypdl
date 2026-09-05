/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale

class YtDlpVersionTest {

    private fun isNewerVersion(candidate: String, current: String): Boolean {
        if (candidate.isBlank()) return false
        if (current.isBlank() || current.equals("Unknown", ignoreCase = true)) return true

        return try {
            val candParts = candidate.split(".").mapNotNull { it.toIntOrNull() }
            val currParts = current.split(".").mapNotNull { it.toIntOrNull() }

            val minLength = minOf(candParts.size, currParts.size)
            for (i in 0 until minLength) {
                if (candParts[i] > currParts[i]) return true
                if (candParts[i] < currParts[i]) return false
            }
            candParts.size > currParts.size
        } catch (e: Exception) {
            candidate != current
        }
    }

    private fun isVersionOutdated(version: String, referenceTimeMillis: Long, thresholdDays: Long = 90): Boolean {
        if (version.isBlank() || version.equals("Unknown", ignoreCase = true)) return false
        return try {
            val parts = version.split(".")
            if (parts.size >= 3) {
                val dateStr = "${parts[0]}.${parts[1]}.${parts[2]}"
                val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.US)
                val releaseDate = sdf.parse(dateStr)
                if (releaseDate != null) {
                    val ageMillis = referenceTimeMillis - releaseDate.time
                    val ageDays = ageMillis / (1000L * 60 * 60 * 24)
                    return ageDays > thresholdDays
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    @Test
    fun testVersionComparison() {
        // 2026.08.19 is newer than 2025.11.12
        assertTrue(isNewerVersion("2026.08.19", "2025.11.12"))
        assertFalse(isNewerVersion("2025.11.12", "2026.08.19"))

        // Same version is not newer
        assertFalse(isNewerVersion("2026.08.19", "2026.08.19"))

        // Patch version comparison
        assertTrue(isNewerVersion("2026.08.19.1", "2026.08.19"))
        assertFalse(isNewerVersion("2026.08.19", "2026.08.19.1"))

        // Candidate newer than Unknown
        assertTrue(isNewerVersion("2026.08.19", "Unknown"))
        assertTrue(isNewerVersion("2026.08.19", ""))
    }

    @Test
    fun testVersionOlderThan90Days() {
        val sdf = SimpleDateFormat("yyyy.MM.dd", Locale.US)
        // Set reference date to September 5, 2026
        val sept2026 = sdf.parse("2026.09.05")!!.time

        // 2025.11.12 is ~297 days old relative to Sep 2026 -> Outdated
        assertTrue(isVersionOutdated("2025.11.12", sept2026, thresholdDays = 90))

        // 2026.08.19 is ~17 days old relative to Sep 2026 -> Not outdated
        assertFalse(isVersionOutdated("2026.08.19", sept2026, thresholdDays = 90))

        // Exactly 91 days old -> Outdated
        val ninetyOneDaysAgo = sept2026 - (91L * 24 * 60 * 60 * 1000)
        val date91Str = sdf.format(ninetyOneDaysAgo)
        assertTrue(isVersionOutdated(date91Str, sept2026, thresholdDays = 90))

        // 30 days old -> Not outdated
        val thirtyDaysAgo = sept2026 - (30L * 24 * 60 * 60 * 1000)
        val date30Str = sdf.format(thirtyDaysAgo)
        assertFalse(isVersionOutdated(date30Str, sept2026, thresholdDays = 90))
    }
}
