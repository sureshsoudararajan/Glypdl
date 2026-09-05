/*
 * Copyright (c) 2024 Glypdl
 *
 * This file is part of Glypdl.
 *
 * Glypdl is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Glypdl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Glypdl.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android.util

import java.io.File

object FileNameSanitizer {
    fun sanitize(filename: String): String {
        var clean = filename.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F-\\x9F]"), "_")
        clean = clean.trim(' ', '.')
        if (clean.length > 200) {
            val ext = clean.substringAfterLast('.', "")
            val name = clean.substringBeforeLast('.')
            val newName = name.take(200 - ext.length - 1)
            clean = if (ext.isNotEmpty()) "$newName.$ext" else newName
        }
        return clean
    }

    fun safeFsTitle(title: String, maxBytes: Int = 100): String {
        var clean = title.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F-\\x9F]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim(' ', '.')

        if (clean.isBlank()) return "media"

        val bytes = clean.toByteArray(Charsets.UTF_8)
        if (bytes.size <= maxBytes) return clean

        // Truncate UTF-8 safely without cutting surrogate pairs or multi-byte code units
        var end = minOf(clean.length, maxBytes)
        while (end > 0) {
            val candidate = clean.substring(0, end)
            if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) {
                return candidate.trimEnd(' ', '.')
            }
            end--
        }
        return "media"
    }

    fun generateFilename(title: String, ext: String, id: String?): String {
        val safeTitle = safeFsTitle(title, 80)
        val cleanExt = ext.removePrefix(".").ifBlank { "mp4" }
        return if (!id.isNullOrBlank()) {
            "${safeTitle}_$id.$cleanExt"
        } else {
            "$safeTitle.$cleanExt"
        }
    }

    fun handleDuplicate(basePath: String, filename: String): String {
        val file = File(basePath, filename)
        if (!file.exists()) return filename
        
        val name = filename.substringBeforeLast('.')
        val ext = filename.substringAfterLast('.', "")
        val dotExt = if (ext.isNotEmpty()) ".$ext" else ""
        
        var i = 1
        var newFilename = "$name ($i)$dotExt"
        while (File(basePath, newFilename).exists()) {
            i++
            newFilename = "$name ($i)$dotExt"
        }
        
        return newFilename
    }
}
