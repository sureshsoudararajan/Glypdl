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

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.glypdl.android.data.local.entity.BookmarkEntity
import com.glypdl.android.data.local.entity.BrowserHistoryEntity
import com.glypdl.android.data.model.*
import com.glypdl.android.data.repository.BrowserRepository
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.service.DownloadManager
import com.glypdl.android.service.browser.DashParser
import com.glypdl.android.service.browser.HlsParser
import com.glypdl.android.service.browser.MediaClassifier
import com.glypdl.android.service.browser.MediaProber
import com.glypdl.android.service.browser.MediaSniffer
import com.glypdl.android.util.FileNameSanitizer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.inject.Inject

const val START_PAGE_URL = "glypdl://start"

data class BrowserUiState(
    val tabs: List<BrowserTab> = listOf(BrowserTab(id = "default_tab", url = START_PAGE_URL, title = "New Tab")),
    val activeTabId: String = "default_tab",
    val currentUrl: String = START_PAGE_URL,
    val inputUrl: String = "",
    val title: String = "New Tab",
    val progress: Int = 0,
    val isLoading: Boolean = false,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isIncognito: Boolean = false,
    val isAdBlockEnabled: Boolean = true,
    val isSecure: Boolean = true,
    val currentSearchEngine: SearchEngine = SearchEngine.DUCKDUCKGO,
    val errorMessage: String? = null,
    val sslErrorDialogUrl: String? = null,
    val isMediaSheetVisible: Boolean = false,
    val isTabSwitcherVisible: Boolean = false,
    val isBookmarksHistoryVisible: Boolean = false,
    val bookmarksHistoryInitialTab: Int = 0,
    val isBookmarked: Boolean = false,
    val isFindInPageVisible: Boolean = false,
    val findQuery: String = "",
    val findCurrentIndex: Int = 0,
    val findTotalMatches: Int = 0,
    val selectedQualityMedia: DetectedMedia? = null,
    val snackbarMessage: String? = null
) {
    val isStartPage: Boolean
        get() = currentUrl == START_PAGE_URL || currentUrl.isBlank() || currentUrl == "about:blank"
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val mediaSniffer: MediaSniffer,
    private val mediaProber: MediaProber,
    private val downloadManager: DownloadManager,
    private val browserRepository: BrowserRepository,
    private val settingsRepository: SettingsRepository,
    private val authCookieManager: com.glypdl.android.service.auth.AuthCookieManager
) : ViewModel() {

    private val initialUrl: String = savedStateHandle.get<String>("url")
        ?.takeIf { it.isNotBlank() && it != "https://duckduckgo.com" } ?: START_PAGE_URL

    private val initialTab = BrowserTab(
        id = UUID.randomUUID().toString(),
        url = initialUrl,
        title = if (initialUrl == START_PAGE_URL) "New Tab" else "Browser"
    )

    private val _uiState = MutableStateFlow(
        BrowserUiState(
            tabs = listOf(initialTab),
            activeTabId = initialTab.id,
            currentUrl = initialUrl,
            inputUrl = if (initialUrl == START_PAGE_URL) "" else initialUrl
        )
    )
    val uiState: StateFlow<BrowserUiState> = _uiState.asStateFlow()

    val detectedMedia: StateFlow<List<DetectedMedia>> = mediaSniffer.detectedMediaFlow
    val bookmarks: StateFlow<List<BookmarkEntity>> = browserRepository.getAllBookmarks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val history: StateFlow<List<BrowserHistoryEntity>> = browserRepository.getAllHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var defaultSearchEngine: String = "duckduckgo"
    private var isAutoDetectEnabled: Boolean = true

    init {
        viewModelScope.launch {
            settingsRepository.browserSearchEngine.collect { engineId ->
                val engine = SearchEngine.fromId(engineId)
                defaultSearchEngine = engine.id
                _uiState.update { it.copy(currentSearchEngine = engine) }
            }
        }
        viewModelScope.launch {
            settingsRepository.browserAutoDetect.collect { isAutoDetectEnabled = it }
        }
        viewModelScope.launch {
            settingsRepository.browserDesktopModeDefault.collect { defaultDesktop ->
                if (defaultDesktop && !_uiState.value.isDesktopMode) {
                    _uiState.update { it.copy(isDesktopMode = true) }
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.browserAdBlockEnabled.collect { enabled ->
                _uiState.update { it.copy(isAdBlockEnabled = enabled) }
            }
        }

        // Restore previous browser session tabs & URL if opened without explicit intent
        val hasExplicitUrl = savedStateHandle.get<String>("url") != null
        if (!hasExplicitUrl) {
            viewModelScope.launch {
                val savedTabsJson = settingsRepository.browserLastSessionTabs.firstOrNull()
                val savedActiveTabId = settingsRepository.browserLastActiveTabId.firstOrNull()
                if (!savedTabsJson.isNullOrBlank()) {
                    try {
                        val jsonArr = org.json.JSONArray(savedTabsJson)
                        val restoredTabs = mutableListOf<BrowserTab>()
                        for (i in 0 until jsonArr.length()) {
                            val obj = jsonArr.getJSONObject(i)
                            val tUrl = obj.getString("url")
                            if (tUrl.isNotBlank()) {
                                restoredTabs.add(
                                    BrowserTab(
                                        id = obj.getString("id"),
                                        url = tUrl,
                                        title = obj.optString("title", "Browser"),
                                        isIncognito = false
                                    )
                                )
                            }
                        }
                        if (restoredTabs.isNotEmpty()) {
                            val activeTab = restoredTabs.find { it.id == savedActiveTabId } ?: restoredTabs.last()
                            _uiState.update {
                                it.copy(
                                    tabs = restoredTabs,
                                    activeTabId = activeTab.id,
                                    currentUrl = activeTab.url,
                                    inputUrl = if (activeTab.url == START_PAGE_URL) "" else activeTab.url,
                                    title = activeTab.title
                                )
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun setSearchEngine(engine: SearchEngine) {
        defaultSearchEngine = engine.id
        _uiState.update { it.copy(currentSearchEngine = engine) }
        viewModelScope.launch {
            settingsRepository.setBrowserSearchEngine(engine.id)
        }
    }

    fun onInputUrlChange(newInput: String) {
        _uiState.update { it.copy(inputUrl = newInput) }
    }

    fun onSubmitInput(input: String) {
        val resolvedUrl = resolveUrlOrSearch(input.trim())
        if (resolvedUrl == START_PAGE_URL) {
            mediaSniffer.resetForPage(START_PAGE_URL)
        }
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(
                    url = resolvedUrl,
                    title = if (resolvedUrl == START_PAGE_URL) "New Tab" else tab.title
                ) else tab
            }
            state.copy(
                tabs = updatedTabs,
                currentUrl = resolvedUrl,
                inputUrl = if (resolvedUrl == START_PAGE_URL) "" else resolvedUrl,
                errorMessage = null
            )
        }
    }

    fun onPageStarted(url: String) {
        if (url == START_PAGE_URL || url == "about:blank") return
        if (isAutoDetectEnabled) {
            mediaSniffer.resetForPage(url)
        }
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(url = url) else tab
            }
            state.copy(
                tabs = updatedTabs,
                currentUrl = url,
                inputUrl = url,
                isLoading = true,
                errorMessage = null,
                isSecure = url.startsWith("https://", ignoreCase = true)
            )
        }
        checkBookmarkStatus(url)
    }

    fun onPageFinished(url: String, canBack: Boolean, canForward: Boolean) {
        if (url == START_PAGE_URL || url == "about:blank") {
            _uiState.update { it.copy(isLoading = false) }
            return
        }
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(
                    url = url,
                    canGoBack = canBack,
                    canGoForward = canForward
                ) else tab
            }
            state.copy(
                tabs = updatedTabs,
                currentUrl = url,
                inputUrl = url,
                isLoading = false,
                canGoBack = canBack,
                canGoForward = canForward,
                isSecure = url.startsWith("https://", ignoreCase = true)
            )
        }
        checkBookmarkStatus(url)
        // Record in browsing history if not incognito
        val currentTab = _uiState.value.tabs.find { it.id == _uiState.value.activeTabId }
        val isIncognito = currentTab?.isIncognito ?: _uiState.value.isIncognito
        if (!isIncognito && url.isNotBlank() && !url.startsWith("about:") && !url.startsWith("data:") && !url.startsWith("glypdl:")) {
            viewModelScope.launch {
                browserRepository.recordVisit(url, _uiState.value.title)
            }
            // Auto-synchronize browser login session cookies with AuthCookieManager for yt-dlp & Settings
            if (url.contains("instagram.com") || url.contains("facebook.com") || url.contains("youtube.com") || url.contains("twitter.com") || url.contains("x.com") || url.contains("tiktok.com")) {
                viewModelScope.launch(Dispatchers.IO) {
                    authCookieManager.syncCookiesFromCookieManager(url)
                }
            }
        }
        persistSession()
    }

    /**
     * Handles single-page application (SPA) dynamic client-side URL changes
     * (e.g. YouTube Shorts, Instagram Reels, Twitter) reported via JavaScript history interception.
     */
    fun onUrlChangedViaJs(newUrl: String, title: String?) {
        if (newUrl.isBlank() || newUrl.startsWith("about:") || newUrl.startsWith("data:")) return
        if (newUrl == _uiState.value.currentUrl) return

        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(
                    url = newUrl,
                    title = title?.takeIf { it.isNotBlank() } ?: tab.title
                ) else tab
            }
            state.copy(
                currentUrl = newUrl,
                inputUrl = newUrl,
                title = title?.takeIf { it.isNotBlank() } ?: state.title,
                tabs = updatedTabs,
                isSecure = newUrl.startsWith("https://", ignoreCase = true)
            )
        }
        checkBookmarkStatus(newUrl)
        mediaSniffer.resetForPage(newUrl)

        val currentTab = _uiState.value.tabs.find { it.id == _uiState.value.activeTabId }
        val isIncognito = currentTab?.isIncognito ?: _uiState.value.isIncognito
        if (!isIncognito && (newUrl.contains("instagram.com") || newUrl.contains("facebook.com") || newUrl.contains("youtube.com") || newUrl.contains("twitter.com") || newUrl.contains("x.com") || newUrl.contains("tiktok.com"))) {
            viewModelScope.launch(Dispatchers.IO) {
                authCookieManager.syncCookiesFromCookieManager(newUrl)
            }
        }
        if (!isIncognito) {
            viewModelScope.launch {
                browserRepository.recordVisit(newUrl, title ?: _uiState.value.title)
            }
        }
        persistSession()
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
            _uiState.update { state ->
                val updatedTabs = state.tabs.map { tab ->
                    if (tab.id == state.activeTabId) tab.copy(title = title) else tab
                }
                state.copy(title = title, tabs = updatedTabs)
            }
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

    // Tabs Management
    fun showTabSwitcher() {
        _uiState.update { it.copy(isTabSwitcherVisible = true) }
    }

    fun hideTabSwitcher() {
        _uiState.update { it.copy(isTabSwitcherVisible = false) }
    }

    fun toggleAdBlock() {
        val next = !_uiState.value.isAdBlockEnabled
        viewModelScope.launch {
            settingsRepository.setBrowserAdBlockEnabled(next)
        }
    }

    fun openNewTab(url: String = START_PAGE_URL, isIncognito: Boolean = false) {
        val newTab = BrowserTab(
            id = UUID.randomUUID().toString(),
            url = url,
            title = if (isIncognito) "Incognito Tab" else "New Tab",
            isDesktopMode = _uiState.value.isDesktopMode,
            isIncognito = isIncognito
        )
        _uiState.update { state ->
            state.copy(
                tabs = state.tabs + newTab,
                activeTabId = newTab.id,
                currentUrl = newTab.url,
                inputUrl = if (newTab.url == START_PAGE_URL) "" else newTab.url,
                title = newTab.title,
                isIncognito = isIncognito,
                isDesktopMode = newTab.isDesktopMode,
                canGoBack = false,
                canGoForward = false,
                isTabSwitcherVisible = false
            )
        }
        if (url != START_PAGE_URL) {
            mediaSniffer.resetForPage(url)
        } else {
            mediaSniffer.resetForPage(START_PAGE_URL)
        }
        persistSession()
    }

    fun navigateToStartPage() {
        mediaSniffer.resetForPage(START_PAGE_URL)
        _uiState.update { state ->
            val updatedTabs = state.tabs.map { tab ->
                if (tab.id == state.activeTabId) tab.copy(url = START_PAGE_URL, title = "New Tab") else tab
            }
            state.copy(
                tabs = updatedTabs,
                currentUrl = START_PAGE_URL,
                inputUrl = "",
                title = "New Tab",
                isLoading = false,
                isMediaSheetVisible = false,
                errorMessage = null
            )
        }
        persistSession()
    }

    fun switchTab(tabId: String) {
        val targetTab = _uiState.value.tabs.find { it.id == tabId } ?: return
        _uiState.update {
            it.copy(
                activeTabId = tabId,
                currentUrl = targetTab.url,
                inputUrl = if (targetTab.url == START_PAGE_URL) "" else targetTab.url,
                title = targetTab.title,
                isDesktopMode = targetTab.isDesktopMode,
                isIncognito = targetTab.isIncognito,
                canGoBack = targetTab.canGoBack,
                canGoForward = targetTab.canGoForward,
                isTabSwitcherVisible = false
            )
        }
        if (targetTab.url == START_PAGE_URL) {
            mediaSniffer.resetForPage(START_PAGE_URL)
        } else {
            mediaSniffer.resetForPage(targetTab.url)
        }
        persistSession()
    }

    fun closeTab(tabId: String) {
        val currentTabs = _uiState.value.tabs
        if (currentTabs.size <= 1) {
            // If last tab is closed, reset to start page tab
            val freshTab = BrowserTab(url = START_PAGE_URL, title = "New Tab")
            mediaSniffer.resetForPage(START_PAGE_URL)
            _uiState.update {
                it.copy(
                    tabs = listOf(freshTab),
                    activeTabId = freshTab.id,
                    currentUrl = freshTab.url,
                    inputUrl = "",
                    title = freshTab.title,
                    isIncognito = false,
                    isTabSwitcherVisible = false
                )
            }
            persistSession()
            return
        }

        val remaining = currentTabs.filter { it.id != tabId }
        val nextActiveId = if (tabId == _uiState.value.activeTabId) {
            remaining.last().id
        } else {
            _uiState.value.activeTabId
        }
        val nextActiveTab = remaining.first { it.id == nextActiveId }

        _uiState.update {
            it.copy(
                tabs = remaining,
                activeTabId = nextActiveId,
                currentUrl = nextActiveTab.url,
                inputUrl = if (nextActiveTab.url == START_PAGE_URL) "" else nextActiveTab.url,
                title = nextActiveTab.title,
                isDesktopMode = nextActiveTab.isDesktopMode,
                isIncognito = nextActiveTab.isIncognito
            )
        }
        if (nextActiveTab.url == START_PAGE_URL) {
            mediaSniffer.resetForPage(START_PAGE_URL)
        } else {
            mediaSniffer.resetForPage(nextActiveTab.url)
        }
        persistSession()
    }

    fun closeAllTabs() {
        val freshTab = BrowserTab(url = START_PAGE_URL, title = "New Tab")
        mediaSniffer.resetForPage(START_PAGE_URL)
        _uiState.update {
            it.copy(
                tabs = listOf(freshTab),
                activeTabId = freshTab.id,
                currentUrl = freshTab.url,
                inputUrl = "",
                title = freshTab.title,
                isIncognito = false,
                isTabSwitcherVisible = false
            )
        }
        persistSession()
    }

    // Bookmarks and History
    fun showBookmarksHistory(initialTab: Int = 0) {
        _uiState.update { it.copy(isBookmarksHistoryVisible = true, bookmarksHistoryInitialTab = initialTab) }
    }

    fun hideBookmarksHistory() {
        _uiState.update { it.copy(isBookmarksHistoryVisible = false) }
    }

    private fun checkBookmarkStatus(url: String) {
        viewModelScope.launch {
            val bookmarked = browserRepository.isBookmarked(url)
            _uiState.update { it.copy(isBookmarked = bookmarked) }
        }
    }

    fun toggleBookmarkCurrentPage() {
        viewModelScope.launch {
            val url = _uiState.value.currentUrl
            val title = _uiState.value.title
            if (_uiState.value.isBookmarked) {
                browserRepository.removeBookmark(url)
                _uiState.update { it.copy(isBookmarked = false, snackbarMessage = "Bookmark removed") }
            } else {
                browserRepository.addBookmark(url, title)
                _uiState.update { it.copy(isBookmarked = true, snackbarMessage = "Bookmark saved") }
            }
        }
    }

    fun deleteBookmark(id: Long) {
        viewModelScope.launch {
            browserRepository.removeBookmarkById(id)
            checkBookmarkStatus(_uiState.value.currentUrl)
        }
    }

    fun deleteHistoryItem(id: Long) {
        viewModelScope.launch {
            browserRepository.deleteHistoryById(id)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            browserRepository.clearHistory()
            _uiState.update { it.copy(snackbarMessage = "Browsing history cleared") }
        }
    }

    fun clearAllBrowsingData(context: Context) {
        viewModelScope.launch {
            browserRepository.clearAllBrowsingData(context)
            _uiState.update { it.copy(snackbarMessage = "All browsing data cleared") }
        }
    }

    // Find in Page
    fun showFindInPage() {
        _uiState.update { it.copy(isFindInPageVisible = true, findQuery = "") }
    }

    fun hideFindInPage() {
        _uiState.update { it.copy(isFindInPageVisible = false, findQuery = "", findTotalMatches = 0) }
    }

    fun onFindQueryChange(query: String) {
        _uiState.update { it.copy(findQuery = query) }
    }

    fun onFindResult(activeMatchOrdinal: Int, numberOfMatches: Int) {
        _uiState.update {
            it.copy(
                findCurrentIndex = activeMatchOrdinal,
                findTotalMatches = numberOfMatches
            )
        }
    }

    /**
     * Layer A: Handle network request intercepted in WebViewClient.shouldInterceptRequest.
     */
    fun onNetworkResourceIntercepted(url: String, headers: Map<String, String>) {
        if (!isAutoDetectEnabled) return
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
        if (!isAutoDetectEnabled) return
        try {
            val json = JSONObject(jsonPayload)
            val url = json.optString("url")
            if (url.isBlank() || url.startsWith("blob:") || url.startsWith("data:")) return

            val poster = json.optString("poster").takeIf { !it.isNullOrBlank() }
            val mime = json.optString("mime").takeIf { !it.isNullOrBlank() }
            val rawTitle = json.optString("title").takeIf { !it.isNullOrBlank() } ?: _uiState.value.title
            val account = json.optString("account").takeIf { !it.isNullOrBlank() }

            val title = MediaClassifier.generateSmartTitle(
                pageUrl = _uiState.value.currentUrl,
                pageTitle = rawTitle,
                candidateUrl = url,
                accountName = account
            )

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
                    source = DetectionSource.DOM,
                    accountName = account
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

    fun resolveUrlOrSearch(
        input: String,
        engine: SearchEngine = _uiState.value.currentSearchEngine
    ): String {
        val trimmed = input.trim()
        if (trimmed.isBlank() ||
            trimmed == START_PAGE_URL ||
            trimmed.equals("about:home", ignoreCase = true) ||
            trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.equals("glypdl://start", ignoreCase = true)
        ) {
            return START_PAGE_URL
        }

        if (trimmed.startsWith("https://", ignoreCase = true) || trimmed.startsWith("http://", ignoreCase = true)) {
            return trimmed
        }

        val domainPattern = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
        if (domainPattern.matches(trimmed) && !trimmed.contains(" ")) {
            return "https://$trimmed"
        }

        return engine.buildSearchUrl(trimmed)
    }

    fun persistSession() {
        val nonIncognitoTabs = _uiState.value.tabs.filter { !it.isIncognito }
        if (nonIncognitoTabs.isEmpty()) return
        val activeTabId = _uiState.value.activeTabId
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val jsonArr = org.json.JSONArray()
                for (tab in nonIncognitoTabs) {
                    val obj = org.json.JSONObject().apply {
                        put("id", tab.id)
                        put("url", tab.url)
                        put("title", tab.title)
                    }
                    jsonArr.put(obj)
                }
                settingsRepository.saveBrowserSession(jsonArr.toString(), activeTabId)
            } catch (_: Exception) {}
        }
    }

    override fun onCleared() {
        super.onCleared()
        persistSession()
    }
}
