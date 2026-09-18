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
import com.glypdl.android.data.model.AppUpdateInfo
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

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
}
