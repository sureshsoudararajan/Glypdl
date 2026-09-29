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

package com.glypdl.android.ui.torrent

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.DownloadType
import com.glypdl.android.data.model.EngineType
import com.glypdl.android.service.DownloadManager
import com.glypdl.android.service.engine.Aria2DownloadEngine
import com.glypdl.android.service.engine.Aria2File
import com.glypdl.android.service.engine.MagnetResolutionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

sealed interface TorrentPreviewUiState {
    data class Resolving(val seeds: Int, val peers: Int) : TorrentPreviewUiState
    data class Ready(
        val title: String,
        val totalSize: Long,
        val files: List<Aria2File>,
        val selectedIndices: Set<Int>,
        val totalSelectedSize: Long,
        val torrentBase64: String? = null
    ) : TorrentPreviewUiState
    data class Error(val message: String) : TorrentPreviewUiState
}

@HiltViewModel
class TorrentPreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val aria2DownloadEngine: Aria2DownloadEngine,
    private val downloadManager: DownloadManager,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {
    
    private val url: String = java.net.URLDecoder.decode(savedStateHandle.get<String>("url") ?: "", "UTF-8")

    private val _uiState = MutableStateFlow<TorrentPreviewUiState>(TorrentPreviewUiState.Resolving(0, 0))
    val uiState: StateFlow<TorrentPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            if (url.startsWith("magnet:")) {
                aria2DownloadEngine.resolveMagnetMetadata(url).collect { state ->
                    when (state) {
                        is MagnetResolutionState.Resolving -> {
                            _uiState.value = TorrentPreviewUiState.Resolving(state.seeds, state.peers)
                        }
                        is MagnetResolutionState.Ready -> {
                            val selected = state.files.map { it.index }.toSet()
                            _uiState.value = TorrentPreviewUiState.Ready(
                                title = state.name.ifBlank { "Unknown Torrent" },
                                totalSize = state.totalSize,
                                files = state.files,
                                selectedIndices = selected,
                                totalSelectedSize = state.files.sumOf { it.length },
                                torrentBase64 = state.torrentBase64
                            )
                        }
                        is MagnetResolutionState.Error -> {
                            _uiState.value = TorrentPreviewUiState.Error(state.message)
                        }
                    }
                }
            } else {
                try {
                    val torrentBase64 = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        if (url.startsWith("torrent-base64:", ignoreCase = true)) {
                            url.removePrefix("torrent-base64:")
                        } else if (url.startsWith("content://", ignoreCase = true)) {
                            val uri = android.net.Uri.parse(url)
                            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                                ?: throw IllegalArgumentException("Could not read torrent file from content URI")
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        } else if (url.startsWith("file://", ignoreCase = true)) {
                            val path = url.removePrefix("file://")
                            val file = java.io.File(path)
                            if (!file.exists()) throw IllegalArgumentException("Torrent file not found: $path")
                            android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                        } else if (url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)) {
                            val bytes = fetchTorrentBytesFromHttp(url)
                            android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                        } else {
                            val file = java.io.File(url)
                            if (file.exists()) {
                                android.util.Base64.encodeToString(file.readBytes(), android.util.Base64.NO_WRAP)
                            } else {
                                throw IllegalArgumentException("Unsupported torrent source: $url")
                            }
                        }
                    }

                    val state = aria2DownloadEngine.resolveTorrentFileMetadata(torrentBase64)
                    when (state) {
                        is MagnetResolutionState.Ready -> {
                            val selected = state.files.map { it.index }.toSet()
                            _uiState.value = TorrentPreviewUiState.Ready(
                                title = state.name.ifBlank { "Torrent Download" },
                                totalSize = state.totalSize,
                                files = state.files,
                                selectedIndices = selected,
                                totalSelectedSize = state.files.sumOf { it.length },
                                torrentBase64 = state.torrentBase64
                            )
                        }
                        is MagnetResolutionState.Error -> {
                            _uiState.value = TorrentPreviewUiState.Error(state.message)
                        }
                        else -> {
                            _uiState.value = TorrentPreviewUiState.Error("Failed to parse torrent metadata")
                        }
                    }
                } catch (e: SecurityException) {
                    _uiState.value = TorrentPreviewUiState.Error("Permission denied reading torrent file. Please select the .torrent file directly using the file picker in Glypdl.")
                } catch (e: Exception) {
                    _uiState.value = TorrentPreviewUiState.Error(e.message ?: "Failed to load torrent file")
                }
            }
        }
    }

    fun toggleFile(index: Int) {
        val currentState = _uiState.value as? TorrentPreviewUiState.Ready ?: return
        val currentSelected = currentState.selectedIndices.toMutableSet()
        if (currentSelected.contains(index)) {
            currentSelected.remove(index)
        } else {
            currentSelected.add(index)
        }
        val selectedSize = currentState.files.filter { currentSelected.contains(it.index) }.sumOf { it.length }
        _uiState.value = currentState.copy(selectedIndices = currentSelected, totalSelectedSize = selectedSize)
    }

    fun selectAll() {
        val currentState = _uiState.value as? TorrentPreviewUiState.Ready ?: return
        val allIndices = currentState.files.map { it.index }.toSet()
        val totalSize = currentState.files.sumOf { it.length }
        _uiState.value = currentState.copy(selectedIndices = allIndices, totalSelectedSize = totalSize)
    }

    fun deselectAll() {
        val currentState = _uiState.value as? TorrentPreviewUiState.Ready ?: return
        _uiState.value = currentState.copy(selectedIndices = emptySet(), totalSelectedSize = 0L)
    }

    fun startDownload(onDownloadEnqueued: () -> Unit) {
        val currentState = _uiState.value as? TorrentPreviewUiState.Ready ?: return
        if (currentState.selectedIndices.isEmpty()) return
        
        viewModelScope.launch {
            val downloadId = UUID.randomUUID().toString()
            val request = DownloadRequest(
                id = downloadId,
                url = if (!currentState.torrentBase64.isNullOrBlank()) {
                    "torrent-base64:${currentState.torrentBase64}"
                } else url,
                title = currentState.title,
                thumbnailUrl = null,
                formatId = currentState.selectedIndices.sorted().joinToString(","),
                ext = "torrent",
                resolution = null,
                isAudioOnly = false,
                destinationUri = null,
                downloadType = DownloadType.TORRENT,
                engineType = EngineType.ARIA2C,
                contentLength = currentState.totalSize,
                selectedFileIndices = currentState.selectedIndices.toList()
            )
            downloadManager.enqueueDownload(request)
            onDownloadEnqueued()
        }
    }

    fun startDirectDownload(onDownloadEnqueued: () -> Unit) {
        viewModelScope.launch {
            val downloadId = UUID.randomUUID().toString()
            val magnetTitle = if (url.startsWith("magnet:", ignoreCase = true)) {
                com.glypdl.android.util.MagnetHelper.extractDisplayName(url) ?: "Magnet Download"
            } else {
                url.substringAfterLast('/').substringBefore('?').takeIf { it.isNotBlank() } ?: "Torrent Download"
            }

            val request = DownloadRequest(
                id = downloadId,
                url = url,
                title = magnetTitle,
                thumbnailUrl = null,
                formatId = "",
                ext = "torrent",
                resolution = null,
                isAudioOnly = false,
                destinationUri = null,
                downloadType = if (url.startsWith("magnet:", ignoreCase = true)) DownloadType.MAGNET else DownloadType.TORRENT,
                engineType = EngineType.ARIA2C,
                contentLength = 0L,
                selectedFileIndices = emptyList()
            )
            downloadManager.enqueueDownload(request)
            onDownloadEnqueued()
        }
    }

    private fun fetchTorrentBytesFromHttp(httpUrl: String): ByteArray {
        var currentUrl = httpUrl
        var redirects = 0
        while (redirects < 5) {
            val conn = java.net.URL(currentUrl).openConnection() as java.net.HttpURLConnection
            conn.connectTimeout = 12000
            conn.readTimeout = 12000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36")
            conn.setRequestProperty("Accept", "application/x-bittorrent, application/octet-stream, */*")
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val location = conn.getHeaderField("Location")
                if (!location.isNullOrBlank()) {
                    currentUrl = if (location.startsWith("http://", ignoreCase = true) || location.startsWith("https://", ignoreCase = true)) {
                        location
                    } else {
                        java.net.URL(java.net.URL(currentUrl), location).toString()
                    }
                    redirects++
                    continue
                }
            }

            if (responseCode !in 200..299) {
                throw java.io.IOException("HTTP error $responseCode downloading torrent file: ${conn.responseMessage}")
            }

            val isGzip = "gzip".equals(conn.contentEncoding, ignoreCase = true)
            val inputStream = if (isGzip) java.util.zip.GZIPInputStream(conn.inputStream) else conn.inputStream
            val bytes = inputStream.use { it.readBytes() }

            if (bytes.isNotEmpty() && bytes[0] == 'd'.code.toByte()) {
                return bytes
            } else if (bytes.size > 10 && (String(bytes.take(15).toByteArray()).contains("<html", ignoreCase = true) || String(bytes.take(15).toByteArray()).contains("<!doctype", ignoreCase = true))) {
                throw IllegalArgumentException("The URL returned a web page instead of a torrent file")
            }
            return bytes
        }
        throw java.io.IOException("Too many redirects downloading torrent file")
    }
}
