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
                                !extracted.startsWith("https://", ignoreCase = true)) {
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
                val data = intent.dataString
                if (data != null && UrlValidator.isValidUrl(data)) {
                    return data
                }
            }
        }
        return null
    }
}
