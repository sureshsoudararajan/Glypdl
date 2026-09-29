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

package com.glypdl.android.data.model

/**
 * Represents the category of the file being downloaded.
 */
enum class FileCategory {
    /** .zip, .rar, .7z, .tar, .gz, .bz2, .xz, .tar.gz, .tar.bz2, .tar.xz */
    ARCHIVE,
    
    /** .iso, .img, .dmg */
    DISK_IMAGE,
    
    /** .apk, .exe, .deb, .rpm, .msi, .appimage, .flatpak, .snap */
    APPLICATION,
    
    /** .pdf, .docx, .xlsx, .pptx, .txt, .odt, .ods, .odp, .epub, .md */
    DOCUMENT,
    
    /** .png, .jpg, .jpeg, .gif, .svg, .webp, .bmp, .ico, .tiff */
    IMAGE,
    
    /** .mp3, .flac, .wav, .aac, .ogg, .opus, .m4a, .wma */
    AUDIO,
    
    /** .mp4, .mkv, .avi, .mov, .webm, .flv, .wmv, .m4v, .3gp */
    VIDEO,
    
    /** .py, .kt, .java, .js, .ts, .rs, .go, .c, .cpp, .h, .rb, .swift */
    CODE,
    
    /** .json, .csv, .xml, .yaml, .yml, .sql, .db, .sqlite */
    DATA,
    
    /** .ttf, .otf, .woff, .woff2, .eot */
    FONT,
    
    /** Fallback for unrecognized file types */
    UNKNOWN
}
