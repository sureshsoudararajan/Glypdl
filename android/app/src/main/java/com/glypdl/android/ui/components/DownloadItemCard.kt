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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.model.DownloadStatus

@Composable
fun DownloadItemCard(
    download: DownloadEntity,
    progress: DownloadProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit = {},
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            ThumbnailImage(
                url = download.thumbnailUrl,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentDescription = download.title
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Info column
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Format info
                val isFbOrIg = com.glypdl.android.util.UrlValidator.isInstagramUrl(download.url) ||
                        com.glypdl.android.util.UrlValidator.isFacebookUrl(download.url)
                val formatText = buildString {
                    if (download.isAudioOnly) append("🎵 ") else append("🎬 ")
                    if (isFbOrIg) {
                        if (download.isAudioOnly) append("Audio • ") else append("Video + Audio • ")
                    } else if (!download.resolution.isNullOrBlank()) {
                        append("${download.resolution} • ")
                    }
                    append(download.ext.uppercase())
                }
                Text(
                    text = formatText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Status-specific content
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        val percent = progress?.percent ?: download.progress
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val downloaded = progress?.downloadedBytes ?: download.downloadedBytes
                            val total = progress?.totalBytes ?: download.totalBytes
                            val context = androidx.compose.ui.platform.LocalContext.current
                            val progressText = buildString {
                                append(String.format(java.util.Locale.getDefault(), "%.1f%%", percent))
                                if (total > 0L) {
                                    val dlStr = android.text.format.Formatter.formatShortFileSize(context, downloaded)
                                    val totalStr = android.text.format.Formatter.formatShortFileSize(context, total)
                                    append(" ($dlStr / $totalStr)")
                                } else if (downloaded > 0L) {
                                    val dlStr = android.text.format.Formatter.formatShortFileSize(context, downloaded)
                                    append(" ($dlStr)")
                                }
                            }
                            Text(
                                text = progressText,
                                style = MaterialTheme.typography.labelSmall
                            )
                            val speedEta = buildString {
                                if (!progress?.speed.isNullOrBlank()) append(progress?.speed)
                                if (!progress?.eta.isNullOrBlank()) {
                                    if (isNotEmpty()) append(" • ")
                                    append("ETA ${progress?.eta}")
                                }
                            }
                            if (speedEta.isNotBlank()) {
                                Text(
                                    text = speedEta,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    DownloadStatus.PREPARING, DownloadStatus.PROCESSING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (download.status == DownloadStatus.PREPARING) "Preparing..." else "Processing...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        Text(
                            text = "✓ Completed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Text(
                            text = "✗ Download failed — tap for details",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DownloadStatus.PAUSED -> {
                        val percent = download.progress
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "⏸ Paused — ${percent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.QUEUED -> {
                        Text(
                            text = "⏳ Queued",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.CANCELLED -> {
                        Text(
                            text = "Cancelled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Action buttons
            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    IconButton(onClick = onPause) {
                        Icon(Icons.Default.Pause, contentDescription = "Pause")
                    }
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel")
                    }
                }
                DownloadStatus.PAUSED -> {
                    IconButton(onClick = onResume) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Resume")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                    IconButton(onClick = onRetry) {
                        Icon(Icons.Default.Refresh, contentDescription = "Retry")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                DownloadStatus.COMPLETED -> {
                    IconButton(onClick = onOpen) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
                else -> {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}
