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
        val baseDir = context.externalCacheDir ?: context.cacheDir
        val dir = File(baseDir, "staging")
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
            "ogg" -> "audio/ogg"
            "opus" -> "audio/opus"
            "aac" -> "audio/aac"
            "3gp" -> "video/3gpp"
            "avi" -> "video/x-msvideo"
            else -> {
                val cleanExt = ext.lowercase().removePrefix(".")
                val systemMime = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(cleanExt)
                if (systemMime != null) {
                    systemMime
                } else {
                    when (com.glypdl.android.service.resolver.FileCategoryClassifier.fromExtension(cleanExt)) {
                        com.glypdl.android.data.model.FileCategory.AUDIO -> "audio/*"
                        com.glypdl.android.data.model.FileCategory.VIDEO -> "video/*"
                        com.glypdl.android.data.model.FileCategory.IMAGE -> "image/*"
                        com.glypdl.android.data.model.FileCategory.ARCHIVE -> "application/zip"
                        com.glypdl.android.data.model.FileCategory.DOCUMENT -> "application/pdf"
                        com.glypdl.android.data.model.FileCategory.APPLICATION -> "application/vnd.android.package-archive"
                        else -> "*/*"
                    }
                }
            }
        }
    }

    /**
     * Determines whether the given MIME type is recognized and accepted by Android's
     * [MediaStore.Audio.Media] table. Android strictly validates audio MIME types and
     * rejects non-standard types like "audio/webm" with an [IllegalArgumentException].
     */
    fun isSupportedAudioMediaStoreMime(mimeType: String): Boolean {
        val lower = mimeType.lowercase().trim()
        return when (lower) {
            "audio/mp4", "audio/mpeg", "audio/aac", "audio/flac",
            "audio/ogg", "audio/wav", "audio/x-wav", "audio/3gpp",
            "audio/amr", "audio/amr-wb", "audio/x-m4a", "audio/m4a",
            "audio/midi", "audio/x-midi" -> true
            else -> false
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

    fun sanitizeSubfolder(name: String): String {
        return name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()
    }

    fun getCategorySubfolder(
        downloadType: com.glypdl.android.data.model.DownloadType?,
        fileCategory: com.glypdl.android.data.model.FileCategory?,
        mimeType: String?,
        isAudio: Boolean,
        torrentTitle: String? = null
    ): String {
        return when {
            downloadType == com.glypdl.android.data.model.DownloadType.TORRENT ||
            downloadType == com.glypdl.android.data.model.DownloadType.MAGNET -> {
                if (!torrentTitle.isNullOrBlank()) {
                    "Torrents/${sanitizeSubfolder(torrentTitle)}"
                } else {
                    "Torrents"
                }
            }
            isAudio || fileCategory == com.glypdl.android.data.model.FileCategory.AUDIO || mimeType?.startsWith("audio/") == true -> "Audio"
            fileCategory == com.glypdl.android.data.model.FileCategory.VIDEO || mimeType?.startsWith("video/") == true -> "Videos"
            fileCategory == com.glypdl.android.data.model.FileCategory.DOCUMENT || mimeType?.contains("pdf") == true || mimeType?.contains("document") == true || mimeType?.startsWith("text/") == true -> "Documents"
            fileCategory == com.glypdl.android.data.model.FileCategory.APPLICATION || mimeType?.contains("android.package-archive") == true -> "Apps"
            fileCategory == com.glypdl.android.data.model.FileCategory.ARCHIVE || mimeType?.contains("zip") == true || mimeType?.contains("tar") == true || mimeType?.contains("compressed") == true -> "Archives"
            fileCategory == com.glypdl.android.data.model.FileCategory.IMAGE || mimeType?.startsWith("image/") == true -> "Images"
            fileCategory == com.glypdl.android.data.model.FileCategory.CODE -> "Code"
            fileCategory == com.glypdl.android.data.model.FileCategory.DATA -> "Data"
            fileCategory == com.glypdl.android.data.model.FileCategory.DISK_IMAGE -> "DiskImages"
            fileCategory == com.glypdl.android.data.model.FileCategory.FONT -> "Fonts"
            else -> "Files"
        }
    }

    fun exportToPermanentStorage(
        context: Context,
        stagingFile: File,
        displayName: String,
        mimeType: String,
        isAudio: Boolean,
        customTreeUriString: String?,
        subfolder: String? = null
    ): Result<Uri> {
        // 0. Verify staging file exists and has content
        if (!stagingFile.exists() || stagingFile.length() <= 0L) {
            return Result.failure(IllegalStateException("Staging file does not exist or is empty: ${stagingFile.absolutePath}"))
        }

        // 1. Try Custom SAF Tree if configured
        if (!customTreeUriString.isNullOrBlank()) {
            try {
                val treeUri = Uri.parse(customTreeUriString)
                val docTree = DocumentFile.fromTreeUri(context, treeUri)
                if (docTree != null && docTree.canWrite()) {
                    var targetFolder: DocumentFile = docTree
                    if (!subfolder.isNullOrBlank()) {
                        val segments = subfolder.split("/").filter { it.isNotBlank() }
                        for (seg in segments) {
                            val subDir = targetFolder.findFile(seg) ?: targetFolder.createDirectory(seg)
                            if (subDir != null) {
                                targetFolder = subDir
                            }
                        }
                    }
                    val targetDoc = targetFolder.createFile(mimeType, displayName)
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

        // 2. Default Public MediaStore Storage under Download/Glypdl/<subfolder>
        return try {
            val relativePath = if (!subfolder.isNullOrBlank()) {
                "${Environment.DIRECTORY_DOWNLOADS}/Glypdl/$subfolder"
            } else {
                "${Environment.DIRECTORY_DOWNLOADS}/Glypdl"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val collection = MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val contentUri = context.contentResolver.insert(collection, contentValues)
                    ?: throw IllegalStateException("Failed to insert MediaStore record for $displayName with MIME $mimeType")

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
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    if (!subfolder.isNullOrBlank()) "Glypdl/$subfolder" else "Glypdl"
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
            // Note: stagingFile is NOT deleted on failure so it can be preserved and retried
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
                mime = context.contentResolver.getType(uri) ?: "*/*"
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
            Toast.makeText(context, "File could not be found", Toast.LENGTH_SHORT).show()
            return false
        }
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            Toast.makeText(context, "No application found to open this file", Toast.LENGTH_SHORT).show()
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
