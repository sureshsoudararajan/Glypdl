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

import com.glypdl.android.data.model.AudioQuality
import com.glypdl.android.data.model.MediaFormat
import com.glypdl.android.data.model.VideoQuality

object FormatNormalizer {

    private data class ResolutionTier(
        val label: String,
        val minDimension: Int,
        val maxDimension: Int,
        val targetHeight: Int
    )

    private val STANDARD_TIERS = listOf(
        ResolutionTier("4K", 2000, 4320, 2160),
        ResolutionTier("1440p", 1350, 1999, 1440),
        ResolutionTier("1080p", 950, 1349, 1080),
        ResolutionTier("720p", 650, 949, 720),
        ResolutionTier("480p", 420, 649, 480),
        ResolutionTier("360p", 300, 419, 360),
        ResolutionTier("240p", 200, 299, 240),
        ResolutionTier("144p", 100, 199, 144)
    )

    /**
     * Determines the effective resolution line height.
     * For portrait/vertical video (e.g. 1080x1920 or 144x256), the effective tier is minOf(width, height).
     */
    fun getEffectiveDimension(fmt: MediaFormat): Int? {
        if (fmt.width != null && fmt.height != null && fmt.width > 0 && fmt.height > 0) {
            return minOf(fmt.width, fmt.height)
        }
        return fmt.height ?: parseHeightFromResolution(fmt.resolution)
    }

    /**
     * Normalizes raw formats into standard user-facing [VideoQuality] tiers.
     * Only returns tiers that actually exist in the source stream.
     * Ensures audio merging format selectors are properly constructed for video-only streams.
     */
    fun normalizeVideoQualities(formats: List<MediaFormat>): List<VideoQuality> {
        if (formats.isEmpty()) return emptyList()

        // Find the best audio format to estimate combined size if video is video-only
        val bestAudio = formats.filter { it.isAudioOnly }
            .maxByOrNull { it.abr ?: 0f }
        val bestAudioSize = bestAudio?.filesize ?: bestAudio?.filesizeApprox ?: 0L

        val result = mutableListOf<VideoQuality>()
        val videoFormats = formats.filter { !it.isAudioOnly && getEffectiveDimension(it) != null }

        for (tier in STANDARD_TIERS) {
            // Find formats matching this tier
            val matching = videoFormats.filter { fmt ->
                val dim = getEffectiveDimension(fmt) ?: 0
                dim in tier.minDimension..tier.maxDimension
            }

            if (matching.isNotEmpty()) {
                // Pick best format in tier: prefer one with audio or highest bitrate/filesize
                val best = matching.maxByOrNull { fmt ->
                    val sizeScore = fmt.filesize ?: fmt.filesizeApprox ?: 0L
                    val tbrScore = ((fmt.tbr ?: 0f) * 1000).toLong()
                    sizeScore.coerceAtLeast(tbrScore)
                } ?: matching.first()

                val rawSize = best.filesize ?: best.filesizeApprox ?: 0L
                val totalEstimatedSize = if (best.isVideoOnly && bestAudioSize > 0) {
                    rawSize + bestAudioSize
                } else {
                    rawSize
                }

                val formatSelector = if (best.isVideoOnly) {
                    "${best.formatId}+bestaudio/bestvideo[height<=${tier.targetHeight}]+bestaudio/best[height<=${tier.targetHeight}]"
                } else {
                    best.formatId
                }

                result.add(
                    VideoQuality(
                        label = tier.label,
                        width = best.width,
                        height = best.height ?: tier.targetHeight,
                        codec = best.vcodec?.takeIf { it.isNotBlank() && it != "none" },
                        estimatedSize = if (totalEstimatedSize > 0) totalEstimatedSize else null,
                        formatSelector = formatSelector,
                        formatId = best.formatId,
                        ext = if (best.ext.isNotBlank() && best.ext != "none") best.ext else "mp4",
                        hasAudio = !best.isVideoOnly
                    )
                )
            }
        }

        // If no standard tiers matched (e.g. non-standard custom aspect ratios)
        if (result.isEmpty() && videoFormats.isNotEmpty()) {
            val best = videoFormats.maxByOrNull { it.filesize ?: it.filesizeApprox ?: 0L } ?: videoFormats.first()
            val dim = getEffectiveDimension(best) ?: 720
            val rawSize = best.filesize ?: best.filesizeApprox ?: 0L
            val totalSize = if (best.isVideoOnly && bestAudioSize > 0) rawSize + bestAudioSize else rawSize

            result.add(
                VideoQuality(
                    label = "${dim}p",
                    width = best.width,
                    height = best.height ?: dim,
                    codec = best.vcodec?.takeIf { it.isNotBlank() && it != "none" },
                    estimatedSize = if (totalSize > 0) totalSize else null,
                    formatSelector = if (best.isVideoOnly) "${best.formatId}+bestaudio/best" else best.formatId,
                    formatId = best.formatId,
                    ext = if (best.ext.isNotBlank() && best.ext != "none") best.ext else "mp4",
                    hasAudio = !best.isVideoOnly
                )
            )
        }

        return result
    }

