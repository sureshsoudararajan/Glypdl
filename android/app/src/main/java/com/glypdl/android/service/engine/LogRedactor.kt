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

object LogRedactor {

    private val patterns = listOf(
        // Cookie headers and assignments
        Regex("(?i)(cookie[s]?[:=]\\s*)([^;\\r\\n]+)") to "$1[REDACTED]",
        Regex("(?i)(--cookies(?:-from-browser)?\\s+)(\\S+)") to "$1[REDACTED]",
        Regex("(?i)(sessionid[=:]\\s*)([a-zA-Z0-9%_-]+)") to "$1[REDACTED]",
        Regex("(?i)(ds_user_id[=:]\\s*)([a-zA-Z0-9%_-]+)") to "$1[REDACTED]",
        Regex("(?i)(csrftoken[=:]\\s*)([a-zA-Z0-9%_-]+)") to "$1[REDACTED]",

        // Authorization headers & Bearer tokens
        Regex("(?i)(authorization[:=]\\s*)(Bearer\\s+)?([a-zA-Z0-9._~+/-]+=*)") to "$1Bearer [REDACTED]",
        Regex("(?i)(access_token[=:]\\s*)([a-zA-Z0-9._~+/-]+=*)") to "$1[REDACTED]",
        Regex("(?i)(api[_-]?key[=:]\\s*)([a-zA-Z0-9._~+/-]+=*)") to "$1[REDACTED]",

        // Passwords and credentials
        Regex("(?i)(password[=:]\\s*)([^&\\s]+)") to "$1[REDACTED]",
        Regex("(?i)(--password\\s+)(\\S+)") to "$1[REDACTED]",
        Regex("(?i)(--username\\s+)(\\S+)") to "$1[REDACTED]"
    )

    fun redact(text: String?): String {
        if (text.isNullOrBlank()) return ""
        var sanitized: String = text
        for ((regex, replacement) in patterns) {
            sanitized = sanitized.replace(regex, replacement)
        }
        return sanitized
    }
}
