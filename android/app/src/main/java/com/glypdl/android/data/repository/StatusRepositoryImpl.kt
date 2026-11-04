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
package com.glypdl.android.data.repository

import android.net.Uri
import com.glypdl.android.data.local.dao.SavedStatusDao
import com.glypdl.android.data.local.entity.SavedStatusEntity
import com.glypdl.android.data.status.StatusMediaScanner
import com.glypdl.android.data.status.StatusScanResult
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.service.status.ExportResult
import com.glypdl.android.service.status.StatusExportManager
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StatusRepositoryImpl @Inject constructor(
    private val scanner: StatusMediaScanner,
    private val exportManager: StatusExportManager,
    private val savedStatusDao: SavedStatusDao,
    private val settingsRepository: SettingsRepository
) : StatusRepository {

    override val configuredFolderUri: Flow<String?> = settingsRepository.whatsappStatusFolderUri

    override var activePreviewMedia: StatusMedia? = null

    override suspend fun setConfiguredFolderUri(uriString: String?) {
        settingsRepository.setWhatsappStatusFolderUri(uriString)
    }

    override suspend fun scanStatuses(treeUri: Uri): StatusScanResult {
        val result = scanner.scanDirectory(treeUri)
        if (result is StatusScanResult.Success) {
            // Annotate items with isSaved flag from database
            val annotated = result.items.map { item ->
                val isSaved = savedStatusDao.isOriginalUriSaved(item.uri.toString())
                if (isSaved) item.copy(isSaved = true) else item
            }
            return StatusScanResult.Success(annotated)
        }
        return result
    }

    override fun getSavedStatuses(): Flow<List<SavedStatusEntity>> {
        return savedStatusDao.getAllSavedStatuses()
    }

    override suspend fun saveStatus(status: StatusMedia, skipDuplicateCheck: Boolean): ExportResult {
        return exportManager.exportStatus(status, skipDuplicateCheck)
    }

    override suspend fun saveMultipleStatuses(
        statuses: List<StatusMedia>,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchSaveResult {
        var succeeded = 0
        var duplicates = 0
        var failed = 0
        val total = statuses.size

        statuses.forEachIndexed { index, status ->
            onProgress(index + 1, total)
            when (exportManager.exportStatus(status, skipDuplicateCheck = false)) {
                is ExportResult.Success -> succeeded++
                is ExportResult.AlreadySaved -> duplicates++
                is ExportResult.Failure -> failed++
            }
        }

        return BatchSaveResult(succeeded = succeeded, duplicates = duplicates, failed = failed)
    }

    override suspend fun deleteSavedStatusRecord(id: Long) {
        savedStatusDao.deleteById(id)
    }

    override suspend fun deleteAllSavedStatusRecords() {
        savedStatusDao.deleteAll()
    }

    override suspend fun isStatusSaved(originalUri: String): Boolean {
        return savedStatusDao.isOriginalUriSaved(originalUri)
    }
}
