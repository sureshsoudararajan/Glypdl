/*
 * Copyright (C) 2026 The Glypdl Authors
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
package com.glypdl.android.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.service.YtDlpService
import com.glypdl.android.service.engine.EngineStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val ytDlpService: YtDlpService,
    private val authCookieManager: com.glypdl.android.service.auth.AuthCookieManager
) : ViewModel() {

    val authPlatforms = com.glypdl.android.service.auth.AuthCookieManager.SUPPORTED_PLATFORMS

    private val _cookieStatusMap = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val cookieStatusMap: StateFlow<Map<String, Boolean>> = _cookieStatusMap.asStateFlow()

    private val _otherSavedDomains = MutableStateFlow<List<String>>(emptyList())
    val otherSavedDomains: StateFlow<List<String>> = _otherSavedDomains.asStateFlow()

    val theme: StateFlow<String> = settingsRepository.theme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "system")

    val wifiOnly: StateFlow<Boolean> = settingsRepository.wifiOnly
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val concurrentDownloads: StateFlow<Int> = settingsRepository.concurrentDownloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 2)

    val defaultQuality: StateFlow<String> = settingsRepository.defaultQuality
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "best")

    val defaultFormat: StateFlow<String> = settingsRepository.defaultFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "mp4")

    val clipboardDetection: StateFlow<Boolean> = settingsRepository.clipboardDetection
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val downloadNotifications: StateFlow<Boolean> = settingsRepository.downloadNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val completionNotifications: StateFlow<Boolean> = settingsRepository.completionNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val errorNotifications: StateFlow<Boolean> = settingsRepository.errorNotifications
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val preferredVideoCodec: StateFlow<String> = settingsRepository.preferredVideoCodec
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "avc1")

    val preferredAudioFormat: StateFlow<String> = settingsRepository.preferredAudioFormat
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), "m4a")

    val askBeforeDownload: StateFlow<Boolean> = settingsRepository.askBeforeDownload
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val firstRunComplete: StateFlow<Boolean> = settingsRepository.firstRunComplete
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val downloadDirUri: StateFlow<String?> = settingsRepository.downloadDirUri
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val autoUpdateYtDlp: StateFlow<Boolean> = settingsRepository.autoUpdateYtDlp
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _engineStatus = MutableStateFlow<EngineStatus?>(null)
    val engineStatus: StateFlow<EngineStatus?> = _engineStatus.asStateFlow()

    private val _isEngineBusy = MutableStateFlow(false)
    val isEngineBusy: StateFlow<Boolean> = _isEngineBusy.asStateFlow()

    private val _engineActionMessage = MutableStateFlow<String?>(null)
    val engineActionMessage: StateFlow<String?> = _engineActionMessage.asStateFlow()

    init {
        refreshEngineStatus(forceOnline = false)
        refreshCookieStatus()
    }

    fun refreshCookieStatus() {
        val map = authPlatforms.associate { platform ->
            platform.id to authCookieManager.hasCookies(platform.domain)
        }
        _cookieStatusMap.value = map
        val supportedDomains = authPlatforms.map { it.domain }.toSet()
        _otherSavedDomains.value = authCookieManager.getSavedDomains().filter { it !in supportedDomains }
    }

    fun clearCookies(domain: String) {
        authCookieManager.clearCookies(domain)
        refreshCookieStatus()
    }

    fun setTheme(theme: String) = viewModelScope.launch { settingsRepository.setTheme(theme) }
    fun setWifiOnly(wifiOnly: Boolean) = viewModelScope.launch { settingsRepository.setWifiOnly(wifiOnly) }
    fun setConcurrentDownloads(count: Int) = viewModelScope.launch { settingsRepository.setConcurrentDownloads(count) }
    fun setDefaultQuality(quality: String) = viewModelScope.launch { settingsRepository.setDefaultQuality(quality) }
    fun setDefaultFormat(format: String) = viewModelScope.launch { settingsRepository.setDefaultFormat(format) }
    fun setClipboardDetection(enabled: Boolean) = viewModelScope.launch { settingsRepository.setClipboardDetection(enabled) }
    fun setDownloadNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepository.setDownloadNotifications(enabled) }
    fun setCompletionNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepository.setCompletionNotifications(enabled) }
    fun setErrorNotifications(enabled: Boolean) = viewModelScope.launch { settingsRepository.setErrorNotifications(enabled) }
    fun setPreferredVideoCodec(codec: String) = viewModelScope.launch { settingsRepository.setPreferredVideoCodec(codec) }
    fun setPreferredAudioFormat(format: String) = viewModelScope.launch { settingsRepository.setPreferredAudioFormat(format) }
    fun setAskBeforeDownload(ask: Boolean) = viewModelScope.launch { settingsRepository.setAskBeforeDownload(ask) }
    fun setFirstRunComplete(complete: Boolean) = viewModelScope.launch { settingsRepository.setFirstRunComplete(complete) }
    fun setDownloadDirUri(uri: String?) = viewModelScope.launch { settingsRepository.setDownloadDirUri(uri) }
    fun setAutoUpdateYtDlp(enabled: Boolean) = viewModelScope.launch { settingsRepository.setAutoUpdateYtDlp(enabled) }

    fun refreshEngineStatus(forceOnline: Boolean = true) {
        viewModelScope.launch {
            _isEngineBusy.value = true
            _engineActionMessage.value = if (forceOnline) "Checking for updates..." else null
            try {
                val status = ytDlpService.checkEngineStatus(forceOnlineCheck = forceOnline)
                _engineStatus.value = status
                _engineActionMessage.value = if (status.isUpdateAvailable) {
                    "Update available: ${status.latestVersion}"
                } else if (forceOnline) {
                    "yt-dlp is up to date (${status.installedVersion})"
                } else null
            } catch (e: Exception) {
                _engineActionMessage.value = "Failed to check: ${e.message}"
            } finally {
                _isEngineBusy.value = false
            }
        }
    }

    fun updateYtDlp() {
        viewModelScope.launch {
            _isEngineBusy.value = true
            _engineActionMessage.value = "Updating download engine..."
            try {
                val result = ytDlpService.updateYtDlp()
                result.onSuccess { newVer ->
                    _engineActionMessage.value = "Updated to yt-dlp $newVer"
                    _engineStatus.value = ytDlpService.checkEngineStatus(forceOnlineCheck = false)
                }.onFailure { error ->
                    _engineActionMessage.value = "Update failed: ${error.message}"
                }
            } finally {
                _isEngineBusy.value = false
            }
        }
    }
}
