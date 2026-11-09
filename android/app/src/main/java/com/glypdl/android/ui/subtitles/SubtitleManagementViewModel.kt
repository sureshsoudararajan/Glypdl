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
package com.glypdl.android.ui.subtitles

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.model.SubtitleFile
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.data.repository.SubtitleRepository
import com.glypdl.android.service.engine.SubtitleParser
import com.glypdl.android.util.StorageHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

data class SubtitleManagementUiState(
    val subtitles: List<SubtitleFile> = emptyList(),
    val filteredSubtitles: List<SubtitleFile> = emptyList(),
    val searchQuery: String = "",
    val selectedFormatFilter: String? = null,
    val filterDownloadId: String? = null,
    val filterDownloadTitle: String? = null,
    val isLoading: Boolean = false
)

@HiltViewModel
class SubtitleManagementViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val subtitleRepository: SubtitleRepository,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    val downloadIdArg: String? = savedStateHandle.get<String>("downloadId")

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _formatFilter = MutableStateFlow<String?>(null)
    val formatFilter: StateFlow<String?> = _formatFilter.asStateFlow()

    private val baseSubtitlesFlow = if (!downloadIdArg.isNullOrBlank()) {
        subtitleRepository.getSubtitlesForDownload(downloadIdArg)
    } else {
        subtitleRepository.getAllSubtitles()
    }

    val uiState: StateFlow<SubtitleManagementUiState> = combine(
        baseSubtitlesFlow,
        _searchQuery,
        _formatFilter
    ) { allSubs, query, format ->
        val filtered = allSubs.filter { sub ->
            val matchesQuery = query.isBlank() ||
                    sub.mediaTitle.contains(query, ignoreCase = true) ||
                    sub.languageName.contains(query, ignoreCase = true) ||
                    sub.languageCode.contains(query, ignoreCase = true) ||
                    sub.fileName.contains(query, ignoreCase = true)

            val matchesFormat = format.isNullOrBlank() || sub.format.equals(format, ignoreCase = true)
            matchesQuery && matchesFormat
        }

        SubtitleManagementUiState(
            subtitles = allSubs,
            filteredSubtitles = filtered,
            searchQuery = query,
            selectedFormatFilter = format,
            filterDownloadId = downloadIdArg,
            filterDownloadTitle = allSubs.firstOrNull()?.mediaTitle,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SubtitleManagementUiState(isLoading = true, filterDownloadId = downloadIdArg)
    )

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun onFormatFilterSelected(format: String?) {
        _formatFilter.value = if (_formatFilter.value == format) null else format
    }

    fun deleteSubtitle(id: Long) {
        viewModelScope.launch {
            subtitleRepository.deleteSubtitle(id, deleteFileFromDisk = true)
        }
    }

    fun importExternalSubtitle(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                var displayName = "imported_subtitle.srt"
                var size = 0L
                cursor?.use {
                    if (it.moveToFirst()) {
                        val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        if (nameIndex != -1) displayName = it.getString(nameIndex) ?: displayName
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1) size = it.getLong(sizeIndex)
                    }
                }

                val ext = displayName.substringAfterLast('.', "srt").lowercase()
                val stagingDir = StorageHelper.getDefaultStagingDir(context)
                val tempFile = File(stagingDir, "import_${System.currentTimeMillis()}_$displayName")

                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (tempFile.exists() && tempFile.length() > 0) {
                    val customTree = settingsRepository.downloadDirUri.first()
                    val mime = StorageHelper.getMimeType(ext)
                    val exportRes = StorageHelper.exportToPermanentStorage(
                        context = context,
                        stagingFile = tempFile,
                        displayName = displayName,
                        mimeType = mime,
                        isAudio = false,
                        customTreeUriString = customTree
                    )

                    exportRes.onSuccess { permUri ->
                        val langCode = displayName.substringBeforeLast('.').substringAfterLast('.', "en")
                        val subModel = SubtitleFile(
                            id = 0,
                            downloadId = downloadIdArg,
                            mediaTitle = displayName.substringBeforeLast('.'),
                            mediaUrl = null,
                            languageCode = langCode,
                            languageName = SubtitleParser.resolveLanguageName(langCode),
                            format = ext,
                            isAutoGenerated = false,
                            contentUri = permUri.toString(),
                            filePath = null,
                            fileName = displayName,
                            fileSize = tempFile.length().takeIf { it > 0 } ?: size,
                            createdAt = System.currentTimeMillis(),
                            isEmbedded = false
                        )
                        subtitleRepository.insertSubtitle(subModel)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
