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
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.glypdl.android.BuildConfig
import com.glypdl.android.data.model.AppUpdateDownloadState
import com.glypdl.android.data.model.AppUpdateInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var downloadJob: Job? = null
    @Volatile
    private var activeConnection: HttpURLConnection? = null
    @Volatile
    private var activeTempFile: File? = null

    private val _downloadState = MutableStateFlow(AppUpdateDownloadState())
    val downloadState: StateFlow<AppUpdateDownloadState> = _downloadState.asStateFlow()

    init {
        cleanUpOldUpdateApks()
    }

    /**
     * Starts downloading the APK update file within an application-scoped background coroutine.
     * Retains active download state across screen navigations.
     */
    fun startDownload(info: AppUpdateInfo) {
        val url = info.apkDownloadUrl
        if (url.isNullOrBlank()) {
            _downloadState.value = AppUpdateDownloadState(
                error = "Direct APK download not found for this release architecture. Please use GitHub."
            )
            return
        }

        val fileName = info.apkFileName ?: "glypdl-v${info.latestVersion}.apk"

        cancelDownload()
        downloadJob = scope.launch {
            _downloadState.value = AppUpdateDownloadState(
                isDownloading = true,
                progress = 0f,
                downloadedBytes = 0L,
                totalBytes = info.apkFileSize,
                speed = "Starting..."
            )

            val result = downloadUpdate(url, fileName) { percent, downloaded, total, speed ->
                _downloadState.value = AppUpdateDownloadState(
                    isDownloading = true,
                    progress = percent,
                    downloadedBytes = downloaded,
                    totalBytes = total,
                    speed = speed
                )
            }

            result.onSuccess { apkFile ->
                _downloadState.value = AppUpdateDownloadState(
                    isDownloading = false,
                    progress = 1f,
                    downloadedBytes = apkFile.length(),
                    totalBytes = apkFile.length(),
                    downloadedApkFile = apkFile,
                    isReadyToInstall = true
                )
                // Automatically attempt to install right after download completes
                installApk(apkFile)
            }.onFailure { error ->
                if (error is CancellationException) {
                    _downloadState.value = AppUpdateDownloadState()
                } else {
                    _downloadState.value = AppUpdateDownloadState(
                        isDownloading = false,
                        error = error.localizedMessage ?: "Download failed. Please check your connection."
                    )
                }
            }
        }
    }

    /**
     * Immediately cancels any active APK update download, aborting the HTTP connection
     * and deleting temporary partial files.
     */
    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        try {
            activeConnection?.disconnect()
        } catch (_: Exception) {}
        activeConnection = null
        try {
            activeTempFile?.delete()
        } catch (_: Exception) {}
        activeTempFile = null
        _downloadState.value = AppUpdateDownloadState()
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
                pInfo.versionName ?: "3.0.0"
            } catch (ex: Exception) {
                "3.0.0"
            }
        }
    }

    private data class ApkAssetCandidate(
        val name: String,
        val downloadUrl: String,
        val size: Long
    )

    /**
     * Finds the best matching APK for the current device architecture in strict priority order.
     */
    private fun findMatchingApkAsset(assets: JSONArray?): ApkAssetCandidate? {
        if (assets == null || assets.length() == 0) return null

        val candidates = mutableListOf<ApkAssetCandidate>()
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name", "")
            val url = asset.optString("browser_download_url", "")
            val size = asset.optLong("size", 0L)
            if (name.endsWith(".apk", ignoreCase = true) && url.isNotBlank()) {
                candidates.add(ApkAssetCandidate(name, url, size))
            }
        }

        if (candidates.isEmpty()) return null

        // 1. Check supported ABIs in exact priority order (e.g. arm64-v8a before armeabi-v7a)
        val supportedAbis = Build.SUPPORTED_ABIS.map { it.lowercase() }
        for (abi in supportedAbis) {
            val match = candidates.firstOrNull { candidate ->
                val name = candidate.name.lowercase()
                if (abi == "x86") {
                    name.contains("x86") && !name.contains("x86_64") && !name.contains("x86-64")
                } else {
                    name.contains(abi)
                }
            }
            if (match != null) return match
        }

        // 2. Check for universal APK
        val universalMatch = candidates.firstOrNull { it.name.lowercase().contains("universal") }
        if (universalMatch != null) return universalMatch

        // 3. Fallback to first available APK
        return candidates.firstOrNull()
    }

    /**
     * Checks GitHub releases for the latest published Glypdl release.
     */
    suspend fun checkForUpdate(): Result<AppUpdateInfo> = withContext(Dispatchers.IO) {
        // Automatically purge any stale APKs when checking for updates
        cleanUpOldUpdateApks()

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

                val matchingAsset = findMatchingApkAsset(json.optJSONArray("assets"))
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
                        apkDownloadUrl = matchingAsset?.downloadUrl,
                        apkFileName = matchingAsset?.name,
                        apkFileSize = matchingAsset?.size ?: 0L
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
     * Streams the APK update file directly with progress callback.
     */
    suspend fun downloadUpdate(
        downloadUrl: String,
        fileName: String,
        onProgress: (percent: Float, downloaded: Long, total: Long, speed: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists()) {
                updatesDir.mkdirs()
            }

            val tempFile = File(updatesDir, "$fileName.tmp")
            val targetFile = File(updatesDir, fileName)
            activeTempFile = tempFile

            var currentUrl = downloadUrl
            var connection: HttpURLConnection? = null
            var redirects = 0
            while (true) {
                val url = URL(currentUrl)
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Glypdl-Android-App")
                    connectTimeout = 15000
                    readTimeout = 30000
                    instanceFollowRedirects = false
                }
                activeConnection = conn
                val status = conn.responseCode
                if (status in 300..399) {
                    val location = conn.getHeaderField("Location")
                        ?: throw Exception("HTTP redirect missing Location header")
                    conn.disconnect()
                    activeConnection = null
                    currentUrl = location
                    redirects++
                    if (redirects > 10) throw Exception("Too many HTTP redirects")
                    continue
                }
                connection = conn
                break
            }

            if (connection.responseCode !in 200..299) {
                val code = connection.responseCode
                connection.disconnect()
                activeConnection = null
                return@withContext Result.failure(Exception("Download failed with HTTP $code"))
            }

            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
                ?: connection.contentLength.toLong()
            var downloadedBytes = 0L
            var lastProgressTime = System.currentTimeMillis()
            var bytesSinceLastProgress = 0L
            var currentSpeed = "0 KB/s"

            try {
                connection.inputStream.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(16384)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            currentCoroutineContext().ensureActive()
                            output.write(buffer, 0, bytesRead)
                            downloadedBytes += bytesRead
                            bytesSinceLastProgress += bytesRead

                            val now = System.currentTimeMillis()
                            val elapsed = now - lastProgressTime
                            if (elapsed >= 300L) {
                                val speedBps = (bytesSinceLastProgress * 1000f) / elapsed
                                currentSpeed = formatSpeed(speedBps)
                                val percent = if (totalBytes > 0) (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
                                onProgress(percent, downloadedBytes, totalBytes, currentSpeed)
                                lastProgressTime = now
                                bytesSinceLastProgress = 0L
                            }
                        }
                    }
                }
            } finally {
                try {
                    connection.disconnect()
                } catch (_: Exception) {}
                activeConnection = null
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!tempFile.renameTo(targetFile)) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }

            onProgress(1f, downloadedBytes, totalBytes, "")
            Result.success(targetFile)
        } catch (e: Exception) {
            try {
                activeTempFile?.delete()
            } catch (_: Exception) {}
            if (e is CancellationException) {
                return@withContext Result.failure(e)
            }
            e.printStackTrace()
            Result.failure(e)
        } finally {
            activeTempFile = null
        }
    }

    /**
     * Checks whether the app has permission to install packages from unknown sources.
     */
    fun canInstallApks(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Creates an intent navigating the user to the system setting to grant install permission.
     */
    fun createInstallPermissionIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Triggers the native Android package installer using FileProvider.
     */
    fun installApk(apkFile: File): Result<Unit> {
        return try {
            val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Scans for old update APKs in the cache directory and deletes them only if
     * the currently installed version is equal to or newer than the APK's version.
     */
    fun cleanUpOldUpdateApks() {
        try {
            val updatesDir = File(context.cacheDir, "updates")
            if (!updatesDir.exists() || !updatesDir.isDirectory) return
            val currentVer = getCurrentVersion()

            updatesDir.listFiles()?.forEach { file ->
                if (file.name.endsWith(".tmp")) {
                    file.delete()
                } else if (file.name.endsWith(".apk", ignoreCase = true)) {
                    val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        context.packageManager.getPackageArchiveInfo(
                            file.absolutePath,
                            android.content.pm.PackageManager.PackageInfoFlags.of(0)
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
                    }
                    val apkVer = pkgInfo?.versionName
                    // If current app version is equal or newer than the APK's version, the update has completed!
                    if (apkVer == null || !isNewerVersion(apkVer, currentVer)) {
                        android.util.Log.d("AppUpdateManager", "Deleting cached update APK: ${file.name} (apkVer=$apkVer, currentVer=$currentVer)")
                        file.delete()
                    }
                }
            }
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    private fun formatSpeed(bytesPerSec: Float): String {
        return when {
            bytesPerSec >= 1024 * 1024 -> String.format(java.util.Locale.US, "%.1f MB/s", bytesPerSec / (1024 * 1024))
            bytesPerSec >= 1024 -> String.format(java.util.Locale.US, "%.0f KB/s", bytesPerSec / 1024)
            else -> String.format(java.util.Locale.US, "%.0f B/s", bytesPerSec)
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
