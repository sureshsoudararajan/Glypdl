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
import com.glypdl.android.data.model.YouTubeAudioLanguage
import com.glypdl.android.data.model.YouTubeAudioTrack
import com.glypdl.android.util.UrlValidator
import org.json.JSONObject
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Universal parser for YouTube multi-language audio tracks from yt-dlp JSON metadata.
 *
 * Inspects all audio streams, identifies available language tracks, accurately classifies
 * creator original audio vs dubbed audio vs unknown, groups quality variants per language,
 * and preserves exact format IDs for zero-fallback download execution.
 */
object YouTubeAudioTrackParser {

    private val LANGUAGE_TAG_REGEX = Regex("""\[([a-zA-Z]{2,3}(?:-[a-zA-Z0-9]+)?)\]""")

    /**
     * Parses audio tracks from a yt-dlp video JSON representation.
     *
     * @param json Root yt-dlp metadata JSON object.
     * @param url Media URL for validation (ensures only YouTube is parsed).
     * @param extractor Extractor name from yt-dlp (e.g. "youtube").
     * @return Pair of flat [YouTubeAudioTrack] list and grouped [YouTubeAudioLanguage] list.
     */
    fun parse(
        json: JSONObject,
        url: String = "",
        extractor: String? = null
    ): Pair<List<YouTubeAudioTrack>, List<YouTubeAudioLanguage>> {
        val jsonExtractor = json.optString("extractor", "").ifBlank { json.optString("extractor_key", "") }
        val webpageUrl = json.optString("webpage_url", "")
        val targetUrl = url.ifBlank { webpageUrl }

        val isYouTube = (extractor?.contains("youtube", ignoreCase = true) == true) ||
                jsonExtractor.contains("youtube", ignoreCase = true) ||
                (targetUrl.isNotBlank() && UrlValidator.isYouTubeUrl(targetUrl))

        if (!isYouTube) {
            return emptyList<YouTubeAudioTrack>() to emptyList<YouTubeAudioLanguage>()
        }

        val formatsArray = json.optJSONArray("formats")
            ?: return emptyList<YouTubeAudioTrack>() to emptyList<YouTubeAudioLanguage>()

        val rawAudioCandidates = mutableListOf<RawFormatCandidate>()

        for (i in 0 until formatsArray.length()) {
            val f = formatsArray.optJSONObject(i) ?: continue
            val formatId = f.optString("format_id", "").trim()
            if (formatId.isBlank() || formatId.startsWith("sb") || f.optString("protocol") == "mhtml") {
                continue
            }

            val vcodec = f.optString("vcodec", "none").trim().lowercase()
            val acodec = f.optString("acodec", "none").trim().lowercase()

            // Exclude video streams and streams without a valid audio codec
            val isAudioOnly = (vcodec == "none" || vcodec.isBlank()) && acodec != "none" && acodec.isNotBlank()
            if (!isAudioOnly) continue

            val rawLang = f.optString("language").trim().takeIf { it.isNotBlank() }
            val formatNote = f.optString("format_note").trim().takeIf { it.isNotBlank() }
            val formatStr = f.optString("format").trim().takeIf { it.isNotBlank() }
            val langPref = if (f.has("language_preference")) f.optInt("language_preference") else null
            val abr = f.optDouble("abr").takeIf { it > 0 } ?: f.optDouble("tbr").takeIf { it > 0 }
            val asr = f.optLong("asr").takeIf { it > 0 }
            val channels = f.optInt("audio_channels").takeIf { it > 0 }
            val ext = f.optString("ext", "m4a").trim().lowercase()
            val filesize = f.optLong("filesize").takeIf { it > 0 } ?: f.optLong("filesize_approx").takeIf { it > 0 }

            rawAudioCandidates.add(
                RawFormatCandidate(
                    formatId = formatId,
                    rawLang = rawLang,
                    formatNote = formatNote,
                    format = formatStr,
                    languagePreference = langPref,
                    abr = abr,
                    asr = asr,
                    channels = channels,
                    acodec = acodec,
                    ext = ext,
                    filesize = filesize
                )
            )
        }

        if (rawAudioCandidates.isEmpty()) {
            return emptyList<YouTubeAudioTrack>() to emptyList<YouTubeAudioLanguage>()
        }

        // Stage 1: Detect if any track has explicit Original indicators
        var originalLanguageCode: String? = null
        var hasExplicitOriginal = false

        for (c in rawAudioCandidates) {
            val isOrig = isExplicitOriginalFormat(c.formatNote, c.format, c.languagePreference)
            if (isOrig) {
                hasExplicitOriginal = true
                val code = extractLanguageCode(c.rawLang, c.formatNote, c.format)
                if (code != null) {
                    originalLanguageCode = code
                    break
                }
            }
        }

        // Stage 2: Parse individual tracks with accurate classification
        val allParsedTracks = mutableListOf<YouTubeAudioTrack>()

        for (c in rawAudioCandidates) {
            val langCode = extractLanguageCode(c.rawLang, c.formatNote, c.format)
            val langName = resolveLanguageName(langCode, c.formatNote)

            val classification = classifyTrack(
                formatNote = c.formatNote,
                format = c.format,
                languagePreference = c.languagePreference,
                langCode = langCode,
                originalLangCode = originalLanguageCode,
                hasExplicitOriginal = hasExplicitOriginal
            )

            val roundedBitrate = c.abr?.roundToInt()?.toLong()
            val qualityLabel = buildQualityLabel(roundedBitrate, c.acodec, c.ext)

            allParsedTracks.add(
                YouTubeAudioTrack(
                    formatId = c.formatId,
                    languageCode = langCode,
                    languageName = langName,
                    formatNote = c.formatNote,
                    codec = c.acodec,
                    bitrate = roundedBitrate,
                    sampleRate = c.asr,
                    channels = c.channels,
                    extension = c.ext,
                    fileSize = c.filesize,
                    isOriginal = classification == AudioTrackClassification.ORIGINAL,
                    trackClassification = classification,
                    qualityLabel = qualityLabel
                )
            )
        }

        // Stage 3: Group by Language
        val groupedByLanguage = mutableMapOf<String, MutableList<YouTubeAudioTrack>>()
        for (track in allParsedTracks) {
            val key = track.languageCode?.lowercase() ?: "und"
            groupedByLanguage.getOrPut(key) { mutableListOf() }.add(track)
        }

        val languages = mutableListOf<YouTubeAudioLanguage>()

        for ((codeKey, tracks) in groupedByLanguage) {
            // Sort variants: highest bitrate first, Opus/AAC prioritized, file sizes prioritized
            val sortedTracks = tracks.sortedWith(
                compareByDescending<YouTubeAudioTrack> { it.bitrate ?: 0L }
                    .thenByDescending { it.fileSize ?: 0L }
                    .thenBy { it.extension != "opus" && it.extension != "webm" }
            )

            // Deduplicate variants with identical bitrate, codec, and extension
            val deduplicatedTracks = mutableListOf<YouTubeAudioTrack>()
            val seenVariants = mutableSetOf<String>()
            for (t in sortedTracks) {
                val variantKey = "${t.bitrate ?: 0}-${t.displayCodec}-${t.extension?.lowercase() ?: ""}"
                if (seenVariants.add(variantKey)) {
                    deduplicatedTracks.add(t)
                }
            }
            val finalTracks = if (deduplicatedTracks.isNotEmpty()) deduplicatedTracks else sortedTracks

            val repTrack = finalTracks.first()
            val languageCode = repTrack.languageCode ?: codeKey
            val languageName = repTrack.languageName ?: resolveLanguageName(languageCode, repTrack.formatNote)

            val groupClassification = when {
                finalTracks.any { it.trackClassification == AudioTrackClassification.ORIGINAL } -> AudioTrackClassification.ORIGINAL
                finalTracks.any { it.trackClassification == AudioTrackClassification.DUBBED } -> AudioTrackClassification.DUBBED
                else -> AudioTrackClassification.UNKNOWN
            }

            languages.add(
                YouTubeAudioLanguage(
                    languageCode = languageCode,
                    languageName = languageName,
                    classification = groupClassification,
                    tracks = finalTracks,
                    selectedTrack = finalTracks.first()
                )
            )
        }

        // Stage 4: Order languages: ORIGINAL at the top, then alphabetically by name
        val orderedLanguages = languages.sortedWith(
            compareByDescending<YouTubeAudioLanguage> { it.isOriginal }
                .thenBy { it.languageName }
        )

        return allParsedTracks to orderedLanguages
    }

