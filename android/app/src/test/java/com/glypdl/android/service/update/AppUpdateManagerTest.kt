/*
 * Glypdl - Media Downloader
 * Copyright (C) 2026 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.service.update

import android.content.Context
import com.glypdl.android.data.model.AppUpdateDownloadState
import com.glypdl.android.data.model.AppUpdateInfo
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AppUpdateManagerTest {

    private lateinit var context: Context
    private lateinit var appUpdateManager: AppUpdateManager

    @Before
    fun setUp() {
        context = mockk(relaxed = true)
        appUpdateManager = AppUpdateManager(context)
    }

    @Test
    fun testIsNewerVersion_newerPatch_returnsTrue() {
        assertTrue(appUpdateManager.isNewerVersion("1.2.2", "1.2.1"))
        assertTrue(appUpdateManager.isNewerVersion("v1.2.2", "1.2.1"))
        assertTrue(appUpdateManager.isNewerVersion("v1.2.2", "v1.2.1"))
    }

    @Test
    fun testIsNewerVersion_newerMinor_returnsTrue() {
        assertTrue(appUpdateManager.isNewerVersion("1.3.0", "1.2.1"))
        assertTrue(appUpdateManager.isNewerVersion("v1.3", "1.2.1"))
    }

    @Test
    fun testIsNewerVersion_newerMajor_returnsTrue() {
        assertTrue(appUpdateManager.isNewerVersion("2.0.0", "1.2.1"))
    }

    @Test
    fun testIsNewerVersion_sameVersion_returnsFalse() {
        assertFalse(appUpdateManager.isNewerVersion("1.2.1", "1.2.1"))
        assertFalse(appUpdateManager.isNewerVersion("v1.2.1", "1.2.1"))
        assertFalse(appUpdateManager.isNewerVersion("1.2.1", "v1.2.1"))
    }

    @Test
    fun testIsNewerVersion_olderVersion_returnsFalse() {
        assertFalse(appUpdateManager.isNewerVersion("1.2.0", "1.2.1"))
        assertFalse(appUpdateManager.isNewerVersion("1.1.9", "1.2.1"))
        assertFalse(appUpdateManager.isNewerVersion("0.9.0", "1.2.1"))
    }

    @Test
    fun testIsNewerVersion_emptyOrBlank_returnsFalse() {
        assertFalse(appUpdateManager.isNewerVersion("", "1.2.1"))
        assertFalse(appUpdateManager.isNewerVersion("1.2.1", ""))
        assertFalse(appUpdateManager.isNewerVersion("", ""))
    }

    @Test
    fun testAppUpdateInfo_properties() {
        val info = AppUpdateInfo(
            currentVersion = "1.2.1",
            latestVersion = "1.2.2",
            isUpdateAvailable = true,
            releaseUrl = "https://github.com/sureshsoudararajan/Glypdl/releases/tag/v1.2.2",
            releaseTitle = "Glypdl v1.2.2",
            releaseNotes = "Fixes & performance improvements",
            apkDownloadUrl = "https://github.com/sureshsoudararajan/Glypdl/releases/download/v1.2.2/Glypdl-1.2.2-arm64-v8a.apk"
        )

        assertEquals("1.2.1", info.currentVersion)
        assertEquals("1.2.2", info.latestVersion)
        assertTrue(info.isUpdateAvailable)
        assertEquals("https://github.com/sureshsoudararajan/Glypdl/releases/tag/v1.2.2", info.releaseUrl)
        assertEquals("Glypdl v1.2.2", info.releaseTitle)
        assertEquals("Fixes & performance improvements", info.releaseNotes)
        assertEquals("https://github.com/sureshsoudararajan/Glypdl/releases/download/v1.2.2/Glypdl-1.2.2-arm64-v8a.apk", info.apkDownloadUrl)
    }

    @Test
    fun testAppUpdateInfo_withApkDetails() {
        val info = AppUpdateInfo(
            currentVersion = "2.0.0",
            latestVersion = "2.1.0",
            isUpdateAvailable = true,
            releaseUrl = "https://github.com/sureshsoudararajan/Glypdl/releases/tag/v2.1.0",
            apkDownloadUrl = "https://github.com/sureshsoudararajan/Glypdl/releases/download/v2.1.0/Glypdl-2.1.0-arm64-v8a.apk",
            apkFileName = "Glypdl-2.1.0-arm64-v8a.apk",
            apkFileSize = 52428800L
        )

        assertEquals("Glypdl-2.1.0-arm64-v8a.apk", info.apkFileName)
        assertEquals(52428800L, info.apkFileSize)
    }

    @Test
    fun testAppUpdateDownloadState_lifecycle() {
        // Initial
        val initial = AppUpdateDownloadState()
        assertFalse(initial.isDownloading)
        assertEquals(0f, initial.progress, 0.001f)
        assertNull(initial.downloadedApkFile)
        assertFalse(initial.isReadyToInstall)
        assertNull(initial.error)

        // Downloading
        val downloading = initial.copy(
            isDownloading = true,
            progress = 0.45f,
            downloadedBytes = 23592960L,
            totalBytes = 52428800L,
            speed = "4.2 MB/s"
        )
        assertTrue(downloading.isDownloading)
        assertEquals(0.45f, downloading.progress, 0.001f)
        assertEquals(23592960L, downloading.downloadedBytes)
        assertEquals(52428800L, downloading.totalBytes)
        assertEquals("4.2 MB/s", downloading.speed)

        // Ready to install
        val dummyFile = File("/tmp/Glypdl-2.1.0-arm64-v8a.apk")
        val ready = downloading.copy(
            isDownloading = false,
            progress = 1f,
            downloadedApkFile = dummyFile,
            isReadyToInstall = true
        )
        assertFalse(ready.isDownloading)
        assertTrue(ready.isReadyToInstall)
        assertEquals(dummyFile, ready.downloadedApkFile)

        // Error state
        val errorState = initial.copy(
            error = "Network timeout"
        )
        assertEquals("Network timeout", errorState.error)
    }

    @Test
    fun testStartDownload_missingUrl_setsError() {
        val info = AppUpdateInfo(
            currentVersion = "4.0.0",
            latestVersion = "4.1.0",
            isUpdateAvailable = true,
            releaseUrl = "https://github.com/sureshsoudararajan/Glypdl/releases/tag/v4.1.0",
            apkDownloadUrl = null
        )
        appUpdateManager.startDownload(info)
        val state = appUpdateManager.downloadState.value
        assertFalse(state.isDownloading)
        assertTrue(state.error?.contains("Direct APK download not found") == true)
    }

    @Test
    fun testCancelDownload_resetsState() {
        appUpdateManager.cancelDownload()
        val state = appUpdateManager.downloadState.value
        assertFalse(state.isDownloading)
        assertNull(state.downloadedApkFile)
        assertFalse(state.isReadyToInstall)
        assertNull(state.error)
    }
}
