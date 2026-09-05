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
package com.glypdl.android.ui.analyze

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.model.AudioQuality
import com.glypdl.android.data.model.DownloadRequest
import com.glypdl.android.data.model.GlypdlError
import com.glypdl.android.data.model.GlypdlException
import com.glypdl.android.data.model.MediaAnalysisResult
import com.glypdl.android.data.model.MediaFormat
import com.glypdl.android.data.model.MediaInfo
import com.glypdl.android.data.model.PlaylistInfo
import com.glypdl.android.data.model.VideoQuality
import com.glypdl.android.domain.usecase.AnalyzeUrlUseCase
import com.glypdl.android.domain.usecase.DownloadMediaUseCase
import com.glypdl.android.service.YtDlpService
import com.glypdl.android.service.engine.YtDlpErrorParser
import com.glypdl.android.util.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.util.UUID
import javax.inject.Inject

enum class SimplifiedMediaChoice(
    val label: String,
    val description: String,
    val formatSelector: String,
    val ext: String
) {
    VIDEO_AND_AUDIO("Video + Audio", "Best video stream with sound", "bestvideo+bestaudio/best", "mp4"),
    VIDEO_ONLY("Video", "Video stream only", "bestvideo/best", "mp4"),
    AUDIO_ONLY("Audio", "Audio stream only", "bestaudio/best", "m4a")
}

typealias InstagramDownloadChoice = SimplifiedMediaChoice

sealed interface AnalyzeUiState {
    data class Loading(val isPlaylist: Boolean = false) : AnalyzeUiState
    data class Success(
        val mediaInfo: MediaInfo,
        val selectedFormat: MediaFormat? = null,
        val selectedVideoQuality: VideoQuality? = null,
        val selectedAudioQuality: AudioQuality? = null,
        val isAudioOnly: Boolean = false,
        val videoFormats: List<MediaFormat> = emptyList(),
        val audioFormats: List<MediaFormat> = emptyList(),
        val isInstagram: Boolean = false,
        val isFacebook: Boolean = false,
        val instagramChoice: SimplifiedMediaChoice = SimplifiedMediaChoice.VIDEO_AND_AUDIO
    ) : AnalyzeUiState {
        val isSimplifiedPlatform: Boolean get() = isInstagram || isFacebook
        val estimatedTotalBytes: Long?
            get() {
                if (isSimplifiedPlatform) return null
                if (isAudioOnly) return selectedAudioQuality?.estimatedSize
                val vSize = selectedVideoQuality?.estimatedSize
                val aSize = selectedAudioQuality?.estimatedSize
                return if (vSize != null && aSize != null) {
                    vSize + aSize
                } else vSize ?: aSize
            }
    }
    data class PlaylistSuccess(
        val playlistInfo: PlaylistInfo,
        val selectedItemIds: Set<String> = emptySet(),
        val isAudioOnly: Boolean = false,
        val preferredQuality: String = "best"
    ) : AnalyzeUiState {
        val selectedCount: Int get() = selectedItemIds.size
        val totalCount: Int get() = playlistInfo.totalCount
        val isAllSelected: Boolean get() = selectedItemIds.size == playlistInfo.entries.size && playlistInfo.entries.isNotEmpty()
    }
    data class Error(
        val error: GlypdlError,
        val isUpdatingEngine: Boolean = false,
        val updateError: String? = null
    ) : AnalyzeUiState {
        val message: String get() = error.userMessage
    }
}

