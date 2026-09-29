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

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class Aria2DownloadEngine @Inject constructor(
    private val processManager: Aria2ProcessManager,
    private val rpcClient: Aria2RpcClient
) {
    suspend fun startDownload(url: String, fileName: String?, options: Map<String, String> = emptyMap()): String {
        processManager.ensureRunning()
        val finalOptions = options.toMutableMap()
        if (!fileName.isNullOrBlank()) {
            finalOptions["out"] = fileName
        }
        finalOptions.putIfAbsent("seed-time", "0")
        finalOptions.putIfAbsent("seed-ratio", "0.0")
        return try {
            rpcClient.addUri(listOf(url), finalOptions)
        } catch (e: Aria2RpcException) {
            if (e.code == 12 || e.message?.contains("already registered", ignoreCase = true) == true) {
                val existingGid = findExistingGid(e.message)
                existingGid ?: throw e
            } else {
                throw e
            }
        }
    }

    private suspend fun findExistingGid(errorMessage: String?): String? {
        val hashRegex = Regex("InfoHash\\s+([0-9a-fA-F]+)", RegexOption.IGNORE_CASE)
        val match = errorMessage?.let { hashRegex.find(it) }
        val targetHash = match?.groupValues?.getOrNull(1)?.lowercase()

        val allTasks = mutableListOf<Aria2Status>()
        try { allTasks.addAll(rpcClient.tellActive()) } catch (ignored: Exception) {}
        try { allTasks.addAll(rpcClient.tellWaiting(0, 100)) } catch (ignored: Exception) {}
        try { allTasks.addAll(rpcClient.tellStopped(0, 100)) } catch (ignored: Exception) {}

        val existing = allTasks.firstOrNull { task ->
            targetHash != null && task.infoHash?.equals(targetHash, ignoreCase = true) == true
        }
        if (existing != null) {
            if (existing.isPaused) {
                try { rpcClient.unpause(existing.gid) } catch (ignored: Exception) {}
            }
            return existing.gid
        }
        return null
    }

    fun pollProgress(initialGid: String, intervalMs: Long = 500L): Flow<Aria2Status> = flow {
        var currentGid = initialGid
        var lastKnownStatus: Aria2Status? = null
        while (true) {
            try {
                val status = rpcClient.tellStatus(currentGid)
                lastKnownStatus = status

                if (!status.followedBy.isNullOrEmpty()) {
                    currentGid = status.followedBy.first()
                    continue
                }

                emit(status)
                if (status.isComplete || status.isError || status.isRemoved) {
                    break
                }
            } catch (e: Exception) {
                // If RPC fails, emit last known or throw if none
                if (lastKnownStatus != null) {
                    emit(lastKnownStatus)
                } else {
                    throw e
                }
            }
            delay(intervalMs)
        }
    }

    suspend fun pauseDownload(gid: String) {
        processManager.ensureRunning()
        try {
            rpcClient.pause(gid)
        } catch (e: Exception) {
            // Task might have finished or been removed in aria2
        }
    }

    suspend fun resumeDownload(gid: String) {
        processManager.ensureRunning()
        try {
            rpcClient.unpause(gid)
        } catch (e: Exception) {
            // Task might have finished or been removed in aria2
        }
    }

    suspend fun cancelDownload(gid: String) {
        processManager.ensureRunning()
        try {
            rpcClient.forceRemove(gid)
        } catch (e: Exception) {
            try {
                rpcClient.remove(gid)
            } catch (ignored: Exception) {}
        }
        try {
            rpcClient.removeDownloadResult(gid)
        } catch (ignored: Exception) {}
    }

    suspend fun getStatus(gid: String): Aria2Status {
        processManager.ensureRunning()
        return rpcClient.tellStatus(gid)
    }

    suspend fun ensureReady() {
        processManager.initialize()
        processManager.ensureRunning()
    }

    suspend fun addTorrentDownload(torrentBase64: String, selectedFileIndexes: List<Int>? = null, options: Map<String, String> = emptyMap()): String {
        processManager.ensureRunning()
        val finalOptions = options.toMutableMap()
        if (selectedFileIndexes != null && selectedFileIndexes.isNotEmpty()) {
            finalOptions["select-file"] = selectedFileIndexes.joinToString(",")
        }
        finalOptions.putIfAbsent("seed-time", "0")
        finalOptions.putIfAbsent("seed-ratio", "0.0")
        return try {
            rpcClient.addTorrent(torrentBase64, emptyList(), finalOptions)
        } catch (e: Aria2RpcException) {
            if (e.code == 12 || e.message?.contains("already registered", ignoreCase = true) == true) {
                val existingGid = findExistingGid(e.message)
                existingGid ?: throw e
            } else {
                throw e
            }
        }
    }

    suspend fun changeSelectedFiles(gid: String, selectedIndexes: List<Int>) {
        rpcClient.changeOption(gid, mapOf("select-file" to selectedIndexes.joinToString(",")))
    }

    suspend fun resolveTorrentFileMetadata(torrentBase64: String): MagnetResolutionState {
        return try {
            processManager.ensureRunning()
            // Add torrent in paused state to inspect metadata and file tree
            val gid = rpcClient.addTorrent(torrentBase64, emptyList(), mapOf("pause" to "true"))
            val status = rpcClient.tellStatus(gid)
            val readyState = MagnetResolutionState.Ready(
                infoHash = status.infoHash ?: "",
                name = status.bittorrent?.name?.ifBlank { "Torrent Download" } ?: "Torrent Download",
                totalSize = status.totalLength,
                files = status.files,
                torrentBase64 = torrentBase64
            )
            // Clean up the temporary paused inspection task
            try {
                rpcClient.forceRemove(gid)
            } catch (e: Exception) {
                try { rpcClient.remove(gid) } catch (e2: Exception) { }
            }
            readyState
        } catch (e: Exception) {
            MagnetResolutionState.Error(e.message ?: "Failed to parse torrent file")
        }
    }

    fun resolveMagnetMetadata(magnetUrl: String): Flow<MagnetResolutionState> = flow {
        try {
            processManager.ensureRunning()
            val enhancedMagnet = com.glypdl.android.util.MagnetHelper.injectTrackers(magnetUrl)
            val gid = rpcClient.addUri(
                listOf(enhancedMagnet),
                mapOf(
                    "bt-metadata-only" to "true",
                    "bt-save-metadata" to "true",
                    "bt-tracker-connect-timeout" to "5",
                    "bt-tracker-timeout" to "5"
                )
            )
            
            var elapsed = 0
            while (true) {
                val status = rpcClient.tellStatus(gid)
                
                if (status.status == "complete" || status.followedBy != null) {
                    val actualGid = status.followedBy?.firstOrNull() ?: gid
                    val actualStatus = if (actualGid != gid) rpcClient.tellStatus(actualGid) else status
                    val torrentFile = java.io.File(processManager.getStagingDir(), "${actualStatus.infoHash}.torrent")
                    if (torrentFile.exists()) {
                        val torrentBase64 = android.util.Base64.encodeToString(torrentFile.readBytes(), android.util.Base64.NO_WRAP)
                        emit(
                            MagnetResolutionState.Ready(
                                infoHash = actualStatus.infoHash ?: "",
                                name = actualStatus.bittorrent?.name ?: "",
                                totalSize = actualStatus.totalLength,
                                files = actualStatus.files,
                                torrentBase64 = torrentBase64
                            )
                        )
                        rpcClient.remove(actualGid)
                    } else {
                        emit(MagnetResolutionState.Error("Metadata saved but .torrent file missing"))
                    }
                    if (actualGid != gid) {
                        try { rpcClient.remove(gid) } catch (e: Exception) { }
                    }
                    break
                } else if (status.status == "error") {
                    emit(MagnetResolutionState.Error(status.errorMessage ?: "Unknown error"))
                    break
                }
                
                emit(MagnetResolutionState.Resolving(status.numSeeders, status.connections, elapsed))
                delay(1000L)
                elapsed++
            }
        } catch (e: Exception) {
            emit(MagnetResolutionState.Error(e.message ?: "Unknown error"))
        }
    }
}

sealed interface MagnetResolutionState {
    data class Resolving(val seeds: Int, val peers: Int, val elapsedSeconds: Int) : MagnetResolutionState
    data class Ready(
        val infoHash: String,
        val name: String,
        val totalSize: Long,
        val files: List<Aria2File>,
        val torrentBase64: String
    ) : MagnetResolutionState
    data class Error(val message: String) : MagnetResolutionState
}
