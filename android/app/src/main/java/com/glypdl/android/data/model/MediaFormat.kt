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

data class AudioQuality(
    val bitrateKbps: Int,         // e.g. 320, 256, 192, 160, 128
    val codec: String? = null,
    val extension: String = "m4a",// e.g. "m4a", "opus", "webm", "mp3"
    val estimatedSize: Long? = null,
    val formatSelector: String,
    val formatId: String
) {
    val displayBitrate: String
        get() = "$bitrateKbps kbps"

    val displayExtension: String
        get() = extension.uppercase()

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
    val displaySize: String
        get() {
            val size = filesize ?: filesizeApprox ?: return "Unknown size"
            if (size <= 0) return "Unknown size"
            val units = arrayOf("B", "KB", "MB", "GB", "TB")
            var digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
            if (digitGroups > 4) digitGroups = 4
            return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
        }
        
    val displayResolution: String
        get() = if (isAudioOnly) "Audio" else resolution ?: "Unknown"
}
