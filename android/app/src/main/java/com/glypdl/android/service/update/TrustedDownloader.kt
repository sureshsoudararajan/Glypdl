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
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal HTTPS downloader that only talks to GitHub release hosts, re-validating every redirect,
 * and enforces a size cap. Integrity checks (SHA-256) are done by the caller.
 */
object TrustedDownloader {

    fun download(url: String, dest: File, maxBytes: Long, userAgent: String = "Glypdl-Android") {
        require(UpdateVerifier.isTrustedDownloadUrl(url)) { "Untrusted download URL" }
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = false
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", userAgent)
            }
            try {
                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw java.io.IOException("Redirect without Location header")
                    current = URL(URL(current), location).toString()
                    if (!UpdateVerifier.isTrustedDownloadUrl(current)) {
                        throw java.io.IOException("Refusing redirect to untrusted host")
                    }
                    if (++redirects > 10) throw java.io.IOException("Too many redirects")
                    continue
                }
                if (status !in 200..299) throw java.io.IOException("HTTP $status")
                if (conn.contentLengthLong > maxBytes) throw java.io.IOException("Download too large")

                var total = 0L
                conn.inputStream.use { input ->
                    FileOutputStream(dest).use { out ->
                        val buf = ByteArray(16384)
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > maxBytes) throw java.io.IOException("Download too large")
                            out.write(buf, 0, n)
                        }
                    }
                }
                return
            } finally {
                conn.disconnect()
            }
        }
    }
}
