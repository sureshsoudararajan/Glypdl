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
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.local.entity.SavedStatusEntity
import com.glypdl.android.data.repository.StatusRepository
import com.glypdl.android.data.status.StatusScanResult
import com.glypdl.android.data.status.model.StatusFilter
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import com.glypdl.android.data.status.model.StatusTab
import com.glypdl.android.service.status.ExportResult
import com.glypdl.android.ui.status.components.StatusEmptyStateType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StatusUiState(
    val isScanning: Boolean = false,
    val folderUri: String? = null,
    val currentTab: StatusTab = StatusTab.AVAILABLE,
    val filter: StatusFilter = StatusFilter.ALL,
    val availableStatuses: List<StatusMedia> = emptyList(),
    val savedStatuses: List<SavedStatusEntity> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val emptyStateType: StatusEmptyStateType? = null,
    val snackbarMessage: String? = null,
    val isSavingBatch: Boolean = false,
    val batchProgress: Pair<Int, Int>? = null
) {
    val filteredAvailable: List<StatusMedia>
        get() = when (filter) {
            StatusFilter.ALL -> availableStatuses
            StatusFilter.IMAGES -> availableStatuses.filter { it.mediaType == StatusMediaType.IMAGE }
            StatusFilter.VIDEOS -> availableStatuses.filter { it.mediaType == StatusMediaType.VIDEO }
        }

    val filteredSaved: List<SavedStatusEntity>
        get() = when (filter) {
            StatusFilter.ALL -> savedStatuses
            StatusFilter.IMAGES -> savedStatuses.filter { it.mediaType == "IMAGE" }
            StatusFilter.VIDEOS -> savedStatuses.filter { it.mediaType == "VIDEO" }
        }
}

