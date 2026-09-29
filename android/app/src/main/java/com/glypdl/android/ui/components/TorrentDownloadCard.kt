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
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadProgress
import com.glypdl.android.data.model.DownloadStatus
import java.util.Locale

@Composable
fun TorrentDownloadCard(
    download: DownloadEntity,
    progress: DownloadProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header: Title + Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = download.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Badge(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = "TORRENT",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            val isDownloading = download.status == DownloadStatus.DOWNLOADING
            val seeds = progress?.seeds ?: 0
            val peers = progress?.peers ?: download.connections

            // Swarm Visualization
            if (isDownloading || download.status == DownloadStatus.RESOLVING || download.status == DownloadStatus.METADATA) {
                SwarmVisualization(
                    seeds = seeds,
                    peers = peers,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    isDownloading = isDownloading
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Piece Grid
            if (download.status == DownloadStatus.DOWNLOADING || download.status == DownloadStatus.PAUSED || download.status == DownloadStatus.COMPLETED) {
                val bitfield = progress?.bitfield
                val totalPieces = progress?.totalPieces ?: 0
                if (totalPieces > 0) {
                    PieceGrid(
                        bitfield = bitfield,
                        totalPieces = totalPieces,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Status and Metrics
            val percent = progress?.percent ?: download.progress
            val downloaded = progress?.downloadedBytes ?: download.downloadedBytes
            val total = progress?.totalBytes ?: download.totalBytes
            
            val context = LocalContext.current
            val dlStr = android.text.format.Formatter.formatShortFileSize(context, downloaded)
            val totalStr = if (total > 0) android.text.format.Formatter.formatShortFileSize(context, total) else "?"
            val percentStr = String.format(Locale.getDefault(), "%.1f%%", percent)
            
            Text(
                text = "$dlStr / $totalStr ($percentStr)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            if (download.status == DownloadStatus.DOWNLOADING) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val dlSpeed = progress?.speed ?: "0 B/s"
                    val upSpeed = progress?.uploadSpeed ?: "0 B/s"
                    val eta = progress?.eta ?: "--:--"
                    
                    Text(
                        text = "↓ $dlSpeed • ↑ $upSpeed",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "ETA $eta",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            when (download.status) {
                DownloadStatus.DOWNLOADING -> {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                DownloadStatus.PREPARING, DownloadStatus.PROCESSING, DownloadStatus.RESOLVING, DownloadStatus.METADATA, DownloadStatus.VERIFYING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Processing...",
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
                        text = "✗ Failed: ${download.errorMessage ?: "Unknown error"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                DownloadStatus.PAUSED -> {
                    LinearProgressIndicator(
                        progress = { percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "⏸ Paused",
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

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
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
}
