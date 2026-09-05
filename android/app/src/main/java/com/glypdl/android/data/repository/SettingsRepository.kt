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
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val dataStore = context.dataStore

    companion object {
        val DEFAULT_QUALITY = stringPreferencesKey("default_quality")
        val DEFAULT_FORMAT = stringPreferencesKey("default_format")
        val CONCURRENT_DOWNLOADS = intPreferencesKey("concurrent_downloads")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only")
        val ASK_BEFORE_DOWNLOAD = booleanPreferencesKey("ask_before_download")
        val CLIPBOARD_DETECTION = booleanPreferencesKey("clipboard_detection")
        val PREFERRED_VIDEO_CODEC = stringPreferencesKey("preferred_video_codec")
        val PREFERRED_AUDIO_FORMAT = stringPreferencesKey("preferred_audio_format")
        val THEME = stringPreferencesKey("theme")
        val DOWNLOAD_NOTIFICATIONS = booleanPreferencesKey("download_notifications")
        val COMPLETION_NOTIFICATIONS = booleanPreferencesKey("completion_notifications")
        val ERROR_NOTIFICATIONS = booleanPreferencesKey("error_notifications")
        val FIRST_RUN_COMPLETE = booleanPreferencesKey("first_run_complete")
        val DOWNLOAD_DIR_URI = stringPreferencesKey("download_dir_uri")
        val AUTO_UPDATE_YTDLP = booleanPreferencesKey("auto_update_ytdlp")
        val LAST_UPDATE_CHECK = androidx.datastore.preferences.core.longPreferencesKey("last_update_check")
        val CACHED_LATEST_VERSION = stringPreferencesKey("cached_latest_version")
    }

    val defaultQuality: Flow<String> = dataStore.data.map { it[DEFAULT_QUALITY] ?: "best" }
    val defaultFormat: Flow<String> = dataStore.data.map { it[DEFAULT_FORMAT] ?: "mp4" }
    val concurrentDownloads: Flow<Int> = dataStore.data.map { (it[CONCURRENT_DOWNLOADS] ?: 8).coerceIn(1, 10) }
    val wifiOnly: Flow<Boolean> = dataStore.data.map { it[WIFI_ONLY] ?: false }
    val askBeforeDownload: Flow<Boolean> = dataStore.data.map { it[ASK_BEFORE_DOWNLOAD] ?: true }
    val clipboardDetection: Flow<Boolean> = dataStore.data.map { it[CLIPBOARD_DETECTION] ?: true }
    val preferredVideoCodec: Flow<String> = dataStore.data.map { it[PREFERRED_VIDEO_CODEC] ?: "any" }
    val preferredAudioFormat: Flow<String> = dataStore.data.map { it[PREFERRED_AUDIO_FORMAT] ?: "m4a" }
    val theme: Flow<String> = dataStore.data.map { it[THEME] ?: "system" }
    val downloadNotifications: Flow<Boolean> = dataStore.data.map { it[DOWNLOAD_NOTIFICATIONS] ?: true }
    val completionNotifications: Flow<Boolean> = dataStore.data.map { it[COMPLETION_NOTIFICATIONS] ?: true }
    val errorNotifications: Flow<Boolean> = dataStore.data.map { it[ERROR_NOTIFICATIONS] ?: true }
    val firstRunComplete: Flow<Boolean> = dataStore.data.map { it[FIRST_RUN_COMPLETE] ?: false }
    val downloadDirUri: Flow<String?> = dataStore.data.map { it[DOWNLOAD_DIR_URI] }
    val autoUpdateYtDlp: Flow<Boolean> = dataStore.data.map { it[AUTO_UPDATE_YTDLP] ?: true }
    val lastUpdateCheck: Flow<Long> = dataStore.data.map { it[LAST_UPDATE_CHECK] ?: 0L }
    val cachedLatestVersion: Flow<String?> = dataStore.data.map { it[CACHED_LATEST_VERSION] }

    suspend fun setDefaultQuality(value: String) { dataStore.edit { it[DEFAULT_QUALITY] = value } }
    suspend fun setDefaultFormat(value: String) { dataStore.edit { it[DEFAULT_FORMAT] = value } }
    suspend fun setConcurrentDownloads(value: Int) { dataStore.edit { it[CONCURRENT_DOWNLOADS] = value } }
    suspend fun setWifiOnly(value: Boolean) { dataStore.edit { it[WIFI_ONLY] = value } }
    suspend fun setAskBeforeDownload(value: Boolean) { dataStore.edit { it[ASK_BEFORE_DOWNLOAD] = value } }
    suspend fun setClipboardDetection(value: Boolean) { dataStore.edit { it[CLIPBOARD_DETECTION] = value } }
    suspend fun setPreferredVideoCodec(value: String) { dataStore.edit { it[PREFERRED_VIDEO_CODEC] = value } }
    suspend fun setPreferredAudioFormat(value: String) { dataStore.edit { it[PREFERRED_AUDIO_FORMAT] = value } }
    suspend fun setTheme(value: String) { dataStore.edit { it[THEME] = value } }
    suspend fun setDownloadNotifications(value: Boolean) { dataStore.edit { it[DOWNLOAD_NOTIFICATIONS] = value } }
    suspend fun setCompletionNotifications(value: Boolean) { dataStore.edit { it[COMPLETION_NOTIFICATIONS] = value } }
    suspend fun setErrorNotifications(value: Boolean) { dataStore.edit { it[ERROR_NOTIFICATIONS] = value } }
    suspend fun setFirstRunComplete(value: Boolean) { dataStore.edit { it[FIRST_RUN_COMPLETE] = value } }
    suspend fun setAutoUpdateYtDlp(value: Boolean) { dataStore.edit { it[AUTO_UPDATE_YTDLP] = value } }
    suspend fun setLastUpdateCheck(value: Long) { dataStore.edit { it[LAST_UPDATE_CHECK] = value } }
    suspend fun setCachedLatestVersion(value: String?) {
        dataStore.edit {
            if (value == null) it.remove(CACHED_LATEST_VERSION)
            else it[CACHED_LATEST_VERSION] = value
        }
    }
    suspend fun setDownloadDirUri(value: String?) { 
        dataStore.edit { 
            if (value == null) it.remove(DOWNLOAD_DIR_URI)
            else it[DOWNLOAD_DIR_URI] = value
        } 
    }
}
