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

package com.glypdl.android.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

@Composable
fun PieceGrid(
    bitfield: String?,
    totalPieces: Int,
    modifier: Modifier = Modifier
) {
    val downloadedColor = MaterialTheme.colorScheme.primary
    val missingColor = MaterialTheme.colorScheme.surfaceVariant

    val decodedBitfield = remember(bitfield, totalPieces) {
        if (bitfield == null || totalPieces <= 0) {
            BooleanArray(totalPieces) { false }
        } else {
            com.glypdl.android.service.engine.BitfieldHelper.decodeHex(bitfield, totalPieces)
        }
    }

    if (totalPieces <= 0) return

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
    ) {
        val spacing = 1.5.dp.toPx()
        val pieceSize = 4.dp.toPx()
        val availableWidth = size.width
        
        val columns = ((availableWidth + spacing) / (pieceSize + spacing)).toInt().coerceAtLeast(1)
        val rows = (totalPieces + columns - 1) / columns
        
        // Scale down piece size if it doesn't fit vertically
        val availableHeight = size.height
        val maxScalePieceSize = if (rows > 0) {
            ((availableHeight + spacing) / rows - spacing).coerceAtMost(pieceSize)
        } else pieceSize
        
        val actualPieceSize = maxScalePieceSize
        val actualSpacing = spacing

        for (i in 0 until totalPieces) {
            val col = i % columns
            val row = i / columns
            
            val x = col * (actualPieceSize + actualSpacing)
            val y = row * (actualPieceSize + actualSpacing)
            
            val isDownloaded = if (i < decodedBitfield.size) decodedBitfield[i] else false
            
            drawRoundRect(
                color = if (isDownloaded) downloadedColor else missingColor,
                topLeft = Offset(x, y),
                size = Size(actualPieceSize, actualPieceSize),
                cornerRadius = CornerRadius(1.dp.toPx())
            )
        }
    }
}
