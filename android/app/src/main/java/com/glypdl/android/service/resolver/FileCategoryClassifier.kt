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
package com.glypdl.android.service.resolver

import com.glypdl.android.data.model.FileCategory
import java.net.URI
import java.net.URLDecoder

object FileCategoryClassifier {
    
    private val extensionMap = mapOf(
        "zip" to FileCategory.ARCHIVE, "rar" to FileCategory.ARCHIVE, "7z" to FileCategory.ARCHIVE, 
        "tar" to FileCategory.ARCHIVE, "gz" to FileCategory.ARCHIVE, "bz2" to FileCategory.ARCHIVE, 
        "xz" to FileCategory.ARCHIVE, "lz" to FileCategory.ARCHIVE, "lzma" to FileCategory.ARCHIVE, 
        "zst" to FileCategory.ARCHIVE, "cab" to FileCategory.ARCHIVE, "arj" to FileCategory.ARCHIVE, 
        "lzh" to FileCategory.ARCHIVE, "z" to FileCategory.ARCHIVE, "tgz" to FileCategory.ARCHIVE, 
        "tbz2" to FileCategory.ARCHIVE, "txz" to FileCategory.ARCHIVE,
        
        "iso" to FileCategory.DISK_IMAGE, "img" to FileCategory.DISK_IMAGE, "dmg" to FileCategory.DISK_IMAGE, 
        "vhd" to FileCategory.DISK_IMAGE, "vhdx" to FileCategory.DISK_IMAGE, "vmdk" to FileCategory.DISK_IMAGE, 
        "qcow2" to FileCategory.DISK_IMAGE,
        
        "apk" to FileCategory.APPLICATION, "exe" to FileCategory.APPLICATION, "msi" to FileCategory.APPLICATION, 
        "deb" to FileCategory.APPLICATION, "rpm" to FileCategory.APPLICATION, "flatpak" to FileCategory.APPLICATION, 
        "snap" to FileCategory.APPLICATION, "appimage" to FileCategory.APPLICATION, "pkg" to FileCategory.APPLICATION,
        
        "pdf" to FileCategory.DOCUMENT, "doc" to FileCategory.DOCUMENT, "docx" to FileCategory.DOCUMENT, 
        "xls" to FileCategory.DOCUMENT, "xlsx" to FileCategory.DOCUMENT, "ppt" to FileCategory.DOCUMENT, 
        "pptx" to FileCategory.DOCUMENT, "odt" to FileCategory.DOCUMENT, "ods" to FileCategory.DOCUMENT, 
        "odp" to FileCategory.DOCUMENT, "rtf" to FileCategory.DOCUMENT, "txt" to FileCategory.DOCUMENT, 
        "md" to FileCategory.DOCUMENT, "epub" to FileCategory.DOCUMENT, "mobi" to FileCategory.DOCUMENT, 
        "djvu" to FileCategory.DOCUMENT, "tex" to FileCategory.DOCUMENT,
        
        "png" to FileCategory.IMAGE, "jpg" to FileCategory.IMAGE, "jpeg" to FileCategory.IMAGE, 
        "gif" to FileCategory.IMAGE, "bmp" to FileCategory.IMAGE, "svg" to FileCategory.IMAGE, 
        "webp" to FileCategory.IMAGE, "ico" to FileCategory.IMAGE, "tiff" to FileCategory.IMAGE, 
        "tif" to FileCategory.IMAGE, "heic" to FileCategory.IMAGE, "heif" to FileCategory.IMAGE, 
        "avif" to FileCategory.IMAGE, "raw" to FileCategory.IMAGE, "cr2" to FileCategory.IMAGE, 
        "nef" to FileCategory.IMAGE, "psd" to FileCategory.IMAGE, "ai" to FileCategory.IMAGE,
        
        "mp3" to FileCategory.AUDIO, "flac" to FileCategory.AUDIO, "wav" to FileCategory.AUDIO, 
        "aac" to FileCategory.AUDIO, "ogg" to FileCategory.AUDIO, "opus" to FileCategory.AUDIO, 
        "m4a" to FileCategory.AUDIO, "wma" to FileCategory.AUDIO, "alac" to FileCategory.AUDIO, 
        "ape" to FileCategory.AUDIO, "mka" to FileCategory.AUDIO, "mid" to FileCategory.AUDIO, 
        "midi" to FileCategory.AUDIO,
        
        "mp4" to FileCategory.VIDEO, "mkv" to FileCategory.VIDEO, "avi" to FileCategory.VIDEO, 
        "mov" to FileCategory.VIDEO, "webm" to FileCategory.VIDEO, "flv" to FileCategory.VIDEO, 
        "wmv" to FileCategory.VIDEO, "m4v" to FileCategory.VIDEO, "3gp" to FileCategory.VIDEO, 
        "mpg" to FileCategory.VIDEO, "mpeg" to FileCategory.VIDEO, "ts" to FileCategory.VIDEO, 
        "vob" to FileCategory.VIDEO, "ogv" to FileCategory.VIDEO,
        
        "py" to FileCategory.CODE, "kt" to FileCategory.CODE, "kts" to FileCategory.CODE, 
        "java" to FileCategory.CODE, "js" to FileCategory.CODE, "jsx" to FileCategory.CODE, 
        "ts" to FileCategory.CODE, "tsx" to FileCategory.CODE, "rs" to FileCategory.CODE, 
        "go" to FileCategory.CODE, "c" to FileCategory.CODE, "cpp" to FileCategory.CODE, 
        "h" to FileCategory.CODE, "hpp" to FileCategory.CODE, "rb" to FileCategory.CODE, 
        "swift" to FileCategory.CODE, "cs" to FileCategory.CODE, "php" to FileCategory.CODE, 
        "sh" to FileCategory.CODE, "bash" to FileCategory.CODE, "zsh" to FileCategory.CODE, 
        "lua" to FileCategory.CODE, "r" to FileCategory.CODE, "scala" to FileCategory.CODE, 
        "dart" to FileCategory.CODE, "zig" to FileCategory.CODE, "nim" to FileCategory.CODE, 
        "hs" to FileCategory.CODE, "ml" to FileCategory.CODE, "clj" to FileCategory.CODE, 
        "ex" to FileCategory.CODE, "exs" to FileCategory.CODE, "erl" to FileCategory.CODE, 
        "pl" to FileCategory.CODE, "pm" to FileCategory.CODE,
        
        "json" to FileCategory.DATA, "csv" to FileCategory.DATA, "xml" to FileCategory.DATA, 
        "yaml" to FileCategory.DATA, "yml" to FileCategory.DATA, "sql" to FileCategory.DATA, 
        "db" to FileCategory.DATA, "sqlite" to FileCategory.DATA, "sqlite3" to FileCategory.DATA, 
        "toml" to FileCategory.DATA, "ini" to FileCategory.DATA, "cfg" to FileCategory.DATA, 
        "conf" to FileCategory.DATA, "log" to FileCategory.DATA, "tsv" to FileCategory.DATA, 
        "parquet" to FileCategory.DATA, "avro" to FileCategory.DATA,
        
        "ttf" to FileCategory.FONT, "otf" to FileCategory.FONT, "woff" to FileCategory.FONT, 
        "woff2" to FileCategory.FONT, "eot" to FileCategory.FONT
    )