    /**
     * Determines whether format metadata explicitly identifies this stream as the original audio.
     */
    fun isExplicitOriginalFormat(
        formatNote: String?,
        format: String?,
        languagePreference: Int?
    ): Boolean {
        if (languagePreference != null && languagePreference > 0) return true
        val noteLower = (formatNote ?: "").lowercase()
        val formatLower = (format ?: "").lowercase()
        return noteLower.contains("original") || formatLower.contains("original")
    }

    /**
     * Classifies a track into [AudioTrackClassification.ORIGINAL], [AudioTrackClassification.DUBBED], or [AudioTrackClassification.UNKNOWN].
     */
    fun classifyTrack(
        formatNote: String?,
        format: String?,
        languagePreference: Int?,
        langCode: String?,
        originalLangCode: String?,
        hasExplicitOriginal: Boolean
    ): AudioTrackClassification {
        val noteLower = (formatNote ?: "").lowercase()
        val formatLower = (format ?: "").lowercase()

        // 1. Explicit Original check
        if (isExplicitOriginalFormat(formatNote, format, languagePreference)) {
            // Guard: If original language is known and this track does NOT match it, it cannot be original
            if (hasExplicitOriginal && originalLangCode != null && langCode != null && !langCode.equals(originalLangCode, ignoreCase = true)) {
                return AudioTrackClassification.DUBBED
            }
            return AudioTrackClassification.ORIGINAL
        }

        // 2. Explicit Dubbed note check
        if (noteLower.contains("dubbed") || formatLower.contains("dubbed")) {
            return AudioTrackClassification.DUBBED
        }

        // 3. Multi-audio video with known original language: non-original languages are DUBBED
        if (hasExplicitOriginal && originalLangCode != null && langCode != null) {
            return if (langCode.equals(originalLangCode, ignoreCase = true)) {
                AudioTrackClassification.ORIGINAL
            } else {
                AudioTrackClassification.DUBBED
            }
        }

        // 4. Dubbed indicator via negative language_preference on multi-audio videos
        if (languagePreference != null && languagePreference < 0) {
            return AudioTrackClassification.DUBBED
        }

        // 5. If classification is uncertain, never guess
        return AudioTrackClassification.UNKNOWN
    }

