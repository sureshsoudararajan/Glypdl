/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.engine

import java.io.File

/** Pure helpers that decide whether a downloaded yt-dlp binary may be installed. */
object YtDlpReleaseVerifier {

    private val TAG_RE = Regex("""^\d{4}\.\d{2}\.\d{2}(\.\d+)?$""")
    private const val MIN_SIZE = 1_000_000L

    fun isValidTag(tag: String?): Boolean = tag != null && TAG_RE.matches(tag)

    /**
     * Extracts the SHA-256 for [asset] from a `SHA2-256SUMS` file. Returns null unless there is
     * exactly one well-formed entry for the asset.
     */
    fun checksumFor(sums: String, asset: String): String? {
        val hits = sums.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size != 2) return@mapNotNull null
                val name = parts[1].removePrefix("*")
                if (name == asset) parts[0].lowercase() else null
            }
            .toList()
        val hash = hits.singleOrNull() ?: return null
        return if (hash.length == 64 && hash.all { it in '0'..'9' || it in 'a'..'f' }) hash else null
    }

    /** True when [file] has the expected hash, a plausible size and the zipapp/script header. */
    fun isAcceptable(file: File, expectedSha256: String): Boolean {
        if (!file.isFile || file.length() < MIN_SIZE) return false
        val head = file.inputStream().use { ByteArray(2).also { b -> it.read(b) } }
        if (!(head[0] == '#'.code.toByte() && head[1] == '!'.code.toByte())) return false
        return com.glypdl.android.service.update.UpdateVerifier.matches(file, expectedSha256)
    }
}