    /**
     * Classifies a file extension into a [FileCategory].
     * @param extension File extension without the leading dot (e.g., "zip", "pdf").
     */
    fun fromExtension(extension: String): FileCategory {
        return extensionMap[extension.lowercase()] ?: FileCategory.UNKNOWN
    }

    /**
     * Classifies a MIME type into a [FileCategory].
     * @param mimeType MIME type string (e.g., "application/pdf", "image/png").
     */
    fun fromMimeType(mimeType: String): FileCategory {
        val lowerMime = mimeType.lowercase().substringBefore(";")
        if (lowerMime == "application/octet-stream") return FileCategory.UNKNOWN
        
        if (lowerMime.startsWith("image/")) return FileCategory.IMAGE
        if (lowerMime.startsWith("audio/")) return FileCategory.AUDIO
        if (lowerMime.startsWith("video/")) return FileCategory.VIDEO
        if (lowerMime.startsWith("text/")) return FileCategory.DOCUMENT
        if (lowerMime.startsWith("font/")) return FileCategory.FONT
        
        if (lowerMime == "application/pdf") return FileCategory.DOCUMENT
        if (lowerMime == "application/zip" || lowerMime.startsWith("application/x-rar") || 
            lowerMime == "application/x-tar" || lowerMime == "application/gzip") return FileCategory.ARCHIVE
            
        if (lowerMime == "application/json" || lowerMime == "application/xml" || lowerMime == "text/csv") return FileCategory.DATA
        
        return FileCategory.UNKNOWN
    }

    /**
     * Extracts the file extension from a URL path.
     * Handles query strings and fragments correctly.
     * @return Extension without dot, or null if none found.
     */
    fun extractExtensionFromUrl(url: String): String? {
        val fileName = extractFileNameFromUrl(url) ?: return null
        val lastDotIndex = fileName.lastIndexOf('.')
        return if (lastDotIndex > 0 && lastDotIndex < fileName.length - 1) {
            fileName.substring(lastDotIndex + 1)
        } else {
            null
        }
    }

    /**
     * Extracts the filename from a URL path.
     * @return Decoded filename, or null if the URL has no path component.
     */
    fun extractFileNameFromUrl(url: String): String? {
        return try {
            val uri = URI(url)
            val path = uri.path
            if (path.isNullOrEmpty() || path == "/") null
            else {
                val fileName = path.substringAfterLast('/')
                if (fileName.isNotEmpty()) URLDecoder.decode(fileName, "UTF-8") else null
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts the filename from a Content-Disposition header.
     * Supports both `filename="..."` and `filename*=UTF-8''...` forms.
     */
    fun extractFileNameFromContentDisposition(header: String): String? {
        val utf8Match = Regex("filename\\*=UTF-8''([^;\\s]+)").find(header)
        if (utf8Match != null) {
            return try {
                URLDecoder.decode(utf8Match.groupValues[1], "UTF-8")
            } catch (e: Exception) {
                null
            }
        }
        
        val standardMatch = Regex("filename=\"([^\"]+)\"").find(header)
        if (standardMatch != null) {
            return standardMatch.groupValues[1]
        }
        
        val fallbackMatch = Regex("filename=([^;\\s]+)").find(header)
        if (fallbackMatch != null) {
            return fallbackMatch.groupValues[1]
        }
        
        return null
    }
}
