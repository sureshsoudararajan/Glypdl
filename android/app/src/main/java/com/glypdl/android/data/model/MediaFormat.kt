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

/**
 * Normalized user-facing video quality preset tier (e.g. 4K, 1440p, 1080p, 720p).
 *
 * Normalized by [FormatNormalizer] from raw stream formats to provide clean, unified options
 * in the download configuration UI.
 *
 * @property label Clean user-facing resolution name (e.g., "4K", "1080p", "720p").
 * @property width Video pixel width if known.
 * @property height Video pixel height / vertical resolution lines.
 * @property codec Video encoding codec identifier (e.g., "avc1", "vp9", "av01").
 * @property fps Frames per second rate if specified.
 * @property estimatedSize Estimated total size in bytes, combining audio size if video-only.
 * @property formatSelector Safe yt-dlp format selector string used for downloading.
 * @property formatId Underlying format identifier from the source provider.
 * @property ext Video container extension (e.g. "mp4", "mkv", "webm").
 * @property hasAudio True if the stream is already multiplexed with an audio channel.
 */
data class VideoQuality(
    val label: String,            // e.g. "4K", "1440p", "1080p", "720p", "480p", "360p", "240p", "144p"
    val width: Int? = null,
    val height: Int? = null,
    val codec: String? = null,
    val fps: Int? = null,
    val estimatedSize: Long? = null,
    val formatSelector: String,   // yt-dlp format selector e.g. "137+140/best"
    val formatId: String,
    val ext: String = "mp4",
    val hasAudio: Boolean = false
) {
    /** Formats [estimatedSize] into human-readable representation (e.g. "124.5 MB"). */
    val displaySize: String
        get() {
            val size = estimatedSize ?: return ""
            if (size <= 0) return ""
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups > 4) digitGroups = 4
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
}

/**
 * Normalized user-facing audio quality tier (e.g. 320 kbps, 256 kbps, 128 kbps).
 *
 * @property bitrateKbps Target or source audio bitrate in kilobits per second.
 * @property codec Audio codec identifier (e.g. "opus", "mp4a.40.2", "mp3").
 * @property extension Audio file extension (e.g. "m4a", "opus", "mp3").
 * @property estimatedSize Estimated file size in bytes.
 * @property formatSelector yt-dlp format selector for audio stream.
 * @property formatId Source format ID.
 */
data class AudioQuality(
    val bitrateKbps: Int,         // e.g. 320, 256, 192, 160, 128
    val codec: String? = null,
    val extension: String = "m4a",// e.g. "m4a", "opus", "webm", "mp3"
    val estimatedSize: Long? = null,
    val formatSelector: String,
    val formatId: String
) {
    /** Formats audio bitrate with unit label (e.g. "320 kbps"). */
    val displayBitrate: String
        get() = "$bitrateKbps kbps"

    /** Formatted uppercase audio extension (e.g. "M4A", "OPUS"). */
    val displayExtension: String
        get() = extension.uppercase()

    /** Formats [estimatedSize] into human-readable representation (e.g. "8.2 MB"). */
    val displaySize: String
        get() {
            val size = estimatedSize ?: return ""
            if (size <= 0) return ""
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups > 4) digitGroups = 4
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
}

/**
 * Raw media stream format metadata directly parsed from yt-dlp extractor output.
 *
 * @property formatId Provider stream ID (e.g. "137", "22", "http-1080").
 * @property ext Stream container extension.
 * @property resolution Descriptive resolution string from extractor.
 * @property width Stream pixel width.
 * @property height Stream pixel height.
 * @property vcodec Video codec name ("none" for audio-only streams).
 * @property acodec Audio codec name ("none" for video-only streams).
 * @property filesize Exact file size in bytes if reported.
 * @property filesizeApprox Approximate file size in bytes if exact size is missing.
 * @property abr Audio bitrate in kbps.
 * @property tbr Total average bitrate in kbps.
 * @property isVideoOnly True if stream contains only video frames and no audio.
 * @property isAudioOnly True if stream contains only audio and no video frames.
 * @property formatSelector Custom selector if defined.
 */
data class MediaFormat(
    val formatId: String,
    val ext: String = "mp4",
    val resolution: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val vcodec: String? = null,
    val acodec: String? = null,
    val filesize: Long? = null,
    val filesizeApprox: Long? = null,
    val abr: Float? = null,
    val tbr: Float? = null,
    val isVideoOnly: Boolean = false,
    val isAudioOnly: Boolean = false,
    val formatSelector: String? = null
) {
    /** Formats [filesize] or [filesizeApprox] into human-readable string. */
    val displaySize: String
        get() {
            val size = filesize ?: filesizeApprox ?: return "Unknown size"
            if (size <= 0) return "Unknown size"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups > 4) digitGroups = 4
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }

    /** Formats user-friendly resolution descriptor. */
    val displayResolution: String
        get() = if (isAudioOnly) "Audio" else resolution ?: "Unknown"
}
