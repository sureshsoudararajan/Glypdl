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

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.model.FileCategory

/**
 * A circular progress indicator for file downloads.
 * Displays the icon in the center, and a progress ring around it.
 */
@Composable
fun CircularFileProgress(
    progress: Float,
    category: FileCategory,
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    strokeWidth: Dp = 6.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    speedEta: String? = null
) {
    val isIndeterminate = progress <= 0f

    val infiniteTransition = rememberInfiniteTransition(label = "ProgressAnimation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RotationAnimation"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(size),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val stroke = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
                val diameter = size.toPx()
                val radius = diameter / 2f
                
                // Draw track
                drawArc(
                    color = trackColor,
                    startAngle = 0f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke
                )

                // Draw progress
                if (isIndeterminate) {
                    drawArc(
                        color = color,
                        startAngle = rotation,
                        sweepAngle = 90f,
                        useCenter = false,
                        style = stroke
                    )
                } else {
                    drawArc(
                        color = color,
                        startAngle = -90f,
                        sweepAngle = 360f * (progress / 100f),
                        useCenter = false,
                        style = stroke
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(size - strokeWidth * 2 - 8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                FileTypeIcon(
                    category = category,
                    modifier = Modifier.size(24.dp),
                    tint = color
                )
            }
        }
        
        if (speedEta != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = speedEta,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
