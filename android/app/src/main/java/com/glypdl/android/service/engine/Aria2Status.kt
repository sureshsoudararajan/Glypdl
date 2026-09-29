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

package com.glypdl.android.service.engine

/**
 * Represents the status of an aria2c download task as returned by the
 * `aria2.tellStatus` JSON-RPC method.
 */
data class Aria2Status(
    val gid: String,
    val status: String,          // "active", "waiting", "paused", "error", "complete", "removed"
    val totalLength: Long,
    val completedLength: Long,
    val downloadSpeed: Long,     // bytes/sec
    val uploadSpeed: Long,       // bytes/sec
    val connections: Int,
    val numSeeders: Int,
    val numPieces: Int,
    val pieceLength: Long,
    val bitfield: String?,       // hex string for piece visualization
    val files: List<Aria2File>,
    val errorCode: String?,
    val errorMessage: String?,
    val dir: String,
    val followedBy: List<String>?,
    val infoHash: String?,
    val bittorrent: Aria2BtInfo?
) {
    val isActive: Boolean get() = status == "active"
    val isPaused: Boolean get() = status == "paused"
    val isComplete: Boolean get() = (status == "complete" && followedBy.isNullOrEmpty()) || (!isPaused && totalLength > 0 && completedLength >= totalLength && completedLength > 0)
    val isError: Boolean get() = status == "error"
    val isWaiting: Boolean get() = status == "waiting"
    val isRemoved: Boolean get() = status == "removed"
    val progressPercent: Float get() = if (totalLength > 0) (completedLength.toFloat() / totalLength * 100f).coerceAtMost(100f) else 0f
}

/**
 * Represents a file inside an aria2c download.
 */
data class Aria2File(
    val index: Int,
    val path: String,
    val length: Long,
    val completedLength: Long,
    val selected: Boolean,
    val uris: List<Aria2Uri>
)

/**
 * Represents a URI for an aria2c file.
 */
data class Aria2Uri(
    val uri: String,
    val status: String  // "used" or "waiting"
)

/**
 * BitTorrent-specific metadata from aria2c.
 * Only present for torrent/magnet downloads.
 */
data class Aria2BtInfo(
    val announceList: List<List<String>>?,
    val comment: String?,
    val creationDate: Long?,
    val mode: String?,          // "single" or "multi"
    val name: String?
)
