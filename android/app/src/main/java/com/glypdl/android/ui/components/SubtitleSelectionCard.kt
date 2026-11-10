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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glypdl.android.data.model.SubtitleDownloadMode
import com.glypdl.android.data.model.SubtitleFormatPreference
import com.glypdl.android.data.model.SubtitleTrack
import com.glypdl.android.data.model.SubtitleType

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SubtitleSelectionCard(
    manualSubtitles: List<SubtitleTrack>,
    automaticSubtitles: List<SubtitleTrack>,
    selectedLanguages: Set<String>,
    downloadMode: SubtitleDownloadMode,
    preferredFormat: SubtitleFormatPreference,
    downloadAllLanguages: Boolean,
    includeManual: Boolean,
    includeAuto: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onToggleLanguage: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onDownloadModeChanged: (SubtitleDownloadMode) -> Unit,
    onPreferredFormatChanged: (SubtitleFormatPreference) -> Unit,
    onDownloadAllChanged: (Boolean) -> Unit,
    onIncludeManualChanged: (Boolean) -> Unit,
    onIncludeAutoChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val totalSubs = manualSubtitles.size + automaticSubtitles.size
    val selectedCount = if (downloadAllLanguages) totalSubs else selectedLanguages.size

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: clickable to expand/collapse
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleExpand() }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Subtitles,
                        contentDescription = "Subtitles",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Subtitles & Closed Captions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (totalSubs == 0) {
                                "None available for this media"
                            } else {
                                "$totalSubs track${if (totalSubs != 1) "s" else ""} • " +
                                        when {
                                            selectedCount > 0 -> "$selectedCount selected (${downloadMode.label})"
                                            else -> "None selected"
                                        }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (isExpanded) "Collapse" else "Expand"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    if (totalSubs == 0) {
                        EmptySubtitleState()
                    } else {
                        // 1. Download Mode Segmented Selector
                        Text(
                            text = "Download Mode",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SubtitleDownloadMode.values().forEach { mode ->
                                FilterChip(
                                    selected = downloadMode == mode,
                                    onClick = { onDownloadModeChanged(mode) },
                                    label = { Text(mode.label) },
                                    leadingIcon = {
                                        val icon = when (mode) {
                                            SubtitleDownloadMode.MEDIA_ONLY -> Icons.Default.Block
                                            SubtitleDownloadMode.MEDIA_WITH_SUBS -> Icons.Default.FolderZip
                                            SubtitleDownloadMode.EMBED_SUBS -> Icons.Default.VideoFile
                                            SubtitleDownloadMode.SUBS_ONLY -> Icons.Default.Subtitles
                                        }
                                        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Format & Filter Options
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Preferred Format",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Medium
                            )

                            var formatDropdownOpen by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(
                                    onClick = { formatDropdownOpen = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text(preferredFormat.displayName)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(
                                    expanded = formatDropdownOpen,
                                    onDismissRequest = { formatDropdownOpen = false }
                                ) {
                                    SubtitleFormatPreference.values().forEach { pref ->
                                        DropdownMenuItem(
                                            text = {
                                                Column {
                                                    Text(pref.displayName, fontWeight = FontWeight.Medium)
                                                    Text(
                                                        text = if (pref == SubtitleFormatPreference.BEST) "Keep provider original format" else "Convert via FFmpeg to .${pref.format}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            },
                                            onClick = {
                                                onPreferredFormatChanged(pref)
                                                formatDropdownOpen = false
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Toggles: Include manual, include auto, download all
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                if (manualSubtitles.isNotEmpty()) {
                                    FilterChip(
                                        selected = includeManual,
                                        onClick = { onIncludeManualChanged(!includeManual) },
                                        label = { Text("Manual (${manualSubtitles.size})") }
                                    )
                                }
                                if (automaticSubtitles.isNotEmpty()) {
                                    FilterChip(
                                        selected = includeAuto,
                                        onClick = { onIncludeAutoChanged(!includeAuto) },
                                        label = { Text("Auto (${automaticSubtitles.size})") }
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                TextButton(onClick = onSelectAll) {
                                    Text("All")
                                }
                                TextButton(onClick = onDeselectAll) {
                                    Text("None")
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // 3. Track List
                        // Manual Subtitles Group
                        if (includeManual && manualSubtitles.isNotEmpty()) {
                            Text(
                                text = "Manual Subtitles (${manualSubtitles.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            manualSubtitles.forEach { track ->
                                SubtitleTrackRow(
                                    track = track,
                                    isSelected = downloadAllLanguages || selectedLanguages.contains(track.languageCode),
                                    onToggle = { onToggleLanguage(track.languageCode) }
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        // Automatic Captions Group
                        if (includeAuto && automaticSubtitles.isNotEmpty()) {
                            Text(
                                text = "Automatic Captions (${automaticSubtitles.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.padding(vertical = 6.dp)
                            )

                            automaticSubtitles.forEach { track ->
                                SubtitleTrackRow(
                                    track = track,
                                    isSelected = downloadAllLanguages || selectedLanguages.contains(track.languageCode),
                                    onToggle = { onToggleLanguage(track.languageCode) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleTrackRow(
    track: SubtitleTrack,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onToggle() }
            .padding(vertical = 6.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = track.languageName,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    Text(
                        text = track.languageCode.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
                if (track.type == SubtitleType.AUTOMATIC) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    ) {
                        Text(
                            text = "AUTO",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }

            if (track.formats.isNotEmpty()) {
                Text(
                    text = "Formats: " + track.formats.take(4).joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun EmptySubtitleState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.SubtitlesOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "No subtitles or closed captions found",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "The host website or creator did not expose subtitle tracks for this media.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
