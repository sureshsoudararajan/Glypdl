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

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.glypdl.android.data.model.DetectedMedia
import com.glypdl.android.data.model.MediaKind

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetectedMediaSheet(
    mediaList: List<DetectedMedia>,
    onDownloadMedia: (DetectedMedia) -> Unit,
    onAnalyzeMedia: (DetectedMedia) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
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
                    text = "Detected Media (${mediaList.size})",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            if (mediaList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.VideoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Play a video on the page and it will appear here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(mediaList, key = { it.id }) { media ->
                        DetectedMediaCard(
                            media = media,
                            onDownload = { onDownloadMedia(media) },
                            onAnalyze = { onAnalyzeMedia(media) },
                            onCopyUrl = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(ClipData.newPlainText("Media URL", media.url))
                                Toast.makeText(context, "Media link copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetectedMediaCard(
    media: DetectedMedia,
    onDownload: () -> Unit,
    onAnalyze: () -> Unit,
    onCopyUrl: () -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail / Icon
            val defaultIcon = when (media.kind) {
                MediaKind.PROGRESSIVE_AUDIO -> Icons.Default.MusicNote
                MediaKind.HLS, MediaKind.DASH -> Icons.Default.LiveTv
                else -> Icons.Default.Movie
            }
            val defaultPainter = rememberVectorPainter(defaultIcon)

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (!media.posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = media.posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = defaultPainter,
                        error = defaultPainter,
                        fallback = defaultPainter,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        defaultIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                val displayName = media.pageTitle?.takeIf { it.isNotBlank() }
                    ?: media.url.substringBefore('?').substringAfterLast('/')
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Type Badge
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = when (media.kind) {
                            MediaKind.HLS -> MaterialTheme.colorScheme.secondaryContainer
                            MediaKind.DASH -> MaterialTheme.colorScheme.tertiaryContainer
                            MediaKind.PROGRESSIVE_AUDIO -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ) {
                        val kindLabel = when (media.kind) {
                            MediaKind.HLS -> "HLS"
                            MediaKind.DASH -> "DASH"
                            MediaKind.PROGRESSIVE_AUDIO -> (media.ext?.uppercase() ?: "AUDIO")
                            else -> (media.ext?.uppercase() ?: "MP4")
                        }
                        Text(
                            text = kindLabel,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    // Size Badge if available
                    if (media.sizeBytes != null && media.sizeBytes > 0) {
                        Text(
                            text = Formatter.formatFileSize(context, media.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // DRM Protection Badge
                    if (media.isDrmProtected) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Text(
                                text = "Protected",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Action Buttons
            if (media.isDrmProtected) {
                IconButton(onClick = {}, enabled = false) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "Protected, cannot be downloaded",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                IconButton(onClick = onCopyUrl) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy link",
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(onClick = onAnalyze) {
                    Icon(
                        Icons.Default.Subtitles,
                        contentDescription = "Subtitles & Formats",
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }

                FilledIconButton(
                    onClick = {
                        if (media.kind == MediaKind.HLS || media.kind == MediaKind.DASH) {
                            onAnalyze()
                        } else {
                            onDownload()
                        }
                    },
                    modifier = Modifier.size(38.dp)
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Download media",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
