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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads and updates community filter rules directly from uBlock Origin's official GitHub repository.
 */
object UblockListUpdater {

    private const val CACHE_DIR = "adblock"
    private const val CACHE_FILE = "ublock_filters.txt"

    private val COMMUNITY_SOURCES = listOf(
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/filters.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/badware.txt",
        "https://raw.githubusercontent.com/uBlockOrigin/uAssets/master/filters/privacy.txt",
        "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"
    )

    suspend fun update(context: Context): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val dir = File(context.filesDir, CACHE_DIR)
            if (!dir.exists()) dir.mkdirs()
            val tempFile = File(dir, "ublock_filters.tmp")
            val targetFile = File(dir, CACHE_FILE)

            val out = FileOutputStream(tempFile)
            var totalLines = 0

            out.bufferedWriter().use { writer ->
                writer.write("! Title: uBlock Origin & Community Filter Rules (Updated)\n")
                writer.write("! Updated at: ${System.currentTimeMillis()}\n\n")

                for (sourceUrl in COMMUNITY_SOURCES) {
                    try {
                        val conn = URL(sourceUrl).openConnection() as HttpURLConnection
                        conn.connectTimeout = 10_000
                        conn.readTimeout = 15_000
                        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Glypdl Android)")
                        if (conn.responseCode == 200) {
                            conn.inputStream.bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    val trimmed = line.trim()
                                    if (trimmed.startsWith("||") || trimmed.startsWith("@@||") ||
                                        trimmed.startsWith("127.0.0.1 ") || trimmed.startsWith("0.0.0.0 ")
                                    ) {
                                        writer.write(trimmed)
                                        writer.newLine()
                                        totalLines++
                                    }
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (totalLines > 100) {
                if (targetFile.exists()) targetFile.delete()
                tempFile.renameTo(targetFile)
                AdBlocker.reloadRules(targetFile)
                Result.success(AdBlocker.getRuleCount())
            } else {
                tempFile.delete()
                Result.failure(Exception("Failed to download sufficient filter rules"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
