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
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.net.URI
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

data class DashManifestResult(
    val isLive: Boolean,
    val isDrmProtected: Boolean,
    val durationSeconds: Double?,
    val videoVariants: List<MediaVariant>,
    val audioVariants: List<MediaVariant>,
    val subtitleVariants: List<MediaVariant> = emptyList()
)

object DashParser {

    private val DRM_SCHEMES = setOf(
        "edef8ba9-79d6-4ace-a3c8-27dcd51d21ed", // Widevine
        "9a04f079-9840-4286-ab92-e65be0885f95", // PlayReady
        "94ce86fb-07ff-4f43-adb8-93d2fa968ca2"  // FairPlay
    )

    fun parse(mpdXml: String, mpdUrl: String): DashManifestResult {
        var isDrmProtected = false
        var durationSeconds: Double? = null
        var isLive = false

        val videoVariants = mutableListOf<MediaVariant>()
        val audioVariants = mutableListOf<MediaVariant>()
        val subtitleVariants = mutableListOf<MediaVariant>()

        try {
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(ByteArrayInputStream(mpdXml.toByteArray(Charsets.UTF_8)))
            val root = doc.documentElement

            val type = root.getAttribute("type").lowercase(Locale.ROOT)
            val mediaDurationAttr = root.getAttribute("mediaPresentationDuration")
            durationSeconds = parseIsoDuration(mediaDurationAttr)
            isLive = type == "dynamic" || (durationSeconds == null && type.isEmpty())

            var globalBaseUrl = mpdUrl
            val mpdChildren = root.childNodes
            for (i in 0 until mpdChildren.length) {
                val node = mpdChildren.item(i)
                if (node is Element && node.tagName.equals("BaseURL", ignoreCase = true)) {
                    val text = node.textContent.trim()
                    if (text.isNotEmpty()) {
                        globalBaseUrl = resolveUri(globalBaseUrl, text)
                    }
                }
            }

            // Find all AdaptationSets in the document
            val adaptationSets = doc.getElementsByTagName("AdaptationSet")
            for (i in 0 until adaptationSets.length) {
                val adaptationSet = adaptationSets.item(i) as? Element ?: continue

                val mimeType = adaptationSet.getAttribute("mimeType").lowercase(Locale.ROOT)
                val contentType = adaptationSet.getAttribute("contentType").lowercase(Locale.ROOT)
                val lang = adaptationSet.getAttribute("lang").takeIf { it.isNotBlank() }
                val defaultCodecs = adaptationSet.getAttribute("codecs").takeIf { it.isNotBlank() }

                // Check for ContentProtection tags (DRM)
                val contentProtections = adaptationSet.getElementsByTagName("ContentProtection")
                for (j in 0 until contentProtections.length) {
                    val cp = contentProtections.item(j) as? Element ?: continue
                    val schemeIdUri = cp.getAttribute("schemeIdUri").lowercase(Locale.ROOT)
                    if (DRM_SCHEMES.any { schemeIdUri.contains(it) }) {
                        isDrmProtected = true
                    }
                }

                // Check AdaptationSet BaseURL
                var adaptBaseUrl = globalBaseUrl
                val adaptChildren = adaptationSet.childNodes
                for (j in 0 until adaptChildren.length) {
                    val node = adaptChildren.item(j)
                    if (node is Element && node.tagName.equals("BaseURL", ignoreCase = true)) {
                        val text = node.textContent.trim()
                        if (text.isNotEmpty()) {
                            adaptBaseUrl = resolveUri(adaptBaseUrl, text)
                        }
                    }
                }

                val isVideo = contentType == "video" || mimeType.startsWith("video/")
                val isAudio = contentType == "audio" || mimeType.startsWith("audio/")
                val isText = contentType == "text" || mimeType.startsWith("text/") || mimeType.contains("vtt") || mimeType.contains("ttml")

                val representations = adaptationSet.getElementsByTagName("Representation")
                for (k in 0 until representations.length) {
                    val rep = representations.item(k) as? Element ?: continue

                    val id = rep.getAttribute("id")
                    val bandwidth = rep.getAttribute("bandwidth").toLongOrNull()
                    val width = rep.getAttribute("width").toIntOrNull()
                    val height = rep.getAttribute("height").toIntOrNull()
                    val frameRate = rep.getAttribute("frameRate").toDoubleOrNull()?.toInt()
                    val codecs = rep.getAttribute("codecs").takeIf { it.isNotBlank() } ?: defaultCodecs

                    var repUrl = adaptBaseUrl
                    val repChildren = rep.childNodes
                    for (l in 0 until repChildren.length) {
                        val node = repChildren.item(l)
                        if (node is Element && node.tagName.equals("BaseURL", ignoreCase = true)) {
                            val text = node.textContent.trim()
                            if (text.isNotEmpty()) {
                                repUrl = resolveUri(repUrl, text)
                            }
                        }
                    }

                    val estimatedSize = if (bandwidth != null && durationSeconds != null) {
                        (bandwidth * durationSeconds / 8.0).toLong()
                    } else null

                    val variant = MediaVariant(
                        label = buildDashLabel(isVideo, isAudio, height, bandwidth, lang),
                        width = width,
                        height = height,
                        fps = frameRate,
                        bandwidthBps = bandwidth,
                        codecs = codecs,
                        estimatedSizeBytes = estimatedSize,
                        url = repUrl,
                        language = lang,
                        isAudioOnly = isAudio
                    )

                    when {
                        isVideo -> videoVariants.add(variant)
                        isAudio -> audioVariants.add(variant)
                        isText -> subtitleVariants.add(variant)
                    }
                }
            }
        } catch (_: Exception) {}

        val sortedVideos = videoVariants.sortedWith(
            compareByDescending<MediaVariant> { it.height ?: 0 }
                .thenByDescending { it.bandwidthBps ?: 0L }
        )

        val sortedAudios = audioVariants.sortedByDescending { it.bandwidthBps ?: 0L }

        return DashManifestResult(
            isLive = isLive,
            isDrmProtected = isDrmProtected,
            durationSeconds = durationSeconds,
            videoVariants = sortedVideos,
            audioVariants = sortedAudios,
            subtitleVariants = subtitleVariants
        )
    }

