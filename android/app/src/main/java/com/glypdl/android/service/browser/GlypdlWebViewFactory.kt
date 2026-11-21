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

package com.glypdl.android.service.browser

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.webkit.WebViewFeature

object GlypdlWebViewFactory {

    /**
     * Creates and returns a securely configured [WebView] instance.
     */
    fun createWebView(context: Context): WebView {
        val webView = WebView(context)
        configureWebView(webView)
        return webView
    }

    /**
     * Applies standard Glypdl security hardening and modern web settings to the given [WebView].
     */
    @SuppressLint("SetJavaScriptEnabled")
    fun configureWebView(webView: WebView) {
        webView.apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            isFocusable = true
            isFocusableInTouchMode = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true

                // Security: block mixed content to protect user credentials and network traffic from MITM
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

                // Security: disable local file system access from WebView
                allowFileAccess = false
                allowContentAccess = false

                useWideViewPort = true
                loadWithOverviewMode = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false

                // Safe Browsing
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }

                // Strip "; wv" to present a standard mobile Chrome User-Agent and prevent site blocks
                val defaultUa = userAgentString
                userAgentString = sanitizeUserAgent(defaultUa)
            }

            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(webView, true)
            }
        }
    }

    /**
     * Strips WebView-identifying tokens like "; wv" from the User-Agent string.
     */
    fun sanitizeUserAgent(userAgent: String): String {
        return userAgent.replace("; wv", "")
    }

    /**
     * Returns a Desktop version of the current User-Agent.
     */
    fun getDesktopUserAgent(currentUa: String): String {
        return sanitizeUserAgent(currentUa)
            .replace("Android", "X11; Linux x86_64")
            .replace("Mobile Safari", "Safari")
    }
}
