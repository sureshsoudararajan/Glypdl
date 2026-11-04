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

import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.text.format.Formatter
import android.view.Surface
import android.view.TextureView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.glypdl.android.data.status.model.StatusMediaType
import com.glypdl.android.ui.theme.WhatsAppGreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusPreviewScreen(
    viewModel: StatusPreviewViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    var showControls by remember { mutableStateOf(true) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Black,
        topBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                text = uiState.displayName.ifEmpty { "Status Preview" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = Color.White,
                                style = MaterialTheme.typography.titleMedium
                            )
                            if (uiState.sizeBytes > 0L) {
                                Text(
                                    text = Formatter.formatFileSize(context, uiState.sizeBytes),
                                    color = Color.White.copy(alpha = 0.7f),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        // Open in external video player (if video)
                        if (uiState.mediaType == StatusMediaType.VIDEO && uiState.uri != Uri.EMPTY) {
                            IconButton(
                                onClick = {
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uiState.uri, uiState.mimeType.ifEmpty { "video/*" })
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Open Video"))
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.OpenInNew,
                                    contentDescription = "Open in Player",
                                    tint = Color.White
                                )
                            }
                        }

                        // Share action
                        IconButton(
                            onClick = {
                                if (uiState.uri != Uri.EMPTY) {
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = uiState.mimeType.ifEmpty { "*/*" }
                                        putExtra(Intent.EXTRA_STREAM, uiState.uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(shareIntent, "Share Status"))
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "Share",
                                tint = Color.White
                            )
                        }

                        // Save action
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(end = 12.dp),
                                color = WhatsAppGreen,
                                strokeWidth = 2.dp
                            )
                        } else {
                            IconButton(
                                onClick = { viewModel.saveCurrentStatus() },
                                enabled = !uiState.isSaved
                            ) {
                                Icon(
                                    imageVector = if (uiState.isSaved) Icons.Default.Check else Icons.Default.Download,
                                    contentDescription = if (uiState.isSaved) "Saved" else "Save to Gallery",
                                    tint = if (uiState.isSaved) WhatsAppGreen else Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black.copy(alpha = 0.65f)
                    )
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = if (uiState.mediaType == StatusMediaType.VIDEO) Color(0xFFE91E63) else WhatsAppGreen,
                                modifier = Modifier.size(10.dp)
                            ) {}
                            Text(
                                text = if (uiState.mediaType == StatusMediaType.VIDEO) "Video" else "Photo",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (uiState.isSaved) {
                                Text(
                                    text = "• Saved to Gallery",
                                    color = WhatsAppGreen,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        if (!uiState.isSaved) {
                            Button(
                                onClick = { viewModel.saveCurrentStatus() },
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Save to Gallery", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentAlignment = Alignment.Center
        ) {
            if (uiState.uri == Uri.EMPTY) {
                CircularProgressIndicator(color = WhatsAppGreen)
            } else if (uiState.mediaType == StatusMediaType.VIDEO) {
                // Basic Video Player
                BasicStatusVideoPlayer(
                    uri = uiState.uri,
                    onToggleControls = { showControls = !showControls },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Basic Image Preview
                var scale by remember { mutableFloatStateOf(1f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { showControls = !showControls },
                                onDoubleTap = {
                                    scale = if (scale > 1f) 1f else 2.5f
                                }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uiState.uri)
                            .crossfade(true)
                            .build(),
                        contentDescription = uiState.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(scaleX = scale, scaleY = scale)
                    )
                }
            }
        }
    }
}

@Composable
fun BasicStatusVideoPlayer(
    uri: Uri,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPlaying by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    val pfdRef = remember { mutableStateOf<ParcelFileDescriptor?>(null) }

    DisposableEffect(uri) {
        val player = MediaPlayer()
        mediaPlayerRef.value = player
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            pfdRef.value = pfd
            if (pfd != null) {
                player.setDataSource(pfd.fileDescriptor)
                player.isLooping = true
                player.setOnPreparedListener { mp ->
                    mp.start()
                    isPlaying = true
                }
                player.setOnErrorListener { _, _, _ ->
                    isError = true
                    true
                }
            } else {
                isError = true
            }
        } catch (e: Exception) {
            isError = true
        }

        onDispose {
            try {
                player.stop()
                player.release()
            } catch (e: Exception) {
                // Ignore
            }
            try {
                pfdRef.value?.close()
            } catch (e: Exception) {
                // Ignore
            }
            mediaPlayerRef.value = null
        }
    }

    if (isError) {
        Column(
            modifier = modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Video preview format not supported natively",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        setDataAndType(uri, "video/*")
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    try {
                        context.startActivity(Intent.createChooser(intent, "Play Video"))
                    } catch (e: Exception) {
                        // Ignore
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open in Video Player", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Box(
            modifier = modifier
                .fillMaxSize()
                .clickable {
                    val p = mediaPlayerRef.value
                    if (p != null && !isError) {
                        if (p.isPlaying) {
                            p.pause()
                            isPlaying = false
                        } else {
                            p.start()
                            isPlaying = true
                        }
                    }
                    onToggleControls()
                },
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                val surface = Surface(st)
                                mediaPlayerRef.value?.setSurface(surface)
                                try {
                                    mediaPlayerRef.value?.prepareAsync()
                                } catch (e: Exception) {
                                    // Already preparing or prepared
                                }
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                mediaPlayerRef.value?.setSurface(null)
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Play pause overlay indicator when paused
            if (!isPlaying) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.6f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}
