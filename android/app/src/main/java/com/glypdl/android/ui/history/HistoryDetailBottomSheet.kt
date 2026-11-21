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

package com.glypdl.android.ui.history

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.glypdl.android.data.local.entity.HistoryEntity
import com.glypdl.android.data.model.SubtitleFile
import com.glypdl.android.ui.components.ThumbnailImage
import com.glypdl.android.util.StorageHelper
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class PlatformMeta(
    val name: String,
    val brandColor: Color,
    val containerColor: Color
)

fun detectPlatformMeta(url: String): PlatformMeta {
    val lower = url.lowercase(Locale.ROOT)
    return when {
        lower.contains("youtube.com") || lower.contains("youtu.be") ->
            PlatformMeta("YouTube", Color(0xFFFF0000), Color(0xFFFFE5E5))
        lower.contains("instagram.com") || lower.contains("instagr.am") ->
            PlatformMeta("Instagram", Color(0xFFE1306C), Color(0xFFFDE8EF))
        lower.contains("facebook.com") || lower.contains("fb.watch") || lower.contains("fb.me") ->
            PlatformMeta("Facebook", Color(0xFF1877F2), Color(0xFFE7F0FD))
        lower.contains("tiktok.com") ->
            PlatformMeta("TikTok", Color(0xFF25F4EE), Color(0xFFE6FEFD))
        lower.contains("twitter.com") || lower.contains("x.com") ->
            PlatformMeta("X (Twitter)", Color(0xFF1DA1F2), Color(0xFFE8F5FD))
        lower.contains("reddit.com") || lower.contains("redd.it") ->
            PlatformMeta("Reddit", Color(0xFFFF4500), Color(0xFFFFEBE5))
        lower.contains("pinterest.com") || lower.contains("pin.it") ->
            PlatformMeta("Pinterest", Color(0xFFBD081C), Color(0xFFF9E6E8))
        lower.contains("twitch.tv") ->
            PlatformMeta("Twitch", Color(0xFF9146FF), Color(0xFFF3ECFF))
        lower.contains("vimeo.com") ->
            PlatformMeta("Vimeo", Color(0xFF1AB7EA), Color(0xFFE8F7FD))
        lower.contains("soundcloud.com") ->
            PlatformMeta("SoundCloud", Color(0xFFFF5500), Color(0xFFFFEFE5))
        lower.contains("spotify.com") ->
            PlatformMeta("Spotify", Color(0xFF1DB954), Color(0xFFE8F8EE))
        else -> {
            val domain = try {
                Uri.parse(url).host?.removePrefix("www.")?.ifBlank { "Web" } ?: "Web"
            } catch (_: Exception) {
                "Web"
            }
            PlatformMeta(domain.replaceFirstChar { it.uppercase() }, Color(0xFF6750A4), Color(0xFFF2EDFC))
        }
    }
}

