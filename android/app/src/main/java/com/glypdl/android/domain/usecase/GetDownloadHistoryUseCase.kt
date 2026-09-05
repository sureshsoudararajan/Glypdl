/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.domain.usecase

import com.glypdl.android.data.local.entity.HistoryEntity
import com.glypdl.android.data.repository.HistoryRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetDownloadHistoryUseCase @Inject constructor(
    private val historyRepository: HistoryRepository
) {
    operator fun invoke(query: String? = null): Flow<List<HistoryEntity>> {
        return if (query.isNullOrBlank()) {
            historyRepository.getAllHistory()
        } else {
            historyRepository.searchHistory(query)
        }
    }
}
