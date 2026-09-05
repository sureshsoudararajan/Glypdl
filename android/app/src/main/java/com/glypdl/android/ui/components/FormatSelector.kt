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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glypdl.android.data.model.AudioQuality
import com.glypdl.android.data.model.MediaFormat
import com.glypdl.android.data.model.VideoQuality

@Composable
fun QualitySelector(
    videoQualities: List<VideoQuality>,
    audioQualities: List<AudioQuality>,
    rawFormats: List<MediaFormat> = emptyList(),
    selectedVideoQuality: VideoQuality?,
    selectedAudioQuality: AudioQuality?,
    isAudioOnly: Boolean,
    onModeChanged: (Boolean) -> Unit,
    onVideoQualitySelected: (VideoQuality) -> Unit,
    onAudioQualitySelected: (AudioQuality) -> Unit,
    onRawFormatSelected: ((MediaFormat) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showAdvancedFormats by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Mode Selector (Video vs Audio Only)
        if (videoQualities.isNotEmpty() && audioQualities.isNotEmpty()) {
            TabRow(
                selectedTabIndex = if (isAudioOnly) 1 else 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = !isAudioOnly,
                    onClick = { onModeChanged(false) },
                    text = { Text("Video & Audio", fontWeight = FontWeight.SemiBold) }
                )
                Tab(
                    selected = isAudioOnly,
                    onClick = { onModeChanged(true) },
                    text = { Text("Audio Only", fontWeight = FontWeight.SemiBold) }
                )
            }
        }

        // 1. VIDEO QUALITIES SECTION (Only when not Audio-Only)
        if (!isAudioOnly && videoQualities.isNotEmpty()) {
            Text(
                text = "VIDEO QUALITY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            videoQualities.forEach { quality ->
                val isSelected = selectedVideoQuality?.formatSelector == quality.formatSelector
                VideoQualityCard(
                    quality = quality,
                    isSelected = isSelected,
                    onClick = { onVideoQualitySelected(quality) }
                )
            }
        }

        // 2. AUDIO QUALITIES SECTION
        if (audioQualities.isNotEmpty()) {
            Spacer(modifier = Modifier.height(if (!isAudioOnly) 16.dp else 4.dp))
            Text(
                text = if (!isAudioOnly) "AUDIO TRACK (MERGED WITH VIDEO)" else "AUDIO QUALITY",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            )

            audioQualities.forEach { quality ->
                val isSelected = selectedAudioQuality?.formatSelector == quality.formatSelector
                AudioQualityCard(
                    quality = quality,
                    isSelected = isSelected,
                    onClick = { onAudioQualitySelected(quality) }
                )
            }
        }

        // 3. OPTIONAL ADVANCED FORMATS SECTION
        if (rawFormats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAdvancedFormats = !showAdvancedFormats }
                    .padding(vertical = 8.dp, horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Advanced formats (${rawFormats.size})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Icon(
                    imageVector = if (showAdvancedFormats) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = showAdvancedFormats) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    rawFormats.forEach { format ->
                        AdvancedFormatCard(format = format)
                    }
                }
            }
        }
    }
}

@Composable
private fun VideoQualityCard(
    quality: VideoQuality,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = quality.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }

            if (quality.displaySize.isNotBlank()) {
                Text(
                    text = quality.displaySize,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AudioQualityCard(
    quality: AudioQuality,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(if (isSelected) 2.dp else 1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = quality.displayBitrate,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (quality.displaySize.isNotBlank()) {
                    Text(
                        text = quality.displaySize,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = quality.displayExtension,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedFormatCard(
    format: MediaFormat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ID: ${format.formatId} • ${format.ext.uppercase()}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    fontWeight = FontWeight.SemiBold
                )
                val codecInfo = listOfNotNull(
                    format.vcodec?.takeIf { it != "none" },
                    format.acodec?.takeIf { it != "none" }
                ).joinToString(" / ")
                if (codecInfo.isNotBlank()) {
                    Text(
                        text = codecInfo,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = format.displayResolution,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FormatSelector(
    formats: List<MediaFormat>,
    selectedFormatId: String?,
    onFormatSelected: (MediaFormat) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoFormats = formats.filter { !it.isAudioOnly }
    val audioFormats = formats.filter { it.isAudioOnly }

    Column(modifier = modifier.fillMaxWidth()) {
        if (videoFormats.isNotEmpty()) {
            Text(
                text = "VIDEO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            videoFormats.forEach { format ->
                Card(
                    onClick = { onFormatSelected(format) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (format.formatId == selectedFormatId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        if (format.formatId == selectedFormatId) 2.dp else 1.dp,
                        if (format.formatId == selectedFormatId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = format.displayResolution,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )

                        if (format.displaySize.isNotBlank()) {
                            Text(
                                text = format.displaySize,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (audioFormats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "AUDIO",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            )
            audioFormats.forEach { format ->
                Card(
                    onClick = { onFormatSelected(format) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (format.formatId == selectedFormatId) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        }
                    ),
                    border = BorderStroke(
                        if (format.formatId == selectedFormatId) 2.dp else 1.dp,
                        if (format.formatId == selectedFormatId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = format.displayResolution,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )

                        if (format.displaySize.isNotBlank()) {
                            Text(
                                text = format.displaySize,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
