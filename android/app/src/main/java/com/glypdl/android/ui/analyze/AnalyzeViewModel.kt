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
import com.glypdl.android.data.model.SubtitleDownloadMode
import com.glypdl.android.data.model.SubtitleDownloadOptions
import com.glypdl.android.data.model.SubtitleFormatPreference
import com.glypdl.android.data.model.SubtitleTrack
import com.glypdl.android.data.model.VideoQuality
import com.glypdl.android.data.model.YouTubeAudioLanguage
import com.glypdl.android.data.model.YouTubeAudioTrack
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.domain.usecase.AnalyzeUrlUseCase
import com.glypdl.android.domain.usecase.DownloadMediaUseCase
import com.glypdl.android.service.YtDlpService
import com.glypdl.android.service.engine.YtDlpErrorParser
import com.glypdl.android.util.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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
    AUDIO_ONLY("Audio", "Audio stream only", "bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio/best", "m4a")
}

typealias InstagramDownloadChoice = SimplifiedMediaChoice

sealed interface AnalyzeUiState {
    data class Loading(val isPlaylist: Boolean = false) : AnalyzeUiState
    object StartingDownload : AnalyzeUiState
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
        val instagramChoice: SimplifiedMediaChoice = SimplifiedMediaChoice.VIDEO_AND_AUDIO,
        val selectedSubtitleLanguages: Set<String> = emptySet(),
        val subtitleDownloadMode: SubtitleDownloadMode = SubtitleDownloadMode.MEDIA_ONLY,
        val downloadAllSubtitles: Boolean = false,
        val includeManualSubtitles: Boolean = true,
        val includeAutoSubtitles: Boolean = false,
        val preferredSubtitleFormat: SubtitleFormatPreference = SubtitleFormatPreference.BEST,
        val subtitleSectionExpanded: Boolean = false,
        val selectedYouTubeAudioLanguage: YouTubeAudioLanguage? = null,
        val selectedYouTubeAudioTrack: YouTubeAudioTrack? = null,
        val isAudioLanguageSheetVisible: Boolean = false
    ) : AnalyzeUiState {
        val isSimplifiedPlatform: Boolean get() = isInstagram || isFacebook
        val hasSubtitles: Boolean get() = mediaInfo.hasSubtitles
        val totalSubtitleCount: Int get() = mediaInfo.totalSubtitleCount
        val hasMultipleAudioLanguages: Boolean get() = mediaInfo.hasMultipleAudioLanguages
        val youtubeAudioLanguages: List<YouTubeAudioLanguage> get() = mediaInfo.youtubeAudioLanguages
        val availableSubtitleTracks: List<SubtitleTrack> get() {
            val list = mutableListOf<SubtitleTrack>()
            if (includeManualSubtitles) list.addAll(mediaInfo.manualSubtitles)
            if (includeAutoSubtitles) list.addAll(mediaInfo.automaticSubtitles)
            return list
        }
        val isSubtitlesOnly: Boolean get() = subtitleDownloadMode == SubtitleDownloadMode.SUBS_ONLY

        val estimatedTotalBytes: Long?
            get() {
                if (isSubtitlesOnly) return null
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
    private val ytDlpService: YtDlpService,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val urlArg: String? = savedStateHandle.get<String>("url")
    private var currentUrl: String? = null
    private var analysisProcessId: String? = null
    private var analysisJob: Job? = null

    private val _uiState = MutableStateFlow<AnalyzeUiState>(AnalyzeUiState.Loading(isPlaylist = false))
    val uiState: StateFlow<AnalyzeUiState> = _uiState.asStateFlow()

    private val _autoDownloadTriggered = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val autoDownloadTriggered: SharedFlow<Unit> = _autoDownloadTriggered.asSharedFlow()

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

                        val askBefore = settingsRepository.askBeforeDownload.first()
                        val prefQuality = settingsRepository.defaultQuality.first()
                        val prefFormat = settingsRepository.defaultFormat.first()
                        val prefVideoCodec = settingsRepository.preferredVideoCodec.first()
                        val prefAudioFormat = settingsRepository.preferredAudioFormat.first()

                        val isAudioOnlyPref = prefQuality.equals("audio", ignoreCase = true)

                        // Select video quality matching preferences
                        val defaultVideoQuality = if (isAudioOnlyPref) {
                            null
                        } else {
                            val candidateQualities = if (prefQuality.equals("best", ignoreCase = true)) {
                                mediaInfo.videoQualities
                            } else {
                                val match = mediaInfo.videoQualities.filter {
                                    it.label.contains(prefQuality, ignoreCase = true) ||
                                    (it.height != null && "${it.height}p".contains(prefQuality, ignoreCase = true))
                                }
                                if (match.isNotEmpty()) match else mediaInfo.videoQualities
                            }

                            val codecMatch = if (!prefVideoCodec.equals("any", ignoreCase = true)) {
                                candidateQualities.filter { it.codec?.contains(prefVideoCodec, ignoreCase = true) == true }
                            } else {
                                emptyList()
                            }
                            codecMatch.firstOrNull() ?: candidateQualities.firstOrNull() ?: mediaInfo.videoQualities.firstOrNull()
                        }

                        val defaultYouTubeLanguage = mediaInfo.originalAudioLanguage ?: mediaInfo.youtubeAudioLanguages.firstOrNull()
                        val defaultYouTubeTrack = defaultYouTubeLanguage?.selectedTrack

                        // Intelligently harmonize default audio: WebM video requires Opus/Vorbis audio.
                        // MP4 video natively pairs with AAC/m4a audio.
                        val isDefaultVideoWebm = defaultVideoQuality?.ext?.equals("webm", ignoreCase = true) == true ||
                                defaultVideoQuality?.codec?.contains("vp", ignoreCase = true) == true
                        val defaultAudioQuality = if (defaultYouTubeTrack != null) {
                            AudioQuality(
                                bitrateKbps = defaultYouTubeTrack.bitrate?.toInt() ?: 128,
                                codec = defaultYouTubeTrack.codec,
                                extension = defaultYouTubeTrack.extension ?: "m4a",
                                estimatedSize = defaultYouTubeTrack.fileSize,
                                formatSelector = defaultYouTubeTrack.formatId,
                                formatId = defaultYouTubeTrack.formatId
                            )
                        } else if (isDefaultVideoWebm) {
                            mediaInfo.audioQualities.firstOrNull {
                                it.extension.equals("opus", ignoreCase = true) ||
                                        it.extension.equals("webm", ignoreCase = true) ||
                                        it.codec?.contains("opus", ignoreCase = true) == true
                            } ?: mediaInfo.audioQualities.firstOrNull()
                        } else {
                            val userPrefAudio = mediaInfo.audioQualities.firstOrNull {
                                it.extension.equals(prefAudioFormat, ignoreCase = true)
                            }
                            userPrefAudio ?: mediaInfo.audioQualities.firstOrNull {
                                it.extension.equals("m4a", ignoreCase = true) ||
                                        it.codec?.contains("mp4a", ignoreCase = true) == true ||
                                        it.codec?.contains("aac", ignoreCase = true) == true
                            } ?: mediaInfo.audioQualities.firstOrNull()
                        }

                        val defaultFormat = mediaInfo.formats.firstOrNull {
                            it.ext.equals(prefFormat, ignoreCase = true) && !it.isAudioOnly
                        } ?: mediaInfo.formats.firstOrNull { !it.isAudioOnly } ?: mediaInfo.formats.firstOrNull()

                        val isInstagram = UrlValidator.isInstagramUrl(mediaInfo.url)
                        val isFacebook = UrlValidator.isFacebookUrl(mediaInfo.url)

                        val instagramChoice = if (isAudioOnlyPref) {
                            SimplifiedMediaChoice.AUDIO_ONLY
                        } else {
                            SimplifiedMediaChoice.VIDEO_AND_AUDIO
                        }

                        // If "Ask before download" is turned off, avoid showing the format selection screen
                        // entirely (eliminating any UI glitch) and immediately start downloading with preferred quality
                        if (!askBefore) {
                            _uiState.value = AnalyzeUiState.StartingDownload
                            val request = createDownloadRequest(
                                mediaInfo = mediaInfo,
                                isSimplifiedPlatform = isInstagram || isFacebook,
                                instagramChoice = instagramChoice,
                                isAudioOnly = isAudioOnlyPref,
                                selectedAudioQuality = defaultAudioQuality,
                                selectedVideoQuality = defaultVideoQuality,
                                selectedFormat = defaultFormat,
                                selectedYouTubeAudioLanguage = defaultYouTubeLanguage,
                                selectedYouTubeAudioTrack = defaultYouTubeTrack
                            )
                            viewModelScope.launch {
                                downloadMediaUseCase(request)
                                _autoDownloadTriggered.emit(Unit)
                            }
                        } else {
                            _uiState.value = AnalyzeUiState.Success(
                                mediaInfo = mediaInfo,
                                selectedFormat = defaultFormat,
                                selectedVideoQuality = defaultVideoQuality,
                                selectedAudioQuality = defaultAudioQuality,
                                isAudioOnly = isAudioOnlyPref,
                                videoFormats = videoFormats,
                                audioFormats = audioFormats,
                                isInstagram = isInstagram,
                                isFacebook = isFacebook,
                                instagramChoice = instagramChoice,
                                selectedYouTubeAudioLanguage = defaultYouTubeLanguage,
                                selectedYouTubeAudioTrack = defaultYouTubeTrack
                            )
                        }
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
                val isWebm = quality.ext.equals("webm", ignoreCase = true) ||
                        quality.codec?.contains("vp", ignoreCase = true) == true
                val currentAudio = state.selectedAudioQuality
                val currentAudioIsWebm = currentAudio?.let {
                    it.extension.equals("opus", ignoreCase = true) ||
                            it.extension.equals("webm", ignoreCase = true) ||
                            it.codec?.contains("opus", ignoreCase = true) == true
                } ?: false

                val harmonizedAudio = if (isWebm && !currentAudioIsWebm) {
                    state.mediaInfo.audioQualities.firstOrNull {
                        it.extension.equals("opus", ignoreCase = true) ||
                                it.extension.equals("webm", ignoreCase = true) ||
                                it.codec?.contains("opus", ignoreCase = true) == true
                    } ?: currentAudio
                } else if (!isWebm && currentAudioIsWebm) {
                    state.mediaInfo.audioQualities.firstOrNull {
                        it.extension.equals("m4a", ignoreCase = true) ||
                                it.codec?.contains("mp4a", ignoreCase = true) == true ||
                                it.codec?.contains("aac", ignoreCase = true) == true
                    } ?: currentAudio
                } else {
                    currentAudio
                }

                state.copy(
                    selectedVideoQuality = quality,
                    selectedAudioQuality = harmonizedAudio,
                    isAudioOnly = false
                )
            } else state
        }
    }

    fun onAudioQualitySelected(quality: AudioQuality) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                val matchingYtTrack = state.selectedYouTubeAudioLanguage?.tracks?.firstOrNull {
                    it.formatId == quality.formatId || (it.bitrate?.toInt() == quality.bitrateKbps && it.extension.equals(quality.extension, ignoreCase = true))
                }
                state.copy(
                    selectedAudioQuality = quality,
                    selectedYouTubeAudioTrack = matchingYtTrack ?: state.selectedYouTubeAudioTrack
                )
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

            viewModelScope.launch {
                val preferredAudio = try {
                    settingsRepository.preferredAudioFormat.first().lowercase().trim()
                } catch (e: Exception) {
                    "m4a"
                }

                val ext = if (isAudio) {
                    preferredAudio.ifBlank { "m4a" }
                } else {
                    "mp4"
                }

                val formatId = if (isAudio) {
                    when (ext) {
                        "m4a" -> "bestaudio[ext=m4a]/bestaudio[ext=aac]/bestaudio/best"
                        "opus" -> "bestaudio[ext=opus]/bestaudio[ext=webm]/bestaudio/best"
                        "mp3" -> "bestaudio[ext=mp3]/bestaudio/best"
                        "flac" -> "bestaudio[ext=flac]/bestaudio/best"
                        "ogg" -> "bestaudio[ext=ogg]/bestaudio/best"
                        else -> "bestaudio[ext=$ext]/bestaudio/best"
                    }
                } else {
                    "bestvideo[ext=mp4]+bestaudio[ext=m4a]/bestvideo+bestaudio/best"
                }

                val resLabel = if (isAudio) "Audio (${ext.uppercase()})" else "Video (Best)"

                selectedItems.forEach { item ->
                    val request = DownloadRequest(
                        id = UUID.randomUUID().toString(),
                        url = item.url,
                        title = item.title,
                        thumbnailUrl = item.thumbnailUrl,
                        formatId = formatId,
                        ext = ext,
                        resolution = resLabel,
                        isAudioOnly = isAudio,
                        destinationUri = null
                    )
                    downloadMediaUseCase(request)
                }
            }
        }
    }

    fun setSubtitleDownloadMode(mode: SubtitleDownloadMode) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(subtitleDownloadMode = mode)
            } else state
        }
    }

    fun toggleSubtitleLanguage(languageCode: String) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                val current = state.selectedSubtitleLanguages.toMutableSet()
                if (current.contains(languageCode)) {
                    current.remove(languageCode)
                } else {
                    current.add(languageCode)
                }
                val newMode = if (current.isNotEmpty() && state.subtitleDownloadMode == SubtitleDownloadMode.MEDIA_ONLY) {
                    SubtitleDownloadMode.MEDIA_WITH_SUBS
                } else if (current.isEmpty() && !state.downloadAllSubtitles && state.subtitleDownloadMode != SubtitleDownloadMode.MEDIA_ONLY) {
                    SubtitleDownloadMode.MEDIA_ONLY
                } else {
                    state.subtitleDownloadMode
                }
                state.copy(
                    selectedSubtitleLanguages = current,
                    downloadAllSubtitles = false,
                    subtitleDownloadMode = newMode
                )
            } else state
        }
    }

    fun selectAllSubtitles() {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                val allLangs = state.availableSubtitleTracks.map { it.languageCode }.toSet()
                state.copy(
                    selectedSubtitleLanguages = allLangs,
                    downloadAllSubtitles = true,
                    subtitleDownloadMode = if (state.subtitleDownloadMode == SubtitleDownloadMode.MEDIA_ONLY) SubtitleDownloadMode.MEDIA_WITH_SUBS else state.subtitleDownloadMode
                )
            } else state
        }
    }

    fun deselectAllSubtitles() {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(
                    selectedSubtitleLanguages = emptySet(),
                    downloadAllSubtitles = false,
                    subtitleDownloadMode = if (state.subtitleDownloadMode == SubtitleDownloadMode.SUBS_ONLY) SubtitleDownloadMode.MEDIA_ONLY else state.subtitleDownloadMode
                )
            } else state
        }
    }

    fun setDownloadAllSubtitles(downloadAll: Boolean) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(
                    downloadAllSubtitles = downloadAll,
                    selectedSubtitleLanguages = if (downloadAll) state.availableSubtitleTracks.map { it.languageCode }.toSet() else emptySet(),
                    subtitleDownloadMode = if (downloadAll && state.subtitleDownloadMode == SubtitleDownloadMode.MEDIA_ONLY) SubtitleDownloadMode.MEDIA_WITH_SUBS else state.subtitleDownloadMode
                )
            } else state
        }
    }

    fun setIncludeManualSubtitles(include: Boolean) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(includeManualSubtitles = include)
            } else state
        }
    }

    fun setIncludeAutoSubtitles(include: Boolean) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(includeAutoSubtitles = include)
            } else state
        }
    }

    fun setPreferredSubtitleFormat(format: SubtitleFormatPreference) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(preferredSubtitleFormat = format)
            } else state
        }
    }

    fun toggleSubtitleSection() {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(subtitleSectionExpanded = !state.subtitleSectionExpanded)
            } else state
        }
    }

    fun onYouTubeAudioLanguageSelected(language: YouTubeAudioLanguage) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                val chosenTrack = language.selectedTrack
                val updatedAudioQuality = AudioQuality(
                    bitrateKbps = chosenTrack.bitrate?.toInt() ?: 128,
                    codec = chosenTrack.codec,
                    extension = chosenTrack.extension ?: "m4a",
                    estimatedSize = chosenTrack.fileSize,
                    formatSelector = chosenTrack.formatId,
                    formatId = chosenTrack.formatId
                )
                state.copy(
                    selectedYouTubeAudioLanguage = language,
                    selectedYouTubeAudioTrack = chosenTrack,
                    selectedAudioQuality = updatedAudioQuality
                )
            } else state
        }
    }

    fun onYouTubeAudioTrackSelected(track: YouTubeAudioTrack) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                val updatedLanguage = state.selectedYouTubeAudioLanguage?.copy(
                    selectedTrack = track
                )
                val updatedAudioQuality = AudioQuality(
                    bitrateKbps = track.bitrate?.toInt() ?: 128,
                    codec = track.codec,
                    extension = track.extension ?: "m4a",
                    estimatedSize = track.fileSize,
                    formatSelector = track.formatId,
                    formatId = track.formatId
                )
                state.copy(
                    selectedYouTubeAudioLanguage = updatedLanguage ?: state.selectedYouTubeAudioLanguage,
                    selectedYouTubeAudioTrack = track,
                    selectedAudioQuality = updatedAudioQuality
                )
            } else state
        }
    }

    fun setAudioLanguageSheetVisible(visible: Boolean) {
        _uiState.update { state ->
            if (state is AnalyzeUiState.Success) {
                state.copy(isAudioLanguageSheetVisible = visible)
            } else state
        }
    }

    private fun createDownloadRequest(
        mediaInfo: MediaInfo,
        isSimplifiedPlatform: Boolean,
        instagramChoice: SimplifiedMediaChoice,
        isAudioOnly: Boolean,
        selectedAudioQuality: AudioQuality?,
        selectedVideoQuality: VideoQuality?,
        selectedFormat: MediaFormat?,
        subtitleDownloadMode: SubtitleDownloadMode = SubtitleDownloadMode.MEDIA_ONLY,
        selectedSubtitleLanguages: Set<String> = emptySet(),
        downloadAllSubtitles: Boolean = false,
        includeManualSubtitles: Boolean = true,
        includeAutoSubtitles: Boolean = false,
        preferredSubtitleFormat: SubtitleFormatPreference = SubtitleFormatPreference.BEST,
        selectedYouTubeAudioLanguage: YouTubeAudioLanguage? = null,
        selectedYouTubeAudioTrack: YouTubeAudioTrack? = null
    ): DownloadRequest {
        val hasSubSelections = selectedSubtitleLanguages.isNotEmpty() || downloadAllSubtitles
        val effectiveSubMode = if (subtitleDownloadMode == SubtitleDownloadMode.MEDIA_ONLY && hasSubSelections) {
            SubtitleDownloadMode.MEDIA_WITH_SUBS
        } else {
            subtitleDownloadMode
        }

        val subOptions = SubtitleDownloadOptions(
            mode = effectiveSubMode,
            selectedLanguages = selectedSubtitleLanguages,
            downloadAllLanguages = downloadAllSubtitles,
            includeManual = includeManualSubtitles,
            includeAutoGenerated = includeAutoSubtitles,
            preferredFormat = preferredSubtitleFormat,
            embedInVideo = effectiveSubMode == SubtitleDownloadMode.EMBED_SUBS
        )

        if (effectiveSubMode == SubtitleDownloadMode.SUBS_ONLY) {
            return DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = mediaInfo.url,
                title = mediaInfo.title,
                thumbnailUrl = mediaInfo.thumbnail,
                formatId = "subtitles",
                ext = preferredSubtitleFormat.format.ifBlank { "srt" },
                resolution = "Subtitles (${preferredSubtitleFormat.displayName})",
                isAudioOnly = false,
                destinationUri = null,
                subtitleOptions = subOptions
            )
        }

        val baseRequest = if (isSimplifiedPlatform) {
            val isAudio = instagramChoice == SimplifiedMediaChoice.AUDIO_ONLY
            DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = mediaInfo.url,
                title = mediaInfo.title,
                thumbnailUrl = mediaInfo.thumbnail,
                formatId = instagramChoice.formatSelector,
                ext = instagramChoice.ext,
                resolution = instagramChoice.label,
                isAudioOnly = isAudio,
                destinationUri = null
            )
        } else if (isAudioOnly && selectedAudioQuality != null) {
            val audio = selectedAudioQuality
            val effectiveFormatId = selectedYouTubeAudioTrack?.formatId ?: audio.formatSelector
            val effectiveExt = selectedYouTubeAudioTrack?.extension ?: audio.extension
            val effectiveResolution = if (selectedYouTubeAudioTrack != null && selectedYouTubeAudioLanguage != null) {
                "${selectedYouTubeAudioTrack.displayBitrate} (${selectedYouTubeAudioLanguage.languageName})"
            } else {
                audio.displayBitrate
            }

            DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = mediaInfo.url,
                title = mediaInfo.title,
                thumbnailUrl = mediaInfo.thumbnail,
                formatId = effectiveFormatId,
                ext = effectiveExt,
                resolution = effectiveResolution,
                isAudioOnly = true,
                destinationUri = null,
                selectedAudioLanguage = selectedYouTubeAudioLanguage?.languageCode,
                selectedAudioTrack = selectedYouTubeAudioTrack
            )
        } else if (selectedVideoQuality != null) {
            val video = selectedVideoQuality
            val audio = selectedAudioQuality
            val audioFormatId = selectedYouTubeAudioTrack?.formatId ?: audio?.formatId ?: ""
            val audioDisplay = if (selectedYouTubeAudioTrack != null && selectedYouTubeAudioLanguage != null) {
                "${selectedYouTubeAudioTrack.displayBitrate} (${selectedYouTubeAudioLanguage.languageName})"
            } else {
                audio?.displayBitrate
            }

            val formatId = if (!video.hasAudio && audio != null && video.formatId.isNotBlank() && audioFormatId.isNotBlank() && video.formatId != audioFormatId) {
                if (UrlValidator.isYouTubeUrl(mediaInfo.url) && selectedYouTubeAudioTrack != null) {
                    "${video.formatId}+${audioFormatId}"
                } else {
                    "${video.formatId}+${audioFormatId}/${video.formatId}/best"
                }
            } else if (video.formatSelector.isNotBlank()) {
                if (video.formatSelector.contains("/")) video.formatSelector else "${video.formatSelector}/best"
            } else {
                "bestvideo+bestaudio/best"
            }
            val resLabel = if (audio != null && !video.hasAudio && video.formatId != audioFormatId) {
                "${video.label} + $audioDisplay"
            } else {
                video.label
            }
            val isVideoWebm = video.ext.equals("webm", ignoreCase = true) ||
                    video.codec?.contains("vp", ignoreCase = true) == true
            val isAudioOpus = audio?.let {
                it.extension.equals("opus", ignoreCase = true) ||
                        it.extension.equals("webm", ignoreCase = true) ||
                        it.codec?.contains("opus", ignoreCase = true) == true
            } ?: false

            val resolvedExt = if (!video.hasAudio && audio != null) {
                if (isVideoWebm && !isAudioOpus) {
                    "mkv"
                } else if (!isVideoWebm && isAudioOpus && !video.ext.equals("mkv", ignoreCase = true)) {
                    "mkv"
                } else {
                    video.ext.ifBlank { "mp4" }
                }
            } else {
                video.ext.ifBlank { "mp4" }
            }

            DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = mediaInfo.url,
                title = mediaInfo.title,
                thumbnailUrl = mediaInfo.thumbnail,
                formatId = formatId,
                ext = resolvedExt,
                resolution = resLabel,
                isAudioOnly = false,
                destinationUri = null,
                selectedAudioLanguage = selectedYouTubeAudioLanguage?.languageCode,
                selectedAudioTrack = selectedYouTubeAudioTrack
            )
        } else if (selectedFormat != null) {
            val format = selectedFormat
            val formatId = if (format.formatId.isNotBlank()) {
                if (format.isAudioOnly) "${format.formatId}/bestaudio/best" else "${format.formatId}/best"
            } else {
                if (format.isAudioOnly) "bestaudio/best" else "bestvideo+bestaudio/best"
            }
            DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = mediaInfo.url,
                title = mediaInfo.title,
                thumbnailUrl = mediaInfo.thumbnail,
                formatId = formatId,
                ext = format.ext.ifBlank { if (format.isAudioOnly) "m4a" else "mp4" },
                resolution = format.displayResolution.ifBlank { "Best" },
                isAudioOnly = format.isAudioOnly,
                destinationUri = null,
                selectedAudioLanguage = selectedYouTubeAudioLanguage?.languageCode,
                selectedAudioTrack = selectedYouTubeAudioTrack
            )
        } else {
            DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = mediaInfo.url,
                title = mediaInfo.title,
                thumbnailUrl = mediaInfo.thumbnail,
                formatId = "bestvideo+bestaudio/best",
                ext = "mp4",
                resolution = "Best",
                isAudioOnly = false,
                destinationUri = null
            )
        }

        return baseRequest.copy(
            subtitleOptions = subOptions,
            requiresAuth = mediaInfo.requiresAuth
        )
    }

    fun startDownload() {
        val currentState = _uiState.value
        if (currentState is AnalyzeUiState.Success) {
            val request = createDownloadRequest(
                mediaInfo = currentState.mediaInfo,
                isSimplifiedPlatform = currentState.isSimplifiedPlatform,
                instagramChoice = currentState.instagramChoice,
                isAudioOnly = currentState.isAudioOnly,
                selectedAudioQuality = currentState.selectedAudioQuality,
                selectedVideoQuality = currentState.selectedVideoQuality,
                selectedFormat = currentState.selectedFormat,
                subtitleDownloadMode = currentState.subtitleDownloadMode,
                selectedSubtitleLanguages = currentState.selectedSubtitleLanguages,
                downloadAllSubtitles = currentState.downloadAllSubtitles,
                includeManualSubtitles = currentState.includeManualSubtitles,
                includeAutoSubtitles = currentState.includeAutoSubtitles,
                preferredSubtitleFormat = currentState.preferredSubtitleFormat,
                selectedYouTubeAudioLanguage = currentState.selectedYouTubeAudioLanguage,
                selectedYouTubeAudioTrack = currentState.selectedYouTubeAudioTrack
            )
            viewModelScope.launch {
                downloadMediaUseCase(request)
            }
        }
    }
}
