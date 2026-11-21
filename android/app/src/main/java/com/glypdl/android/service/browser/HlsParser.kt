/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.glypdl.android.service.browser

import com.glypdl.android.data.model.MediaVariant
import java.net.URI
import java.util.Locale

data class HlsAudioTrack(
    val groupId: String,
    val name: String,
    val language: String?,
    val uri: String?,
    val isDefault: Boolean
)

data class HlsSubtitleTrack(
    val groupId: String,
    val name: String,
    val language: String?,
    val uri: String?,
    val isDefault: Boolean
)

data class HlsManifestResult(
    val isMaster: Boolean,
    val isLive: Boolean,
    val isDrmProtected: Boolean,
    val durationSeconds: Double?,
    val variants: List<MediaVariant>,
    val audioTracks: List<HlsAudioTrack> = emptyList(),
    val subtitleTracks: List<HlsSubtitleTrack> = emptyList()
)

object HlsParser {

    private val DRM_KEY_FORMATS = listOf(
        "com.widevine",
        "widevine",
        "com.apple.streamingkeydelivery",
        "fairplay",
        "com.microsoft.playready",
        "playready",
        "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"
    )

    fun parse(manifestText: String, manifestUrl: String): HlsManifestResult {
        val lines = manifestText.lines().map { it.trim() }

        var isDrmProtected = false
        var durationSeconds: Double? = null
        var isMaster = false
        var hasEndList = false

        val variants = mutableListOf<MediaVariant>()
        val audioTracks = mutableListOf<HlsAudioTrack>()
        val subtitleTracks = mutableListOf<HlsSubtitleTrack>()

        var pendingStreamInf: Map<String, String>? = null
        var totalInfDuration = 0.0

        for (i in lines.indices) {
            val line = lines[i]
            if (line.isBlank()) continue

            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                isMaster = true
                pendingStreamInf = parseAttributes(line.removePrefix("#EXT-X-STREAM-INF:"))
            } else if (pendingStreamInf != null && !line.startsWith("#")) {
                // Next non-comment line is the rendition URI
                val streamAttrs = pendingStreamInf
                pendingStreamInf = null

                val resolvedUrl = resolveUri(manifestUrl, line)
                val bandwidth = streamAttrs["BANDWIDTH"]?.toLongOrNull()
                    ?: streamAttrs["AVERAGE-BANDWIDTH"]?.toLongOrNull()

                val resolution = streamAttrs["RESOLUTION"]
                val (width, height) = parseResolution(resolution)
                val frameRate = streamAttrs["FRAME-RATE"]?.toDoubleOrNull()?.toInt()
                val codecs = streamAttrs["CODECS"]
                val audioGroup = streamAttrs["AUDIO"]

                val label = buildQualityLabel(height, bandwidth, frameRate)

                variants.add(
                    MediaVariant(
                        label = label,
                        width = width,
                        height = height,
                        fps = frameRate,
                        bandwidthBps = bandwidth,
                        codecs = codecs,
                        estimatedSizeBytes = null,
                        url = resolvedUrl,
                        audioGroup = audioGroup,
                        isAudioOnly = (width == null || width == 0) && (height == null || height == 0)
                    )
                )
            } else if (line.startsWith("#EXT-X-MEDIA:")) {
                isMaster = true
                val attrs = parseAttributes(line.removePrefix("#EXT-X-MEDIA:"))
                val type = attrs["TYPE"]?.uppercase(Locale.ROOT)
                val groupId = attrs["GROUP-ID"] ?: ""
                val name = attrs["NAME"] ?: "Track"
                val lang = attrs["LANGUAGE"]
                val uri = attrs["URI"]?.let { resolveUri(manifestUrl, it) }
                val isDefault = attrs["DEFAULT"]?.equals("YES", ignoreCase = true) == true

                when (type) {
                    "AUDIO" -> {
                        audioTracks.add(
                            HlsAudioTrack(
                                groupId = groupId,
                                name = name,
                                language = lang,
                                uri = uri,
                                isDefault = isDefault
                            )
                        )
                    }
                    "SUBTITLES" -> {
                        subtitleTracks.add(
                            HlsSubtitleTrack(
                                groupId = groupId,
                                name = name,
                                language = lang,
                                uri = uri,
                                isDefault = isDefault
                            )
                        )
                    }
                }
            } else if (line.startsWith("#EXT-X-KEY:")) {
                val attrs = parseAttributes(line.removePrefix("#EXT-X-KEY:"))
                val keyFormat = attrs["KEYFORMAT"]?.lowercase(Locale.ROOT) ?: ""
                val method = attrs["METHOD"]?.uppercase(Locale.ROOT) ?: ""

                if (DRM_KEY_FORMATS.any { keyFormat.contains(it) } ||
                    (method.isNotEmpty() && method != "NONE" && method != "AES-128" && method != "SAMPLE-AES-CTR")) {
                    isDrmProtected = true
                }
            } else if (line.startsWith("#EXTINF:")) {
                val durStr = line.removePrefix("#EXTINF:").substringBefore(',').trim()
                durStr.toDoubleOrNull()?.let {
                    totalInfDuration += it
                }
            } else if (line.startsWith("#EXT-X-ENDLIST")) {
                hasEndList = true
            }
        }

