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

sealed class GlypdlError(
    val userTitle: String,
    val userMessage: String,
    val technicalDetails: String? = null,
    val canRetry: Boolean = true,
    val requiresUpdate: Boolean = false
) {
    class AuthenticationRequired(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Authentication Required",
        userMessage = "This content requires you to be signed in or authentication is required by the website.",
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    )

    class RateLimited(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Temporarily Rate Limited",
        userMessage = "The website is limiting requests (HTTP 429). Please wait a while before trying again.",
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    )

    class ContentUnavailable(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Content Unavailable",
        userMessage = "The requested media is no longer available, was deleted by the user, or does not exist.",
        technicalDetails = technicalDetails,
        canRetry = false,
        requiresUpdate = false
    )

    class UnsupportedSite(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Unsupported Website",
        userMessage = "Glypdl could not find a supported extractor for this URL. Please verify the URL.",
        technicalDetails = technicalDetails,
        canRetry = false,
        requiresUpdate = false
    )

    class GeoRestricted(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Content Unavailable in Region",
        userMessage = "The website has geographically restricted this media to specific regions.",
        technicalDetails = technicalDetails,
        canRetry = false,
        requiresUpdate = false
    )

    class DRMProtected(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Protected Content",
        userMessage = "This media uses Digital Rights Management (DRM) and cannot be downloaded by Glypdl.",
        technicalDetails = technicalDetails,
        canRetry = false,
        requiresUpdate = false
    )

    class NetworkError(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Network Unavailable",
        userMessage = "Unable to connect to the server. Please check your internet connection and try again.",
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    )

    class EngineOutdated(
        val installedVersion: String,
        val latestVersion: String? = null,
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Download Engine Update Required",
        userMessage = "Your yt-dlp download engine ($installedVersion) is older than 90 days or obsolete. An update is required to analyze and download this content.",
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = true
    )

    class EngineMissing(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Download Engine Missing",
        userMessage = "The download engine has not been initialized. Please check for updates in Settings.",
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = true
    )

    class InvalidUrl(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Invalid URL",
        userMessage = "The provided URL is not valid. Please enter a valid http or https link.",
        technicalDetails = technicalDetails,
        canRetry = false,
        requiresUpdate = false
    )

    open class FFmpegError(
        userMessage: String = "FFmpeg was unable to process or merge the downloaded media streams.",
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Processing Failed",
        userMessage = userMessage,
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    ) {
        class Missing(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg binary is missing or not installed on this device.",
            technicalDetails = details
        )
        class CodecError(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg encountered an unsupported audio/video codec combination.",
            technicalDetails = details
        )
        class InputFileError(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg input stream file was missing or incomplete.",
            technicalDetails = details
        )
        class OutputFileError(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg was unable to create the output media file.",
            technicalDetails = details
        )
        class PermissionError(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg was denied filesystem permission to write output.",
            technicalDetails = details
        )
        class NoSpace(details: String? = null) : FFmpegError(
            userMessage = "Not enough storage space for FFmpeg to merge media files. Free some storage and retry.",
            technicalDetails = details
        )
        class CorruptInput(details: String? = null) : FFmpegError(
            userMessage = "Downloaded media stream was corrupted or truncated before merging.",
            technicalDetails = details
        )
        class MergeError(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg failed while combining the video and audio streams.",
            technicalDetails = details
        )
        class ConversionError(details: String? = null) : FFmpegError(
            userMessage = "FFmpeg failed while converting the media format.",
            technicalDetails = details
        )
    }

    class StoryMismatch(
        requestedId: String? = null,
        downloadedId: String? = null,
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Story Mismatch",
        userMessage = "The downloaded media does not match the requested Instagram Story.",
        technicalDetails = technicalDetails ?: "Requested ID: $requestedId, Downloaded ID: $downloadedId",
        canRetry = false,
        requiresUpdate = false
    )

    class RequestedFormatUnavailable(
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Format Unavailable",
        userMessage = "The requested stream or format is not available for this media.",
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    )

    class PrivateContent(
        userMessage: String = "This media is private and is not accessible to the current account.",
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Private Content",
        userMessage = userMessage,
        technicalDetails = technicalDetails,
        canRetry = false,
        requiresUpdate = false
    )

    class ExtractorError(
        userMessage: String = "The download engine could not extract this media. This may be caused by platform access restrictions or API changes.",
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Extractor Limitation",
        userMessage = userMessage,
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = true
    )

    class TimedOut(
        userMessage: String = "Analysis timed out. The website may be slow, unavailable, rate-limited, or requiring authentication.",
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Analysis Timed Out",
        userMessage = userMessage,
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    )

    class UnknownError(
        userMessage: String = "An unexpected error occurred while processing the media.",
        technicalDetails: String? = null
    ) : GlypdlError(
        userTitle = "Analysis Failed",
        userMessage = userMessage,
        technicalDetails = technicalDetails,
        canRetry = true,
        requiresUpdate = false
    )
}

class GlypdlException(val error: GlypdlError) : Exception(error.userMessage)
