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

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.repository.StatusRepository
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import com.glypdl.android.service.status.ExportResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject

data class StatusPreviewUiState(
    val uri: Uri = Uri.EMPTY,
    val displayName: String = "",
    val mimeType: String = "",
    val mediaType: StatusMediaType = StatusMediaType.IMAGE,
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val isSaved: Boolean = false,
    val isSaving: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class StatusPreviewViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val statusRepository: StatusRepository
) : ViewModel() {

    private val rawUriString: String = savedStateHandle.get<String>("mediaUri")
        ?.let {
            try {
                URLDecoder.decode(it, StandardCharsets.UTF_8.toString())
            } catch (e: Exception) {
                it
            }
        } ?: ""

    private val _uiState = MutableStateFlow(StatusPreviewUiState())
    val uiState: StateFlow<StatusPreviewUiState> = _uiState.asStateFlow()

    init {
        if (rawUriString.isNotBlank()) {
            val uri = Uri.parse(rawUriString)
            loadMetadata(uri)
        }
    }

    private fun loadMetadata(uri: Uri) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                var displayName = "Status_${System.currentTimeMillis()}"
                var sizeBytes = 0L
                var mimeType = context.contentResolver.getType(uri) ?: ""
                var modifiedAt = System.currentTimeMillis()

                try {
                    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (cursor.moveToFirst()) {
                            if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                                displayName = cursor.getString(nameIndex)
                            }
                            if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                                sizeBytes = cursor.getLong(sizeIndex)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to URI path
                    uri.lastPathSegment?.let { segment ->
                        displayName = segment.substringAfterLast('/')
                    }
                }

                if (mimeType.isBlank()) {
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
                        ?: if (ext in setOf("mp4", "mkv", "webm", "3gp", "ts")) "video/mp4" else "image/jpeg"
                }

                val mediaType = if (mimeType.startsWith("video/")) {
                    StatusMediaType.VIDEO
                } else {
                    StatusMediaType.IMAGE
                }

                // Check if already in saved statuses
                val savedList = try {
                    statusRepository.getSavedStatuses().first()
                } catch (e: Exception) {
                    emptyList()
                }
                val isAlreadySaved = savedList.any { it.originalUri == rawUriString || it.savedUri == rawUriString }

                _uiState.update {
                    it.copy(
                        uri = uri,
                        displayName = displayName,
                        mimeType = mimeType,
                        mediaType = mediaType,
                        sizeBytes = sizeBytes,
                        modifiedAt = modifiedAt,
                        isSaved = isAlreadySaved
                    )
                }
            }
        }
    }

    fun saveCurrentStatus() {
        val state = _uiState.value
        if (state.uri == Uri.EMPTY || state.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val media = StatusMedia(
                id = state.uri.toString(),
                uri = state.uri,
                displayName = state.displayName,
                mimeType = state.mimeType,
                mediaType = state.mediaType,
                sizeBytes = state.sizeBytes,
                modifiedAt = state.modifiedAt,
                isSaved = state.isSaved
            )

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
                            message = "Status already saved in gallery."
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