    /**
     * Normalizes audio formats into standard bitrate tiers (e.g. 320, 256, 192, 160, 128 kbps).
     * Only displays bitrates that are actually available from the source stream.
     */
    fun normalizeAudioQualities(formats: List<MediaFormat>): List<AudioQuality> {
        val audioFormats = formats.filter {
            it.isAudioOnly || (it.acodec != null && it.acodec != "none" && (it.vcodec == null || it.vcodec == "none")) || (it.abr != null && it.abr > 0f && (it.vcodec == null || it.vcodec == "none"))
        }
        if (audioFormats.isEmpty()) return emptyList()

        val result = mutableListOf<AudioQuality>()
        val seenBitrateAndExt = mutableSetOf<String>()

        // Sort descending by audio bitrate
        val sorted = audioFormats.sortedByDescending { it.abr ?: it.tbr ?: 0f }

        for (fmt in sorted) {
            val rawAbr = (fmt.abr ?: fmt.tbr ?: 0f).toInt()
            val effectiveBitrate = if (rawAbr > 0) snapToCommonBitrate(rawAbr) else 128

            val ext = when {
                fmt.ext.isNotBlank() && fmt.ext != "none" -> fmt.ext.lowercase()
                fmt.acodec?.contains("opus", ignoreCase = true) == true -> "opus"
                fmt.acodec?.contains("mp4a", ignoreCase = true) == true -> "m4a"
                fmt.acodec?.contains("mp3", ignoreCase = true) == true -> "mp3"
                else -> "m4a"
            }

            val key = "$effectiveBitrate-$ext"
            if (seenBitrateAndExt.add(key)) {
                result.add(
                    AudioQuality(
                        bitrateKbps = effectiveBitrate,
                        codec = fmt.acodec?.takeIf { it.isNotBlank() && it != "none" },
                        extension = ext,
                        estimatedSize = fmt.filesize ?: fmt.filesizeApprox,
                        formatSelector = fmt.formatId.ifBlank { "bestaudio" },
                        formatId = fmt.formatId
                    )
                )
            }
        }

        return result.sortedByDescending { it.bitrateKbps }
    }

    private fun snapToCommonBitrate(rawAbr: Int): Int {
        return when {
            rawAbr >= 300 -> 320
            rawAbr in 240..299 -> 256
            rawAbr in 180..239 -> 192
            rawAbr in 140..179 -> 160
            rawAbr in 110..139 -> 128
            rawAbr in 80..109 -> 96
            rawAbr in 50..79 -> 64
            else -> rawAbr.coerceAtLeast(32)
        }
    }

    private fun parseHeightFromResolution(res: String?): Int? {
        if (res == null) return null
        val match = Regex("(\\d+)[pP]").find(res)
        if (match != null) return match.groupValues[1].toIntOrNull()
        val dimMatch = Regex("(\\d+)x(\\d+)").find(res)
        if (dimMatch != null) {
            val d1 = dimMatch.groupValues[1].toIntOrNull() ?: 0
            val d2 = dimMatch.groupValues[2].toIntOrNull() ?: 0
            return if (d1 > 0 && d2 > 0) minOf(d1, d2) else maxOf(d1, d2)
        }
        return null
    }
}
