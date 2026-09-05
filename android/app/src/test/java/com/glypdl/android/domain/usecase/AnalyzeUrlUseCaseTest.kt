/*
 * Copyright (C) 2026 The Glypdl Authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.domain.usecase

import com.glypdl.android.data.model.MediaAnalysisResult
import com.glypdl.android.data.model.MediaInfo
import com.glypdl.android.data.model.PlaylistInfo
import com.glypdl.android.data.model.PlaylistItem
import com.glypdl.android.service.YtDlpService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AnalyzeUrlUseCaseTest {

    private lateinit var ytDlpService: YtDlpService
    private lateinit var validateUrlUseCase: ValidateUrlUseCase
    private lateinit var analyzeUrlUseCase: AnalyzeUrlUseCase

    @Before
    fun setup() {
        ytDlpService = mockk()
        validateUrlUseCase = mockk()
        analyzeUrlUseCase = AnalyzeUrlUseCase(validateUrlUseCase, ytDlpService)
    }

    @Test
    fun `valid URL returns success with MediaInfo`() = runTest {
        val url = "https://example.com/video"
        val mediaInfo = MediaInfo(
            url = url,
            title = "Video",
            uploader = "User",
            duration = 60L,
            thumbnail = null,
            formats = emptyList()
        )
        
        every { validateUrlUseCase(url) } returns Result.success(url)
        every { ytDlpService.isPlaylist(url) } returns false
        coEvery { ytDlpService.extractInfo(url) } returns Result.success(mediaInfo)

        val result = analyzeUrlUseCase(url)
        
        assertTrue(result.isSuccess)
        assertEquals(MediaAnalysisResult.SingleMedia(mediaInfo), result.getOrNull())
    }

    @Test
    fun `playlist URL returns success with PlaylistInfo`() = runTest {
        val url = "https://youtube.com/playlist?list=PL123"
        val playlistInfo = PlaylistInfo(
            id = "PL123",
            url = url,
            title = "Sample Playlist",
            uploader = "Channel",
            thumbnailUrl = null,
            entries = listOf(
                PlaylistItem(
                    id = "item1",
                    url = "https://youtube.com/watch?v=item1",
                    title = "Item 1",
                    duration = 120L
                )
            )
        )

        every { validateUrlUseCase(url) } returns Result.success(url)
        every { ytDlpService.isPlaylist(url) } returns true
        coEvery { ytDlpService.extractPlaylistInfo(url, any()) } returns Result.success(playlistInfo)

        val result = analyzeUrlUseCase(url)

        assertTrue(result.isSuccess)
        assertEquals(MediaAnalysisResult.Playlist(playlistInfo), result.getOrNull())
    }

    @Test
    fun `invalid URL returns failure`() = runTest {
        val url = "invalid_url"
        val exception = IllegalArgumentException("Invalid URL")
        
        every { validateUrlUseCase(url) } returns Result.failure(exception)

        val result = analyzeUrlUseCase(url)
        
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is com.glypdl.android.data.model.GlypdlException)
        assertTrue((ex as com.glypdl.android.data.model.GlypdlException).error is com.glypdl.android.data.model.GlypdlError.InvalidUrl)
    }

    @Test
    fun `YtDlpService failure propagates`() = runTest {
        val url = "https://example.com/video"
        val exception = Exception("Extraction failed")
        
        every { validateUrlUseCase(url) } returns Result.success(url)
        every { ytDlpService.isPlaylist(url) } returns false
        coEvery { ytDlpService.extractInfo(url) } returns Result.failure(exception)

        val result = analyzeUrlUseCase(url)
        
        assertTrue(result.isFailure)
        assertEquals(exception, result.exceptionOrNull())
    }

    @Test
    fun `empty URL returns failure`() = runTest {
        val url = ""
        val exception = IllegalArgumentException("URL cannot be empty")
        
        every { validateUrlUseCase(url) } returns Result.failure(exception)

        val result = analyzeUrlUseCase(url)
        
        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is com.glypdl.android.data.model.GlypdlException)
        assertTrue((ex as com.glypdl.android.data.model.GlypdlException).error is com.glypdl.android.data.model.GlypdlError.InvalidUrl)
    }
}
