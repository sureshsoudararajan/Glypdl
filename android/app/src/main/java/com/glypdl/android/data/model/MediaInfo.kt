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
