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

import com.glypdl.android.data.model.GlypdlError

object YtDlpErrorParser {

    /**
     * Parses a raw yt-dlp stderr, exception message, or log output into a structured [GlypdlError].
     * The raw output is sanitized using [LogRedactor] before being stored in [GlypdlError.technicalDetails].
     */
    fun parse(rawError: String?, installedVersion: String? = null): GlypdlError {
        if (rawError.isNullOrBlank()) {
            return GlypdlError.UnknownError(
                userMessage = "An unknown error occurred while communicating with the download engine.",
                technicalDetails = null
            )
        }

        val redactedDetails = LogRedactor.redact(rawError.trim())
        val lower = rawError.lowercase()

        // 1. Engine Outdated / 90 days warning
        if (lower.contains("older than 90 days") ||
            lower.contains("please update") ||
            lower.contains("update yt-dlp") ||
            lower.contains("version is outdated")
        ) {
            return GlypdlError.EngineOutdated(
                installedVersion = installedVersion ?: "Unknown",
                technicalDetails = redactedDetails
            )
        }

        // 2. Private Content (Instagram Close Friends / Private Accounts, Facebook Private Groups/Videos)
        if (lower.contains("this account is private") ||
            lower.contains("this story is private") ||
            lower.contains("close friends") ||
            lower.contains("this video is private") ||
            lower.contains("private video") ||
            lower.contains("not available to your account") ||
            lower.contains("this content isn't available right now") ||
            lower.contains("only shared with") ||
            lower.contains("private group")
        ) {
            return GlypdlError.PrivateContent(
                userMessage = "This media is private and not accessible to the current account. If this is a private account or story, verify your login session in Settings.",
                technicalDetails = redactedDetails
            )
        }

        // 3. Authentication & Login Required (Instagram, Facebook, YouTube, TikTok)
        if (lower.contains("sign in to confirm") ||
            lower.contains("login required") ||
            lower.contains("log in to view") ||
            lower.contains("log in to instagram") ||
            lower.contains("log in with facebook") ||
            lower.contains("confirm you’re not a bot") ||
            lower.contains("confirm you're not a bot") ||
            lower.contains("members-only") ||
            lower.contains("join this channel") ||
            lower.contains("cookies are required") ||
            lower.contains("use --cookies") ||
            lower.contains("checkpoint_required") ||
            lower.contains("please sign in") ||
            lower.contains("requires authentication") ||
            lower.contains("sign in to view") ||
            lower.contains("session has expired") ||
            lower.contains("session expired")
        ) {
            return GlypdlError.AuthenticationRequired(technicalDetails = redactedDetails)
        }

        // 4. Rate Limited / Bot Detection / HTTP 429
        if (lower.contains("429") ||
            lower.contains("too many requests") ||
            lower.contains("rate limit") ||
            lower.contains("rate-limit") ||
            lower.contains("quota exceeded") ||
            lower.contains("temporarily blocked") ||
            lower.contains("action blocked")
        ) {
            return GlypdlError.RateLimited(technicalDetails = redactedDetails)
        }

        // 5. DRM Protected Content
        if (lower.contains("drm") ||
            lower.contains("drm protected") ||
            lower.contains("encrypted") ||
            lower.contains("widevine") ||
            lower.contains("playready")
        ) {
            return GlypdlError.DRMProtected(technicalDetails = redactedDetails)
        }

        // 6. Geographically Restricted Content
        if (lower.contains("not made this video available") ||
            lower.contains("not available in your country") ||
            lower.contains("not available in your region") ||
            lower.contains("georestricted") ||
            lower.contains("geographic restriction") ||
            lower.contains("blocked in your country") ||
            lower.contains("country restriction")
        ) {
            return GlypdlError.GeoRestricted(technicalDetails = redactedDetails)
        }

        // 7. Content Unavailable / Deleted / Expired Story / 404
        if (lower.contains("video unavailable") ||
            lower.contains("content unavailable") ||
            lower.contains("story has expired") ||
            lower.contains("story expired") ||
            lower.contains("story unavailable") ||
            lower.contains("user not found") ||
            lower.contains("this video has been removed") ||
            lower.contains("404") ||
            lower.contains("not found") ||
            lower.contains("does not exist") ||
            lower.contains("post has been removed") ||
            lower.contains("deleted by user") ||
            lower.contains("media unavailable") ||
            lower.contains("video is no longer available")
        ) {
            val message = if (lower.contains("story")) {
                "This Instagram Story has expired (past 24 hours) or has been removed."
            } else {
                "The requested media is no longer available, was deleted, or does not exist."
            }
            return GlypdlError.ContentUnavailable(technicalDetails = redactedDetails)
        }

        // 8. Analysis / Request Timed Out
        if (lower.contains("timed out") ||
            lower.contains("timeout") ||
            lower.contains("sockettimeout") ||
            lower.contains("operation timed out")
        ) {
            return GlypdlError.TimedOut(
                userMessage = "Analysis timed out. The website may be slow, rate-limited, or requiring authentication.",
                technicalDetails = redactedDetails
            )
        }

        // 9. Extractor Errors & Platform API Changes (e.g. Instagram/Facebook 'cannot parse data')
        if (lower.contains("cannot parse data") ||
            lower.contains("unable to extract") ||
            lower.contains("extractor error") ||
            lower.contains("failed to parse json") ||
            lower.contains("unexpected data received")
        ) {
            return GlypdlError.ExtractorError(
                userMessage = "The download engine could not extract this media. This may be caused by Instagram/Facebook access restrictions or recent website API changes.",
                technicalDetails = redactedDetails
            )
        }

        // 10. Unsupported Website / Invalid Extractor
        if (lower.contains("unsupported url") ||
            lower.contains("no suitable extractor") ||
            lower.contains("no video formats found") ||
            lower.contains("is not a valid url")
        ) {
            return GlypdlError.UnsupportedSite(technicalDetails = redactedDetails)
        }

        // 11. Network / Connection Errors
        if (lower.contains("unable to download webpage") ||
            lower.contains("connection refused") ||
            lower.contains("failed to connect") ||
            lower.contains("temporary failure in name resolution") ||
            lower.contains("network is unreachable") ||
            lower.contains("name or service not known") ||
            lower.contains("unknownhost") ||
            lower.contains("no address associated with hostname") ||
            lower.contains("ssl: certificate_verify_failed")
        ) {
            return GlypdlError.NetworkError(technicalDetails = redactedDetails)
        }

        // 12. Requested format is not available
        if (lower.contains("requested format is not available") ||
            lower.contains("requested format not available") ||
            lower.contains("format not available")
        ) {
            return GlypdlError.RequestedFormatUnavailable(technicalDetails = redactedDetails)
        }

        // 13. FFmpeg / Postprocessing Errors
        if (lower.contains("ffmpeg") ||
            lower.contains("ffprobe") ||
            lower.contains("postprocessing") ||
            lower.contains("conversion failed") ||
            lower.contains("error opening filters") ||
            lower.contains("muxer does not support") ||
            lower.contains("merger")
        ) {
            val ffmpegError = when {
                lower.contains("no space") || lower.contains("enospc") || lower.contains("disk full") ->
                    GlypdlError.FFmpegError.NoSpace(redactedDetails)
                lower.contains("not found") && (lower.contains("binary") || lower.contains("executable")) ->
                    GlypdlError.FFmpegError.Missing(redactedDetails)
                lower.contains("permission denied") || lower.contains("eacces") ->
                    GlypdlError.FFmpegError.PermissionError(redactedDetails)
                lower.contains("does not support") || lower.contains("invalid codec") || lower.contains("unknown codec") ->
                    GlypdlError.FFmpegError.CodecError(redactedDetails)
                lower.contains("no such file") || lower.contains("cannot open input") || lower.contains("input file error") ->
                    GlypdlError.FFmpegError.InputFileError(redactedDetails)
                lower.contains("corrupt") || lower.contains("invalid data") || lower.contains("moov atom not found") ->
                    GlypdlError.FFmpegError.CorruptInput(redactedDetails)
                lower.contains("merging formats") || lower.contains("merger failed") ->
                    GlypdlError.FFmpegError.MergeError(redactedDetails)
                lower.contains("conversion failed") ->
                    GlypdlError.FFmpegError.ConversionError(redactedDetails)
                else ->
                    GlypdlError.FFmpegError(technicalDetails = redactedDetails)
            }
            return ffmpegError
        }

        // 14. Instagram Story Mismatch
        if (lower.contains("requested instagram story") || lower.contains("story mismatch")) {
            return GlypdlError.StoryMismatch(technicalDetails = redactedDetails)
        }

        // 10. Engine missing or not initialized
        if (lower.contains("not initialized") ||
            lower.contains("cannot find yt-dlp") ||
            lower.contains("binary not found")
        ) {
            return GlypdlError.EngineMissing(technicalDetails = redactedDetails)
        }

        // Fallback to generic UnknownError with the first clean line of the error message
        val firstLine = rawError.lines()
            .firstOrNull { it.isNotBlank() && !it.startsWith("Traceback") }
            ?.removePrefix("ERROR: ")
            ?.trim()
            ?: "An unexpected error occurred during extraction."

        return GlypdlError.UnknownError(
            userMessage = firstLine,
            technicalDetails = redactedDetails
        )
    }
}
