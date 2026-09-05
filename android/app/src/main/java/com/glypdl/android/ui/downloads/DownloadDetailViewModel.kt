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

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.repository.DownloadRepository
import com.glypdl.android.domain.usecase.CancelDownloadUseCase
import com.glypdl.android.domain.usecase.PauseDownloadUseCase
import com.glypdl.android.domain.usecase.ResumeDownloadUseCase
import com.glypdl.android.domain.usecase.RetryDownloadUseCase
import com.glypdl.android.service.DownloadManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DownloadDetailUiState(
    val download: DownloadEntity? = null,
    val progress: DownloadProgress? = null
)

@HiltViewModel
class DownloadDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val downloadRepository: DownloadRepository,
    private val downloadManager: DownloadManager,
    private val pauseUseCase: PauseDownloadUseCase,
    private val resumeUseCase: ResumeDownloadUseCase,
    private val cancelUseCase: CancelDownloadUseCase,
    private val retryUseCase: RetryDownloadUseCase,
    private val deleteDownloadUseCase: com.glypdl.android.domain.usecase.DeleteDownloadUseCase
) : ViewModel() {

    private val downloadId: String = checkNotNull(savedStateHandle.get<String>("downloadId"))

    val uiState: StateFlow<DownloadDetailUiState> = combine(
        downloadRepository.getDownloadById(downloadId),
        downloadManager.downloadProgress
    ) { download, progressMap ->
        DownloadDetailUiState(
            download = download,
            progress = progressMap[downloadId]
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DownloadDetailUiState()
    )

    fun pauseDownload() {
        viewModelScope.launch { pauseUseCase(downloadId) }
    }

    fun resumeDownload() {
        viewModelScope.launch { resumeUseCase(downloadId) }
    }

    fun cancelDownload() {
        viewModelScope.launch { cancelUseCase(downloadId) }
    }

    fun retryDownload() {
        viewModelScope.launch { retryUseCase(downloadId) }
    }

    fun deleteDownload(deleteFile: Boolean = true, onDeleted: () -> Unit) {
        viewModelScope.launch {
            deleteDownloadUseCase(downloadId, deleteFile)
            onDeleted()
        }
    }
}
