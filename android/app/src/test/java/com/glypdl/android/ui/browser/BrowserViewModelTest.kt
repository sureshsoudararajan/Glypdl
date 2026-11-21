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
import com.glypdl.android.service.browser.GlypdlWebViewFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserViewModelTest {

    @Test
    fun defaultInitialState_loadsDuckDuckGo() {
        val viewModel = BrowserViewModel(SavedStateHandle())
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
        val viewModel = BrowserViewModel(handle)
        val state = viewModel.uiState.value

        assertEquals("https://example.com/test", state.currentUrl)
        assertEquals("https://example.com/test", state.inputUrl)
    }

    @Test
    fun resolveUrlOrSearch_handlesDirectUrls() {
        val viewModel = BrowserViewModel(SavedStateHandle())

        assertEquals("https://example.com", viewModel.resolveUrlOrSearch("https://example.com"))
        assertEquals("http://insecure.site", viewModel.resolveUrlOrSearch("http://insecure.site"))
    }

    @Test
    fun resolveUrlOrSearch_handlesDomainsWithoutScheme() {
        val viewModel = BrowserViewModel(SavedStateHandle())

        assertEquals("https://wikipedia.org", viewModel.resolveUrlOrSearch("wikipedia.org"))
        assertEquals("https://github.com/trending", viewModel.resolveUrlOrSearch("github.com/trending"))
    }

    @Test
    fun resolveUrlOrSearch_handlesSearchQueries() {
        val viewModel = BrowserViewModel(SavedStateHandle())

        val resolved = viewModel.resolveUrlOrSearch("open source android downloader")
        assertTrue(resolved.startsWith("https://duckduckgo.com/?q="))
        assertTrue(resolved.contains("open+source+android+downloader"))
    }

    @Test
    fun pageLifecycle_updatesStateCorrectly() {
        val viewModel = BrowserViewModel(SavedStateHandle())

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
    fun httpPage_marksAsInsecure() {
        val viewModel = BrowserViewModel(SavedStateHandle())

        viewModel.onPageStarted("http://example.com")
        assertFalse(viewModel.uiState.value.isSecure)
    }

    @Test
    fun errorAndSslHandling() {
        val viewModel = BrowserViewModel(SavedStateHandle())

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
        val viewModel = BrowserViewModel(SavedStateHandle())
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
