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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.ui.components.DownloadItemCard
import com.glypdl.android.ui.components.FilterChipRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(
    viewModel: DownloadsViewModel = hiltViewModel(),
    onNavigateToDownloadDetail: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var downloadToDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var deleteFileWithDownload by remember { mutableStateOf(true) }
    var showClearCompletedDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Downloads") },
                actions = {
                    val hasCompleted = uiState.downloads.any { it.status == com.glypdl.android.data.model.DownloadStatus.COMPLETED }
                    if (hasCompleted) {
                        IconButton(onClick = { showClearCompletedDialog = true }) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = "Clear Completed")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            FilterChipRow(
                selectedFilter = uiState.selectedFilter,
                onFilterSelected = viewModel::setFilter
            )

            if (uiState.downloads.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.DownloadDone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "No downloads yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val context = androidx.compose.ui.platform.LocalContext.current
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.downloads, key = { it.id }) { download ->
                        DownloadItemCard(
                            download = download,
                            progress = uiState.progressMap[download.id],
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onCancel = { viewModel.cancelDownload(download.id) },
                            onRetry = { viewModel.retryDownload(download.id) },
                            onOpen = {
                                com.glypdl.android.util.StorageHelper.openMedia(
                                    context,
                                    download.filePath ?: "",
                                    download.isAudioOnly
                                )
                            },
                            onDelete = {
                                deleteFileWithDownload = true
                                downloadToDelete = download
                            },
                            onClick = { onNavigateToDownloadDetail(download.id) }
                        )
                    }
                }
            }
        }
    }

    // Confirmation dialog for deleting an individual download
    downloadToDelete?.let { download ->
        AlertDialog(
            onDismissRequest = { downloadToDelete = null },
            title = { Text("Delete Download") },
            text = {
                Column {
                    Text("Are you sure you want to delete \"${download.title}\"?")
                    if (!download.filePath.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = deleteFileWithDownload,
                                onCheckedChange = { deleteFileWithDownload = it }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Also delete downloaded media file from storage")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDownload(download.id, deleteFile = deleteFileWithDownload)
                        downloadToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { downloadToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirmation dialog for clearing all completed downloads
    if (showClearCompletedDialog) {
        AlertDialog(
            onDismissRequest = { showClearCompletedDialog = false },
            title = { Text("Clear Completed") },
            text = { Text("Remove all completed downloads from the list? (Downloaded files on storage will not be deleted).") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearCompletedDownloads()
                        showClearCompletedDialog = false
                    }
                ) {
                    Text("Clear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearCompletedDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
