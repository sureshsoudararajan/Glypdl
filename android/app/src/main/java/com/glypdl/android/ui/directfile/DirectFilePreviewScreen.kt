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
package com.glypdl.android.ui.directfile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.ui.components.FileTypeIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectFilePreviewScreen(
    viewModel: DirectFileViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("File Preview") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            when (val state = uiState) {
                is DirectFileUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is DirectFileUiState.Error -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = onNavigateBack) {
                            Text("Go Back")
                        }
                    }
                }
                is DirectFileUiState.Success -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Large Icon
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            FileTypeIcon(
                                category = state.category,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Category Chip
                        Badge(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = state.category.name,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // Filename
                        Text(
                            text = state.fileName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // File info
                        val context = LocalContext.current
                        val sizeStr = if (state.fileSize > 0) {
                            android.text.format.Formatter.formatShortFileSize(context, state.fileSize)
                        } else {
                            "Unknown Size"
                        }
                        val typeLabel = when {
                            state.mimeType == "application/octet-stream" || state.mimeType.isBlank() -> {
                                // Use the file extension from the filename as a fallback
                                val ext = state.fileName.substringAfterLast('.', "").uppercase()
                                if (ext.isNotBlank() && ext != state.fileName.uppercase()) "${ext} File" else "Binary File"
                            }
                            state.mimeType.startsWith("image/") -> state.mimeType.substringAfter("image/").uppercase() + " Image"
                            state.mimeType.startsWith("audio/") -> state.mimeType.substringAfter("audio/").uppercase() + " Audio"
                            state.mimeType.startsWith("video/") -> state.mimeType.substringAfter("video/").uppercase() + " Video"
                            state.mimeType == "application/pdf" -> "PDF Document"
                            state.mimeType == "application/zip" -> "ZIP Archive"
                            state.mimeType.contains("rar") -> "RAR Archive"
                            state.mimeType.contains("gzip") || state.mimeType.contains("x-tar") -> "Archive"
                            state.mimeType == "application/vnd.android.package-archive" -> "Android App (APK)"
                            else -> {
                                val ext = state.fileName.substringAfterLast('.', "").uppercase()
                                if (ext.isNotBlank() && ext != state.fileName.uppercase()) "${ext} File" else state.mimeType
                            }
                        }
                        Text(
                            text = "$sizeStr • $typeLabel",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        
                        Spacer(modifier = Modifier.height(32.dp))
                        
                        // Download Button
                        Button(
                            onClick = {
                                viewModel.startDownload(state)
                                onNavigateToDownloads()
                            },
                            modifier = Modifier.fillMaxWidth().height(56.dp)
                        ) {
                            Icon(Icons.Default.Download, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Download File", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}
