/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.model

import java.util.Locale

/**
 * Classification of an audio track on YouTube.
 */
enum class AudioTrackClassification {
    /**
     * Confidently identified as the creator's original uploaded audio track.
     */
    ORIGINAL,

    /**
     * Confidently identified as a dubbed or translated audio track.
     */
    DUBBED,

    /**
     * Metadata does not clearly distinguish whether the track is original or dubbed.
     */
    UNKNOWN
}

/**
 * Represents an individual audio stream track for a YouTube video parsed from yt-dlp metadata.
 *
 * @property formatId Underlying format identifier from the source provider (e.g. "251-9", "140-20").
 * @property languageCode Normalized BCP-47 or ISO language tag (e.g. "en-US", "ta", "hi").
 * @property languageName Clean human-readable language name (e.g. "English (United States)", "Tamil").
 * @property formatNote Format note or descriptive string from yt-dlp (e.g. "Tamil, medium").
 * @property codec Audio encoding codec (e.g. "opus", "mp4a.40.2").
 * @property bitrate Audio bitrate in kbps (e.g. 128, 48).
 * @property sampleRate Audio sampling rate in Hz (e.g. 48000).
 * @property channels Audio channels count (e.g. 2 for stereo).
 * @property extension File extension of the audio container (e.g. "webm", "m4a").
 * @property fileSize File size in bytes if known or estimated.
 * @property isOriginal True if this stream represents the original audio.
 * @property trackClassification Classification of the track ([AudioTrackClassification.ORIGINAL], [AudioTrackClassification.DUBBED], or [AudioTrackClassification.UNKNOWN]).
 * @property qualityLabel Human-readable label for quality selection (e.g. "128 kbps (Opus)").
 */
data class YouTubeAudioTrack(
    val formatId: String,
    val languageCode: String? = null,
    val languageName: String? = null,
    val formatNote: String? = null,
    val codec: String? = null,
    val bitrate: Long? = null,
    val sampleRate: Long? = null,
    val channels: Int? = null,
    val extension: String? = null,
    val fileSize: Long? = null,
    val isOriginal: Boolean = false,
    val trackClassification: AudioTrackClassification = AudioTrackClassification.UNKNOWN,
    val qualityLabel: String? = null
) {
    /** Formats audio bitrate with unit label (e.g. "128 kbps"). */
    val displayBitrate: String
        get() {
            val br = bitrate ?: return ""
            return "$br kbps"
        }

    /** Formats audio codec in uppercase representation (e.g. "OPUS", "AAC"). */
    val displayCodec: String
        get() {
            val c = codec ?: return ""
            return when {
                c.contains("opus", ignoreCase = true) -> "Opus"
                c.contains("mp4a", ignoreCase = true) -> "AAC"
                c.contains("mp3", ignoreCase = true) -> "MP3"
                c.contains("flac", ignoreCase = true) -> "FLAC"
                else -> c.uppercase()
            }
        }

    /** Formatted uppercase audio extension (e.g. "WEBM", "M4A"). */
    val displayExtension: String
        get() = (extension ?: "m4a").uppercase()

    /** Formats [fileSize] into human-readable representation (e.g. "23.5 MB"). */
    val displaySize: String
        get() {
            val size = fileSize ?: return ""
            if (size <= 0) return ""
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups > 4) digitGroups = 4
            return String.format(Locale.US, "%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
}

/**
 * Represents a language group containing one or more audio quality variants.
 *
 * @property languageCode Normalized language code (e.g. "en-US", "ta", "hi").
 * @property languageName Clean human-readable language name (e.g. "English (United States)", "Tamil").
 * @property classification Classification of this language track ([AudioTrackClassification]).
 * @property tracks Available quality streams for this language, ordered from best to lowest quality.
 * @property selectedTrack Currently active track choice for this language.
 */
data class YouTubeAudioLanguage(
    val languageCode: String,
    val languageName: String,
    val classification: AudioTrackClassification,
    val tracks: List<YouTubeAudioTrack>,
    val selectedTrack: YouTubeAudioTrack = tracks.firstOrNull() ?: YouTubeAudioTrack(formatId = "")
) {
    /** True if this is the creator's original audio track. */
    val isOriginal: Boolean
        get() = classification == AudioTrackClassification.ORIGINAL

    /** Clean classification badge label. */
    val classificationLabel: String
        get() = when (classification) {
            AudioTrackClassification.ORIGINAL -> "Original"
            AudioTrackClassification.DUBBED -> "Dubbed"
            AudioTrackClassification.UNKNOWN -> "Unknown"
        }
}
