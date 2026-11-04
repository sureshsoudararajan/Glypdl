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
package com.glypdl.android.service.status

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.glypdl.android.data.local.dao.SavedStatusDao
import com.glypdl.android.data.local.entity.SavedStatusEntity
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.data.status.model.StatusMedia
import com.glypdl.android.data.status.model.StatusMediaType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.security.DigestInputStream
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

sealed class ExportResult {
    data class Success(val savedUri: Uri, val isDuplicate: Boolean = false) : ExportResult()
    data class AlreadySaved(val existingUri: Uri) : ExportResult()
    data class Failure(val message: String, val error: Throwable? = null) : ExportResult()
}

@Singleton
class StatusExportManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val savedStatusDao: SavedStatusDao,
    private val settingsRepository: SettingsRepository
) {

    companion object {
        private const val BUFFER_SIZE = 64 * 1024 // 64 KB buffer for streaming
    }

    /**
     * Exports a WhatsApp status media item to `Glypdl/status`.
     * Both images and videos are saved to the unified `Glypdl/status` directory.
     */
    suspend fun exportStatus(
        status: StatusMedia,
        skipDuplicateCheck: Boolean = false
    ): ExportResult = withContext(Dispatchers.IO) {
        try {
            // 1. Fast preliminary check: was original URI already recorded in database?
            if (!skipDuplicateCheck) {
                val existing = savedStatusDao.getByOriginalUri(status.uri.toString())
                if (existing != null) {
                    val existingUri = Uri.parse(existing.savedUri)
                    if (isUriAccessible(existingUri)) {
                        return@withContext ExportResult.AlreadySaved(existingUri)
                    }
                }
            }

            // 2. Prepare destination filename
            val baseName = sanitizeFilename(status.displayName)
            val isVideo = status.mediaType == StatusMediaType.VIDEO
            val mimeType = status.mimeType

            // 3. Export stream to Custom SAF Tree (if configured) or MediaStore/Downloads
            val md = MessageDigest.getInstance("SHA-256")
            val customTreeUri = try {
                settingsRepository.downloadDirUri.first()
            } catch (e: Exception) {
                null
            }

            val (savedUri, hashHex) = if (!customTreeUri.isNullOrBlank()) {
                try {
                    exportToCustomTree(status.uri, baseName, mimeType, customTreeUri, md)
                } catch (e: Exception) {
                    // Fallback to default MediaStore if custom tree fails
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        exportToMediaStore(status.uri, baseName, mimeType, md)
                    } else {
                        exportToLegacyStorage(status.uri, baseName, mimeType, md)
                    }
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                exportToMediaStore(status.uri, baseName, mimeType, md)
            } else {
                exportToLegacyStorage(status.uri, baseName, mimeType, md)
            }

            // 4. Duplicate content hash check: If another file with identical SHA-256 exists
            if (!skipDuplicateCheck && hashHex.isNotBlank()) {
                val existingHash = savedStatusDao.getByHash(hashHex)
                if (existingHash != null) {
                    val existingUri = Uri.parse(existingHash.savedUri)
                    if (isUriAccessible(existingUri) && existingUri != savedUri) {
                        // Clean up redundant copy
                        deleteUriSafely(savedUri)
                        return@withContext ExportResult.AlreadySaved(existingUri)
                    }
                }
            }

            // 5. Save record in database
            val entity = SavedStatusEntity(
                originalUri = status.uri.toString(),
                savedUri = savedUri.toString(),
                displayName = baseName,
                mimeType = mimeType,
                mediaType = status.mediaType.name,
                sizeBytes = status.sizeBytes,
                savedAt = System.currentTimeMillis(),
                contentHash = hashHex,
                sourcePackage = status.sourcePackage
            )
            savedStatusDao.insert(entity)

            ExportResult.Success(savedUri)
        } catch (e: Exception) {
            e.printStackTrace()
            ExportResult.Failure("Failed to export status: ${e.message}", e)
        }
    }

    private fun exportToCustomTree(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        treeUriString: String,
        md: MessageDigest
    ): Pair<Uri, String> {
        val treeUri = Uri.parse(treeUriString)
        val docTree = DocumentFile.fromTreeUri(context, treeUri)
            ?: throw IllegalStateException("Cannot access custom directory")

        val statusFolder = docTree.findFile("status")
            ?: docTree.createDirectory("status")
            ?: docTree

        val targetDoc = statusFolder.createFile(mimeType, displayName)
            ?: throw IllegalStateException("Cannot create document file: $displayName")

        var hashString = ""
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open input stream for: $sourceUri")

            val outputStream = context.contentResolver.openOutputStream(targetDoc.uri)
                ?: throw IllegalStateException("Cannot open output stream for: ${targetDoc.uri}")

            DigestInputStream(inputStream, md).use { dis ->
                outputStream.use { os ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (dis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                    os.flush()
                }
            }

            hashString = md.digest().joinToString("") { "%02x".format(it) }
            return targetDoc.uri to hashString
        } catch (e: Exception) {
            targetDoc.delete()
            throw e
        }
    }

    private fun exportToMediaStore(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        md: MessageDigest
    ): Pair<Uri, String> {
        val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val relativePath = "${Environment.DIRECTORY_DOWNLOADS}/Glypdl/status"

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val targetUri = context.contentResolver.insert(collection, values)
            ?: throw IllegalStateException("Failed to insert MediaStore entry for $displayName")

        var hashString = ""
        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open input stream for status: $sourceUri")

            val outputStream = context.contentResolver.openOutputStream(targetUri)
                ?: throw IllegalStateException("Cannot open output stream for MediaStore URI: $targetUri")

            DigestInputStream(inputStream, md).use { dis ->
                outputStream.use { os ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (dis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                    os.flush()
                }
            }

            hashString = md.digest().joinToString("") { "%02x".format(it) }

            // Finalize entry in MediaStore
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            context.contentResolver.update(targetUri, values, null, null)

            return targetUri to hashString
        } catch (e: Exception) {
            // Cleanup on failure
            context.contentResolver.delete(targetUri, null, null)
            throw e
        }
    }

    private fun exportToLegacyStorage(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        md: MessageDigest
    ): Pair<Uri, String> {
        val parentDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "Glypdl/status"
        )
        if (!parentDir.exists()) {
            parentDir.mkdirs()
        }

        val targetFile = File(parentDir, displayName)
        var hashString = ""

        try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
                ?: throw IllegalStateException("Cannot open input stream for: $sourceUri")

            DigestInputStream(inputStream, md).use { dis ->
                targetFile.outputStream().use { os ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead: Int
                    while (dis.read(buffer).also { bytesRead = it } != -1) {
                        os.write(buffer, 0, bytesRead)
                    }
                    os.flush()
                }
            }

            hashString = md.digest().joinToString("") { "%02x".format(it) }

            var scannedUri: Uri? = null
            MediaScannerConnection.scanFile(
                context,
                arrayOf(targetFile.absolutePath),
                arrayOf(mimeType)
            ) { _, uri ->
                scannedUri = uri
            }

            val finalUri = scannedUri ?: Uri.fromFile(targetFile)
            return finalUri to hashString
        } catch (e: Exception) {
            if (targetFile.exists()) {
                targetFile.delete()
            }
            throw e
        }
    }

    fun sanitizeFilename(original: String): String {
        val trimmed = original.trim()
        val sanitized = trimmed.replace(Regex("""[/\\:*?"<>|]"""), "_")
        return if (sanitized.isNotBlank()) sanitized else "status_${System.currentTimeMillis()}"
    }

    private fun isUriAccessible(uri: Uri): Boolean {
        return try {
            context.contentResolver.openInputStream(uri)?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }

    private fun deleteUriSafely(uri: Uri) {
        try {
            context.contentResolver.delete(uri, null, null)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
