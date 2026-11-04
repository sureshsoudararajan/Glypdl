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
package com.glypdl.android.ui.status.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.status.model.StatusMedia

@Composable
fun StatusGrid(
    items: List<StatusMedia>,
    selectedIds: Set<String>,
    isSelectionMode: Boolean,
    onItemClick: (StatusMedia) -> Unit,
    onItemLongClick: (StatusMedia) -> Unit,
    onSaveClick: (StatusMedia) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier.fillMaxSize()
    ) {
        items(
            items = items,
            key = { it.id }
        ) { status ->
            val isSelected = selectedIds.contains(status.id)
            StatusGridItem(
                status = status,
                isSelected = isSelected,
                isSelectionMode = isSelectionMode,
                onItemClick = { onItemClick(status) },
                onItemLongClick = { onItemLongClick(status) },
                onSaveClick = { onSaveClick(status) }
            )
        }
    }
}