fun formatDurationSeconds(seconds: Long?): String? {
    if (seconds == null || seconds <= 0L) return null
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", m, s)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailBottomSheet(
    item: HistoryEntity,
    subtitles: List<SubtitleFile> = emptyList(),
    onDismissRequest: () -> Unit,
    onRedownload: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val targetUri = item.fileUri?.ifBlank { null } ?: item.filePath ?: ""
    val isAudio = item.format.contains("audio", ignoreCase = true) ||
            item.format.contains("m4a", ignoreCase = true) ||
            item.format.contains("mp3", ignoreCase = true) ||
            item.format.contains("opus", ignoreCase = true) ||
            item.format.contains("flac", ignoreCase = true)

    val platformMeta = remember(item.url) { detectPlatformMeta(item.url) }

    val effectiveSize = remember(item.fileSize, targetUri) {
        if (item.fileSize > 0L) {
            item.fileSize
        } else {
            StorageHelper.getFileSizeFromUri(context, targetUri)
        }
    }

    val formattedSize = remember(effectiveSize) {
        Formatter.formatShortFileSize(context, effectiveSize)
    }
    val exactBytesFormatted = remember(effectiveSize) {
        if (effectiveSize > 0L) String.format(Locale.getDefault(), "%,d bytes", effectiveSize) else "Unknown"
    }

    val fullDateFormatted = remember(item.completedAt) {
        SimpleDateFormat("EEEE, MMMM d, yyyy • hh:mm:ss a", Locale.getDefault()).format(Date(item.completedAt))
    }
    val relativeTimeFormatted = remember(item.completedAt) {
        DateUtils.getRelativeTimeSpanString(
            item.completedAt,
            System.currentTimeMillis(),
            DateUtils.MINUTE_IN_MILLIS
        ).toString()
    }

    val durationText = remember(item.duration) { formatDurationSeconds(item.duration) }

    val fileAvailable = remember(targetUri) {
        StorageHelper.isFileAvailable(context, targetUri)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header: Title and Close button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Download Details",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = onDismissRequest) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Media Banner / Thumbnail
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                ThumbnailImage(
                    url = item.thumbnailUrl,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxSize()
                )

                if (targetUri.isNotBlank() && fileAvailable) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.65f),
                        modifier = Modifier
                            .size(56.dp)
                            .clickable {
                                StorageHelper.openMedia(context, targetUri, isAudio)
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Play",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Media Title (Selectable)
            SelectionContainer {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Badges Row: Platform, Format, Duration, Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Platform chip
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = platformMeta.containerColor
                ) {
                    Text(
                        text = platformMeta.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = platformMeta.brandColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Format badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = item.format.uppercase(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Duration badge
                if (durationText != null) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = durationText,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                // Availability badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (fileAvailable) Color(0xFFE8F5E9) else Color(0xFFFFEBEE)
                ) {
                    Text(
                        text = if (fileAvailable) "Available" else "File Missing",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (fileAvailable) Color(0xFF2E7D32) else Color(0xFFC62828),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (targetUri.isNotBlank()) {
                    Button(
                        onClick = {
                            StorageHelper.openMedia(context, targetUri, isAudio)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = fileAvailable
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open")
                    }

                    OutlinedButton(
                        onClick = {
                            StorageHelper.shareMedia(context, targetUri, item.title, isAudio)
                        },
                        modifier = Modifier.weight(1f),
                        enabled = fileAvailable
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Share")
                    }
                }

                OutlinedButton(
                    onClick = onRedownload,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Analyze")
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            // Detailed Information Section
            Text(
                text = "Detailed Information",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Source URL
                    DetailInfoRow(
                        label = "Source URL",
                        value = item.url,
                        isUrl = true,
                        onCopy = {
                            copyTextToClipboard(context, "URL", item.url)
                        },
                        onOpenUrl = {
                            openUrlInBrowser(context, item.url)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Date & Time
                    DetailInfoRow(
                        label = "Downloaded On",
                        value = "$fullDateFormatted ($relativeTimeFormatted)",
                        onCopy = {
                            copyTextToClipboard(context, "Date", fullDateFormatted)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // File Size
                    DetailInfoRow(
                        label = "File Size",
                        value = "$formattedSize ($exactBytesFormatted)",
                        onCopy = {
                            copyTextToClipboard(context, "File Size", formattedSize)
                        }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Format & Quality
                    DetailInfoRow(
                        label = "Format & Quality",
                        value = item.format,
                        onCopy = {
                            copyTextToClipboard(context, "Format", item.format)
                        }
                    )

                    if (targetUri.isNotBlank()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        // Storage Location / Path
                        DetailInfoRow(
                            label = "Storage Location",
                            value = targetUri,
                            isMonospace = true,
                            onCopy = {
                                copyTextToClipboard(context, "Storage Path", targetUri)
                            }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Task UUID
                    DetailInfoRow(
                        label = "Task ID",
                        value = item.downloadId,
                        isMonospace = true,
                        onCopy = {
                            copyTextToClipboard(context, "Task ID", item.downloadId)
                        }
                    )
                }
            }

            // Subtitles Section (if any were associated with this download)
            if (subtitles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))

                Text(
                    text = "Subtitles (${subtitles.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        subtitles.forEach { sub ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        if (sub.isEmbedded) {
                                            StorageHelper.openMedia(context, sub.contentUri, false)
                                        } else {
                                            StorageHelper.openSubtitle(context, sub.contentUri)
                                        }
                                    }
                                    .padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        Icons.Default.Subtitles,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${sub.languageName} (${sub.languageCode.uppercase()})",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (sub.isEmbedded) {
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = MaterialTheme.colorScheme.secondaryContainer
                                                ) {
                                                    Text(
                                                        text = "EMBEDDED",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                        }
                                        val subInfoText = if (sub.isEmbedded) {
                                            "Embedded track in video"
                                        } else {
                                            val subSize = if (sub.fileSize > 0L) {
                                                Formatter.formatShortFileSize(context, sub.fileSize)
                                            } else {
                                                Formatter.formatShortFileSize(context, StorageHelper.getFileSizeFromUri(context, sub.contentUri))
                                            }
                                            "${sub.format.uppercase()} • $subSize"
                                        }
                                        Text(
                                            text = subInfoText,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (!sub.isEmbedded) {
                                    IconButton(onClick = {
                                        StorageHelper.shareSubtitle(context, sub.contentUri, sub.mediaTitle)
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Share Subtitle", modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Delete Action
            OutlinedButton(
                onClick = onDelete,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Delete from History")
            }
        }
    }
}

@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    isUrl: Boolean = false,
    onCopy: (() -> Unit)? = null,
    onOpenUrl: (() -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onOpenUrl != null) {
                    IconButton(onClick = onOpenUrl, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "Open in browser",
                            modifier = Modifier.size(15.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                if (onCopy != null) {
                    IconButton(onClick = onCopy, modifier = Modifier.size(24.dp)) {
                        Icon(
                            Icons.Default.ContentCopy,
                            contentDescription = "Copy",
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        SelectionContainer {
            Text(
                text = value,
                style = if (isMonospace) {
                    MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (isUrl) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText(label, text)
    clipboard.setPrimaryClip(clip)
    Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
}

private fun openUrlInBrowser(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        Toast.makeText(context, "Unable to open link in browser", Toast.LENGTH_SHORT).show()
    }
}
