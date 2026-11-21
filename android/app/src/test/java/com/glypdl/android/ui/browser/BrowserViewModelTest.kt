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
import com.glypdl.android.data.model.DetectedMedia
import com.glypdl.android.data.model.MediaKind
import com.glypdl.android.data.repository.BrowserRepository
import com.glypdl.android.data.repository.SettingsRepository
import com.glypdl.android.service.DownloadManager
import com.glypdl.android.service.browser.GlypdlWebViewFactory
import com.glypdl.android.service.browser.MediaProber
import com.glypdl.android.service.browser.MediaSniffer
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BrowserViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var mediaProber: MediaProber
    private lateinit var mediaSniffer: MediaSniffer
    private lateinit var downloadManager: DownloadManager
    private lateinit var browserRepository: BrowserRepository
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var authCookieManager: com.glypdl.android.service.auth.AuthCookieManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mediaProber = mockk(relaxed = true)
        mediaSniffer = MediaSniffer(mediaProber)
        downloadManager = mockk(relaxed = true)
        browserRepository = mockk(relaxed = true)
        settingsRepository = mockk(relaxed = true)
        authCookieManager = mockk(relaxed = true)

        every { browserRepository.getAllBookmarks() } returns flowOf(emptyList())
        every { browserRepository.getAllHistory() } returns flowOf(emptyList())
        every { settingsRepository.browserSearchEngine } returns flowOf("duckduckgo")
        every { settingsRepository.browserAutoDetect } returns flowOf(true)
        every { settingsRepository.browserDesktopModeDefault } returns flowOf(false)
        every { settingsRepository.browserAdBlockEnabled } returns flowOf(true)
        every { settingsRepository.browserLastSessionTabs } returns flowOf(null)
        every { settingsRepository.browserLastActiveTabId } returns flowOf(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(handle: SavedStateHandle = SavedStateHandle()): BrowserViewModel {
        return BrowserViewModel(
            savedStateHandle = handle,
            mediaSniffer = mediaSniffer,
            mediaProber = mediaProber,
            downloadManager = downloadManager,
            browserRepository = browserRepository,
            settingsRepository = settingsRepository,
            authCookieManager = authCookieManager
        )
    }

    @Test
    fun defaultInitialState_loadsStartPage() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(START_PAGE_URL, state.currentUrl)
        assertEquals("", state.inputUrl)
        assertTrue(state.isStartPage)
        assertTrue(state.isSecure)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertEquals(1, state.tabs.size)
    }

    @Test
    fun passedUrl_loadsSpecifiedUrl() {
        val handle = SavedStateHandle(mapOf("url" to "https://example.com/test"))
        val viewModel = createViewModel(handle)
        val state = viewModel.uiState.value

        assertEquals("https://example.com/test", state.currentUrl)
        assertEquals("https://example.com/test", state.inputUrl)
    }

    @Test
    fun resolveUrlOrSearch_handlesDirectUrls() {
        val viewModel = createViewModel()

        assertEquals("https://example.com", viewModel.resolveUrlOrSearch("https://example.com"))
        assertEquals("http://insecure.site", viewModel.resolveUrlOrSearch("http://insecure.site"))
    }

    @Test
    fun resolveUrlOrSearch_handlesDomainsWithoutScheme() {
        val viewModel = createViewModel()

        assertEquals("https://wikipedia.org", viewModel.resolveUrlOrSearch("wikipedia.org"))
        assertEquals("https://github.com/trending", viewModel.resolveUrlOrSearch("github.com/trending"))
    }

    @Test
    fun resolveUrlOrSearch_handlesSearchQueries() {
        val viewModel = createViewModel()

        val resolved = viewModel.resolveUrlOrSearch("open source android downloader")
        assertTrue(resolved.startsWith("https://duckduckgo.com/?q="))
        assertTrue(resolved.contains("open+source+android+downloader"))

        val googleSearch = viewModel.resolveUrlOrSearch("hello world", com.glypdl.android.data.model.SearchEngine.GOOGLE)
        assertEquals("https://www.google.com/search?q=hello+world", googleSearch)
    }

    @Test
    fun setSearchEngine_updatesActiveEngine() {
        val viewModel = createViewModel()
        viewModel.setSearchEngine(com.glypdl.android.data.model.SearchEngine.GOOGLE)
        assertEquals(com.glypdl.android.data.model.SearchEngine.GOOGLE, viewModel.uiState.value.currentSearchEngine)
        val search = viewModel.resolveUrlOrSearch("test query")
        assertEquals("https://www.google.com/search?q=test+query", search)
    }

    @Test
    fun tabsManagement_openSwitchClose() {
        val viewModel = createViewModel()

        assertEquals(1, viewModel.uiState.value.tabs.size)
        val tab1Id = viewModel.uiState.value.activeTabId

        // Open new tab
        viewModel.openNewTab("https://wikipedia.org")
        assertEquals(2, viewModel.uiState.value.tabs.size)
        val tab2Id = viewModel.uiState.value.activeTabId
        assertTrue(tab1Id != tab2Id)
        assertEquals("https://wikipedia.org", viewModel.uiState.value.currentUrl)

        // Switch back to tab 1
        viewModel.switchTab(tab1Id)
        assertEquals(tab1Id, viewModel.uiState.value.activeTabId)
        assertEquals(START_PAGE_URL, viewModel.uiState.value.currentUrl)

        // Close tab 2
        viewModel.closeTab(tab2Id)
        assertEquals(1, viewModel.uiState.value.tabs.size)
    }

    @Test
    fun bookmarks_toggle() {
        val viewModel = createViewModel()
        coEvery { browserRepository.isBookmarked("https://example.com") } returns false

        viewModel.onPageStarted("https://example.com")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.toggleBookmarkCurrentPage()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { browserRepository.addBookmark("https://example.com", any()) }
        assertTrue(viewModel.uiState.value.isBookmarked)
    }

    @Test
    fun findInPage_lifecycle() {
        val viewModel = createViewModel()

        viewModel.showFindInPage()
        assertTrue(viewModel.uiState.value.isFindInPageVisible)

        viewModel.onFindQueryChange("download")
        assertEquals("download", viewModel.uiState.value.findQuery)

        viewModel.onFindResult(2, 5)
        assertEquals(2, viewModel.uiState.value.findCurrentIndex)
        assertEquals(5, viewModel.uiState.value.findTotalMatches)

        viewModel.hideFindInPage()
        assertFalse(viewModel.uiState.value.isFindInPageVisible)
        assertEquals("", viewModel.uiState.value.findQuery)
    }

    @Test
    fun pageLifecycle_updatesStateCorrectly() {
        val viewModel = createViewModel()

        viewModel.onPageStarted("https://example.com")
        var state = viewModel.uiState.value
        assertEquals("https://example.com", state.currentUrl)
        assertTrue(state.isLoading)
        assertTrue(state.isSecure)

        viewModel.onProgressChanged(75)
        assertEquals(75, viewModel.uiState.value.progress)
        assertTrue(viewModel.uiState.value.isLoading)

        viewModel.onTitleReceived("Example Domain")
        assertEquals("Example Domain", viewModel.uiState.value.title)

        viewModel.onProgressChanged(100)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.onPageFinished("https://example.com", canBack = true, canForward = false)
        testDispatcher.scheduler.advanceUntilIdle()
        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)

        coVerify { browserRepository.recordVisit("https://example.com", "Example Domain") }
    }

    @Test
    fun onNetworkResourceIntercepted_detectsMedia() {
        val viewModel = createViewModel()
        viewModel.onPageStarted("https://example.com")

        viewModel.onNetworkResourceIntercepted(
            url = "https://example.com/video.mp4",
            headers = mapOf("Referer" to "https://example.com")
        )

        val detected = viewModel.detectedMedia.value
        assertEquals(1, detected.size)
        assertEquals("https://example.com/video.mp4", detected[0].url)
        assertEquals(MediaKind.PROGRESSIVE_VIDEO, detected[0].kind)
    }

    @Test
    fun downloadDetectedMedia_enqueuesViaDownloadManager() {
        val viewModel = createViewModel()
        val media = DetectedMedia(
            id = "test-media-1",
            url = "https://example.com/clip.mp4",
            pageUrl = "https://example.com",
            pageTitle = "Exciting Clip",
            kind = MediaKind.PROGRESSIVE_VIDEO,
            ext = "mp4"
        )

        viewModel.downloadDetectedMedia(media)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify {
            downloadManager.enqueueDownload(match {
                it.url == "https://example.com/clip.mp4" &&
                it.title.contains("Exciting Clip") &&
                it.sourceKind == "BROWSER_DIRECT"
            })
        }
    }

    @Test
    fun desktopModeToggle() {
        val viewModel = createViewModel()
        assertFalse(viewModel.uiState.value.isDesktopMode)

        viewModel.toggleDesktopMode()
        assertTrue(viewModel.uiState.value.isDesktopMode)

        viewModel.toggleDesktopMode()
        assertFalse(viewModel.uiState.value.isDesktopMode)
    }

    @Test
    fun userAgentSanitization_andDesktopMode() {
        val mobileUa = "Mozilla/5.0 (Linux; U; Android 14; en-US; Pixel 8 Build/UD1A.230803.041; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120.0.6099.144 Mobile Safari/537.36"
        val sanitized = GlypdlWebViewFactory.sanitizeUserAgent(mobileUa)
        assertFalse(sanitized.contains("; wv"))

        val desktopUa = GlypdlWebViewFactory.getDesktopUserAgent(mobileUa)
        assertFalse(desktopUa.contains("; wv"))
        assertTrue(desktopUa.contains("X11; Linux x86_64"))
        assertFalse(desktopUa.contains("Mobile Safari"))
    }

    @Test
    fun `incognito tab does not record history visits`() {
        val viewModel = createViewModel()
        viewModel.openNewTab("https://duckduckgo.com", isIncognito = true)

        val state = viewModel.uiState.value
        assertTrue(state.isIncognito)

        viewModel.onPageFinished("https://private-example.com", canBack = false, canForward = false)
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify(exactly = 0) { browserRepository.recordVisit("https://private-example.com", any()) }
    }

    @Test
    fun `toggleAdBlock toggles setting in repository`() {
        val viewModel = createViewModel()
        coEvery { settingsRepository.setBrowserAdBlockEnabled(any()) } returns Unit

        viewModel.toggleAdBlock()
        testDispatcher.scheduler.advanceUntilIdle()

        coVerify { settingsRepository.setBrowserAdBlockEnabled(false) }
    }

    @Test
    fun `onUrlChangedViaJs updates active tab url, currentUrl, and title`() {
        val viewModel = createViewModel()
        viewModel.onPageStarted("https://m.youtube.com/")
        assertEquals("https://m.youtube.com/", viewModel.uiState.value.currentUrl)

        viewModel.onUrlChangedViaJs("https://m.youtube.com/shorts/OnJPmKH", "Exciting Short Video")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("https://m.youtube.com/shorts/OnJPmKH", state.currentUrl)
        assertEquals("https://m.youtube.com/shorts/OnJPmKH", state.inputUrl)
        assertEquals("Exciting Short Video", state.title)

        coVerify { browserRepository.recordVisit("https://m.youtube.com/shorts/OnJPmKH", "Exciting Short Video") }
    }
}
