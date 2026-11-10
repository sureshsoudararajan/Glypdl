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
package com.glypdl.android.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.R
import com.glypdl.android.data.local.entity.DownloadEntity
import com.glypdl.android.ui.components.ClipboardBanner
import com.glypdl.android.ui.components.DownloadItemCard
import com.glypdl.android.ui.components.UrlInputField
import com.glypdl.android.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToAnalyze: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    onNavigateToHistory: () -> Unit,
    onNavigateToDownloadDetail: (String) -> Unit,
    onNavigateToStatusSaver: () -> Unit = {},
    onNavigateToSubtitles: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var downloadToDelete by remember { mutableStateOf<DownloadEntity?>(null) }
    var deleteFileWithDownload by remember { mutableStateOf(true) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // Auto-detect URLs copied to system clipboard when returning to app (if clipboard detection is enabled)
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.checkClipboard(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Glypdl") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onBackground
                ),
                actions = {
                    IconButton(onClick = onNavigateToSubtitles) {
                        Icon(Icons.Default.Subtitles, contentDescription = "Subtitles")
                    }
                    IconButton(onClick = onNavigateToStatusSaver) {
                        Icon(
                            painter = painterResource(R.drawable.ic_whatsapp),
                            contentDescription = "WhatsApp Status Saver",
                            tint = WhatsAppGreen,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            UrlInputField(
                url = uiState.url,
                onUrlChange = viewModel::onUrlChange,
                onAnalyzeClick = { 
                    viewModel.onAnalyzeClick()
                    onNavigateToAnalyze(uiState.url) 
                },
                isValid = uiState.isUrlValid,
                isLoading = false
            )

            uiState.clipboardUrl?.let { clipboardUrl ->
                Spacer(modifier = Modifier.height(16.dp))
                ClipboardBanner(
                    url = clipboardUrl,
                    onAnalyzeClick = { onNavigateToAnalyze(clipboardUrl) },
                    onDismiss = viewModel::clearClipboardBanner
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Recent Downloads",
                    style = MaterialTheme.typography.titleMedium
                )
                TextButton(onClick = onNavigateToDownloads) {
                    Text("See all")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(uiState.recentDownloads.take(5), key = { it.id }) { download ->
                    DownloadItemCard(
                        download = download,
                        progress = uiState.recentProgress[download.id],
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
}
