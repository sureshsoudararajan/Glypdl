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
package com.glypdl.android.data.status

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class StatusScanResult {
    data class Success(val items: List<StatusMedia>) : StatusScanResult()
    data class FolderNotStatuses(val folderName: String) : StatusScanResult()
    data class PermissionDenied(val message: String) : StatusScanResult()
    data class Error(val message: String, val cause: Throwable? = null) : StatusScanResult()
}

@Singleton
class StatusMediaScanner @Inject constructor(
    @ApplicationContext private val context: Context
) {

    suspend fun scanDirectory(treeUri: Uri): StatusScanResult = withContext(Dispatchers.IO) {
        try {
            // Verify persisted permission is available
            val hasPermission = context.contentResolver.persistedUriPermissions.any {
                it.uri == treeUri && it.isReadPermission
            }
            val rootDoc = try {
                DocumentFile.fromTreeUri(context, treeUri)
            } catch (e: SecurityException) {
                return@withContext StatusScanResult.PermissionDenied("Access to the selected folder was revoked.")
            }

            if (rootDoc == null || !rootDoc.exists() || !rootDoc.canRead()) {
                return@withContext StatusScanResult.PermissionDenied("Cannot read the selected folder.")
            }

            // Determine whether root is .Statuses or Media containing .Statuses
            val targetDir = resolveStatusesDirectory(rootDoc)
                ?: return@withContext StatusScanResult.FolderNotStatuses(rootDoc.name ?: "Selected Folder")

            val files = try {
                targetDir.listFiles()
            } catch (e: Exception) {
                return@withContext StatusScanResult.Error("Failed to list status files: ${e.message}", e)
            }

            val statusItems = mutableListOf<StatusMedia>()
            for (file in files) {
                if (!file.isFile) continue
                val name = file.name ?: continue
                if (name.startsWith(".nomedia", ignoreCase = true)) continue

                val mimeType = resolveMimeType(file)
                val mediaType = resolveMediaType(name, mimeType) ?: continue
                val length = file.length()
                if (length <= 0L) continue

                val lastModified = file.lastModified()
                statusItems.add(
                    StatusMedia(
                        id = file.uri.toString(),
                        uri = file.uri,
                        displayName = name,
                        mimeType = mimeType,
                        mediaType = mediaType,
                        sizeBytes = length,
                        modifiedAt = lastModified
                    )
                )
            }

            // Most recent statuses first
            statusItems.sortByDescending { it.modifiedAt }

            StatusScanResult.Success(statusItems)
        } catch (e: SecurityException) {
            StatusScanResult.PermissionDenied("Permission denied: ${e.message}")
        } catch (e: Exception) {
            StatusScanResult.Error("Error scanning status folder: ${e.message}", e)
        }
    }

    /**
     * Resolves the actual directory containing statuses.
     * Supports:
     * 1. Option B: User picked `.Statuses` directly.
     * 2. Option A: User picked `Media` (or WhatsApp root) containing child `.Statuses` or `Statuses`.
     */
    fun resolveStatusesDirectory(root: DocumentFile): DocumentFile? {
        val rootName = root.name?.trim().orEmpty()
        if (rootName.equals(".Statuses", ignoreCase = true) || rootName.equals("Statuses", ignoreCase = true)) {
            return root
        }

        // Try direct findFile for .Statuses
        val directChild = root.findFile(".Statuses") ?: root.findFile("Statuses")
        if (directChild != null && directChild.isDirectory) {
            return directChild
        }

        // Search one level deep among children if user selected WhatsApp parent
        val children = try { root.listFiles() } catch (e: Exception) { emptyArray() }
        val childStatuses = children.firstOrNull {
            it.isDirectory && (it.name.equals(".Statuses", ignoreCase = true) || it.name.equals("Statuses", ignoreCase = true))
        }
        if (childStatuses != null) {
            return childStatuses
        }

        // If the directory itself already contains media files (e.g. user selected .Statuses inside a renamed folder)
        val hasMediaFiles = children.any { file ->
            file.isFile && resolveMediaType(file.name.orEmpty(), resolveMimeType(file)) != null
        }
        if (hasMediaFiles) {
            return root
        }

        return null
    }

    fun resolveMimeType(file: DocumentFile): String {
        val systemType = file.type?.lowercase()?.trim()
        if (!systemType.isNullOrBlank() && systemType != "application/octet-stream") {
            return systemType
        }
        val ext = file.name?.substringAfterLast('.', "")?.lowercase()?.trim().orEmpty()
        return when (ext) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "mp4", "m4v" -> "video/mp4"
            "3gp" -> "video/3gpp"
            "mkv" -> "video/x-matroska"
            "webm" -> "video/webm"
            else -> systemType ?: "application/octet-stream"
        }
    }

    fun resolveMediaType(name: String, mimeType: String): StatusMediaType? {
        val cleanMime = mimeType.lowercase().trim()
        if (cleanMime.startsWith("image/")) return StatusMediaType.IMAGE
        if (cleanMime.startsWith("video/")) return StatusMediaType.VIDEO

        val ext = name.substringAfterLast('.', "").lowercase().trim()
        return when (ext) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp" -> StatusMediaType.IMAGE
            "mp4", "3gp", "mkv", "webm", "mov", "ts" -> StatusMediaType.VIDEO
            else -> null
        }
    }
}
