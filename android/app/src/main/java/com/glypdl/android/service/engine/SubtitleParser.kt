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

import com.glypdl.android.data.model.SubtitleTrack
import com.glypdl.android.data.model.SubtitleType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Parses subtitle and caption metadata from yt-dlp structured JSON (`-J`).
 *
 * Handles:
 * - Manually authored subtitles (`subtitles`)
 * - Machine-generated automatic captions (`automatic_captions`)
 * - Format lists (SRT, VTT, ASS, SSA, JSON3, TTML, etc.)
 * - Exclusion of live chat replay logs (`live_chat`)
 * - Localization of language codes to human-readable names
 * - Duplicate language deduplication and format merging
 */
object SubtitleParser {

    private val EXCLUDED_KEYS = setOf(
        "live_chat",
        "chat",
        "danmaku"
    )

    /**
     * Parse manually authored subtitle tracks from the raw yt-dlp JSON object.
     */
    fun parseManualSubtitles(json: JSONObject?): List<SubtitleTrack> {
        if (json == null) return emptyList()
        val subsObj = json.optJSONObject("subtitles") ?: return emptyList()
        return parseTracksFromObject(subsObj, SubtitleType.MANUAL)
    }

    /**
     * Parse auto-generated caption tracks from the raw yt-dlp JSON object.
     */
    fun parseAutomaticCaptions(json: JSONObject?): List<SubtitleTrack> {
        if (json == null) return emptyList()
        val autoSubsObj = json.optJSONObject("automatic_captions") ?: return emptyList()
        return parseTracksFromObject(autoSubsObj, SubtitleType.AUTOMATIC)
    }

    /**
     * Parse both manual and automatic subtitle tracks from the raw yt-dlp JSON object.
     */
    fun parseAllSubtitles(json: JSONObject?): Pair<List<SubtitleTrack>, List<SubtitleTrack>> {
        val manual = parseManualSubtitles(json)
        val auto = parseAutomaticCaptions(json)
        return Pair(manual, auto)
    }

    private fun parseTracksFromObject(
        obj: JSONObject,
        type: SubtitleType
    ): List<SubtitleTrack> {
        val tracksMap = mutableMapOf<String, SubtitleTrack>()

        // Pre-scan keys to identify original ASR tracks (e.g. "ta-orig")
        val allKeys = mutableSetOf<String>()
        val keyScanner = obj.keys()
        while (keyScanner.hasNext()) {
            allKeys.add(keyScanner.next().trim())
        }

        val keys = obj.keys()
        while (keys.hasNext()) {
            val langCode = keys.next()?.trim() ?: continue
            if (langCode.isBlank() || isExcludedKey(langCode)) continue

            // For auto captions, prefer native original transcript (e.g. "ta-orig") over translated ("ta")
            // to prevent calling YouTube's rate-limited translation endpoint (HTTP 429)
            if (type == SubtitleType.AUTOMATIC && !langCode.endsWith("-orig") && allKeys.contains("$langCode-orig")) {
                continue
            }

            val formatsArray = obj.optJSONArray(langCode) ?: JSONArray()
            val formatList = mutableListOf<String>()
            var extractedName: String? = null
            var firstUrl: String? = null
            var isDefault = false

            for (i in 0 until formatsArray.length()) {
                val fmt = formatsArray.optJSONObject(i) ?: continue
                val ext = fmt.optString("ext").trim().lowercase().removePrefix(".")
                if (ext.isNotBlank() && !formatList.contains(ext)) {
                    formatList.add(ext)
                }

                if (extractedName.isNullOrBlank()) {
                    val n = fmt.optString("name").trim()
                    if (n.isNotBlank()) extractedName = n
                }

                if (firstUrl.isNullOrBlank()) {
                    val u = fmt.optString("url").trim()
                    if (u.isNotBlank()) firstUrl = u
                }

                if (fmt.optBoolean("default", false)) {
                    isDefault = true
                }
            }

            val languageName = resolveLanguageName(langCode, extractedName)
            val isTranslated = type == SubtitleType.AUTOMATIC && (firstUrl?.contains("tlang=") == true || (!langCode.endsWith("-orig") && allKeys.contains("$langCode-orig")))
            val isOriginalTrack = when (type) {
                SubtitleType.MANUAL -> true
                SubtitleType.AUTOMATIC -> !isTranslated
            }

            // User requirement: Only show and allow selecting original subtitles.
            // Exclude all machine-translated auto-caption tracks (which contain tlang= and are rate-limited/blocked).
            if (type == SubtitleType.AUTOMATIC && !isOriginalTrack) {
                continue
            }

            val existing = tracksMap[langCode]
            if (existing != null) {
                // Merge formats from duplicate language entries
                val mergedFormats = (existing.formats + formatList).distinct()
                tracksMap[langCode] = existing.copy(
                    formats = mergedFormats,
                    isDefault = existing.isDefault || isDefault,
                    url = existing.url ?: firstUrl,
                    isOriginal = existing.isOriginal || isOriginalTrack
                )
            } else {
                tracksMap[langCode] = SubtitleTrack(
                    languageCode = langCode,
                    languageName = languageName,
                    type = type,
                    formats = formatList,
                    url = firstUrl,
                    isDefault = isDefault,
                    isOriginal = isOriginalTrack
                )
            }
        }

        return tracksMap.values.sortedWith(
            compareBy<SubtitleTrack> { !it.isDefault }
                .thenBy { !isPreferredLocale(it.languageCode) }
                .thenBy { it.languageName.lowercase(Locale.getDefault()) }
        )
    }

    private fun isExcludedKey(key: String): Boolean {
        val lower = key.lowercase(Locale.ROOT)
        return EXCLUDED_KEYS.any { lower.contains(it) }
    }

    /**
     * Resolves a human-readable language label from the ISO code and metadata name.
     */
    fun resolveLanguageName(langCode: String, metadataName: String? = null): String {
        if (!metadataName.isNullOrBlank() && !metadataName.equals(langCode, ignoreCase = true)) {
            return metadataName
        }

        val isOrig = langCode.endsWith("-orig")
        val cleanCode = langCode.removeSuffix("-orig")

        return try {
            val locale = Locale.forLanguageTag(cleanCode)
            val displayName = locale.getDisplayName(Locale.getDefault())
            val baseName = if (displayName.isNotBlank() && !displayName.equals(cleanCode, ignoreCase = true)) {
                displayName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } else {
                // Fallback: test primary language tag before subtag (e.g. "en" from "en-US")
                val baseTag = cleanCode.substringBefore('-').substringBefore('_')
                val baseLocale = Locale(baseTag)
                val directName = baseLocale.getDisplayLanguage(Locale.getDefault())
                if (directName.isNotBlank() && !directName.equals(baseTag, ignoreCase = true)) {
                    val region = locale.displayCountry.takeIf { it.isNotBlank() }
                    val label = if (region != null) "$directName ($region)" else directName
                    label.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                } else {
                    cleanCode.uppercase(Locale.ROOT)
                }
            }
            if (isOrig) "$baseName (Original)" else baseName
        } catch (e: Exception) {
            if (isOrig) "${cleanCode.uppercase(Locale.ROOT)} (Original)" else cleanCode.uppercase(Locale.ROOT)
        }
    }

    private fun isPreferredLocale(langCode: String): Boolean {
        val current = Locale.getDefault().language
        val base = langCode.substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
        return base == current.lowercase(Locale.ROOT) || base == "en"
    }
}
