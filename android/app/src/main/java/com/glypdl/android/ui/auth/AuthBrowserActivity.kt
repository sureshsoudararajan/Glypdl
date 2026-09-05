/*
 * Glypdl - Media Downloader
 * Copyright (C) 2024 Glypdl Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */
package com.glypdl.android.ui.auth

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.glypdl.android.service.auth.AuthCookieManager
import com.glypdl.android.ui.theme.GlypdlTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

import android.view.ViewGroup
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest

@AndroidEntryPoint
class AuthBrowserActivity : ComponentActivity() {

    @Inject
    lateinit var authCookieManager: AuthCookieManager

    private var webView: WebView? = null

    companion object {
        const val EXTRA_LOGIN_URL = "extra_login_url"
        const val EXTRA_DOMAIN = "extra_domain"
        const val EXTRA_TITLE = "extra_title"

        fun createIntent(context: Context, loginUrl: String, domain: String, title: String): Intent {
            return Intent(context, AuthBrowserActivity::class.java).apply {
                putExtra(EXTRA_LOGIN_URL, loginUrl)
                putExtra(EXTRA_DOMAIN, domain)
                putExtra(EXTRA_TITLE, title)
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loginUrl = intent.getStringExtra(EXTRA_LOGIN_URL) ?: "https://www.instagram.com/accounts/login/"
        val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: "instagram.com"
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Log In"

        setContent {
            var pageTitle by remember { mutableStateOf(title) }
            var pageProgress by remember { mutableIntStateOf(0) }
            var hasDetectedSession by remember { mutableStateOf(false) }
            var pageError by remember { mutableStateOf<String?>(null) }

            GlypdlTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = {
                                Column {
                                    Text(
                                        text = pageTitle,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = domain,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            navigationIcon = {
                                IconButton(onClick = {
                                    if (webView?.canGoBack() == true) {
                                        webView?.goBack()
                                    } else {
                                        finish()
                                    }
                                }) {
                                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                                }
                            },
                            actions = {
                                IconButton(onClick = {
                                    pageError = null
                                    webView?.reload()
                                }) {
                                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                                }
                                Button(
                                    onClick = {
                                        saveCookiesAndFinish(domain, loginUrl)
                                    },
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Save Session")
                                }
                            }
                        )
                    }
                ) { paddingValues ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    this@AuthBrowserActivity.webView = this
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
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        setSupportZoom(true)
                                        builtInZoomControls = true
                                        displayZoomControls = false
                                        javaScriptCanOpenWindowsAutomatically = true
                                        setSupportMultipleWindows(false)
                                        // Remove the "; wv" indicator to prevent sites (e.g. Instagram) from blocking WebView
                                        val defaultUa = userAgentString
                                        userAgentString = defaultUa.replace("; wv", "")
                                    }
                                    CookieManager.getInstance().setAcceptCookie(true)
                                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            pageProgress = newProgress
                                        }

                                        override fun onReceivedTitle(view: WebView?, newTitle: String?) {
                                            if (!newTitle.isNullOrBlank() && !newTitle.startsWith("http")) {
                                                pageTitle = newTitle
                                            }
                                        }
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val scheme = request?.url?.scheme?.lowercase() ?: return false
                                            if (scheme == "http" || scheme == "https") {
                                                return false
                                            }
                                            // Intercept deep links (e.g. intent://, instagram://) to keep browsing in WebView
                                            return true
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            pageError = null
                                            checkSessionCookies(url ?: loginUrl, domain) { detected ->
                                                hasDetectedSession = detected
                                            }
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            CookieManager.getInstance().flush()
                                            checkSessionCookies(url ?: loginUrl, domain) { detected ->
                                                hasDetectedSession = detected
                                            }
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                pageError = error?.description?.toString() ?: "Failed to connect to page"
                                            }
                                        }
                                    }

                                    loadUrl(loginUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (pageProgress in 1..99) {
                            LinearProgressIndicator(
                                progress = { pageProgress / 100f },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.TopCenter)
                            )
                        }

                        if (hasDetectedSession) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.BottomCenter)
                                    .padding(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Session detected! Tap 'Save Session' above to finish.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }

                        pageError?.let { err ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .align(Alignment.Center)
                                    .padding(24.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(36.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = err,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(
                                        onClick = {
                                            pageError = null
                                            webView?.reload()
                                        }
                                    ) {
                                        Text("Retry")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun checkSessionCookies(url: String, domain: String, onResult: (Boolean) -> Unit) {
        val urlsToProbe = listOf(
            url,
            "https://$domain",
            "https://www.$domain",
            "https://m.$domain"
        ).distinct()

        var allCookies = ""
        for (probeUrl in urlsToProbe) {
            val c = CookieManager.getInstance().getCookie(probeUrl)
            if (!c.isNullOrBlank()) {
                allCookies += " $c"
            }
        }

        val hasSession = when {
            domain.contains("instagram.com") -> allCookies.contains("sessionid=")
            domain.contains("facebook.com") -> allCookies.contains("c_user=") || allCookies.contains("xs=")
            domain.contains("youtube.com") -> allCookies.contains("SAPISID=") || allCookies.contains("SSID=")
            domain.contains("tiktok.com") -> allCookies.contains("sessionid=")
            domain.contains("twitter.com") -> allCookies.contains("auth_token=")
            else -> allCookies.length > 20
        }
        onResult(hasSession)
    }

    private fun saveCookiesAndFinish(domain: String, url: String) {
        CookieManager.getInstance().flush()
        val currentUrl = webView?.url ?: url
        val cookieMap = mutableMapOf<String, String>()

        val urlsToProbe = listOf(
            currentUrl,
            if (!url.startsWith("http")) "https://$url" else url,
            "https://$domain",
            "https://www.$domain",
            "https://m.$domain"
        ).distinct()

        for (probeUrl in urlsToProbe) {
            val c = CookieManager.getInstance().getCookie(probeUrl)
            if (!c.isNullOrBlank()) {
                c.split(";").forEach { pair ->
                    val trimmed = pair.trim()
                    if (trimmed.contains("=")) {
                        val k = trimmed.substringBefore("=").trim()
                        val v = trimmed.substringAfter("=").trim()
                        if (k.isNotEmpty() && v.isNotEmpty()) {
                            cookieMap[k] = v
                        }
                    }
                }
            }
        }

        if (cookieMap.isNotEmpty()) {
            val combinedCookies = cookieMap.entries.joinToString("; ") { "${it.key}=${it.value}" }
            val key = authCookieManager.resolveDomainKey(domain)
            authCookieManager.saveCookies(key, combinedCookies)
            Toast.makeText(this, "Session saved for $key", Toast.LENGTH_SHORT).show()
            setResult(Activity.RESULT_OK)
            finish()
        } else {
            Toast.makeText(this, "No cookies detected. Please log in first.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        super.onDestroy()
    }
}
