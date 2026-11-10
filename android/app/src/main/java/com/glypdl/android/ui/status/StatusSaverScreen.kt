/*
 * Copyright (c) 2026. Glypdl Contributors
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
 */
package com.glypdl.android.ui.status

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.R
import com.glypdl.android.data.local.entity.SavedStatusEntity
import com.glypdl.android.data.status.model.StatusFilter
import com.glypdl.android.data.status.model.StatusTab
import com.glypdl.android.ui.status.components.*
import com.glypdl.android.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusSaverScreen(
    viewModel: StatusViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToPreview: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Folder Picker Launcher (SAF OpenDocumentTree)
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let { viewModel.onFolderSelected(it) }
    }

    // Manual Media File Import Launcher (SAF OpenMultipleDocuments)
    val manualImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.onManualMediaImport(uris)
        }
    }

    // Snackbar handling
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearSnackbar()
        }
    }

    // Delete confirmation dialog state
    var itemToDelete by remember { mutableStateOf<SavedStatusEntity?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Status Saver",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = WhatsAppGreen.copy(alpha = 0.15f),
                            border = null
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_whatsapp),
                                    contentDescription = null,
                                    tint = WhatsAppGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "WhatsApp",
                                    color = WhatsAppGreen,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = "Select WhatsApp Folder"
                        )
                    }
                    IconButton(onClick = { viewModel.onRefresh() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Statuses"
                        )
                    }
                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More Options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Change WhatsApp Folder") },
                                onClick = {
                                    showMenu = false
                                    folderPickerLauncher.launch(null)
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Import Files Manually") },
                                onClick = {
                                    showMenu = false
                                    manualImportLauncher.launch(arrayOf("image/*", "video/*"))
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FileUpload, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }),
                exit = slideOutVertically(targetOffsetY = { it })
            ) {
                StatusSelectionBar(
                    selectedCount = uiState.selectedIds.size,
                    isAllSelected = uiState.selectedIds.size == uiState.filteredAvailable.size && uiState.filteredAvailable.isNotEmpty(),
                    onSelectAllClick = { viewModel.selectAll() },
                    onClearClick = { viewModel.clearSelection() },
                    onSaveSelectedClick = { viewModel.saveSelected() }
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Tab Row (Available vs Saved)
            TabRow(
                selectedTabIndex = uiState.currentTab.ordinal,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                Tab(
                    selected = uiState.currentTab == StatusTab.AVAILABLE,
                    onClick = { viewModel.setTab(StatusTab.AVAILABLE) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Available")
                            if (uiState.availableStatuses.isNotEmpty()) {
                                Badge(
                                    containerColor = WhatsAppGreen,
                                    contentColor = Color.White
                                ) {
                                    Text(uiState.availableStatuses.size.toString())
                                }
                            }
                        }
                    }
                )
                Tab(
                    selected = uiState.currentTab == StatusTab.SAVED,
                    onClick = { viewModel.setTab(StatusTab.SAVED) },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("Saved")
                            if (uiState.savedStatuses.isNotEmpty()) {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(uiState.savedStatuses.size.toString())
                                }
                            }
                        }
                    }
                )
            }

            // Filter Chips (All, Photos, Videos)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = uiState.filter == StatusFilter.ALL,
                    onClick = { viewModel.setFilter(StatusFilter.ALL) },
                    label = { Text("All") },
                    leadingIcon = if (uiState.filter == StatusFilter.ALL) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = uiState.filter == StatusFilter.IMAGES,
                    onClick = { viewModel.setFilter(StatusFilter.IMAGES) },
                    label = { Text("Photos") },
                    leadingIcon = if (uiState.filter == StatusFilter.IMAGES) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
                FilterChip(
                    selected = uiState.filter == StatusFilter.VIDEOS,
                    onClick = { viewModel.setFilter(StatusFilter.VIDEOS) },
                    label = { Text("Videos") },
                    leadingIcon = if (uiState.filter == StatusFilter.VIDEOS) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    } else null
                )
            }

            // Main Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                if (uiState.isScanning) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            CircularProgressIndicator(color = WhatsAppGreen)
                            Text(
                                text = "Scanning WhatsApp statuses...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (uiState.currentTab == StatusTab.AVAILABLE) {
                    if (uiState.emptyStateType != null) {
                        StatusEmptyState(
                            type = uiState.emptyStateType!!,
                            onConfigureFolderClick = { folderPickerLauncher.launch(null) },
                            onManualImportClick = { manualImportLauncher.launch(arrayOf("image/*", "video/*")) },
                            onRefreshClick = { viewModel.onRefresh() }
                        )
                    } else {
                        StatusGrid(
                            items = uiState.filteredAvailable,
                            selectedIds = uiState.selectedIds,
                            isSelectionMode = uiState.isSelectionMode,
                            onItemClick = { item ->
                                if (uiState.isSelectionMode) {
                                    viewModel.toggleSelection(item.id)
                                } else {
                                    viewModel.setPreviewItem(item)
                                    onNavigateToPreview()
                                }
                            },
                            onItemLongClick = { item ->
                                viewModel.toggleSelection(item.id)
                            },
                            onSaveClick = { item ->
                                viewModel.saveSingle(item)
                            }
                        )
                    }
                } else {
                    // Saved Tab
                    if (uiState.filteredSaved.isEmpty()) {
                        StatusEmptyState(
                            type = StatusEmptyStateType.NoSavedStatuses,
                            onConfigureFolderClick = { viewModel.setTab(StatusTab.AVAILABLE) },
                            onManualImportClick = { manualImportLauncher.launch(arrayOf("image/*", "video/*")) },
                            onRefreshClick = { viewModel.setTab(StatusTab.AVAILABLE) }
                        )
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            contentPadding = PaddingValues(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(
                                items = uiState.filteredSaved,
                                key = { it.id }
                            ) { item ->
                                StatusSavedGridItem(
                                    item = item,
                                    onOpenClick = {
                                        viewModel.setPreviewItemFromSaved(item)
                                        onNavigateToPreview()
                                    },
                                    onShareClick = {
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = item.mimeType
                                            putExtra(Intent.EXTRA_STREAM, Uri.parse(item.savedUri))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                                    },
                                    onDeleteClick = {
                                        itemToDelete = item
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Record Confirmation Dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Remove from History?") },
            text = { Text("This will remove the entry from Glypdl's saved list. The media file will remain in your phone's gallery.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSavedRecord(item.id)
                        itemToDelete = null
                    }
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Batch Saving Progress Dialog
    if (uiState.isSavingBatch) {
        Dialog(onDismissRequest = {}) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    CircularProgressIndicator(color = WhatsAppGreen)
                    Text(
                        text = "Saving Selected Statuses...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    uiState.batchProgress?.let { (current, total) ->
                        LinearProgressIndicator(
                            progress = { if (total > 0) current.toFloat() / total.toFloat() else 0f },
                            modifier = Modifier.fillMaxWidth(),
                            color = WhatsAppGreen
                        )
                        Text(
                            text = "$current / $total completed",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