    private fun buildDashLabel(isVideo: Boolean, isAudio: Boolean, height: Int?, bandwidth: Long?, lang: String?): String {
        return when {
            isVideo -> {
                when {
                    height != null && height >= 2160 -> "4K (2160p)"
                    height != null && height >= 1440 -> "1440p"
                    height != null && height >= 1080 -> "1080p"
                    height != null && height >= 720 -> "720p"
                    height != null && height >= 480 -> "480p"
                    height != null && height >= 360 -> "360p"
                    height != null -> "${height}p"
                    bandwidth != null -> "${bandwidth / 1000} kbps"
                    else -> "Video"
                }
            }
            isAudio -> {
                val kbps = if (bandwidth != null) "${bandwidth / 1000} kbps" else "Audio"
                val langLabel = if (!lang.isNullOrBlank()) " ($lang)" else ""
                "Audio $kbps$langLabel"
            }
            else -> "Subtitle" + if (!lang.isNullOrBlank()) " ($lang)" else ""
        }
    }

    fun parseIsoDuration(durationStr: String?): Double? {
        if (durationStr.isNullOrBlank()) return null
        val regex = Regex("PT(?:([0-9]+)H)?(?:([0-9]+)M)?(?:([0-9.]+)S)?")
        val match = regex.find(durationStr) ?: return null

        val hours = match.groupValues[1].toDoubleOrNull() ?: 0.0
        val minutes = match.groupValues[2].toDoubleOrNull() ?: 0.0
        val seconds = match.groupValues[3].toDoubleOrNull() ?: 0.0

        val total = hours * 3600.0 + minutes * 60.0 + seconds
        return if (total > 0.0) total else null
    }

    private fun resolveUri(baseUri: String, relativeUri: String): String {
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
