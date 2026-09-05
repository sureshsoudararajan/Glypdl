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

import com.glypdl.android.data.model.GlypdlError
import com.glypdl.android.data.model.GlypdlException
import com.glypdl.android.data.model.MediaAnalysisResult
import com.glypdl.android.service.YtDlpService
import javax.inject.Inject

class AnalyzeUrlUseCase @Inject constructor(
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val ytDlpService: YtDlpService
) {
    suspend operator fun invoke(url: String, processId: String? = null): Result<MediaAnalysisResult> {
        val validationResult = validateUrlUseCase(url)
        if (validationResult.isFailure) {
            val error = GlypdlError.InvalidUrl(validationResult.exceptionOrNull()?.message)
            return Result.failure(GlypdlException(error))
        }

        val cleanUrl = validationResult.getOrThrow()

        return if (ytDlpService.isPlaylist(cleanUrl)) {
            ytDlpService.extractPlaylistInfo(cleanUrl, processId).map { playlistInfo ->
                MediaAnalysisResult.Playlist(playlistInfo)
            }
        } else {
            ytDlpService.extractInfo(cleanUrl).map { mediaInfo ->
                MediaAnalysisResult.SingleMedia(mediaInfo)
            }
        }
    }
}
