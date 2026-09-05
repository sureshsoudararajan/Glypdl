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
package com.glypdl.android.ui.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.data.model.DownloadStatus
import com.glypdl.android.ui.components.ThumbnailImage
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.glypdl.android.util.UrlValidator
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadDetailScreen(
    viewModel: DownloadDetailViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val download = uiState.download
    val progress = uiState.progress
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deleteFileWithDownload by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (download != null) {
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Delete Download",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (download != null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                download.thumbnailUrl?.let { url ->
                    ThumbnailImage(
                        url = url,
                        contentDescription = "Thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = download.title,
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(8.dp))

                val chipColor = when (download.status) {
                    DownloadStatus.COMPLETED -> Color(0xFF4CAF50)
                    DownloadStatus.DOWNLOADING -> Color(0xFF2196F3)
                    DownloadStatus.FAILED -> Color(0xFFF44336)
                    else -> Color.Gray
                }

                Surface(
                    color = chipColor.copy(alpha = 0.2f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Text(
                        text = download.status.name,
                        color = chipColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (download.status == DownloadStatus.DOWNLOADING && progress != null) {
                    LinearProgressIndicator(
                        progress = { progress.percent / 100f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(String.format(Locale.getDefault(), "%.1f%%", progress.percent))
                        Text("${progress.speed} - ETA: ${progress.eta}")
                    }
                    val context = androidx.compose.ui.platform.LocalContext.current
                    val sizeText = buildString {
                        if (progress.totalBytes > 0L) {
                            val dlStr = android.text.format.Formatter.formatShortFileSize(context, progress.downloadedBytes)
                            val totalStr = android.text.format.Formatter.formatShortFileSize(context, progress.totalBytes)
                            append("$dlStr / $totalStr")
                        } else if (progress.downloadedBytes > 0L) {
                            val dlStr = android.text.format.Formatter.formatShortFileSize(context, progress.downloadedBytes)
                            append("Downloaded: $dlStr")
                        }
                    }
                    if (sizeText.isNotBlank()) {
                        Text(
                            text = sizeText,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Text("Info", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                
                val isFbOrIg = UrlValidator.isInstagramUrl(download.url) || UrlValidator.isFacebookUrl(download.url)
                val formatDisplay = when {
                    isFbOrIg && download.isAudioOnly -> "Audio (${download.ext.uppercase()})"
                    isFbOrIg -> "Video + Audio (${download.ext.uppercase()})"
                    download.formatId.contains("+") || download.formatId.contains("bestvideo") -> "Video + Audio (${download.ext.uppercase()})"
                    download.isAudioOnly -> "Audio (${download.ext.uppercase()})"
                    else -> "${download.formatId} (${download.ext.uppercase()})"
                }
                val qualityDisplay = when {
                    isFbOrIg -> "Best available"
                    !download.resolution.isNullOrBlank() -> download.resolution
                    else -> "Standard"
                }

                Text("URL: ${download.url}", style = MaterialTheme.typography.bodyMedium)
                Text("Format: $formatDisplay", style = MaterialTheme.typography.bodyMedium)
                Text("Quality: $qualityDisplay", style = MaterialTheme.typography.bodyMedium)
                if (!download.filePath.isNullOrBlank()) {
                    Text("File Path: ${download.filePath}", style = MaterialTheme.typography.bodyMedium)
                }

                if (download.status == DownloadStatus.FAILED) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Failure reason",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = download.errorMessage ?: "Download failed.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            val log = download.technicalLog
                            if (!log.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                var expanded by remember { mutableStateOf(false) }
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expanded = !expanded },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (expanded) "Error Details ▲" else "Error Details ▼",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (expanded) {
                                        val clipboard = LocalClipboardManager.current
                                        TextButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(log))
                                            }
                                        ) {
                                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy logs")
                                        }
                                    }
                                }
                                if (expanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Surface(
                                        color = MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(max = 220.dp)
                                    ) {
                                        Text(
                                            text = log,
                                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                            modifier = Modifier
                                                .padding(12.dp)
                                                .verticalScroll(rememberScrollState())
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val context = androidx.compose.ui.platform.LocalContext.current
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (download.status) {
                        DownloadStatus.DOWNLOADING -> {
                            Button(onClick = viewModel::pauseDownload, modifier = Modifier.weight(1f)) { Text("Pause") }
                            Button(onClick = viewModel::cancelDownload, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        }
                        DownloadStatus.PAUSED -> {
                            Button(onClick = viewModel::resumeDownload, modifier = Modifier.weight(1f)) { Text("Resume") }
                            Button(onClick = viewModel::cancelDownload, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        }
                        DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                            Button(onClick = viewModel::retryDownload, modifier = Modifier.weight(1f)) { Text("Retry") }
                            Button(onClick = { showDeleteDialog = true }, modifier = Modifier.weight(1f)) { Text("Clear") }
                        }
                        DownloadStatus.COMPLETED -> {
                            Button(onClick = {
                                com.glypdl.android.util.StorageHelper.openMedia(
                                    context,
                                    download.filePath ?: "",
                                    download.isAudioOnly
                                )
                            }, modifier = Modifier.weight(1f)) { Text("Open") }
                            Button(onClick = {
                                com.glypdl.android.util.StorageHelper.shareMedia(
                                    context,
                                    download.filePath ?: "",
                                    download.title,
                                    download.isAudioOnly
                                )
                            }, modifier = Modifier.weight(1f)) { Text("Share") }
                        }
                        else -> {
                            Button(onClick = viewModel::cancelDownload, modifier = Modifier.weight(1f)) { Text("Cancel") }
                        }
                    }

                    // Always provide Delete button in detail screen
                    OutlinedButton(
                        onClick = { showDeleteDialog = true },
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }

    if (showDeleteDialog && download != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Download") },
            text = {
                Column {
                    Text("Delete \"${download.title}\"?")
                    if (!download.filePath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteFileWithDownload,
                                onCheckedChange = { deleteFileWithDownload = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Also delete file from storage")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(deleteFile = deleteFileWithDownload) {
                            showDeleteDialog = false
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
