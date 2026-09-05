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
package com.glypdl.android.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.model.DownloadStatus
import com.glypdl.android.data.repository.DownloadRepository
import com.glypdl.android.domain.usecase.CancelDownloadUseCase
import com.glypdl.android.domain.usecase.PauseDownloadUseCase
import com.glypdl.android.domain.usecase.ResumeDownloadUseCase
import com.glypdl.android.domain.usecase.RetryDownloadUseCase
import com.glypdl.android.service.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadsUiState(
    val downloads: List<DownloadEntity> = emptyList(),
    val selectedFilter: DownloadStatus? = null,
    val progressMap: Map<String, DownloadProgress> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val pauseUseCase: PauseDownloadUseCase,
    private val resumeUseCase: ResumeDownloadUseCase,
    private val cancelUseCase: CancelDownloadUseCase,
    private val retryUseCase: RetryDownloadUseCase,
    private val deleteDownloadUseCase: com.glypdl.android.domain.usecase.DeleteDownloadUseCase
) : ViewModel() {

    private val _selectedFilter = MutableStateFlow<DownloadStatus?>(null)

    val uiState: StateFlow<DownloadsUiState> = _selectedFilter
        .flatMapLatest { filter ->
            val downloadsFlow = if (filter == null) {
                downloadRepository.getAllDownloads()
            } else {
                downloadRepository.getDownloadsByStatus(filter)
            }
            combine(
                downloadsFlow,
                downloadManager.downloadProgress
            ) { downloads, progress ->
                DownloadsUiState(
                    downloads = downloads,
                    selectedFilter = filter,
                    progressMap = progress
                )
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = DownloadsUiState()
        )

    fun setFilter(status: DownloadStatus?) {
        _selectedFilter.value = status
    }

    fun pauseDownload(id: String) {
        viewModelScope.launch { pauseUseCase(id) }
    }

    fun resumeDownload(id: String) {
        viewModelScope.launch { resumeUseCase(id) }
    }

    fun cancelDownload(id: String) {
        viewModelScope.launch { cancelUseCase(id) }
    }

    fun retryDownload(id: String) {
        viewModelScope.launch { retryUseCase(id) }
    }

    fun deleteDownload(id: String, deleteFile: Boolean = true) {
        viewModelScope.launch {
            deleteDownloadUseCase(id, deleteFile)
        }
    }

    fun clearCompletedDownloads() {
        viewModelScope.launch {
            downloadRepository.clearCompleted()
        }
    }
}
