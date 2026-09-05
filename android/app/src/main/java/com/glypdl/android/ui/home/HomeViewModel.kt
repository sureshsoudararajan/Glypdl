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
package com.glypdl.android.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.repository.DownloadRepository
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.domain.usecase.DeleteDownloadUseCase
import com.glypdl.android.domain.usecase.ValidateUrlUseCase
import com.glypdl.android.service.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val url: String = "",
    val isUrlValid: Boolean = false,
    val clipboardUrl: String? = null,
    val recentDownloads: List<DownloadEntity> = emptyList(),
    val recentProgress: Map<String, DownloadProgress> = emptyMap()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val settingsRepository: SettingsRepository,
    private val validateUrlUseCase: ValidateUrlUseCase,
    private val downloadManager: DownloadManager,
    private val deleteDownloadUseCase: DeleteDownloadUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = combine(
        _uiState,
        downloadRepository.getAllDownloads(),
        downloadManager.downloadProgress
    ) { state, downloads, progress ->
        state.copy(
            recentDownloads = downloads.take(5),
            recentProgress = progress
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HomeUiState()
    )

    fun onUrlChange(url: String) {
        val isValid = validateUrlUseCase(url).isSuccess
        _uiState.update { it.copy(url = url, isUrlValid = isValid) }
    }

    fun onPasteFromClipboard(url: String) {
        val isValid = validateUrlUseCase(url).isSuccess
        _uiState.update { it.copy(url = url, clipboardUrl = url, isUrlValid = isValid) }
    }

    fun clearClipboardBanner() {
        _uiState.update { it.copy(clipboardUrl = null) }
    }

    fun onAnalyzeClick() {
        // No-op, navigation handled by screen
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch { downloadManager.pauseDownload(id) }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch { downloadManager.resumeDownload(id) }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch { downloadManager.cancelDownload(id) }
    }

    fun retryDownload(id: String) {
        viewModelScope.launch { downloadManager.retryDownload(id) }
    }

    fun deleteDownload(id: String, deleteFile: Boolean = true) {
        viewModelScope.launch {
            deleteDownloadUseCase(id, deleteFile)
        }
    }
}
