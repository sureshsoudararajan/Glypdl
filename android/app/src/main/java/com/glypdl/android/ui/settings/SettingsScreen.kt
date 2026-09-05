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
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.glypdl.android.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.glypdl.android.ui.auth.AuthBrowserActivity
import com.glypdl.android.util.StorageHelper
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val theme by viewModel.theme.collectAsState()
    val wifiOnly by viewModel.wifiOnly.collectAsState()
    val concurrentDownloads by viewModel.concurrentDownloads.collectAsState()
    val defaultQuality by viewModel.defaultQuality.collectAsState()
    val defaultFormat by viewModel.defaultFormat.collectAsState()
    val clipboardDetection by viewModel.clipboardDetection.collectAsState()
    val downloadNotifications by viewModel.downloadNotifications.collectAsState()
    val completionNotifications by viewModel.completionNotifications.collectAsState()
    val errorNotifications by viewModel.errorNotifications.collectAsState()
    val preferredVideoCodec by viewModel.preferredVideoCodec.collectAsState()
    val preferredAudioFormat by viewModel.preferredAudioFormat.collectAsState()
    val askBeforeDownload by viewModel.askBeforeDownload.collectAsState()
    val autoUpdateYtDlp by viewModel.autoUpdateYtDlp.collectAsState()
    val downloadDirUri by viewModel.downloadDirUri.collectAsState()
    val engineStatus by viewModel.engineStatus.collectAsState()
    val isEngineBusy by viewModel.isEngineBusy.collectAsState()
    val engineActionMessage by viewModel.engineActionMessage.collectAsState()

    val scope = rememberCoroutineScope()

    // Dialog selection states
    var qualityDialogVisible by remember { mutableStateOf(false) }
    var formatDialogVisible by remember { mutableStateOf(false) }
    var videoCodecDialogVisible by remember { mutableStateOf(false) }
    var audioFormatDialogVisible by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Settings",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. DOWNLOAD ENGINE SECTION
            item {
                SettingsGroupCard(title = "Download Engine") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Default.Memory,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = "yt-dlp Engine",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    val versionText = engineStatus?.installedVersion ?: "Loading..."
                                    Text(
                                        text = "Installed: $versionText",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // Engine Status Badge
                            val status = engineStatus
                            if (status != null) {
                                when {
                                    status.isOutdated -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.errorContainer,
                                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                                        ) {
                                            Text(
                                                "Outdated (>90d)",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    status.isUpdateAvailable -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                        ) {
                                            Text(
                                                "Update available",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                    else -> {
                                        Badge(
                                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        ) {
                                            Text(
                                                "Up to date",
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Download Engine diagnostics: yt-dlp, FFmpeg, FFprobe
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "yt-dlp: ${engineStatus?.installedVersion ?: "Ready"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val ffmpegReady = engineStatus?.isFfmpegAvailable == true
                            Text(
                                text = if (ffmpegReady) "FFmpeg: ✓ Ready (Integrated)" else "FFmpeg: ✗ Not available",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ffmpegReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                            Text(
                                text = if (ffmpegReady) "FFprobe: ✓ Ready (Integrated)" else "FFprobe: ✗ Not available",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (ffmpegReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                            )
                        }

                        if (!engineActionMessage.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = engineActionMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 54.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Action Buttons: Check & Update
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.refreshEngineStatus(forceOnline = true) },
                                enabled = !isEngineBusy,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isEngineBusy && engineActionMessage?.contains("Checking") == true) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check")
                                }
                            }

                            Button(
                                onClick = { viewModel.updateYtDlp() },
                                enabled = !isEngineBusy,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isEngineBusy && engineActionMessage?.contains("Updating") == true) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        Icons.Default.Upgrade,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Update")
                                }
                            }
                        }
                    }

                    SettingsItemDivider()

                    SettingsSwitchRow(
                        icon = Icons.Default.Autorenew,
                        title = "Auto-update download engine",
                        subtitle = "Checks & updates yt-dlp automatically on launch",
                        checked = autoUpdateYtDlp,
                        onCheckedChange = { scope.launch { viewModel.setAutoUpdateYtDlp(it) } }
                    )
                }
            }

            // 2. ACCOUNTS & AUTHENTICATION SECTION
            item {
                SettingsGroupCard(title = "Accounts & Authentication") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "Log into platforms via the in-app browser to download private media, stories, and login-restricted videos.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    val cookieMap by viewModel.cookieStatusMap.collectAsState()
                    val authLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.StartActivityForResult()
                    ) {
                        viewModel.refreshCookieStatus()
                    }

                    viewModel.authPlatforms.forEachIndexed { index, platform ->
                        val isLoggedIn = cookieMap[platform.id] == true

                        if (index > 0) {
                            SettingsItemDivider()
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                SettingsIconBadge(
                                    icon = Icons.Default.AccountCircle,
                                    tint = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(14.dp))
                                Column {
                                    Text(
                                        text = platform.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = if (isLoggedIn) "Connected (Cookies saved)" else "Not logged in",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (isLoggedIn) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            if (isLoggedIn) {
                                OutlinedButton(
                                    onClick = { viewModel.clearCookies(platform.domain) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Log Out", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                FilledTonalButton(
                                    onClick = {
                                        val intent = AuthBrowserActivity.createIntent(
                                            context,
                                            platform.loginUrl,
                                            platform.domain,
                                            "Log in to ${platform.name}"
                                        )
                                        authLauncher.launch(intent)
                                    },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text("Log In", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    val otherDomains by viewModel.otherSavedDomains.collectAsState()
                    if (otherDomains.isNotEmpty()) {
                        SettingsItemDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "Other Saved Sessions",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        otherDomains.forEach { domain ->
                            SettingsItemDivider()
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    SettingsIconBadge(
                                        icon = Icons.Default.Public,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(14.dp))
                                    Column {
                                        Text(
                                            text = domain,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "Connected (Cookies saved)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.clearCookies(domain) },
                                    shape = RoundedCornerShape(10.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("Log Out", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }

                    SettingsItemDivider()
                    Text(
                        text = "Any other site: When analyzing a private or members-only video from any website, Glypdl will automatically prompt you to log in via the in-app browser and will securely remember that site's session.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            // 3. DOWNLOADS SECTION
            item {
                SettingsGroupCard(title = "Downloads") {
                    // Default quality picker row
                    SettingsSelectRow(
                        icon = Icons.Default.HighQuality,
                        title = "Default quality",
                        subtitle = "Preferred video resolution preset",
                        currentValue = formatQualityLabel(defaultQuality),
                        onClick = { qualityDialogVisible = true }
                    )

                    SettingsItemDivider()

                    // Default format picker row
                    SettingsSelectRow(
                        icon = Icons.Default.VideoFile,
                        title = "Default container format",
                        subtitle = "Output file extension",
                        currentValue = defaultFormat.uppercase(),
                        onClick = { formatDialogVisible = true }
                    )

                    SettingsItemDivider()

                    // Concurrent downloads slider row
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Default.Speed,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Concurrent downloads",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$concurrentDownloads simultaneous tasks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "$concurrentDownloads",
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }

                        Slider(
                            value = concurrentDownloads.toFloat(),
                            onValueChange = { scope.launch { viewModel.setConcurrentDownloads(it.toInt()) } },
                            valueRange = 1f..10f,
                            steps = 8,
                            modifier = Modifier.padding(start = 54.dp, top = 4.dp)
                        )
                    }

                    SettingsItemDivider()

                    // Wi-Fi only switch
                    SettingsSwitchRow(
                        icon = Icons.Default.Wifi,
                        title = "Download over Wi-Fi only",
                        subtitle = "Pause queue when mobile data is in use",
                        checked = wifiOnly,
                        onCheckedChange = { scope.launch { viewModel.setWifiOnly(it) } }
                    )

                    SettingsItemDivider()

                    // Ask before download switch
                    SettingsSwitchRow(
                        icon = Icons.AutoMirrored.Filled.Help,
                        title = "Ask before download",
                        subtitle = "Always show format picker before downloading",
                        checked = askBeforeDownload,
                        onCheckedChange = { scope.launch { viewModel.setAskBeforeDownload(it) } }
                    )
                }
            }

            // 4. DOWNLOAD STORAGE SECTION
            item {
                SettingsGroupCard(title = "Download Storage") {
                    val dirPickerLauncher = rememberLauncherForActivityResult(
                        contract = ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        if (uri != null) {
                            try {
                                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                            scope.launch {
                                viewModel.setDownloadDirUri(uri.toString())
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Default.FolderOpen,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Storage Location",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                val currentStorageName = remember(downloadDirUri) {
                                    StorageHelper.getReadableStorageName(context, downloadDirUri)
                                }
                                Text(
                                    text = currentStorageName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 54.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { dirPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Change Folder", style = MaterialTheme.typography.labelMedium)
                            }

                            if (!downloadDirUri.isNullOrBlank()) {
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            viewModel.setDownloadDirUri("")
                                        }
                                    }
                                ) {
                                    Text("Reset to default", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }
            }

            // 5. VIDEO & AUDIO SECTION
            item {
                SettingsGroupCard(title = "Video & Audio Codecs") {
                    SettingsSelectRow(
                        icon = Icons.Default.Movie,
                        title = "Preferred video codec",
                        subtitle = "Select encoding standard preference",
                        currentValue = preferredVideoCodec.uppercase(),
                        onClick = { videoCodecDialogVisible = true }
                    )

                    SettingsItemDivider()

                    SettingsSelectRow(
                        icon = Icons.Default.Audiotrack,
                        title = "Preferred audio format",
                        subtitle = "Target format for audio-only downloads",
                        currentValue = preferredAudioFormat.uppercase(),
                        onClick = { audioFormatDialogVisible = true }
                    )
                }
            }

            // 6. NOTIFICATIONS SECTION
            item {
                SettingsGroupCard(title = "Notifications") {
                    SettingsSwitchRow(
                        icon = Icons.Default.NotificationsActive,
                        title = "Download notifications",
                        subtitle = "Show ongoing download progress in notification bar",
                        checked = downloadNotifications,
                        onCheckedChange = { scope.launch { viewModel.setDownloadNotifications(it) } }
                    )

                    SettingsItemDivider()

                    SettingsSwitchRow(
                        icon = Icons.Default.CheckCircle,
                        title = "Completion notifications",
                        subtitle = "Notify when downloads finish successfully",
                        checked = completionNotifications,
                        onCheckedChange = { scope.launch { viewModel.setCompletionNotifications(it) } }
                    )

                    SettingsItemDivider()

                    SettingsSwitchRow(
                        icon = Icons.Default.Error,
                        title = "Error notifications",
                        subtitle = "Notify if a download fails or encounters an error",
                        checked = errorNotifications,
                        onCheckedChange = { scope.launch { viewModel.setErrorNotifications(it) } }
                    )
                }
            }

            // 7. APPEARANCE SECTION
            item {
                SettingsGroupCard(title = "Appearance") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            SettingsIconBadge(
                                icon = Icons.Default.Palette,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "App Theme",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "Choose light, dark, or system matching theme",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Modern 3-Segmented Pill Selector
                        SettingsThemeSegmentedPicker(
                            selectedTheme = theme,
                            onThemeSelected = { newTheme ->
                                scope.launch { viewModel.setTheme(newTheme) }
                            },
                            modifier = Modifier.padding(start = 54.dp)
                        )
                    }
                }
            }

            // 8. PRIVACY SECTION
            item {
                SettingsGroupCard(title = "Privacy & System") {
                    SettingsSwitchRow(
                        icon = Icons.Default.ContentPaste,
                        title = "Clipboard detection",
                        subtitle = "Prompt to analyze video links copied from other apps",
                        checked = clipboardDetection,
                        onCheckedChange = { scope.launch { viewModel.setClipboardDetection(it) } }
                    )
                }
            }

            // 9. AUTHOR & PROJECT SECTION
            item {
                SettingsGroupCard(title = "Author & Project") {
                    // Author Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(
                            icon = Icons.Default.Person,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Author",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Suresh Soundararajan",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsItemDivider()

                    // Website Row
                    SettingsLinkRow(
                        icon = Icons.Default.Language,
                        title = "Website",
                        subtitle = "https://github.com/sureshsoudararajan/Glypdl",
                        onClick = {
                            try {
                                uriHandler.openUri("https://github.com/sureshsoudararajan/Glypdl")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )

                    SettingsItemDivider()

                    // Issue Submit Row
                    SettingsLinkRow(
                        icon = Icons.Default.BugReport,
                        title = "Submit an Issue",
                        subtitle = "https://github.com/sureshsoudararajan/Glypdl/issues",
                        onClick = {
                            try {
                                uriHandler.openUri("https://github.com/sureshsoudararajan/Glypdl/issues")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    )
                }
            }

            // 10. ABOUT SECTION
            item {
                SettingsGroupCard(title = "About Glypdl") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Glypdl for Android",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Universal High-Speed Media Downloader",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "v1.2.0",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    SettingsItemDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(
                            icon = Icons.Default.Security,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Open Source License",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "GNU General Public License v3.0 (GPL-3.0-or-later)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    SettingsItemDivider()

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SettingsIconBadge(
                            icon = Icons.Default.Info,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Powered by Open Source",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "yt-dlp, FFmpeg, Jetpack Compose & Material 3",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Quality Selection Dialog
    if (qualityDialogVisible) {
        val qualityOptions = listOf(
            "best" to "Best Available Quality",
            "2160p" to "4K UHD (2160p)",
            "1440p" to "2K QHD (1440p)",
            "1080p" to "Full HD (1080p)",
            "720p" to "High Definition (720p)",
            "480p" to "Standard Definition (480p)",
            "audio" to "Audio Only"
        )
        SettingsSelectionDialog(
            title = "Default Quality Preset",
            options = qualityOptions,
            selectedKey = defaultQuality,
            onSelect = {
                scope.launch { viewModel.setDefaultQuality(it) }
                qualityDialogVisible = false
            },
            onDismiss = { qualityDialogVisible = false }
        )
    }

    // Format Selection Dialog
    if (formatDialogVisible) {
        val formatOptions = listOf(
            "mp4" to "MP4 (Universal compatibility)",
            "mkv" to "MKV (Supports multi-audio & subtitles)",
            "webm" to "WebM (Open web standard)"
        )
        SettingsSelectionDialog(
            title = "Default Format",
            options = formatOptions,
            selectedKey = defaultFormat,
            onSelect = {
                scope.launch { viewModel.setDefaultFormat(it) }
                formatDialogVisible = false
            },
            onDismiss = { formatDialogVisible = false }
        )
    }

    // Video Codec Dialog
    if (videoCodecDialogVisible) {
        val codecOptions = listOf(
            "any" to "Any / Best compatible",
            "avc1" to "H.264 / AVC (Most compatible across devices)",
            "vp9" to "VP9 (High compression efficiency)",
            "av01" to "AV1 (Next-gen modern compression)"
        )
        SettingsSelectionDialog(
            title = "Preferred Video Codec",
            options = codecOptions,
            selectedKey = preferredVideoCodec.lowercase(),
            onSelect = {
                scope.launch { viewModel.setPreferredVideoCodec(it) }
                videoCodecDialogVisible = false
            },
            onDismiss = { videoCodecDialogVisible = false }
        )
    }

    // Audio Format Dialog
    if (audioFormatDialogVisible) {
        val audioOptions = listOf(
            "m4a" to "M4A / AAC (Universal audio)",
            "mp3" to "MP3 (Legacy universal audio)",
            "opus" to "Opus (High quality at lower bitrate)",
            "flac" to "FLAC (Lossless audio)"
        )
        SettingsSelectionDialog(
            title = "Preferred Audio Format",
            options = audioOptions,
            selectedKey = preferredAudioFormat.lowercase(),
            onSelect = {
                scope.launch { viewModel.setPreferredAudioFormat(it) }
                audioFormatDialogVisible = false
            },
            onDismiss = { audioFormatDialogVisible = false }
        )
    }
}

/* =====================================================================
 * Material 3 Settings Components (Android 14/15 Grouped Cards Style)
 * ===================================================================== */

@Composable
fun SettingsGroupCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            ),
            border = BorderStroke(
                width = 0.8.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
        ) {
            content()
        }
    }
}

@Composable
fun SettingsIconBadge(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun SettingsItemDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 68.dp, end = 16.dp),
        thickness = 0.8.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
    )
}

@Composable
fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Switch) { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsIconBadge(icon = icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun SettingsSelectRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    currentValue: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsIconBadge(icon = icon)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = currentValue,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsLinkRow(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            SettingsIconBadge(icon = icon, tint = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SettingsThemeSegmentedPicker(
    selectedTheme: String,
    onThemeSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val options = listOf(
        Triple("system", "System", Icons.Default.BrightnessAuto),
        Triple("light", "Light", Icons.Default.LightMode),
        Triple("dark", "Dark", Icons.Default.DarkMode)
    )

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            options.forEach { (key, label, icon) ->
                val isSelected = selectedTheme.equals(key, ignoreCase = true)

                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onThemeSelected(key) },
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSelectionDialog(
    title: String,
    options: List<Pair<String, String>>, // (key, label)
    selectedKey: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                options.forEach { (key, label) ->
                    val isSelected = selectedKey.equals(key, ignoreCase = true)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .selectable(
                                selected = isSelected,
                                onClick = { onSelect(key) },
                                role = Role.RadioButton
                            )
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = null
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatQualityLabel(quality: String): String {
    return when (quality.lowercase()) {
        "best" -> "Best"
        "2160p" -> "4K (2160p)"
        "1440p" -> "2K (1440p)"
        "1080p" -> "1080p"
        "720p" -> "720p"
        "480p" -> "480p"
        "audio" -> "Audio Only"
        else -> quality
    }
}
