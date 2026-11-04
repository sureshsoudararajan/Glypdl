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
import com.glypdl.android.data.local.entity.SavedStatusEntity
import com.glypdl.android.data.status.StatusScanResult
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.service.status.ExportResult
import kotlinx.coroutines.flow.Flow

data class BatchSaveResult(
    val succeeded: Int,
    val duplicates: Int,
    val failed: Int
)

interface StatusRepository {

    val configuredFolderUri: Flow<String?>

    suspend fun setConfiguredFolderUri(uriString: String?)

    suspend fun scanStatuses(treeUri: Uri): StatusScanResult

    fun getSavedStatuses(): Flow<List<SavedStatusEntity>>

    suspend fun saveStatus(status: StatusMedia, skipDuplicateCheck: Boolean = false): ExportResult

    suspend fun saveMultipleStatuses(
        statuses: List<StatusMedia>,
        onProgress: (current: Int, total: Int) -> Unit
    ): BatchSaveResult

    suspend fun deleteSavedStatusRecord(id: Long)

    suspend fun deleteAllSavedStatusRecords()

    suspend fun isStatusSaved(originalUri: String): Boolean
}
