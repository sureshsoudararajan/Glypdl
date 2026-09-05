/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.domain.usecase

import java.net.URI
import javax.inject.Inject

class ValidateUrlUseCase @Inject constructor() {
    operator fun invoke(url: String): Result<String> {
        return try {
            val trimmedUrl = url.trim()
            val uri = URI.create(trimmedUrl)
            
            if (uri.scheme == "http" || uri.scheme == "https") {
                Result.success(trimmedUrl)
            } else {
                Result.failure(IllegalArgumentException("URL must use HTTP or HTTPS protocol"))
            }
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Invalid URL format"))
        }
    }
}