@HiltViewModel
class StatusViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val statusRepository: StatusRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatusUiState(isScanning = true))
    val uiState: StateFlow<StatusUiState> = _uiState.asStateFlow()

    init {
        // Observe saved statuses from Room
        viewModelScope.launch {
            statusRepository.getSavedStatuses().collect { savedList ->
                _uiState.update { current ->
                    // Also update isSaved flag in available statuses
                    val savedUris = savedList.map { it.originalUri }.toSet()
                    val updatedAvailable = current.availableStatuses.map { item ->
                        item.copy(isSaved = savedUris.contains(item.uri.toString()))
                    }
                    current.copy(
                        savedStatuses = savedList,
                        availableStatuses = updatedAvailable
                    )
                }
            }
        }

        // Load configured folder URI and scan if available
        viewModelScope.launch {
            val savedUriString = statusRepository.configuredFolderUri.first()
            if (savedUriString.isNullOrBlank()) {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        folderUri = null,
                        emptyStateType = StatusEmptyStateType.FolderNotConfigured
                    )
                }
            } else {
                _uiState.update { it.copy(folderUri = savedUriString) }
                scanFolder(Uri.parse(savedUriString))
            }
        }
    }

    fun onFolderSelected(uri: Uri) {
        viewModelScope.launch {
            try {
                // Persist read permission
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)

                val uriString = uri.toString()
                statusRepository.setConfiguredFolderUri(uriString)
                _uiState.update { it.copy(folderUri = uriString) }
                scanFolder(uri)
            } catch (e: SecurityException) {
                _uiState.update {
                    it.copy(
                        emptyStateType = StatusEmptyStateType.PermissionRevoked,
                        snackbarMessage = "Unable to persist folder permission"
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        emptyStateType = StatusEmptyStateType.GenericError(e.message ?: "Failed to set folder"),
                        snackbarMessage = e.message
                    )
                }
            }
        }
    }

    fun onRefresh() {
        val uriString = _uiState.value.folderUri
        if (uriString.isNullOrBlank()) {
            _uiState.update { it.copy(emptyStateType = StatusEmptyStateType.FolderNotConfigured) }
        } else {
            scanFolder(Uri.parse(uriString))
        }
    }

    private fun scanFolder(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, emptyStateType = null) }
            when (val result = statusRepository.scanStatuses(treeUri)) {
                is StatusScanResult.Success -> {
                    val items = result.items
                    val emptyType = if (items.isEmpty()) StatusEmptyStateType.NoStatuses else null
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            availableStatuses = items,
                            emptyStateType = emptyType,
                            selectedIds = emptySet(),
                            isSelectionMode = false
                        )
                    }
                }
                is StatusScanResult.FolderNotStatuses -> {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            availableStatuses = emptyList(),
                            emptyStateType = StatusEmptyStateType.FolderNotStatuses(result.folderName)
                        )
                    }
                }
                is StatusScanResult.PermissionDenied -> {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            availableStatuses = emptyList(),
                            emptyStateType = StatusEmptyStateType.PermissionRevoked
                        )
                    }
                }
                is StatusScanResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isScanning = false,
                            availableStatuses = emptyList(),
                            emptyStateType = StatusEmptyStateType.GenericError(result.message)
                        )
                    }
                }
            }
        }
    }

    fun setTab(tab: StatusTab) {
        _uiState.update { it.copy(currentTab = tab, selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun setFilter(filter: StatusFilter) {
        _uiState.update { it.copy(filter = filter) }
    }

    fun toggleSelection(id: String) {
        _uiState.update { current ->
            val updated = current.selectedIds.toMutableSet()
            if (updated.contains(id)) {
                updated.remove(id)
            } else {
                updated.add(id)
            }
            current.copy(
                selectedIds = updated,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { current ->
            val allIds = current.filteredAvailable.map { it.id }.toSet()
            val newSelected = if (current.selectedIds.size == allIds.size) emptySet() else allIds
            current.copy(
                selectedIds = newSelected,
                isSelectionMode = newSelected.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedIds = emptySet(), isSelectionMode = false) }
    }

    fun saveSingle(status: StatusMedia) {
        viewModelScope.launch {
            when (val result = statusRepository.saveStatus(status)) {
                is ExportResult.Success -> {
                    _uiState.update { current ->
                        val updated = current.availableStatuses.map {
                            if (it.id == status.id) it.copy(isSaved = true) else it
                        }
                        current.copy(
                            availableStatuses = updated,
                            snackbarMessage = "Status saved to gallery!"
                        )
                    }
                }
                is ExportResult.AlreadySaved -> {
                    _uiState.update { current ->
                        val updated = current.availableStatuses.map {
                            if (it.id == status.id) it.copy(isSaved = true) else it
                        }
                        current.copy(
                            availableStatuses = updated,
                            snackbarMessage = "Status was already saved in gallery."
                        )
                    }
                }
                is ExportResult.Failure -> {
                    _uiState.update { it.copy(snackbarMessage = "Save failed: ${result.message}") }
                }
            }
        }
    }

    fun saveSelected() {
        val selectedIds = _uiState.value.selectedIds
        if (selectedIds.isEmpty()) return

        val toSave = _uiState.value.availableStatuses.filter { selectedIds.contains(it.id) }
        viewModelScope.launch {
            _uiState.update { it.copy(isSavingBatch = true, batchProgress = 0 to toSave.size) }
            val batchResult = statusRepository.saveMultipleStatuses(toSave) { current, total ->
                _uiState.update { it.copy(batchProgress = current to total) }
            }

            val summary = buildString {
                append("Saved ${batchResult.succeeded} statuses")
                if (batchResult.duplicates > 0) append(", ${batchResult.duplicates} duplicates skipped")
                if (batchResult.failed > 0) append(", ${batchResult.failed} failed")
            }

            _uiState.update {
                it.copy(
                    isSavingBatch = false,
                    batchProgress = null,
                    selectedIds = emptySet(),
                    isSelectionMode = false,
                    snackbarMessage = summary
                )
            }
        }
    }

    fun deleteSavedRecord(id: Long) {
        viewModelScope.launch {
            statusRepository.deleteSavedStatusRecord(id)
            _uiState.update { it.copy(snackbarMessage = "History record removed") }
        }
    }

    fun onManualMediaImport(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true) }
            val manualStatuses = uris.mapIndexedNotNull { index, uri ->
                try {
                    val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val isVideo = mime.startsWith("video/")
                    val mediaType = if (isVideo) StatusMediaType.VIDEO else StatusMediaType.IMAGE
                    StatusMedia(
                        id = uri.toString(),
                        uri = uri,
                        displayName = "Imported_Status_${System.currentTimeMillis()}_$index",
                        mimeType = mime,
                        mediaType = mediaType,
                        sizeBytes = 0L,
                        modifiedAt = System.currentTimeMillis()
                    )
                } catch (e: Exception) {
                    null
                }
            }

            if (manualStatuses.isNotEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        isScanning = false,
                        availableStatuses = manualStatuses + current.availableStatuses,
                        emptyStateType = null,
                        snackbarMessage = "Imported ${manualStatuses.size} media files"
                    )
                }
            } else {
                _uiState.update { it.copy(isScanning = false) }
            }
        }
    }

    fun setPreviewItem(item: StatusMedia) {
        statusRepository.activePreviewMedia = item
    }

    fun setPreviewItemFromSaved(saved: SavedStatusEntity) {
        statusRepository.activePreviewMedia = StatusMedia(
            id = saved.originalUri,
            uri = Uri.parse(saved.savedUri),
            displayName = saved.displayName,
            mimeType = saved.mimeType,
            mediaType = if (saved.mediaType == "VIDEO") StatusMediaType.VIDEO else StatusMediaType.IMAGE,
            sizeBytes = saved.sizeBytes,
            modifiedAt = saved.savedAt,
            isSaved = true
        )
    }

    fun clearSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }
}
