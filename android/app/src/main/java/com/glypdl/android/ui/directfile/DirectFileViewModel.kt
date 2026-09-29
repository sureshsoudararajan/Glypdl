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
package com.glypdl.android.ui.directfile

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.DownloadType
import com.glypdl.android.data.model.EngineType
import com.glypdl.android.data.model.FileCategory
import com.glypdl.android.service.DownloadManager
import com.glypdl.android.service.resolver.DownloadResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

sealed interface DirectFileUiState {
    object Loading : DirectFileUiState
    data class Success(
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val category: FileCategory,
        val url: String
    ) : DirectFileUiState
    data class Error(val message: String) : DirectFileUiState
}

@HiltViewModel
class DirectFileViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val downloadResolver: DownloadResolver,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val urlEncoded: String = checkNotNull(savedStateHandle["url"])
    private val url: String = URLDecoder.decode(urlEncoded, "UTF-8")

    private val _uiState = MutableStateFlow<DirectFileUiState>(DirectFileUiState.Loading)
    val uiState: StateFlow<DirectFileUiState> = _uiState.asStateFlow()

    init {
        resolveFile()
    }

    private fun resolveFile() {
        viewModelScope.launch {
            _uiState.value = DirectFileUiState.Loading
            try {
                val metadata = downloadResolver.resolveWithProbe(url)
                
                val category = metadata.fileCategory
                _uiState.value = DirectFileUiState.Success(
                    fileName = metadata.detectedFileName ?: "Unknown File",
                    fileSize = metadata.detectedFileSize ?: 0L,
                    mimeType = metadata.detectedMimeType ?: "application/octet-stream",
                    category = category,
                    url = url
                )
            } catch (e: Exception) {
                _uiState.value = DirectFileUiState.Error(e.message ?: "Failed to resolve file")
            }
        }
    }

    fun startDownload(state: DirectFileUiState.Success) {
        viewModelScope.launch {
            val ext = com.glypdl.android.service.resolver.FileCategoryClassifier.extractExtensionFromUrl(state.url) ?: "bin"
            val request = DownloadRequest(
                id = java.util.UUID.randomUUID().toString(),
                url = state.url,
                title = state.fileName,
                thumbnailUrl = null,
                formatId = "direct",
                ext = ext,
                resolution = if (state.fileSize > 0L) android.text.format.Formatter.formatShortFileSize(context, state.fileSize) else null,
                isAudioOnly = state.category == FileCategory.AUDIO,
                destinationUri = null,
                downloadType = DownloadType.DIRECT_FILE,
                engineType = EngineType.ARIA2C,
                fileCategory = state.category,
                fileName = state.fileName,
                contentLength = state.fileSize,
                detectedMimeType = state.mimeType
            )
            downloadManager.enqueueDownload(request)
        }
    }
}
