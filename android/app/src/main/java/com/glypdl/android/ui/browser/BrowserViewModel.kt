/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024-2026 Glypdl Contributors
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

package com.glypdl.android.ui.browser

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.model.*
import com.glypdl.android.service.DownloadManager
import com.glypdl.android.service.browser.DashParser
import com.glypdl.android.service.browser.HlsParser
import com.glypdl.android.service.browser.MediaClassifier
import com.glypdl.android.service.browser.MediaProber
import com.glypdl.android.service.browser.MediaSniffer
import com.glypdl.android.util.FileNameSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

data class BrowserUiState(
    val currentUrl: String = "https://duckduckgo.com",
    val inputUrl: String = "https://duckduckgo.com",
    val title: String = "Browser",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isSecure: Boolean = true,
    val errorMessage: String? = null,
    val sslErrorDialogUrl: String? = null,
    val isMediaSheetVisible: Boolean = false,
    val selectedQualityMedia: DetectedMedia? = null,
    val snackbarMessage: String? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaSniffer: MediaSniffer,
    private val mediaProber: MediaProber,
    private val downloadManager: DownloadManager
) : ViewModel() {

    private val initialUrl: String = savedStateHandle.get<String>("url")
        ?.takeIf { it.isNotBlank() } ?: "https://duckduckgo.com"

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            currentUrl = initialUrl,
            inputUrl = initialUrl
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val detectedMedia: StateFlow<List<DetectedMedia>> = mediaSniffer.detectedMediaFlow

    fun onInputUrlChange(newInput: String) {
        _uiState.update { it.copy(inputUrl = newInput) }
    }

    fun onSubmitInput(input: String) {
        val resolvedUrl = resolveUrlOrSearch(input.trim())
        _uiState.update {
            it.copy(
                currentUrl = resolvedUrl,
                inputUrl = resolvedUrl,
                errorMessage = null
            )
        }
    }

    fun onPageStarted(url: String) {
        mediaSniffer.resetForPage(url)
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                isLoading = true,
                errorMessage = null,
                isSecure = url.startsWith("https://", ignoreCase = true)
            )
        }
    }

    fun onPageFinished(url: String, canBack: Boolean, canForward: Boolean) {
        _uiState.update {
            it.copy(
                currentUrl = url,
                inputUrl = url,
                isLoading = false,
                canGoBack = canBack,
                canGoForward = canForward,
                isSecure = url.startsWith("https://", ignoreCase = true)
            )
        }
    }

    fun onProgressChanged(progress: Int) {
        _uiState.update {
            it.copy(
                progress = progress,
                isLoading = progress < 100
            )
        }
    }

    fun onTitleReceived(title: String) {
        if (title.isNotBlank() && !title.startsWith("http://") && !title.startsWith("https://")) {
            _uiState.update { it.copy(title = title) }
        }
    }

    fun onErrorReceived(description: String) {
        _uiState.update {
            it.copy(
                errorMessage = description,
                isLoading = false
            )
        }
    }

    fun onSslError(url: String) {
        _uiState.update {
            it.copy(
                sslErrorDialogUrl = url,
                isLoading = false
            )
        }
    }

    fun dismissSslDialog() {
        _uiState.update { it.copy(sslErrorDialogUrl = null) }
    }

    fun toggleDesktopMode() {
        _uiState.update { it.copy(isDesktopMode = !it.isDesktopMode) }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    fun showMediaSheet() {
        _uiState.update { it.copy(isMediaSheetVisible = true) }
    }

    fun hideMediaSheet() {
        _uiState.update { it.copy(isMediaSheetVisible = false) }
    }

    fun onSelectQualityMedia(media: DetectedMedia?) {
        _uiState.update { it.copy(selectedQualityMedia = media) }
    }

    fun dismissQualityPicker() {
        _uiState.update { it.copy(selectedQualityMedia = null) }
    }

    fun clearSnackbarMessage() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    /**
     * Layer A: Handle network request intercepted in WebViewClient.shouldInterceptRequest.
     */
    fun onNetworkResourceIntercepted(url: String, headers: Map<String, String>) {
        if (url.isBlank() || MediaClassifier.isAdOrTrackingUrl(url) || MediaClassifier.isSegmentOrChunk(url)) return

        val kind = MediaClassifier.classify(url)
        if (kind != null) {
            val candidateId = mediaSniffer.addCandidate(
                url = url,
                pageUrl = _uiState.value.currentUrl,
                pageTitle = _uiState.value.title,
                kind = kind,
                headers = headers,
                source = DetectionSource.NETWORK
            )
            if (candidateId != null && (kind == MediaKind.HLS || kind == MediaKind.DASH)) {
                parseManifestIfStreaming(candidateId, url, kind, headers)
            }
        } else {
            // If URL path has ambiguous hints, probe it in background
            val lower = url.lowercase()
            if (lower.contains("videoplayback") || lower.contains("/stream") || lower.contains("media")) {
                viewModelScope.launch(Dispatchers.IO) {
                    val probeResult = mediaProber.probeUrl(url, _uiState.value.currentUrl, headers)
                    if (probeResult.isMedia && probeResult.kind != null) {
                        val candidateId = mediaSniffer.addCandidate(
                            url = url,
                            pageUrl = _uiState.value.currentUrl,
                            pageTitle = _uiState.value.title,
                            kind = probeResult.kind,
                            mime = probeResult.mimeType,
                            sizeBytes = probeResult.contentLength,
                            headers = headers,
                            source = DetectionSource.PROBE
                        )
                        if (candidateId != null && (probeResult.kind == MediaKind.HLS || probeResult.kind == MediaKind.DASH)) {
                            parseManifestIfStreaming(candidateId, url, probeResult.kind, headers)
                        }
                    }
                }
            }
        }
    }

    /**
     * Layer B: Handle DOM / JS reported media from injected scripts.
     */
    fun onDomMediaReported(jsonPayload: String) {
        try {
            val json = JSONObject(jsonPayload)
            val url = json.optString("url")
            if (url.isBlank() || url.startsWith("blob:") || url.startsWith("data:")) return

            val poster = json.optString("poster").takeIf { !it.isNullOrBlank() }
            val mime = json.optString("mime").takeIf { !it.isNullOrBlank() }
            val title = json.optString("title").takeIf { !it.isNullOrBlank() } ?: _uiState.value.title

            val headers = mapOf(
                "Referer" to _uiState.value.currentUrl,
                "User-Agent" to "Mozilla/5.0 (Linux; Android 14) Mobile Safari/537.36"
            )

            val kind = MediaClassifier.classify(url, mime)
            if (kind != null) {
                val candidateId = mediaSniffer.addCandidate(
                    url = url,
                    pageUrl = _uiState.value.currentUrl,
                    pageTitle = title,
                    kind = kind,
                    mime = mime,
                    posterUrl = poster,
                    headers = headers,
                    source = DetectionSource.DOM
                )
                if (candidateId != null && (kind == MediaKind.HLS || kind == MediaKind.DASH)) {
                    parseManifestIfStreaming(candidateId, url, kind, headers)
                }
            } else {
                viewModelScope.launch(Dispatchers.IO) {
                    val probeResult = mediaProber.probeUrl(url, _uiState.value.currentUrl)
                    if (probeResult.isMedia && probeResult.kind != null) {
                        val candidateId = mediaSniffer.addCandidate(
                            url = url,
                            pageUrl = _uiState.value.currentUrl,
                            pageTitle = title,
                            kind = probeResult.kind,
                            mime = probeResult.mimeType,
                            sizeBytes = probeResult.contentLength,
                            posterUrl = poster,
                            headers = headers,
                            source = DetectionSource.DOM
                        )
                        if (candidateId != null && (probeResult.kind == MediaKind.HLS || probeResult.kind == MediaKind.DASH)) {
                            parseManifestIfStreaming(candidateId, url, probeResult.kind, headers)
                        }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    private fun parseManifestIfStreaming(
        candidateId: String,
        url: String,
        kind: MediaKind,
        headers: Map<String, String>
    ) {
        if (kind != MediaKind.HLS && kind != MediaKind.DASH) return
        viewModelScope.launch(Dispatchers.IO) {
            val manifestText = mediaProber.fetchText(url, headers) ?: return@launch
            when (kind) {
                MediaKind.HLS -> {
                    val result = HlsParser.parse(manifestText, url)
                    val durationMs = result.durationSeconds?.let { (it * 1000).toLong() }
                    mediaSniffer.updateCandidateVariants(
                        id = candidateId,
                        variants = result.variants,
                        isLive = result.isLive,
                        isDrmProtected = result.isDrmProtected,
                        durationMs = durationMs
                    )
                }
                MediaKind.DASH -> {
                    val result = DashParser.parse(manifestText, url)
                    val durationMs = result.durationSeconds?.let { (it * 1000).toLong() }
                    mediaSniffer.updateCandidateVariants(
                        id = candidateId,
                        variants = result.videoVariants + result.audioVariants,
                        isLive = result.isLive,
                        isDrmProtected = result.isDrmProtected,
                        durationMs = durationMs
                    )
                }
                else -> {}
            }
        }
    }

    /**
     * Enqueues a detected media item into the Glypdl download pipeline.
     */
    fun downloadDetectedMedia(media: DetectedMedia) {
        viewModelScope.launch {
            val rawTitle = media.pageTitle?.takeIf { it.isNotBlank() }
                ?: media.url.substringBefore('?').substringAfterLast('/')
            val safeTitle = FileNameSanitizer.safeFsTitle(rawTitle)

            val ext = media.ext?.takeIf { it.isNotBlank() }
                ?: if (media.kind == MediaKind.PROGRESSIVE_AUDIO) "mp3" else "mp4"

            val sourceKind = when (media.kind) {
                MediaKind.HLS -> "BROWSER_HLS"
                MediaKind.DASH -> "BROWSER_DASH"
                MediaKind.PROGRESSIVE_AUDIO -> "BROWSER_AUDIO"
                else -> "BROWSER_DIRECT"
            }

            val request = DownloadRequest(
                id = UUID.randomUUID().toString(),
                url = media.url,
                title = safeTitle,
                thumbnailUrl = media.posterUrl,
                formatId = "best",
                ext = ext,
                resolution = "Best",
                isAudioOnly = media.kind == MediaKind.PROGRESSIVE_AUDIO,
                destinationUri = null,
                headers = media.headers,
                sourceKind = sourceKind,
                pageUrl = media.pageUrl,
                requiresAuth = true
            )

            enqueueDownloadRequest(request)
        }
    }

    fun enqueueDownloadRequest(request: DownloadRequest) {
        viewModelScope.launch {
            downloadManager.enqueueDownload(request)
            _uiState.update {
                it.copy(
                    isMediaSheetVisible = false,
                    selectedQualityMedia = null,
                    snackbarMessage = "Added \"${request.title}\" to Downloads"
                )
            }
        }
    }

    fun resolveUrlOrSearch(input: String): String {
        if (input.isBlank()) return "https://duckduckgo.com"

        if (input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true)) {
            return input
        }

        val domainPattern = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
        if (domainPattern.matches(input) && !input.contains(" ")) {
            return "https://$input"
        }

        val encoded = URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
        return "https://duckduckgo.com/?q=$encoded"
    }
}
