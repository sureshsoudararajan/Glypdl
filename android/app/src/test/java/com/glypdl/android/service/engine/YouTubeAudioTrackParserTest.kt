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

import com.glypdl.android.data.model.AudioTrackClassification
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeAudioTrackParserTest {

    @Test
    fun parse_multiLanguageYouTubeVideo_detectsAllLanguagesAndOriginalTrack() {
        val json = JSONObject().apply {
            put("extractor", "youtube")
            put("webpage_url", "https://www.youtube.com/watch?v=0e3GPea1Tyg")
            put("formats", JSONArray().apply {
                // English original (medium, 125k)
                put(JSONObject().apply {
                    put("format_id", "251-23")
                    put("format_note", "English (US) original (default), medium")
                    put("language", "en-US")
                    put("language_preference", 10)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 125.3)
                    put("filesize", 24140597L)
                })
                // English original (low, 48k)
                put(JSONObject().apply {
                    put("format_id", "139-23")
                    put("format_note", "English (US) original (default), low")
                    put("language", "en-US")
                    put("language_preference", 10)
                    put("acodec", "mp4a.40.5")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 48.8)
                    put("filesize", 9399983L)
                })
                // Tamil dubbed (medium, 128k)
                put(JSONObject().apply {
                    put("format_id", "251-9")
                    put("format_note", "Tamil, medium")
                    put("language", "ta")
                    put("language_preference", -1)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 128.3)
                    put("filesize", 24725380L)
                })
                // Tamil dubbed (low, 48k)
                put(JSONObject().apply {
                    put("format_id", "139-15")
                    put("format_note", "Tamil, low")
                    put("language", "ta")
                    put("language_preference", -1)
                    put("acodec", "mp4a.40.5")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 48.8)
                    put("filesize", 9399742L)
                })
                // Hindi dubbed (medium, 131k)
                put(JSONObject().apply {
                    put("format_id", "251-18")
                    put("format_note", "Hindi, medium")
                    put("language", "hi")
                    put("language_preference", -1)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 131.0)
                    put("filesize", 23990000L)
                })
                // Arabic dubbed with explicit dubbed note
                put(JSONObject().apply {
                    put("format_id", "233-0")
                    put("format_note", "العربية - dubbed")
                    put("language", "ar")
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("ext", "mp4")
                    put("abr", 128.0)
                })
                // Video-only stream (should be excluded)
                put(JSONObject().apply {
                    put("format_id", "137")
                    put("vcodec", "avc1.640028")
                    put("acodec", "none")
                    put("ext", "mp4")
                })
            })
        }

        val (tracks, languages) = YouTubeAudioTrackParser.parse(json, "https://www.youtube.com/watch?v=0e3GPea1Tyg")

        // 6 valid audio candidates (137 excluded)
        assertEquals(6, tracks.size)

        // 4 distinct languages: en-US, ta, hi, ar
        assertEquals(4, languages.size)

        // Original audio language is at the very top (index 0)
        val firstLang = languages[0]
        assertEquals("en-US", firstLang.languageCode)
        assertEquals(AudioTrackClassification.ORIGINAL, firstLang.classification)
        assertTrue(firstLang.isOriginal)
        assertEquals(2, firstLang.tracks.size)
        // Best quality variant in en-US is 251-23 (125k)
        assertEquals("251-23", firstLang.selectedTrack.formatId)

        // Other languages classified as DUBBED
        val tamilLang = languages.firstOrNull { it.languageCode == "ta" }
        assertNotNull(tamilLang)
        assertEquals(AudioTrackClassification.DUBBED, tamilLang?.classification)
        assertFalse(tamilLang?.isOriginal ?: true)
        assertEquals(2, tamilLang?.tracks?.size)
        assertEquals("251-9", tamilLang?.selectedTrack?.formatId)

        val hindiLang = languages.firstOrNull { it.languageCode == "hi" }
        assertNotNull(hindiLang)
        assertEquals(AudioTrackClassification.DUBBED, hindiLang?.classification)
        assertEquals("251-18", hindiLang?.selectedTrack?.formatId)

        val arabicLang = languages.firstOrNull { it.languageCode == "ar" }
        assertNotNull(arabicLang)
        assertEquals(AudioTrackClassification.DUBBED, arabicLang?.classification)
    }

    @Test
    fun parse_singleAudioYouTubeVideo_detectsOnlySingleLanguage() {
        val json = JSONObject().apply {
            put("extractor", "youtube")
            put("webpage_url", "https://www.youtube.com/watch?v=dQw4w9WgXcQ")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "140")
                    put("format_note", "medium")
                    put("language", "en")
                    put("language_preference", -1)
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 129.5)
                })
                put(JSONObject().apply {
                    put("format_id", "251")
                    put("format_note", "medium")
                    put("language", "en")
                    put("language_preference", -1)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 128.9)
                })
            })
        }

        val (tracks, languages) = YouTubeAudioTrackParser.parse(json, "https://www.youtube.com/watch?v=dQw4w9WgXcQ")

        assertEquals(2, tracks.size)
        assertEquals(1, languages.size)
        assertEquals("en", languages[0].languageCode)
    }

    @Test
    fun parse_nonYouTubeUrl_returnsEmptyLists() {
        val json = JSONObject().apply {
            put("extractor", "instagram")
            put("webpage_url", "https://www.instagram.com/reel/C8qK...")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "audio_0")
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 128.0)
                })
            })
        }

        val (tracks, languages) = YouTubeAudioTrackParser.parse(json, "https://www.instagram.com/reel/C8qK...")

        assertTrue(tracks.isEmpty())
        assertTrue(languages.isEmpty())
    }

    @Test
    fun parse_explicitOriginalClassification_worksForNonEnglishCreator() {
        // Creator is Hindi speaker; original is Hindi, English is dubbed
        val json = JSONObject().apply {
            put("extractor", "youtube")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "251-1")
                    put("format_note", "Hindi original (default), medium")
                    put("language", "hi")
                    put("language_preference", 10)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 128.0)
                })
                put(JSONObject().apply {
                    put("format_id", "251-2")
                    put("format_note", "English - dubbed, medium")
                    put("language", "en")
                    put("language_preference", -1)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 128.0)
                })
            })
        }

        val (tracks, languages) = YouTubeAudioTrackParser.parse(json, url = "https://youtu.be/sample123")

        assertEquals(2, languages.size)

        // Hindi MUST be first and marked ORIGINAL
        assertEquals("hi", languages[0].languageCode)
        assertEquals(AudioTrackClassification.ORIGINAL, languages[0].classification)
        assertTrue(languages[0].isOriginal)

        // English MUST be marked DUBBED
        assertEquals("en", languages[1].languageCode)
        assertEquals(AudioTrackClassification.DUBBED, languages[1].classification)
        assertFalse(languages[1].isOriginal)
    }

    @Test
    fun parse_missingLanguageMetadata_handledSafelyWithoutCrashing() {
        val json = JSONObject().apply {
            put("extractor", "youtube")
            put("formats", JSONArray().apply {
                put(JSONObject().apply {
                    put("format_id", "raw_audio_1")
                    put("acodec", "opus")
                    put("vcodec", "none")
                })
                put(JSONObject().apply {
                    put("format_id", "") // Blank formatId
                    put("acodec", "opus")
                })
            })
        }

        val (tracks, languages) = YouTubeAudioTrackParser.parse(json, url = "https://www.youtube.com/watch?v=sample")

        assertEquals(1, tracks.size)
        assertEquals("raw_audio_1", tracks[0].formatId)
        assertEquals(AudioTrackClassification.UNKNOWN, tracks[0].trackClassification)
    }

    @Test
    fun resolveLanguageName_returnsAccurateNames() {
        assertEquals("Tamil", YouTubeAudioTrackParser.resolveLanguageName("ta", "Tamil, low"))
        assertEquals("Hindi", YouTubeAudioTrackParser.resolveLanguageName("hi", "Hindi, medium"))
        assertEquals("English", YouTubeAudioTrackParser.resolveLanguageName("en", "English original"))
        assertEquals("Spanish", YouTubeAudioTrackParser.resolveLanguageName("es", "Spanish - dubbed"))
        assertEquals("French", YouTubeAudioTrackParser.resolveLanguageName("fr", "French"))
        assertEquals("German", YouTubeAudioTrackParser.resolveLanguageName("de", "Deutsch"))
        assertEquals("Arabic", YouTubeAudioTrackParser.resolveLanguageName("ar", "العربية - dubbed"))
    }

    @Test
    fun parse_diwali2026Video_tamilIsOriginalAndEnglishIsDubbed() {
        val json = JSONObject().apply {
            put("extractor", "youtube")
            put("webpage_url", "https://youtu.be/J60Chj8yQrI")
            put("formats", JSONArray().apply {
                // Formats as extracted by yt-dlp for J60Chj8yQrI
                put(JSONObject().apply {
                    put("format_id", "139-0")
                    put("format_note", "English (US), low")
                    put("language", "en-US")
                    put("language_preference", -1)
                    put("acodec", "mp4a.40.5")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 48.788)
                })
                put(JSONObject().apply {
                    put("format_id", "139-1")
                    put("format_note", "Tamil original (default), low")
                    put("language", "ta")
                    put("language_preference", 10)
                    put("acodec", "mp4a.40.5")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 48.788)
                })
                put(JSONObject().apply {
                    put("format_id", "140-0")
                    put("format_note", "English (US), medium")
                    put("language", "en-US")
                    put("language_preference", -1)
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 129.477)
                })
                put(JSONObject().apply {
                    put("format_id", "140-1")
                    put("format_note", "Tamil original (default), medium")
                    put("language", "ta")
                    put("language_preference", 10)
                    put("acodec", "mp4a.40.2")
                    put("vcodec", "none")
                    put("ext", "m4a")
                    put("abr", 129.478)
                })
                put(JSONObject().apply {
                    put("format_id", "251-0")
                    put("format_note", "English (US), medium")
                    put("language", "en-US")
                    put("language_preference", -1)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 120.635)
                })
                put(JSONObject().apply {
                    put("format_id", "251-1")
                    put("format_note", "Tamil original (default), medium")
                    put("language", "ta")
                    put("language_preference", 10)
                    put("acodec", "opus")
                    put("vcodec", "none")
                    put("ext", "webm")
                    put("abr", 121.437)
                })
            })
        }

        val (tracks, languages) = YouTubeAudioTrackParser.parse(json, "https://youtu.be/J60Chj8yQrI")

        assertEquals(2, languages.size)

        // Tamil MUST be original and ranked first
        val tamil = languages[0]
        assertEquals("ta", tamil.languageCode)
        assertEquals("Tamil", tamil.languageName)
        assertEquals(AudioTrackClassification.ORIGINAL, tamil.classification)
        assertTrue(tamil.isOriginal)
        assertEquals("140-1", tamil.selectedTrack.formatId)

        // English MUST be dubbed and NOT original
        val english = languages[1]
        assertEquals("en-US", english.languageCode)
        assertEquals(AudioTrackClassification.DUBBED, english.classification)
        assertFalse(english.isOriginal)
        assertEquals("140-0", english.selectedTrack.formatId)
    }
}