@HiltViewModel
class AnalyzeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val analyzeUrlUseCase: AnalyzeUrlUseCase,
    private val downloadMediaUseCase: DownloadMediaUseCase,
    private val ytDlpService: YtDlpService
) : ViewModel() {

    private val urlArg: String? = savedStateHandle.get<String>("url")
    private var currentUrl: String? = null
    private var analysisProcessId: String? = null
    private var analysisJob: Job? = null

    private val _uiState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Loading(isPlaylist = false))
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    init {
        val decoded = urlArg?.let {
            try {
                URLDecoder.decode(it, "UTF-8")
            } catch (e: Exception) {
                it
            }
        }
        currentUrl = decoded
        analyze(decoded)
    }

    val targetUrl: String get() = currentUrl ?: ""

    fun retry() {
        analyze(currentUrl)
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisProcessId?.let { pid ->
            ytDlpService.cancelProcess(pid)
        }
        _uiState.value = AnalyzeUiState.Error(
            GlypdlError.TimedOut("Analysis was cancelled by user.")
        )
    }

    private fun analyze(url: String?) {
        if (url.isNullOrBlank()) {
            _uiState.value = AnalyzeUiState.Error(
                GlypdlError.InvalidUrl("No URL was provided for analysis.")
            )
            return
        }

        val isPlaylist = ytDlpService.isPlaylist(url)
        _uiState.value = AnalyzeUiState.Loading(isPlaylist = isPlaylist)

        analysisProcessId = UUID.randomUUID().toString()
        val processId = analysisProcessId

        analysisJob = viewModelScope.launch {
            val result = analyzeUrlUseCase(url, processId)
            result.onSuccess { analysisResult ->
                when (analysisResult) {
                    is MediaAnalysisResult.SingleMedia -> {
                        val mediaInfo = analysisResult.mediaInfo
                        val videoFormats = mediaInfo.formats.filter { it.isVideoOnly || (it.vcodec != null && it.vcodec != "none") }
                        val audioFormats = mediaInfo.formats.filter { it.isAudioOnly || (it.acodec != null && it.vcodec == "none") }
                        val defaultVideoQuality = mediaInfo.videoQualities.firstOrNull()
                        val defaultAudioQuality = mediaInfo.audioQualities.firstOrNull()

                        val isInstagram = UrlValidator.isInstagramUrl(mediaInfo.url)
                        val isFacebook = UrlValidator.isFacebookUrl(mediaInfo.url)

                        _uiState.value = AnalyzeUiState.Success(
                            mediaInfo = mediaInfo,
                            selectedFormat = null,
                            selectedVideoQuality = defaultVideoQuality,
                            selectedAudioQuality = defaultAudioQuality,
                            isAudioOnly = false,
                            videoFormats = videoFormats,
                            audioFormats = audioFormats,
                            isInstagram = isInstagram,
                            isFacebook = isFacebook,
                            instagramChoice = SimplifiedMediaChoice.VIDEO_AND_AUDIO
                        )
                    }
                    is MediaAnalysisResult.Playlist -> {
                        val playlist = analysisResult.playlistInfo
                        val initialSelected = playlist.entries.filter { it.isAvailable }.map { it.id }.toSet()
                        _uiState.value = AnalyzeUiState.PlaylistSuccess(
                            playlistInfo = playlist,
                            selectedItemIds = initialSelected,
                            isAudioOnly = false
                        )
                    }
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) return@launch
                val error = when (throwable) {
                    is GlypdlException -> throwable.error
                    else -> YtDlpErrorParser.parse(throwable.message, ytDlpService.getInstalledVersion())
                }
                _uiState.value = AnalyzeUiState.Error(error = error)
            }
        }
    }

    fun updateEngineAndRetry() {
        val currentState = _uiState.value
        if (currentState is AnalyzeUiState.Error) {
            _uiState.value = currentState.copy(isUpdatingEngine = true, updateError = null)
        }

        viewModelScope.launch {
            val updateResult = ytDlpService.updateYtDlp()
            updateResult.onSuccess {
                analyze(currentUrl)
            }.onFailure { e ->
                val errorMsg = e.message ?: "Failed to update download engine."
                val cur = _uiState.value
                if (cur is AnalyzeUiState.Error) {
                    _uiState.value = cur.copy(isUpdatingEngine = false, updateError = errorMsg)
                }
            }
        }
    }

    fun setAudioOnly(audioOnly: Boolean) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(isAudioOnly = audioOnly)
            } else state
        }
    }

    fun onVideoQualitySelected(quality: VideoQuality) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(
                    selectedVideoQuality = quality,
                    isAudioOnly = false
                )
            } else state
        }
    }

    fun onAudioQualitySelected(quality: AudioQuality) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(selectedAudioQuality = quality)
            } else state
        }
    }

    fun onInstagramChoiceSelected(choice: InstagramDownloadChoice) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(
                    instagramChoice = choice,
                    isAudioOnly = choice == InstagramDownloadChoice.AUDIO_ONLY
                )
            } else state
        }
    }

    fun onFormatSelected(format: MediaFormat) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(selectedFormat = format)
            } else state
        }
    }

    fun togglePlaylistItem(itemId: String) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.PlaylistSuccess) {
                val updated = state.selectedItemIds.toMutableSet().apply {
                    if (contains(itemId)) remove(itemId) else add(itemId)
                }
                state.copy(selectedItemIds = updated)
            } else state
        }
    }

    fun selectAllPlaylistItems() {
        _uiState.update { state ->
            if (state is AnalyzeUiState.PlaylistSuccess) {
                state.copy(selectedItemIds = state.playlistInfo.entries.map { it.id }.toSet())
            } else state
        }
    }

    fun deselectAllPlaylistItems() {
        _uiState.update { state ->
            if (state is AnalyzeUiState.PlaylistSuccess) {
                state.copy(selectedItemIds = emptySet())
            } else state
        }
    }

    fun setPlaylistAudioOnly(audioOnly: Boolean) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.PlaylistSuccess) {
                state.copy(isAudioOnly = audioOnly)
            } else state
        }
    }

    fun downloadSelectedPlaylist() {
        val currentState = _uiState.value
        if (currentState is AnalyzeUiState.PlaylistSuccess) {
            val selectedItems = currentState.playlistInfo.entries.filter { it.id in currentState.selectedItemIds }
            val isAudio = currentState.isAudioOnly
            val formatId = if (isAudio) "bestaudio/best" else "bestvideo+bestaudio/best"
            val ext = if (isAudio) "m4a" else "mp4"

            viewModelScope.launch {
                selectedItems.forEach { item ->
                    val request = DownloadRequest(
                        id = UUID.randomUUID().toString(),
                        url = item.url,
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        formatId = formatId,
                        ext = ext,
                        resolution = if (isAudio) "Audio (Best)" else "Video (Best)",
                        isAudioOnly = isAudio,
                        destinationUri = null
                    )
                    downloadMediaUseCase(request)
                }
            }
        }
    }

    fun startDownload() {
        val currentState = _uiState.value
        if (currentState is AnalyzeUiState.Success) {
            val mediaInfo = currentState.mediaInfo

            val request = if (currentState.isSimplifiedPlatform) {
                val choice = currentState.instagramChoice
                val isAudio = choice == SimplifiedMediaChoice.AUDIO_ONLY
                DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = mediaInfo.url,
                    title = mediaInfo.title,
                    thumbnailUrl = mediaInfo.thumbnail,
                    formatId = choice.formatSelector,
                    ext = choice.ext,
                    resolution = choice.label,
                    isAudioOnly = isAudio,
                    destinationUri = null
                )
            } else if (currentState.isAudioOnly && currentState.selectedAudioQuality != null) {
                val audio = currentState.selectedAudioQuality
                DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = mediaInfo.url,
                    title = mediaInfo.title,
                    thumbnailUrl = mediaInfo.thumbnail,
                    formatId = audio.formatSelector,
                    ext = audio.extension,
                    resolution = audio.displayBitrate,
                    isAudioOnly = true,
                    destinationUri = null
                )
            } else if (currentState.selectedVideoQuality != null) {
                val video = currentState.selectedVideoQuality
                val audio = currentState.selectedAudioQuality
                val formatId = if (audio != null && video.formatId.isNotBlank() && audio.formatId.isNotBlank()) {
                    "${video.formatId}+${audio.formatId}"
                } else {
                    video.formatSelector
                }
                val resLabel = if (audio != null) "${video.label} + ${audio.displayBitrate}" else video.label
                DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = mediaInfo.url,
                    title = mediaInfo.title,
                    thumbnailUrl = mediaInfo.thumbnail,
                    formatId = formatId,
                    ext = video.ext.ifBlank { "mp4" },
                    resolution = resLabel,
                    isAudioOnly = false,
                    destinationUri = null
                )
            } else if (currentState.selectedFormat != null) {
                val format = currentState.selectedFormat
                DownloadRequest(
                    id = UUID.randomUUID().toString(),
                    url = mediaInfo.url,
                    title = mediaInfo.title,
                    thumbnailUrl = mediaInfo.thumbnail,
                    formatId = format.formatId,
                    ext = format.ext,
                    resolution = format.resolution,
                    isAudioOnly = format.isAudioOnly,
                    destinationUri = null
                )
            } else {
                return
            }

            viewModelScope.launch {
                downloadMediaUseCase(request)
            }
        }
    }
}
