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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
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
        containerColor = Color.Black
    ) { _ ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            // Media Layer (Image / Video) - Full screen, fixed bounds, no layout jumping
            if (uiState.uri == Uri.EMPTY) {
                CircularProgressIndicator(color = WhatsAppGreen)
            } else if (uiState.mediaType == StatusMediaType.VIDEO) {
                // Video Player
                BasicStatusVideoPlayer(
                    uri = uiState.uri,
                    onToggleControls = { showControls = !showControls },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Interactive Image Preview with smooth pinch-to-zoom, pan, and double-tap reset
                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    scale = 1f
                                    offset = Offset.Zero
                                },
                                onTap = { showControls = !showControls }
                            )
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                scale = newScale
                                offset = if (newScale > 1f) {
                                    offset + pan
                                } else {
                                    Offset.Zero
                                }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(uiState.uri)
                            .crossfade(false)
                            .build(),
                        contentDescription = uiState.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offset.x
                                translationY = offset.y
                            }
                    )
                }
            }

            // Top Bar Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
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
                                    Icons.AutoMirrored.Filled.OpenInNew,
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

            // Bottom Bar Overlay
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
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
    }
}

@Composable
fun BasicStatusVideoPlayer(
    uri: Uri,
    onToggleControls: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var isError by remember { mutableStateOf(false) }
    val mediaPlayerRef = remember { mutableStateOf<MediaPlayer?>(null) }
    val pfdRef = remember { mutableStateOf<ParcelFileDescriptor?>(null) }
    val surfaceRef = remember { mutableStateOf<Surface?>(null) }

    // Track actual video dimensions to preserve original aspect ratio (avoid zoom)
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }

    var currentPositionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isUserSeeking by remember { mutableStateOf(false) }
    var seekSliderPosition by remember { mutableFloatStateOf(0f) }

    // Progress update loop while playing
    LaunchedEffect(isPlaying, isUserSeeking) {
        while (isPlaying && !isUserSeeking) {
            mediaPlayerRef.value?.let { mp ->
                try {
                    if (mp.isPlaying) {
                        currentPositionMs = mp.currentPosition.toLong()
                        val d = mp.duration.toLong()
                        if (d > 0) durationMs = d
                    }
                } catch (_: Exception) {}
            }
            kotlinx.coroutines.delay(200L)
        }
    }

    DisposableEffect(uri) {
        val player = MediaPlayer()
        mediaPlayerRef.value = player
        try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
            pfdRef.value = pfd
            if (pfd != null) {
                player.setDataSource(pfd.fileDescriptor)
                player.isLooping = true
                player.setOnVideoSizeChangedListener { _, w, h ->
                    if (w > 0 && h > 0) {
                        videoWidth = w
                        videoHeight = h
                    }
                }
                player.setOnPreparedListener { mp ->
                    isPrepared = true
                    if (mp.videoWidth > 0 && mp.videoHeight > 0) {
                        videoWidth = mp.videoWidth
                        videoHeight = mp.videoHeight
                    }
                    val d = mp.duration.toLong()
                    if (d > 0) durationMs = d
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
            } catch (_: Exception) {}
            try {
                pfdRef.value?.close()
            } catch (_: Exception) {}
            try {
                surfaceRef.value?.release()
            } catch (_: Exception) {}
            mediaPlayerRef.value = null
            surfaceRef.value = null
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
                    } catch (_: Exception) {}
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
                    if (p != null && isPrepared && !isError) {
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
            // Use original aspect ratio when dimensions are known; fall back to fillMaxSize
            val videoModifier = if (videoWidth > 0 && videoHeight > 0) {
                Modifier.aspectRatio(videoWidth.toFloat() / videoHeight.toFloat())
            } else {
                Modifier.fillMaxSize()
            }

            // CRITICAL FIX: Alpha depends ONLY on isPrepared, NEVER on isPlaying!
            // When paused, alpha remains 1f so the paused frame stays visible without turning black!
            val videoAlpha by animateFloatAsState(
                targetValue = if (isPrepared && videoWidth > 0 && videoHeight > 0) 1f else 0f,
                animationSpec = tween(200),
                label = "statusVideoFadeIn"
            )

            AndroidView(
                factory = { ctx ->
                    TextureView(ctx).apply {
                        surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                            override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                                val surface = Surface(st)
                                surfaceRef.value = surface
                                mediaPlayerRef.value?.setSurface(surface)
                                try {
                                    mediaPlayerRef.value?.prepareAsync()
                                } catch (_: Exception) {}
                            }

                            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {}

                            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                                mediaPlayerRef.value?.setSurface(null)
                                surfaceRef.value?.release()
                                surfaceRef.value = null
                                return true
                            }

                            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
                        }
                    }
                },
                modifier = videoModifier.alpha(videoAlpha)
            )

            // Play pause overlay indicator when paused
            if (!isPlaying && isPrepared) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(42.dp)
                        )
                    }
                }
            }

            // Loading spinner before video prepares
            if (!isPrepared) {
                CircularProgressIndicator(
                    color = WhatsAppGreen,
                    modifier = Modifier.size(48.dp)
                )
            }

            // Bottom Video Progress Bar & Seek Scrubber (shown above bottom controls)
            if (isPrepared && durationMs > 0) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 76.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatStatusDuration(if (isUserSeeking) (seekSliderPosition * durationMs).toLong() else currentPositionMs),
                            color = Color.White,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = formatStatusDuration(durationMs),
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Slider(
                        value = if (isUserSeeking) seekSliderPosition else (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f),
                        onValueChange = { newVal ->
                            isUserSeeking = true
                            seekSliderPosition = newVal
                        },
                        onValueChangeFinished = {
                            val targetMs = (seekSliderPosition * durationMs).toInt()
                            mediaPlayerRef.value?.seekTo(targetMs)
                            currentPositionMs = targetMs.toLong()
                            isUserSeeking = false
                        },
                        colors = SliderDefaults.colors(
                            thumbColor = WhatsAppGreen,
                            activeTrackColor = WhatsAppGreen,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                    )
                }
            }
        }
    }
}

private fun formatStatusDuration(millis: Long): String {
    val totalSecs = (millis / 1000).coerceAtLeast(0)
    val mins = totalSecs / 60
    val secs = totalSecs % 60
    return "%02d:%02d".format(mins, secs)
}
