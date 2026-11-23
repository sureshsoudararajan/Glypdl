/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.update

import java.io.File
import java.net.URI
import java.security.MessageDigest

/**
 * Pure helpers used to make sure an update downloaded from the network is the exact file that
 * the release published, before it is installed or executed.
 */
object UpdateVerifier {

    private val TRUSTED_HOSTS = setOf(
        "github.com",
        "objects.githubusercontent.com",
        "release-assets.githubusercontent.com"
    )

    /** Only https URLs on GitHub's release hosts are followed (initial URL and every redirect). */
    fun isTrustedDownloadUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return try {
            val uri = URI(url)
            uri.scheme.equals("https", ignoreCase = true) &&
                uri.userInfo == null &&
                (uri.port == -1 || uri.port == 443) &&
                uri.host?.lowercase() in TRUSTED_HOSTS
        } catch (_: Exception) {
            false
        }
    }

    /** Parses GitHub's asset `digest` field ("sha256:<64 hex>") into lowercase hex, or null. */
    fun parseSha256Digest(digest: String?): String? {
        val value = digest?.trim()?.lowercase() ?: return null
        val hex = value.removePrefix("sha256:")
        return if (hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }) hex else null
    }

    fun sha256Of(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(65536)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun matches(file: File, expectedSha256: String): Boolean =
        sha256Of(file).equals(expectedSha256, ignoreCase = true)

    /** Strips any path components from a server-supplied file name. */
    fun safeFileName(name: String): String {
        val base = name.substringAfterLast('/').substringAfterLast('\\').trim()
        return if (base.isEmpty() || base == "." || base == "..") "glypdl-update.apk" else base
    }
}
