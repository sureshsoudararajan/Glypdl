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
 * Metadata model holding analyzed information about a video or audio track.
 *
 * Produced by `YtDlpManager.extractInfo()` and consumed by the analysis and format picker screens.
 *
 * @property id Unique media ID assigned by the host platform (e.g. YouTube video ID).
 * @property url Original URL of the analyzed media.
 * @property title Title of the media track or video.
 * @property thumbnail Remote URL of the primary video poster/thumbnail image.
 * @property duration Total playback duration in seconds.
 * @property uploader Channel name or creator handle who published the media.
 * @property uploadDate Upload date formatted as string (YYYYMMDD) if available.
 * @property description Video description text.
 * @property websiteUrl Canonical webpage URL.
 * @property extractor Name of the yt-dlp extractor that parsed the URL (e.g. "youtube", "instagram").
 * @property formats Complete list of raw [MediaFormat] streams returned by yt-dlp.
 * @property videoQualities Normalized user-facing [VideoQuality] resolution tiers.
 * @property audioQualities Normalized user-facing [AudioQuality] bitrate tiers.
 */
data class MediaInfo(
    val id: String = "",
    val url: String,
    val title: String,
    val thumbnail: String? = null,
    val duration: Long? = null,
    val uploader: String? = null,
    val uploadDate: String? = null,
    val description: String? = null,
    val websiteUrl: String? = null,
    val extractor: String? = null,
    val formats: List<MediaFormat> = emptyList(),
    val videoQualities: List<VideoQuality> = emptyList(),
    val audioQualities: List<AudioQuality> = emptyList()
)