    /**
     * Extracts language tag from raw language field or format description string.
     */
    fun extractLanguageCode(rawLang: String?, formatNote: String?, format: String?): String? {
        if (!rawLang.isNullOrBlank() && rawLang != "und") {
            return rawLang.trim()
        }

        val noteMatch = formatNote?.let { LANGUAGE_TAG_REGEX.find(it) }
        if (noteMatch != null) {
            return noteMatch.groupValues[1]
        }

        val formatMatch = format?.let { LANGUAGE_TAG_REGEX.find(it) }
        if (formatMatch != null) {
            return formatMatch.groupValues[1]
        }

        return rawLang?.takeIf { it.isNotBlank() }
    }

    /**
     * Resolves human-readable language name using Android/Java standard Locale and fallbacks.
     */
    fun resolveLanguageName(languageCode: String?, formatNote: String?): String {
        if (languageCode.isNullOrBlank() || languageCode.equals("und", ignoreCase = true)) {
            // Attempt to derive name from formatNote e.g. "Tamil, low" or "العربية - dubbed"
            if (!formatNote.isNullOrBlank()) {
                val cleaned = formatNote.substringBefore(',').substringBefore('-').trim()
                if (cleaned.isNotBlank() && !cleaned.contains("audio", ignoreCase = true)) {
                    return cleaned
                }
            }
            return "Unknown Language"
        }

        // Standard Locale lookup
        val locale = Locale.forLanguageTag(languageCode)
        val displayName = locale.getDisplayName(Locale.ENGLISH)

        if (displayName.isNotBlank() && !displayName.equals(languageCode, ignoreCase = true)) {
            return displayName
        }

        // Known language tag overrides
        return when (languageCode.lowercase()) {
            "en", "en-us" -> "English"
            "ta" -> "Tamil"
            "hi" -> "Hindi"
            "te" -> "Telugu"
            "ml" -> "Malayalam"
            "mr" -> "Marathi"
            "bn" -> "Bangla"
            "pa" -> "Punjabi"
            "es" -> "Spanish"
            "fr" -> "French"
            "de" -> "German"
            "ja" -> "Japanese"
            "ko" -> "Korean"
            "zh-hans" -> "Chinese (Simplified)"
            "zh-hant" -> "Chinese (Traditional)"
            "ru" -> "Russian"
            "pt" -> "Portuguese"
            "it" -> "Italian"
            "ar" -> "Arabic"
            "th" -> "Thai"
            "tr" -> "Turkish"
            "vi" -> "Vietnamese"
            "id" -> "Indonesian"
            "pl" -> "Polish"
            else -> {
                if (!formatNote.isNullOrBlank()) {
                    val parsed = formatNote.substringBefore(',').substringBefore('-').trim()
                    if (parsed.isNotBlank() && !parsed.contains("audio", ignoreCase = true)) {
                        return parsed
                    }
                }
                languageCode.uppercase()
            }
        }
    }

    private fun buildQualityLabel(bitrate: Long?, codec: String?, ext: String?): String {
        val brStr = if (bitrate != null && bitrate > 0) "$bitrate kbps" else "Standard"
        val codecStr = when {
            codec?.contains("opus", ignoreCase = true) == true -> "Opus"
            codec?.contains("mp4a", ignoreCase = true) == true -> "AAC"
            codec?.contains("mp3", ignoreCase = true) == true -> "MP3"
            else -> ext?.uppercase() ?: "Audio"
        }
        return "$brStr ($codecStr)"
    }

    private data class RawFormatCandidate(
        val formatId: String,
        val rawLang: String?,
        val formatNote: String?,
        val format: String?,
        val languagePreference: Int?,
        val abr: Double?,
        val asr: Long?,
        val channels: Int?,
        val acodec: String,
        val ext: String,
        val filesize: Long?
    )
}
