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
package com.glypdl.android.ui.analyze

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.data.model.GlypdlError
import com.glypdl.android.ui.components.FormatSelector
import com.glypdl.android.ui.components.QualitySelector
import com.glypdl.android.ui.components.ThumbnailImage
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyzeScreen(
    viewModel: AnalyzeViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToDownloads: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Analyze") },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState is AnalyzeUiState.Loading) {
                            viewModel.cancelAnalysis()
                        }
                        onNavigateBack()
                    }) {
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
        ) {
            when (val state = uiState) {
                is AnalyzeUiState.Loading -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            text = if (state.isPlaylist) "Discovering playlist items..." else "Analyzing media...",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (state.isPlaylist) {
                                "Fetching playlist metadata via flat discovery watchdog..."
                            } else {
                                "Extracting streams and formats..."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedButton(
                            onClick = {
                                viewModel.cancelAnalysis()
                                onNavigateBack()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Cancel Analysis")
                        }
                    }
                }
                is AnalyzeUiState.Error -> {
                    ErrorContent(
                        state = state,
                        targetUrl = viewModel.targetUrl,
                        onRetry = viewModel::retry,
                        onUpdateEngine = viewModel::updateEngineAndRetry,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                is AnalyzeUiState.PlaylistSuccess -> {
                    PlaylistContent(
                        state = state,
                        onToggleItem = viewModel::togglePlaylistItem,
                        onSelectAll = viewModel::selectAllPlaylistItems,
                        onDeselectAll = viewModel::deselectAllPlaylistItems,
                        onAudioOnlyChanged = viewModel::setPlaylistAudioOnly,
                        onDownload = {
                            viewModel.downloadSelectedPlaylist()
                            onNavigateToDownloads()
                        }
                    )
                }
                is AnalyzeUiState.Success -> {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(16.dp)
                    ) {
                        state.mediaInfo.thumbnail?.let {
                            ThumbnailImage(
                                url = it,
                                contentDescription = "Thumbnail",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = state.mediaInfo.title,
                            style = MaterialTheme.typography.headlineSmall
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = state.mediaInfo.uploader ?: "Unknown",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            state.mediaInfo.duration?.let { duration ->
                                if (duration > 0) {
                                    Text(
                                        text = " • ${String.format(Locale.getDefault(), "%02d:%02d", duration / 60, duration % 60)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(16.dp))

                        if (state.isSimplifiedPlatform) {
                            InstagramFormatSelector(
                                selectedChoice = state.instagramChoice,
                                onChoiceSelected = viewModel::onInstagramChoiceSelected
                            )
                        } else if (state.mediaInfo.videoQualities.isNotEmpty() || state.mediaInfo.audioQualities.isNotEmpty()) {
                            QualitySelector(
                                videoQualities = state.mediaInfo.videoQualities,
                                audioQualities = state.mediaInfo.audioQualities,
                                rawFormats = state.mediaInfo.formats,
                                selectedVideoQuality = state.selectedVideoQuality,
                                selectedAudioQuality = state.selectedAudioQuality,
                                isAudioOnly = state.isAudioOnly,
                                onModeChanged = viewModel::setAudioOnly,
                                onVideoQualitySelected = viewModel::onVideoQualitySelected,
                                onAudioQualitySelected = viewModel::onAudioQualitySelected
                            )
                        } else {
                            FormatSelector(
                                formats = state.videoFormats + state.audioFormats,
                                selectedFormatId = state.selectedFormat?.formatId,
                                onFormatSelected = viewModel::onFormatSelected
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        val canDownload = state.isSimplifiedPlatform ||
                                state.selectedVideoQuality != null ||
                                state.selectedAudioQuality != null ||
                                state.selectedFormat != null

                        val context = androidx.compose.ui.platform.LocalContext.current
                        val (primaryLabel, secondaryLabel) = when {
                            state.isSimplifiedPlatform -> {
                                Pair("Download ${state.instagramChoice.label}", "Best available • ${state.instagramChoice.ext.uppercase()}")
                            }
                            state.isAudioOnly -> {
                                val aLabel = state.selectedAudioQuality?.displayBitrate ?: "Audio"
                                val sizeStr = state.selectedAudioQuality?.displaySize?.takeIf { it.isNotBlank() } ?: ""
                                Pair("Download Audio", listOf(aLabel, sizeStr).filter { it.isNotBlank() }.joinToString(" • "))
                            }
                            state.selectedVideoQuality != null -> {
                                val vLabel = state.selectedVideoQuality.label
                                val aLabel = state.selectedAudioQuality?.displayBitrate
                                val comboLabel = if (aLabel != null) "$vLabel + $aLabel" else vLabel
                                val totalSize = state.estimatedTotalBytes?.let { bytes ->
                                    android.text.format.Formatter.formatShortFileSize(context, bytes)
                                } ?: ""
                                Pair("Download Video", listOf(comboLabel, totalSize).filter { it.isNotBlank() }.joinToString(" • "))
                            }
                            else -> Pair("Download", "")
                        }

                        Button(
                            onClick = {
                                viewModel.startDownload()
                                onNavigateToDownloads()
                            },
                            enabled = canDownload,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 54.dp)
                                .navigationBarsPadding(),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(22.dp))
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = primaryLabel,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (secondaryLabel.isNotBlank()) {
                                        Text(
                                            text = secondaryLabel,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
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

@Composable
private fun PlaylistContent(
    state: AnalyzeUiState.PlaylistSuccess,
    onToggleItem: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAudioOnlyChanged: (Boolean) -> Unit,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                state.playlistInfo.thumbnailUrl?.let { thumb ->
                    ThumbnailImage(
                        url = thumb,
                        contentDescription = "Playlist Thumbnail",
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }

                Text(
                    text = state.playlistInfo.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.playlistInfo.uploader?.let { uploader ->
                        Text(
                            text = uploader,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${state.totalCount} items",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !state.isAudioOnly,
                            onClick = { onAudioOnlyChanged(false) },
                            label = { Text("Video") },
                            leadingIcon = {
                                Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                        FilterChip(
                            selected = state.isAudioOnly,
                            onClick = { onAudioOnlyChanged(true) },
                            label = { Text("Audio Only") },
                            leadingIcon = {
                                Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        )
                    }

                    TextButton(
                        onClick = if (state.isAllSelected) onDeselectAll else onSelectAll
                    ) {
                        Text(if (state.isAllSelected) "Deselect All" else "Select All")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            items(state.playlistInfo.entries, key = { it.id }) { item ->
                val isSelected = item.id in state.selectedItemIds
                Card(
                    onClick = { onToggleItem(item.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        }
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThumbnailImage(
                            url = item.thumbnailUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(width = 72.dp, height = 48.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = { onToggleItem(item.id) }
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.title,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.displayDuration.isNotBlank()) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = item.displayDuration,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Surface(
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = onDownload,
                    enabled = state.selectedCount > 0,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Selected (${state.selectedCount} / ${state.totalCount})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorContent(
    state: AnalyzeUiState.Error,
    targetUrl: String = "",
    onRetry: () -> Unit,
    onUpdateEngine: () -> Unit,
    modifier: Modifier = Modifier
) {
    var detailsExpanded by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val error = state.error

    val errorIcon: ImageVector = when (error) {
        is GlypdlError.AuthenticationRequired -> Icons.Default.Lock
        is GlypdlError.RateLimited -> Icons.Default.HourglassEmpty
        is GlypdlError.EngineOutdated, is GlypdlError.EngineMissing -> Icons.Default.SystemUpdate
        is GlypdlError.ContentUnavailable -> Icons.Default.VideocamOff
        is GlypdlError.GeoRestricted -> Icons.Default.PublicOff
        is GlypdlError.DRMProtected -> Icons.Default.Security
        is GlypdlError.NetworkError -> Icons.Default.WifiOff
        is GlypdlError.FFmpegError -> Icons.Default.Build
        else -> Icons.Default.ErrorOutline
    }

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
            ),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = errorIcon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = error.userTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = error.userMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                if (state.updateError != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.updateError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (error is GlypdlError.AuthenticationRequired) {
                        val authContext = androidx.compose.ui.platform.LocalContext.current
                        val authLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                            androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
                        ) { res ->
                            if (res.resultCode == android.app.Activity.RESULT_OK) {
                                onRetry()
                            }
                        }

                        Button(
                            onClick = {
                                val host = try {
                                    android.net.Uri.parse(targetUrl).host?.lowercase() ?: ""
                                } catch (e: Exception) {
                                    ""
                                }
                                val domain = when {
                                    host.contains("facebook.com") || host.contains("fb.watch") || error.userMessage.contains("Facebook", ignoreCase = true) -> "facebook.com"
                                    host.contains("youtube.com") || host.contains("youtu.be") || error.userMessage.contains("YouTube", ignoreCase = true) -> "youtube.com"
                                    host.contains("instagram.com") || error.userMessage.contains("Instagram", ignoreCase = true) -> "instagram.com"
                                    host.contains("tiktok.com") || error.userMessage.contains("TikTok", ignoreCase = true) -> "tiktok.com"
                                    host.contains("twitter.com") || host.contains("x.com") -> "twitter.com"
                                    host.isNotEmpty() -> {
                                        val parts = host.split(".")
                                        if (parts.size >= 2) "${parts[parts.size - 2]}.${parts[parts.size - 1]}" else host
                                    }
                                    else -> "instagram.com"
                                }
                                val loginUrl = when (domain) {
                                    "facebook.com" -> "https://www.facebook.com/login/"
                                    "youtube.com" -> "https://accounts.google.com/ServiceLogin?service=youtube"
                                    "instagram.com" -> "https://www.instagram.com/accounts/login/"
                                    "tiktok.com" -> "https://www.tiktok.com/login"
                                    "twitter.com" -> "https://twitter.com/i/flow/login"
                                    else -> if (targetUrl.startsWith("http")) targetUrl else "https://$domain"
                                }
                                val intent = com.glypdl.android.ui.auth.AuthBrowserActivity.createIntent(
                                    authContext,
                                    loginUrl,
                                    domain,
                                    "Log In to $domain"
                                )
                                authLauncher.launch(intent)
                            }
                        ) {
                            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Log In")
                        }
                    }

                    if (error.requiresUpdate || error is GlypdlError.EngineOutdated) {
                        Button(
                            onClick = onUpdateEngine,
                            enabled = !state.isUpdatingEngine,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            if (state.isUpdatingEngine) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onError,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Updating...")
                            } else {
                                Icon(Icons.Default.Upgrade, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Update Engine")
                            }
                        }
                    }

                    if (error.canRetry) {
                        OutlinedButton(
                            onClick = onRetry,
                            enabled = !state.isUpdatingEngine
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            }
        }

        // Expandable technical details accordion
        if (!error.technicalDetails.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = { detailsExpanded = !detailsExpanded }
            ) {
                Icon(
                    imageVector = if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(if (detailsExpanded) "Hide technical details" else "Show technical details")
            }

            AnimatedVisibility(visible = detailsExpanded) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Technical Logs (Sanitized)",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(error.technicalDetails))
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ContentCopy,
                                    contentDescription = "Copy logs",
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = error.technicalDetails,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun InstagramFormatSelector(
    selectedChoice: InstagramDownloadChoice,
    onChoiceSelected: (InstagramDownloadChoice) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Download Options",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Select your preferred media stream",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))

        InstagramDownloadChoice.entries.forEach { choice ->
            val isSelected = choice == selectedChoice
            Card(
                onClick = { onChoiceSelected(choice) },
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    }
                ),
                border = if (isSelected) {
                    androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                } else null,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = isSelected,
                        onClick = { onChoiceSelected(choice) }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = choice.label,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = choice.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
