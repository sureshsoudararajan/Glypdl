/*
 * Copyright (c) 2026. Glypdl Contributors
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
 */
package com.glypdl.android.ui.status

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.repository.StatusRepository
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import com.glypdl.android.service.status.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatusPreviewUiState(
    val uri: Uri = Uri.EMPTY,
    val displayName: String = "",
    val mimeType: String = "",
    val mediaType: StatusMediaType = StatusMediaType.IMAGE,
    val sizeBytes: Long = 0L,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class StatusPreviewViewModel @Inject constructor(
    private val statusRepository: StatusRepository
) : ViewModel() {

    private var activeMedia: StatusMedia? = null

    private val _uiState = MutableStateFlow(StatusPreviewUiState())
    val uiState: StateFlow<StatusPreviewUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    private fun loadMedia() {
        val media = statusRepository.activePreviewMedia
        activeMedia = media
        if (media != null) {
            _uiState.update {
                it.copy(
                    uri = media.uri,
                    displayName = media.displayName,
                    mimeType = media.mimeType,
                    mediaType = media.mediaType,
                    sizeBytes = media.sizeBytes,
                    isSaved = media.isSaved
                )
            }
        }
    }

    fun saveCurrentStatus() {
        val media = activeMedia ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = statusRepository.saveStatus(media)) {
                is ExportResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            message = "Status saved to gallery!"
                        )
                    }
                }
                is ExportResult.AlreadySaved -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            isSaved = true,
                            message = "Status is already saved in gallery."
                        )
                    }
                }
                is ExportResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            message = "Save failed: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
