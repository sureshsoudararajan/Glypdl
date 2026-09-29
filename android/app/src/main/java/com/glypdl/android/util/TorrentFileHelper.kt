/*
 * Copyright (C) 2026 The Glypdl Authors
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

package com.glypdl.android.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object TorrentFileHelper {

    private const val TAG = "TorrentFileHelper"

    /**
     * Copies a content:// or file:// URI stream into the app's private cache directory
     * while the caller Activity context still holds valid transient read permissions.
     * Returns the absolute path of the cached file, or the original URI string on error.
     */
    fun cacheUri(context: Context, uriString: String): String {
        return try {
            val torrentDir = File(context.cacheDir, "torrents").apply { mkdirs() }

            if (uriString.startsWith("content://", ignoreCase = true)) {
                val uri = Uri.parse(uriString)
                val resolver = context.contentResolver
                
                try {
                    resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } catch (e: Exception) {
                    // Not all content providers support persistable permissions
                }

                val displayName = try {
                    resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                        if (cursor.moveToFirst()) {
                            val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            if (idx != -1) cursor.getString(idx) else null
                        } else null
                    }
                } catch (e: Exception) { null } ?: "download.torrent"

                val cleanName = if (displayName.endsWith(".torrent", ignoreCase = true)) displayName else "$displayName.torrent"
                val targetFile = File(torrentDir, "${System.currentTimeMillis()}_$cleanName")
                
                resolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                if (targetFile.exists() && targetFile.length() > 0L) {
                    targetFile.absolutePath
                } else {
                    uriString
                }
            } else if (uriString.startsWith("file://", ignoreCase = true)) {
                val path = uriString.removePrefix("file://")
                val sourceFile = File(path)
                if (sourceFile.exists() && sourceFile.canRead()) {
                    val targetFile = File(torrentDir, "${System.currentTimeMillis()}_${sourceFile.name}")
                    sourceFile.copyTo(targetFile, overwrite = true)
                    targetFile.absolutePath
                } else {
                    uriString
                }
            } else {
                uriString
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error caching URI: $uriString", e)
            uriString
        }
    }

    /**
     * Backward-compatible alias for cacheUri.
     */
    fun cacheContentUri(context: Context, contentUriString: String): String {
        return cacheUri(context, contentUriString)
    }
}

