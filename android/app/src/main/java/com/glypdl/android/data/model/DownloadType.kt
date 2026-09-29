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
 * Represents the type of a download.
 */
enum class DownloadType {
    /** yt-dlp (YouTube, Instagram, Facebook, TikTok, etc.) */
    MEDIA,
    
    /** aria2c HTTP/HTTPS/FTP direct file download */
    DIRECT_FILE,
    
    /** aria2c BitTorrent (.torrent file) */
    TORRENT,
    
    /** aria2c BitTorrent (magnet link) */
    MAGNET
}
