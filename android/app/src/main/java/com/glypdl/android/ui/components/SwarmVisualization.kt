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
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun SwarmVisualization(
    seeds: Int,
    peers: Int,
    modifier: Modifier = Modifier,
    isDownloading: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "swarm_animation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swarm_rotation"
    )

    val pulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "swarm_pulse"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val onSurfaceColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)
    val surfaceColor = MaterialTheme.colorScheme.surface
    
    val totalNodes = (seeds + peers).coerceAtMost(30).coerceAtLeast(3)
    val nodes = remember(totalNodes) {
        List(totalNodes) {
            val isSeed = it < seeds
            val angle = Random.nextFloat() * 360f
            val distanceRatio = Random.nextFloat() * 0.5f + 0.5f // 0.5 to 1.0 of radius
            val speed = Random.nextFloat() * 0.5f + 0.5f
            NodeData(isSeed, angle, distanceRatio, speed)
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = minOf(size.width, size.height) / 2f - 16.dp.toPx()

            withTransform({
                if (isDownloading) {
                    rotate(rotation, center)
                }
            }) {
                nodes.forEach { node ->
                    val nodeAngle = Math.toRadians((node.angle + (rotation * node.speed)).toDouble())
                    val nodeRadius = maxRadius * node.distanceRatio
                    
                    val nx = center.x + cos(nodeAngle).toFloat() * nodeRadius
                    val ny = center.y + sin(nodeAngle).toFloat() * nodeRadius
                    val nodePos = Offset(nx, ny)

                    // Draw connection line
                    drawLine(
                        color = onSurfaceColor,
                        start = center,
                        end = nodePos,
                        strokeWidth = 1.dp.toPx()
                    )

                    // Draw pulse
                    if (isDownloading) {
                        val pulsePos = Offset(
                            center.x + (nx - center.x) * pulse,
                            center.y + (ny - center.y) * pulse
                        )
                        drawCircle(
                            color = if (node.isSeed) secondaryColor else tertiaryColor,
                            radius = 2.dp.toPx(),
                            center = pulsePos,
                            alpha = 1f - pulse
                        )
                    }

                    // Draw node
                    drawCircle(
                        color = if (node.isSeed) secondaryColor else tertiaryColor,
                        radius = 4.dp.toPx(),
                        center = nodePos
                    )
                }
            }

            // Draw central node
            drawCircle(
                color = primaryColor,
                radius = 16.dp.toPx(),
                center = center
            )
            drawCircle(
                color = surfaceColor,
                radius = 10.dp.toPx(),
                center = center
            )
            drawCircle(
                color = primaryColor,
                radius = 6.dp.toPx(),
                center = center
            )
        }
        
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Seeds: $seeds",
                style = MaterialTheme.typography.labelSmall,
                color = secondaryColor
            )
            Text(
                text = "Peers: $peers",
                style = MaterialTheme.typography.labelSmall,
                color = tertiaryColor
            )
        }
    }
}

private data class NodeData(
    val isSeed: Boolean,
    val angle: Float,
    val distanceRatio: Float,
    val speed: Float
)
