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
 * Glypdl is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Glypdl.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.glypdl.android.util

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import java.io.File

object StorageHelper {

    fun getDefaultStagingDir(context: Context): File {
        val dir = File(context.cacheDir, "staging")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getMimeType(ext: String, isAudio: Boolean): String {
        return when (ext.lowercase().removePrefix(".")) {
            "mp4", "m4v" -> if (isAudio) "audio/mp4" else "video/mp4"
            "mkv" -> "video/x-matroska"
            "webm" -> if (isAudio) "audio/webm" else "video/webm"
            "mp3" -> "audio/mpeg"
            "m4a" -> "audio/mp4"
            "flac" -> "audio/flac"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            else -> if (isAudio) "audio/*" else "video/*"
        }
    }

    fun getReadableStorageName(context: Context, treeUriString: String?): String {
        if (treeUriString.isNullOrBlank()) {
            return "Default: Movies/Glypdl (Videos) & Music/Glypdl (Audio)"
        }
        return try {
            val uri = Uri.parse(treeUriString)
            val doc = DocumentFile.fromTreeUri(context, uri)
            val displayName = doc?.name
            if (!displayName.isNullOrBlank()) {
                "Folder: $displayName"
            } else {
                val lastSeg = uri.lastPathSegment?.substringAfterLast(':')
                if (!lastSeg.isNullOrBlank()) "Folder: $lastSeg" else treeUriString
            }
        } catch (e: Exception) {
            "Custom Folder"
        }
    }

    fun exportToPermanentStorage(
        context: Context,
        stagingFile: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        customTreeUriString: String?
    ): Result<Uri> {
        // 1. Try Custom SAF Tree if configured
        if (!customTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUriString)
                val docTree = DocumentFile.fromTreeUri(context, treeUri)
                if (docTree != null && docTree.canWrite()) {
                    val targetDoc = docTree.createFile(mimeType, displayName)
                        ?: throw IllegalStateException("Cannot create file in custom folder")
                    context.contentResolver.openOutputStream(targetDoc.uri)?.use { out ->
                        stagingFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Cannot open output stream for document: ${targetDoc.uri}")

                    stagingFile.delete()
                    return Result.success(targetDoc.uri)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Fallback to default MediaStore below if custom folder fails
            }
        }

        // 2. Default Public MediaStore Storage
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = if (isAudio) {
                    MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                } else {
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
                }

                val relativePath = if (isAudio) "Music/Glypdl" else "Movies/Glypdl"

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val contentUri = context.contentResolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Failed to insert MediaStore record")

                try {
                    context.contentResolver.openOutputStream(contentUri)?.use { out ->
                        stagingFile.inputStream().use { input ->
                            input.copyTo(out)
                        }
                    } ?: throw IllegalStateException("Failed to open output stream for MediaStore URI")

                    contentValues.clear()
                    contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    context.contentResolver.update(contentUri, contentValues, null, null)

                    stagingFile.delete()
                    Result.success(contentUri)
                } catch (e: Exception) {
                    context.contentResolver.delete(contentUri, null, null)
                    throw e
                }
            } else {
                // API 26-28 Fallback
                val targetDir = File(
                    Environment.getExternalStoragePublicDirectory(
                        if (isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
                    ),
                    "Glypdl"
                )
                if (!targetDir.exists()) {
                    targetDir.mkdirs()
                }
                val targetFile = File(targetDir, displayName)
                stagingFile.inputStream().use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                stagingFile.delete()

                var scannedUri: Uri? = null
                MediaScannerConnection.scanFile(
                    context,
                    arrayOf(targetFile.absolutePath),
                    arrayOf(mimeType)
                ) { _, uri ->
                    scannedUri = uri
                }

                Result.success(scannedUri ?: Uri.fromFile(targetFile))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    fun createOpenIntent(context: Context, uriOrPath: String, isAudio: Boolean): Intent? {
        if (uriOrPath.isBlank()) return null
        return try {
            val uri: Uri
            val mime: String
            if (uriOrPath.startsWith("content://")) {
                uri = Uri.parse(uriOrPath)
                mime = context.contentResolver.getType(uri) ?: (if (isAudio) "audio/*" else "video/*")
            } else {
                val file = File(uriOrPath)
                if (!file.exists()) return null
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val ext = file.extension
                mime = getMimeType(ext, isAudio)
            }

            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun openMedia(context: Context, uriOrPath: String, isAudio: Boolean): Boolean {
        val intent = createOpenIntent(context, uriOrPath, isAudio) ?: run {
            Toast.makeText(context, "Media file could not be found", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No application found to open this media", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun shareMedia(context: Context, uriOrPath: String, title: String, isAudio: Boolean): Boolean {
        if (uriOrPath.isBlank()) return false
        return try {
            val uri: Uri
            val mime: String
            if (uriOrPath.startsWith("content://")) {
                uri = Uri.parse(uriOrPath)
                mime = context.contentResolver.getType(uri) ?: (if (isAudio) "audio/*" else "video/*")
            } else {
                val file = File(uriOrPath)
                if (!file.exists()) return false
                uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                val ext = file.extension
                mime = getMimeType(ext, isAudio)
            }

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mime
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooser = Intent.createChooser(shareIntent, "Share Media").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "Unable to share media", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
