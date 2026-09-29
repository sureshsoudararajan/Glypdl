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

package com.glypdl.android.ui.torrent

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.ui.components.SwarmVisualization

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TorrentPreviewScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit,
    viewModel: TorrentPreviewViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Torrent Preview") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            if (uiState is TorrentPreviewUiState.Ready) {
                val state = uiState as TorrentPreviewUiState.Ready
                BottomAppBar(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Button(
                        onClick = {
                            viewModel.startDownload {
                                onNavigateToDownloads()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = state.selectedIndices.isNotEmpty()
                    ) {
                        Text("Start Download")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is TorrentPreviewUiState.Resolving -> {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        SwarmVisualization(
                            seeds = state.seeds,
                            peers = state.peers,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            isDownloading = true
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            text = "Resolving magnet metadata...",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                viewModel.startDirectDownload {
                                    onNavigateToDownloads()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("Download Now (Skip Preview)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth(0.7f)
                        ) {
                            Text("Cancel")
                        }
                    }
                }
                is TorrentPreviewUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Error",
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
                is TorrentPreviewUiState.Ready -> {
                    Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = state.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val selectedSizeStr = android.text.format.Formatter.formatShortFileSize(context, state.totalSelectedSize)
                            val totalSizeStr = android.text.format.Formatter.formatShortFileSize(context, state.totalSize)
                            
                            Text(
                                text = "Selected: $selectedSizeStr of $totalSizeStr (${state.selectedIndices.size} of ${state.files.size} files)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = state.selectedIndices.size == state.files.size,
                                    onClick = { viewModel.selectAll() },
                                    label = { Text("Select All") }
                                )
                                FilterChip(
                                    selected = state.selectedIndices.isEmpty(),
                                    onClick = { viewModel.deselectAll() },
                                    label = { Text("Deselect All") }
                                )
                            }
                        }
                        
                        HorizontalDivider()
                        
                        // File List
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(vertical = 8.dp)
                        ) {
                            items(state.files, key = { it.index }) { file ->
                                val isSelected = state.selectedIndices.contains(file.index)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.toggleFile(file.index) }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { viewModel.toggleFile(file.index) }
                                    )
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = file.path.substringAfterLast('/'), // Simplify path name if needed
                                            style = MaterialTheme.typography.bodyLarge,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = android.text.format.Formatter.formatShortFileSize(context, file.length),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
