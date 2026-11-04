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
package com.glypdl.android.ui.status.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import com.glypdl.android.ui.theme.WhatsAppGreen

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StatusGridItem(
    status: StatusMedia,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onItemClick: () -> Unit,
    onItemLongClick: () -> Unit,
    onSaveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isVideo = status.mediaType == StatusMediaType.VIDEO

    Card(
        shape = RoundedCornerShape(12.dp),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onItemClick,
                onLongClick = onItemLongClick
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Thumbnail
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(status.uri)
                    .apply {
                        if (isVideo) {
                            decoderFactory(VideoFrameDecoder.Factory())
                        }
                    }
                    .crossfade(true)
                    .build(),
                contentDescription = status.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )

            // Gradient overlay at bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                        )
                    )
            )

            // Video indicator
            if (isVideo) {
                Box(
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomStart)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Video",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "VIDEO",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Quick Save Button or Saved Badge (when NOT in selection mode)
            if (!isSelectionMode) {
                if (status.isSaved) {
                    Surface(
                        shape = CircleShape,
                        color = WhatsAppGreen,
                        contentColor = Color.White,
                        modifier = Modifier
                            .padding(6.dp)
                            .size(28.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Saved",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(5.dp)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onSaveClick,
                        modifier = Modifier
                            .padding(2.dp)
                            .size(32.dp)
                            .align(Alignment.BottomEnd)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Save Status",
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp)
                            )
                        }
                    }
                }
            }

            // Selection checkbox (in selection mode or when selected)
            if (isSelectionMode || isSelected) {
                Surface(
                    shape = CircleShape,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.45f),
                    contentColor = Color.White,
                    border = BorderStroke(1.5.dp, Color.White),
                    modifier = Modifier
                        .padding(8.dp)
                        .size(24.dp)
                        .align(Alignment.TopEnd)
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(3.dp)
                        )
                    }
                }
            }
        }
    }
}
