/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.ui.analyze

import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.AudioTrackClassification
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.DownloadStatus
import com.glypdl.android.data.model.MediaFormat
import com.glypdl.android.data.model.MediaInfo
import com.glypdl.android.data.model.YouTubeAudioLanguage
import com.glypdl.android.data.model.YouTubeAudioTrack
import com.glypdl.android.service.engine.FormatNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class YouTubeAudioLanguageSelectionTest {

    @Test
    fun formatNormalizer_prioritizesOriginalAudioWhenMultiLanguageAvailable() {
        val formats = listOf(
            // Dubbed Indonesian with higher raw bitrate (134k)
            MediaFormat(
                formatId = "251-22",
                ext = "webm",
                vcodec = "none",
                acodec = "opus",
                abr = 134f,
                language = "id",
                languagePreference = -1,
                formatNote = "Indonesian, medium",
                isAudioOnly = true
            ),
            // Original English with lower raw bitrate (125k)
            MediaFormat(
                formatId = "251-23",
                ext = "webm",
                vcodec = "none",
                acodec = "opus",
                abr = 125f,
                language = "en-US",
                languagePreference = 10,
                formatNote = "English (US) original (default), medium",
                isAudioOnly = true
            ),
            // Dubbed Tamil (128k)
            MediaFormat(
                formatId = "251-9",
                ext = "webm",
                vcodec = "none",
                acodec = "opus",
                abr = 128f,
                language = "ta",
                languagePreference = -1,
                formatNote = "Tamil, medium",
                isAudioOnly = true
            )
        )

        // Default call without specific language preference
        val normalized = FormatNormalizer.normalizeAudioQualities(formats)

        // MUST pick English original (251-23), NOT Indonesian (251-22)
        assertEquals(1, normalized.size)
        assertEquals("251-23", normalized[0].formatId)
    }

    @Test
    fun formatNormalizer_filtersToRequestedLanguageWhenSpecified() {
        val formats = listOf(
            MediaFormat(
                formatId = "251-23",
                ext = "webm",
                vcodec = "none",
                acodec = "opus",
                abr = 125f,
                language = "en-US",
                languagePreference = 10,
                formatNote = "English original",
                isAudioOnly = true
            ),
            MediaFormat(
                formatId = "251-9",
                ext = "webm",
                vcodec = "none",
                acodec = "opus",
                abr = 128f,
                language = "ta",
                languagePreference = -1,
                formatNote = "Tamil, medium",
                isAudioOnly = true
            ),
            MediaFormat(
                formatId = "139-15",
                ext = "m4a",
                vcodec = "none",
                acodec = "mp4a.40.5",
                abr = 48f,
                language = "ta",
                languagePreference = -1,
                formatNote = "Tamil, low",
                isAudioOnly = true
            )
        )

        // Request Tamil
        val tamilQualities = FormatNormalizer.normalizeAudioQualities(formats, preferredLanguageCode = "ta")

        assertEquals(2, tamilQualities.size)
        assertEquals("251-9", tamilQualities[0].formatId)
        assertEquals("139-15", tamilQualities[1].formatId)
    }

    @Test
    fun downloadRequest_preservesExactSelectedAudioFormatIdAndLanguage() {
        val selectedTrack = YouTubeAudioTrack(
            formatId = "251-9",
            languageCode = "ta",
            languageName = "Tamil",
            formatNote = "Tamil, medium",
            codec = "opus",
            bitrate = 128L,
            extension = "webm",
            fileSize = 24725380L,
            isOriginal = false,
            trackClassification = AudioTrackClassification.DUBBED,
            qualityLabel = "128 kbps (Opus)"
        )

        val selectedLanguage = YouTubeAudioLanguage(
            languageCode = "ta",
            languageName = "Tamil",
            classification = AudioTrackClassification.DUBBED,
            tracks = listOf(selectedTrack),
            selectedTrack = selectedTrack
        )

        val request = DownloadRequest(
            id = UUID.randomUUID().toString(),
            url = "https://www.youtube.com/watch?v=0e3GPea1Tyg",
            title = "Test Video Title",
            thumbnailUrl = null,
            formatId = selectedTrack.formatId,
            ext = selectedTrack.extension ?: "webm",
            resolution = "${selectedTrack.displayBitrate} (${selectedLanguage.languageName})",
            isAudioOnly = true,
            destinationUri = null,
            selectedAudioLanguage = selectedLanguage.languageCode,
            selectedAudioTrack = selectedTrack
        )

        // Assert exact format ID is preserved
        assertEquals("251-9", request.formatId)
        assertEquals("webm", request.ext)
        assertEquals("128 kbps (Tamil)", request.resolution)
        assertEquals("ta", request.selectedAudioLanguage)
        assertEquals(AudioTrackClassification.DUBBED, request.selectedAudioTrack?.trackClassification)
    }

    @Test
    fun downloadEntity_persistsExactFormatIdForQueueAndRetry() {
        // Enqueuing download request creates DownloadEntity
        val formatId = "251-9"
        val resolution = "128 kbps (Tamil)"

        val entity = DownloadEntity(
            id = "test-uuid-1",
            url = "https://www.youtube.com/watch?v=0e3GPea1Tyg",
            title = "Sample Video",
            thumbnailUrl = null,
            formatId = formatId,
            ext = "webm",
            resolution = resolution,
            isAudioOnly = true,
            status = DownloadStatus.QUEUED,
            progress = 0f,
            downloadedBytes = 0L,
            totalBytes = 24725380L,
            speed = null,
            filePath = null,
            errorMessage = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        // When processed or retried, formatId and resolution remain intact
        assertEquals("251-9", entity.formatId)
        assertEquals("128 kbps (Tamil)", entity.resolution)
        assertTrue(entity.isAudioOnly)
    }

    @Test
    fun mediaInfo_hasMultipleAudioLanguages_detectsMultiLanguageStatus() {
        val track1 = YouTubeAudioTrack(formatId = "251-23", languageCode = "en-US", isOriginal = true, trackClassification = AudioTrackClassification.ORIGINAL)
        val track2 = YouTubeAudioTrack(formatId = "251-9", languageCode = "ta", isOriginal = false, trackClassification = AudioTrackClassification.DUBBED)

        val lang1 = YouTubeAudioLanguage(languageCode = "en-US", languageName = "English (US)", classification = AudioTrackClassification.ORIGINAL, tracks = listOf(track1))
        val lang2 = YouTubeAudioLanguage(languageCode = "ta", languageName = "Tamil", classification = AudioTrackClassification.DUBBED, tracks = listOf(track2))

        val multiLangMediaInfo = MediaInfo(
            url = "https://www.youtube.com/watch?v=0e3GPea1Tyg",
            title = "Multi-Audio Video",
            youtubeAudioTracks = listOf(track1, track2),
            youtubeAudioLanguages = listOf(lang1, lang2)
        )

        assertTrue(multiLangMediaInfo.hasMultipleAudioLanguages)
        assertEquals(lang1, multiLangMediaInfo.originalAudioLanguage)

        val singleLangMediaInfo = MediaInfo(
            url = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            title = "Single-Audio Video",
            youtubeAudioTracks = listOf(track1),
            youtubeAudioLanguages = listOf(lang1)
        )

        assertFalse(singleLangMediaInfo.hasMultipleAudioLanguages)
    }
}
