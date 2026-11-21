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
import com.glypdl.android.util.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
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
    val sslErrorDialogUrl: String? = null
)

@HiltViewModel
class BrowserViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle
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

    fun resolveUrlOrSearch(input: String): String {
        if (input.isBlank()) return "https://duckduckgo.com"

        if (input.startsWith("https://", ignoreCase = true) || input.startsWith("http://", ignoreCase = true)) {
            return input
        }

        // Check if user entered something like domain.com or domain.com/path
        val domainPattern = Regex("^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}(/.*)?$")
        if (domainPattern.matches(input) && !input.contains(" ")) {
            return "https://$input"
        }

        // Otherwise resolve as DuckDuckGo search query
        val encoded = URLEncoder.encode(input, StandardCharsets.UTF_8.toString())
        return "https://duckduckgo.com/?q=$encoded"
    }
}
