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

import android.content.Intent

object IntentHandler {
    fun extractUrlFromIntent(intent: Intent?): String? {
        if (intent == null) return null
        
        when (intent.action) {
            Intent.ACTION_SEND -> {
                // Check if a file (e.g. .torrent) was shared via EXTRA_STREAM
                val streamUri = try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(Intent.EXTRA_STREAM, android.net.Uri::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(Intent.EXTRA_STREAM)
                    }
                } catch (e: Throwable) {
                    null
                }

                if (streamUri != null) {
                    return streamUri.toString()
                }

                if ("text/plain" == intent.type || intent.type?.startsWith("text/") == true) {
                    val text = try {
                        intent.getStringExtra(Intent.EXTRA_TEXT)
                    } catch (e: Throwable) {
                        null
                    } ?: try {
                        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                    } catch (e: Throwable) {
                        null
                    }
                    if (!text.isNullOrBlank()) {
                        val extracted = UrlValidator.extractUrlFromText(text)
                        if (extracted != null) {
                            val url = if (!extracted.startsWith("http://", ignoreCase = true) && 
                                !extracted.startsWith("https://", ignoreCase = true) &&
                                !UrlValidator.isMagnetLink(extracted)) {
                                "https://$extracted"
                            } else {
                                extracted
                            }
                            if (UrlValidator.isValidUrl(url)) {
                                return url
                            }
                        }
                    }
                }
            }
            Intent.ACTION_VIEW -> {
                val data = (try { intent.dataString } catch (e: Throwable) { null })
                    ?: (try { intent.data?.toString() } catch (e: Throwable) { null })
                if (data != null) {
                    if (UrlValidator.isMagnetLink(data) || UrlValidator.isValidUrl(data)) {
                        return data
                    }
                    val type = try { intent.type?.lowercase() } catch (e: Throwable) { null }
                    val isTorrentType = type == "application/x-bittorrent" ||
                            type == "application/x-torrent" ||
                            type?.contains("torrent") == true
                    val isTorrentPath = data.contains(".torrent", ignoreCase = true)

                    if (isTorrentType || isTorrentPath) {
                        return data
                    }
                }
            }
        }
        return null
    }
}
