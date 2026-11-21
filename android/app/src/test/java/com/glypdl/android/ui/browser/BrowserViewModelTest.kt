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
import com.glypdl.android.service.DownloadManager
import com.glypdl.android.service.browser.GlypdlWebViewFactory
import com.glypdl.android.service.browser.MediaProber
import com.glypdl.android.service.browser.MediaSniffer
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        mediaProber = mockk(relaxed = true)
        mediaSniffer = MediaSniffer(mediaProber)
        downloadManager = mockk(relaxed = true)
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
            downloadManager = downloadManager
        )
    }

    @Test
    fun defaultInitialState_loadsDuckDuckGo() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals("https://duckduckgo.com", state.currentUrl)
        assertEquals("https://duckduckgo.com", state.inputUrl)
        assertTrue(state.isSecure)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
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
        state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.canGoBack)
        assertFalse(state.canGoForward)
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
    fun onDomMediaReported_addsCandidate() {
        val viewModel = createViewModel()
        viewModel.onPageStarted("https://example.com")

        val payload = """
            {
                "url": "https://example.com/audio.mp3",
                "title": "Sample Song",
                "mime": "audio/mpeg"
            }
        """.trimIndent()
        viewModel.onDomMediaReported(payload)

        val detected = viewModel.detectedMedia.value
        assertEquals(1, detected.size)
        assertEquals("https://example.com/audio.mp3", detected[0].url)
        assertEquals(MediaKind.PROGRESSIVE_AUDIO, detected[0].kind)
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
    fun httpPage_marksAsInsecure() {
        val viewModel = createViewModel()

        viewModel.onPageStarted("http://example.com")
        assertFalse(viewModel.uiState.value.isSecure)
    }

    @Test
    fun errorAndSslHandling() {
        val viewModel = createViewModel()

        viewModel.onErrorReceived("Connection timeout")
        assertEquals("Connection timeout", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.errorMessage)

        viewModel.onSslError("https://self-signed.badssl.com")
        assertEquals("https://self-signed.badssl.com", viewModel.uiState.value.sslErrorDialogUrl)

        viewModel.dismissSslDialog()
        assertNull(viewModel.uiState.value.sslErrorDialogUrl)
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
}
