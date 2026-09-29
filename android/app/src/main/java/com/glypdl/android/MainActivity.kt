/*
 * Copyright (c) 2024 Glypdl
 *
 * This file is part of Glypdl.
 *
 * Glypdl is free software: you can redistribute it and/or modify
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
 * along with Glypdl.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.rememberNavController
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.ui.navigation.GlypdlRootApp
import com.glypdl.android.ui.navigation.Screen
import com.glypdl.android.ui.theme.GlypdlTheme
import com.glypdl.android.util.IntentHandler
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var downloadResolver: com.glypdl.android.service.resolver.DownloadResolver

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _: Boolean ->
        // Permission result handled — notification permission is optional
    }

    private val sharedUrlFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)
    private val navigateToDownloadIdFlow = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val startDestination = runBlocking {
            val isFirstRunComplete = settingsRepository.firstRunComplete.first()
            if (isFirstRunComplete) Screen.Home.route else Screen.Welcome.route
        }

        val initialUrl = processIncomingUrl(IntentHandler.extractUrlFromIntent(intent))
        sharedUrlFlow.value = initialUrl
        intent.getStringExtra(EXTRA_NAVIGATE_TO_DOWNLOAD_ID)?.let {
            navigateToDownloadIdFlow.value = it
        }

        setContent {
            val themePreference by settingsRepository.theme.collectAsState(initial = "system")
            val pendingUrl by sharedUrlFlow.collectAsState()
            val pendingDownloadId by navigateToDownloadIdFlow.collectAsState()

            GlypdlTheme(themeSetting = themePreference, dynamicColor = false) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()

                    LaunchedEffect(pendingUrl) {
                        pendingUrl?.let { url ->
                            val result = downloadResolver.resolve(url)
                            val dest = if (result.downloadType == com.glypdl.android.data.model.DownloadType.DIRECT_FILE) {
                                Screen.DirectFilePreview.createRoute(url)
                            } else if (result.downloadType == com.glypdl.android.data.model.DownloadType.MAGNET || result.downloadType == com.glypdl.android.data.model.DownloadType.TORRENT) {
                                Screen.TorrentPreview.createRoute(url)
                            } else {
                                Screen.Analyze.createRoute(url)
                            }
                            navController.navigate(dest) {
                                launchSingleTop = true
                            }
                            sharedUrlFlow.value = null
                        }
                    }

                    LaunchedEffect(pendingDownloadId) {
                        pendingDownloadId?.let { downloadId ->
                            navController.navigate(Screen.DownloadDetail.createRoute(downloadId)) {
                                launchSingleTop = true
                            }
                            navigateToDownloadIdFlow.value = null
                        }
                    }

                    GlypdlRootApp(
                        navController = navController,
                        startDestination = startDestination,
                        settingsRepository = settingsRepository,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        processIncomingUrl(IntentHandler.extractUrlFromIntent(intent))?.let { url ->
            sharedUrlFlow.value = url
        }
        intent.getStringExtra(EXTRA_NAVIGATE_TO_DOWNLOAD_ID)?.let { downloadId ->
            navigateToDownloadIdFlow.value = downloadId
        }
    }

    private fun processIncomingUrl(rawUrl: String?): String? {
        if (rawUrl == null) return null
        if (rawUrl.startsWith("content://", ignoreCase = true) || rawUrl.startsWith("file://", ignoreCase = true)) {
            return com.glypdl.android.util.TorrentFileHelper.cacheUri(this, rawUrl)
        }
        return rawUrl
    }

    companion object {
        const val EXTRA_NAVIGATE_TO_DOWNLOAD_ID = "com.glypdl.android.extra.NAVIGATE_TO_DOWNLOAD_ID"
    }
}
