/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Top-level DataStore extension property creating a singleton instance of Preferences DataStore.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Central repository managing application settings and user preferences.
 *
 * Backed by Jetpack [Preferences DataStore] for asynchronous, thread-safe,
 * and transaction-safe key-value persistence. Exposes values as reactive [Flow]s.
 *
 * @property context Application context used to access DataStore.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        /** Default video quality preset preference (e.g., "best", "1080p", "720p"). */
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")

        /** Default media container format preference (e.g., "mp4", "mkv", "webm"). */
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")

        /** Maximum number of parallel/simultaneous active downloads (1 to 10). */
        val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")

        /** True if downloads should only progress when connected to unmetered Wi-Fi. */
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")

        /** True if the app should always prompt with the format selection screen before downloading. */
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")

        /** True to monitor the system clipboard on resume and suggest copied video URLs. */
        val CLIPBOARD_DETECTION = booleanPreferencesKey("clipboard_detection")

        /** Preferred video encoding codec ("avc1", "vp9", "av01", or "any"). */
        val PREFERRED_VIDEO_CODEC = stringPreferencesKey("preferred_video_codec")

        /** Preferred audio format for audio-only downloads ("m4a", "opus", "mp3", "flac"). */
        val PREFERRED_AUDIO_FORMAT = stringPreferencesKey("preferred_audio_format")

        /** UI theme selection ("light", "dark", or "system"). */
        val THEME = stringPreferencesKey("theme")

        /** Show ongoing download progress in notification drawer. */
        val DOWNLOAD_NOTIFICATIONS = booleanPreferencesKey("download_notifications")

        /** Show notification banner upon successful download completion. */
        val COMPLETION_NOTIFICATIONS = booleanPreferencesKey("completion_notifications")

        /** Show notification alert if a download fails. */
        val ERROR_NOTIFICATIONS = booleanPreferencesKey("error_notifications")

        /** True if the user has completed the initial onboarding walkthrough. */
        val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")

        /** Custom user-selected Storage Access Framework (SAF) folder tree URI string. */
        val DOWNLOAD_DIR_URI = stringPreferencesKey("download_dir_uri")

        /** True to automatically check and update yt-dlp binary on app launch. */
        val AUTO_UPDATE_YTDLP = booleanPreferencesKey("auto_update_ytdlp")

        /** Epoch timestamp in milliseconds of the last online yt-dlp update check. */
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")

        /** Cached latest yt-dlp version tag string from GitHub. */
        val CACHED_LATEST_VERSION = stringPreferencesKey("cached_latest_version")
    }

    // Reactive Flow Getters

    /** Flow emitting preferred default quality preset (defaults to "best"). */
    val defaultQuality: Flow<String> = dataStore.data.map { it[DEFAULT_QUALITY] ?: "best" }

    /** Flow emitting preferred default container format (defaults to "mp4"). */
    val defaultFormat: Flow<String> = dataStore.data.map { it[DEFAULT_FORMAT] ?: "mp4" }

    /** Flow emitting allowed concurrent downloads, constrained between 1 and 10 (defaults to 8). */
    val concurrentDownloads: Flow<Int> = dataStore.data.map { (it[CONCURRENT_DOWNLOADS] ?: 8).coerceIn(1, 10) }

    /** Flow emitting Wi-Fi only restriction flag (defaults to false). */
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[WIFI_ONLY] ?: false }

    /** Flow emitting whether format picker dialog appears before download (defaults to true). */
    val askBeforeDownload: Flow<Boolean> = dataStore.data.map { it[ASK_BEFORE_DOWNLOAD] ?: true }

    /** Flow emitting clipboard link auto-detection preference (defaults to true). */
    val clipboardDetection: Flow<Boolean> = dataStore.data.map { it[CLIPBOARD_DETECTION] ?: true }

    /** Flow emitting preferred video codec (defaults to "any"). */
    val preferredVideoCodec: Flow<String> = dataStore.data.map { it[PREFERRED_VIDEO_CODEC] ?: "any" }

    /** Flow emitting preferred audio format (defaults to "m4a"). */
    val preferredAudioFormat: Flow<String> = dataStore.data.map { it[PREFERRED_AUDIO_FORMAT] ?: "m4a" }

    /** Flow emitting active theme mode (defaults to "system"). */
    val theme: Flow<String> = dataStore.data.map { it[THEME] ?: "system" }

    /** Flow emitting ongoing download notification preference (defaults to true). */
    val downloadNotifications: Flow<Boolean> = dataStore.data.map { it[DOWNLOAD_NOTIFICATIONS] ?: true }

    /** Flow emitting completion notification preference (defaults to true). */
    val completionNotifications: Flow<Boolean> = dataStore.data.map { it[COMPLETION_NOTIFICATIONS] ?: true }

    /** Flow emitting error notification preference (defaults to true). */
    val errorNotifications: Flow<Boolean> = dataStore.data.map { it[ERROR_NOTIFICATIONS] ?: true }

    /** Flow emitting first run completion flag (defaults to false). */
    val firstRunComplete: Flow<Boolean> = dataStore.data.map { it[FIRST_RUN_COMPLETE] ?: false }

    /** Flow emitting custom download storage directory tree URI string, or null for default. */
    val downloadDirUri: Flow<String?> = dataStore.data.map { it[DOWNLOAD_DIR_URI] }

    /** Flow emitting auto-update yt-dlp preference (defaults to true). */
    val autoUpdateYtDlp: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_YTDLP] ?: true }

    /** Flow emitting epoch timestamp of the last yt-dlp version check (defaults to 0L). */
    val lastUpdateCheck: Flow<Long> = dataStore.data.map { it[LAST_UPDATE_CHECK] ?: 0L }

    /** Flow emitting cached latest yt-dlp version string, or null. */
    val cachedLatestVersion: Flow<String?> = dataStore.data.map { it[CACHED_LATEST_VERSION] }

    // Asynchronous Setters

    /** Sets the preferred default quality preset. */
    suspend fun setDefaultQuality(value: String) { dataStore.edit { it[DEFAULT_QUALITY] = value } }

    /** Sets the preferred default container format. */
    suspend fun setDefaultFormat(value: String) { dataStore.edit { it[DEFAULT_FORMAT] = value } }

    /** Sets the maximum simultaneous download limit. */
    suspend fun setConcurrentDownloads(value: Int) { dataStore.edit { it[CONCURRENT_DOWNLOADS] = value } }

    /** Enables or disables the Wi-Fi only download constraint. */
    suspend fun setWifiOnly(value: Boolean) { dataStore.edit { it[WIFI_ONLY] = value } }

    /** Configures whether to always ask for format confirmation before starting. */
    suspend fun setAskBeforeDownload(value: Boolean) { dataStore.edit { it[ASK_BEFORE_DOWNLOAD] = value } }

    /** Configures clipboard URL detection on app foregrounding. */
    suspend fun setClipboardDetection(value: Boolean) { dataStore.edit { it[CLIPBOARD_DETECTION] = value } }

    /** Sets the preferred video encoding codec. */
    suspend fun setPreferredVideoCodec(value: String) { dataStore.edit { it[PREFERRED_VIDEO_CODEC] = value } }

    /** Sets the preferred audio format for audio-only downloads. */
    suspend fun setPreferredAudioFormat(value: String) { dataStore.edit { it[PREFERRED_AUDIO_FORMAT] = value } }

    /** Sets the app UI theme ("light", "dark", "system"). */
    suspend fun setTheme(value: String) { dataStore.edit { it[THEME] = value } }

    /** Configures ongoing download notification visibility. */
    suspend fun setDownloadNotifications(value: Boolean) { dataStore.edit { it[DOWNLOAD_NOTIFICATIONS] = value } }

    /** Configures completion notification alerts. */
    suspend fun setCompletionNotifications(value: Boolean) { dataStore.edit { it[COMPLETION_NOTIFICATIONS] = value } }

    /** Configures error notification alerts. */
    suspend fun setErrorNotifications(value: Boolean) { dataStore.edit { it[ERROR_NOTIFICATIONS] = value } }

    /** Marks the onboarding flow as complete. */
    suspend fun setFirstRunComplete(value: Boolean) { dataStore.edit { it[FIRST_RUN_COMPLETE] = value } }

    /** Configures automatic background yt-dlp engine updates. */
    suspend fun setAutoUpdateYtDlp(value: Boolean) { dataStore.edit { it[AUTO_UPDATE_YTDLP] = value } }

    /** Records the timestamp of an update check. */
    suspend fun setLastUpdateCheck(value: Long) { dataStore.edit { it[LAST_UPDATE_CHECK] = value } }

    /** Caches the latest discovered yt-dlp release version. */
    suspend fun setCachedLatestVersion(value: String?) {
        dataStore.edit {
            if (value == null) it.remove(CACHED_LATEST_VERSION)
            else it[CACHED_LATEST_VERSION] = value
        }
    }

    /** Configures or clears the custom download storage directory tree URI. */
    suspend fun setDownloadDirUri(value: String?) { 
        dataStore.edit { 
            if (value == null) it.remove(DOWNLOAD_DIR_URI)
            else it[DOWNLOAD_DIR_URI] = value
        } 
    }
}
