/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
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

package com.glypdl.android.ui.browser.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.model.DetectedMedia
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.MediaKind
import com.glypdl.android.data.model.MediaVariant
import com.glypdl.android.data.model.SubtitleDownloadMode
import com.glypdl.android.data.model.SubtitleDownloadOptions
import com.glypdl.android.util.FileNameSanitizer
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualityPickerSheet(
    media: DetectedMedia,
    onConfirmDownload: (DownloadRequest) -> Unit,
    onAnalyzePage: (String) -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val defaultTitle = remember(media) {
        val raw = media.pageTitle?.takeIf { it.isNotBlank() }
            ?: media.url.substringBefore('?').substringAfterLast('/')
        FileNameSanitizer.safeFsTitle(raw)
    }
    var titleInput by remember { mutableStateOf(defaultTitle) }
    var isAudioOnly by remember { mutableStateOf(media.kind == MediaKind.PROGRESSIVE_AUDIO) }
    var embedSubtitles by remember { mutableStateOf(media.kind != MediaKind.PROGRESSIVE_AUDIO) }

    val variants = media.variants
    var selectedVariant by remember {
        mutableStateOf(variants.firstOrNull { !it.isAudioOnly } ?: variants.firstOrNull())
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Download Options",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Editable title field
            OutlinedTextField(
                value = titleInput,
                onValueChange = { titleInput = it },
                label = { Text("File Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Audio-only toggle
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { isAudioOnly = !isAudioOnly },
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MusicNote, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Audio Only", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                            Text("Extract track as MP3 / M4A", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(checked = isAudioOnly, onCheckedChange = { isAudioOnly = it })
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Subtitle Options toggle
            if (!isAudioOnly) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { embedSubtitles = !embedSubtitles },
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Subtitles, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Embed Subtitles", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                                Text("Extract and bake subtitles into video", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(checked = embedSubtitles, onCheckedChange = { embedSubtitles = it })
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (!isAudioOnly && variants.isNotEmpty()) {
                Text(
                    text = "Available Qualities",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 240.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(variants) { variant ->
                        val isSelected = variant == selectedVariant
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedVariant = variant },
                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = variant.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    val details = listOfNotNull(
                                        variant.fps?.let { "$it fps" },
                                        variant.codecs,
                                        variant.estimatedSizeBytes?.let { Formatter.formatFileSize(context, it) }
                                    ).joinToString(" • ")

                                    if (details.isNotEmpty()) {
                                        Text(
                                            text = details,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                RadioButton(
                                    selected = isSelected,
                                    onClick = { selectedVariant = variant }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Download CTA
            val subtitleOptions = if (embedSubtitles && !isAudioOnly) {
                SubtitleDownloadOptions(
                    mode = SubtitleDownloadMode.EMBED_SUBS,
                    downloadAllLanguages = true,
                    embedInVideo = true
                )
            } else {
                SubtitleDownloadOptions(mode = SubtitleDownloadMode.MEDIA_ONLY)
            }

            Button(
                onClick = {
                    val targetUrl = selectedVariant?.url ?: media.url
                    val ext = if (isAudioOnly) "m4a" else (media.ext ?: "mp4")
                    val sourceKind = when (media.kind) {
                        MediaKind.HLS -> "BROWSER_HLS"
                        MediaKind.DASH -> "BROWSER_DASH"
                        MediaKind.PROGRESSIVE_AUDIO -> "BROWSER_AUDIO"
                        else -> "BROWSER_DIRECT"
                    }

                    val request = DownloadRequest(
                        id = UUID.randomUUID().toString(),
                        url = targetUrl,
                        title = FileNameSanitizer.safeFsTitle(titleInput),
                        thumbnailUrl = media.posterUrl,
                        formatId = selectedVariant?.label ?: "best",
                        ext = ext,
                        resolution = selectedVariant?.label ?: "Best",
                        isAudioOnly = isAudioOnly,
                        destinationUri = null,
                        headers = media.headers,
                        sourceKind = sourceKind,
                        pageUrl = media.pageUrl,
                        requiresAuth = true,
                        subtitleOptions = subtitleOptions
                    )
                    onConfirmDownload(request)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Download", style = MaterialTheme.typography.titleSmall)
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle & Advanced Format Analysis CTA
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onAnalyzePage(media.pageUrl.ifBlank { media.url })
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(
                    Icons.Default.Subtitles,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Advanced Subtitle & Format Options", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
