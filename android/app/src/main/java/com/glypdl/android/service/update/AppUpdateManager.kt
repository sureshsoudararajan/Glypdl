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
import android.os.Build
import com.glypdl.android.BuildConfig
import com.glypdl.android.data.model.AppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val GITHUB_OWNER = "sureshsoudararajan"
        const val GITHUB_REPO = "Glypdl"
        const val LATEST_RELEASE_API_URL =
            "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
        const val GITHUB_RELEASES_URL =
            "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    }

    /**
     * Returns the installed version name of the application.
     */
    fun getCurrentVersion(): String {
        return try {
            BuildConfig.VERSION_NAME
        } catch (e: Throwable) {
            try {
                val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(
                        context.packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(context.packageName, 0)
                }
                pInfo.versionName ?: "2.0.0"
            } catch (ex: Exception) {
                "2.0.0"
            }
        }
    }

    /**
     * Checks GitHub releases for the latest published Glypdl release.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        try {
            val url = URL(LATEST_RELEASE_API_URL)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github.v3+json")
                setRequestProperty("User-Agent", "Glypdl-Android-App")
                connectTimeout = 8000
                readTimeout = 8000
            }

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val json = JSONObject(response)
                val rawTag = json.optString("tag_name", "").trim()
                val tagName = rawTag.removePrefix("v").removePrefix("V")
                val htmlUrl = json.optString("html_url", GITHUB_RELEASES_URL)
                val releaseTitle = json.optString("name", "Glypdl v$tagName")
                val releaseNotes = json.optString("body", "")

                // Look for matching APK in release assets
                var apkUrl: String? = null
                val assets = json.optJSONArray("assets")
                if (assets != null) {
                    val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
                    var bestMatchUrl: String? = null
                    var fallbackApkUrl: String? = null

                    for (i in 0 until assets.length()) {
                        val asset = assets.optJSONObject(i) ?: continue
                        val assetName = asset.optString("name", "")
                        val downloadUrl = asset.optString("browser_download_url", "")

                        if (assetName.endsWith(".apk", ignoreCase = true)) {
                            if (fallbackApkUrl == null) {
                                fallbackApkUrl = downloadUrl
                            }
                            val lowerName = assetName.lowercase()
                            for (abi in supportedAbis) {
                                if (lowerName.contains(abi)) {
                                    bestMatchUrl = downloadUrl
                                    break
                                }
                            }
                            if (bestMatchUrl != null) break
                        }
                    }
                    apkUrl = bestMatchUrl ?: fallbackApkUrl
                }

                val currentVer = getCurrentVersion()
                val hasUpdate = isNewerVersion(tagName, currentVer)

                Result.success(
                    AppUpdateInfo(
                        currentVersion = currentVer,
                        latestVersion = tagName,
                        isUpdateAvailable = hasUpdate,
                        releaseUrl = htmlUrl.ifBlank { GITHUB_RELEASES_URL },
                        releaseTitle = releaseTitle,
                        releaseNotes = releaseNotes,
                        apkDownloadUrl = apkUrl
                    )
                )
            } else {
                conn.disconnect()
                Result.failure(Exception("GitHub API returned HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Compares two semantic version strings (e.g., "1.2.2" vs "1.2.1", "v1.3.0" vs "1.2.1").
     * Returns true if latest is strictly newer than current.
     */
    fun isNewerVersion(latest: String, current: String): Boolean {
        val cleanLatest = latest.trim().removePrefix("v").removePrefix("V")
        val cleanCurrent = current.trim().removePrefix("v").removePrefix("V")
        if (cleanLatest.isBlank() || cleanCurrent.isBlank()) return false
        if (cleanLatest.equals(cleanCurrent, ignoreCase = true)) return false

        val latestParts = cleanLatest.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = cleanCurrent.split("-")[0].split(".").mapNotNull { it.toIntOrNull() }

        val maxLen = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until maxLen) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l > c) return true
            if (l < c) return false
        }
        return false
    }
}
