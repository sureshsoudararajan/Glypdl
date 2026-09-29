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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.model.DownloadStatus
import com.glypdl.android.data.model.FileCategory
import com.glypdl.android.service.resolver.FileCategoryClassifier

/**
 * Distinctive card component for Aria2 (direct file) downloads.
 */
@Composable
fun Aria2DownloadCard(
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
    val category = FileCategoryClassifier.fromExtension(download.ext)
    
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
            // Icon / Progress
            Box(
                modifier = Modifier.size(64.dp),
                contentAlignment = Alignment.Center
            ) {
                if (download.status == DownloadStatus.DOWNLOADING) {
                    val percent = progress?.percent ?: download.progress
                    CircularFileProgress(
                        progress = percent,
                        category = category,
                        size = 56.dp,
                        strokeWidth = 4.dp
                    )
                } else if (download.status == DownloadStatus.PREPARING) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(48.dp),
                        strokeWidth = 3.dp
                    )
                } else if (download.status == DownloadStatus.COMPLETED) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                } else if (download.status == DownloadStatus.FAILED) {
                    Icon(
                        imageVector = Icons.Default.Error,
                        contentDescription = "Failed",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.error
                    )
                } else {
                    FileTypeIcon(
                        category = category,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Details
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Badge(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ) {
                    Text(
                        text = category.name,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // Status info
                when (download.status) {
                    DownloadStatus.DOWNLOADING -> {
                        val downloaded = progress?.downloadedBytes ?: download.downloadedBytes
                        val total = progress?.totalBytes ?: download.totalBytes
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val dlStr = android.text.format.Formatter.formatShortFileSize(context, downloaded)
                        val totalStr = android.text.format.Formatter.formatShortFileSize(context, total)
                        val percent = progress?.percent ?: download.progress
                        val speedEta = buildString {
                            if (!progress?.speed.isNullOrBlank()) append(progress?.speed)
                            if (!progress?.eta.isNullOrBlank()) {
                                if (isNotEmpty()) append(" • ETA ")
                                append(progress?.eta)
                            }
                        }
                        
                        val remStr = if (total > downloaded && downloaded > 0L) {
                            " • " + android.text.format.Formatter.formatShortFileSize(context, total - downloaded) + " left"
                        } else ""
                        
                        Text(
                            text = if (total > 0L) {
                                "$dlStr / $totalStr • ${String.format(java.util.Locale.getDefault(), "%.1f%%", percent)}$remStr"
                            } else if (downloaded > 0L) {
                                dlStr
                            } else {
                                "Starting..."
                            },
                            style = MaterialTheme.typography.labelSmall
                        )
                        if (speedEta.isNotBlank()) {
                            Text(
                                text = speedEta,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        Text(
                            text = "Paused",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DownloadStatus.FAILED -> {
                        Text(
                            text = "Download Failed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    DownloadStatus.COMPLETED -> {
                        val context = androidx.compose.ui.platform.LocalContext.current
                        val totalStr = android.text.format.Formatter.formatShortFileSize(context, download.totalBytes)
                        Text(
                            text = "Completed • $totalStr",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    DownloadStatus.PREPARING -> {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Preparing download...",
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
                    else -> {
                        Text(
                            text = download.status.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            // Actions
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
                DownloadStatus.QUEUED, DownloadStatus.PREPARING -> {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.Default.Cancel, contentDescription = "Cancel")
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