        if (totalInfDuration > 0.0) {
            durationSeconds = totalInfDuration
        }

        val isLive = !isMaster && !hasEndList

        // Sort variants by bandwidth/resolution descending
        val sortedVariants = variants.sortedWith(
            compareByDescending<MediaVariant> { it.height ?: 0 }
                .thenByDescending { it.bandwidthBps ?: 0L }
        )

        return HlsManifestResult(
            isMaster = isMaster,
            isLive = isLive,
            isDrmProtected = isDrmProtected,
            durationSeconds = durationSeconds,
            variants = sortedVariants,
            audioTracks = audioTracks,
            subtitleTracks = subtitleTracks
        )
    }

    private fun parseAttributes(attrString: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val regex = Regex("""([A-Z0-9-]+)=(?:"([^"]*)"|([^,]*))""")
        for (match in regex.findAll(attrString)) {
            val key = match.groupValues[1]
            val value = match.groupValues[2].ifEmpty { match.groupValues[3] }
            result[key] = value.trim()
        }
        return result
    }

    private fun parseResolution(resolution: String?): Pair<Int?, Int?> {
        if (resolution.isNullOrBlank() || !resolution.contains("x")) return null to null
        val parts = resolution.split("x")
        val w = parts.getOrNull(0)?.toIntOrNull()
        val h = parts.getOrNull(1)?.toIntOrNull()
        return w to h
    }

    private fun buildQualityLabel(height: Int?, bandwidth: Long?, fps: Int?): String {
        return when {
            height != null && height >= 2160 -> "4K (2160p)" + if (fps != null && fps > 30) " $fps fps" else ""
            height != null && height >= 1440 -> "1440p" + if (fps != null && fps > 30) " $fps fps" else ""
            height != null && height >= 1080 -> "1080p" + if (fps != null && fps > 30) " $fps fps" else ""
            height != null && height >= 720 -> "720p" + if (fps != null && fps > 30) " $fps fps" else ""
            height != null && height >= 480 -> "480p"
            height != null && height >= 360 -> "360p"
            height != null -> "${height}p"
            bandwidth != null -> "${bandwidth / 1000} kbps"
            else -> "Stream"
        }
    }

    fun resolveUri(baseUri: String, relativeUri: String): String {
        return try {
            if (relativeUri.startsWith("http://") || relativeUri.startsWith("https://")) {
                relativeUri
            } else {
                URI(baseUri).resolve(relativeUri).toString()
            }
        } catch (_: Exception) {
            relativeUri
        }
    }
}
