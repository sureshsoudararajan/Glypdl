/*
 * Copyright (c) 2026. Glypdl
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

package com.glypdl.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.model.DownloadStatus

private data class FilterOption(
    val label: String,
    val status: DownloadStatus?
)

private val filterOptions = listOf(
    FilterOption("All", null),
    FilterOption("Downloading", DownloadStatus.DOWNLOADING),
    FilterOption("Queued", DownloadStatus.QUEUED),
    FilterOption("Completed", DownloadStatus.COMPLETED),
    FilterOption("Failed", DownloadStatus.FAILED),
    FilterOption("Cancelled", DownloadStatus.CANCELLED)
)

@Composable
fun FilterChipRow(
    selectedFilter: DownloadStatus?,
    onFilterSelected: (DownloadStatus?) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(filterOptions) { option ->
            FilterChip(
                selected = selectedFilter == option.status,
                onClick = { onFilterSelected(option.status) },
                label = { Text(option.label) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    }
}
