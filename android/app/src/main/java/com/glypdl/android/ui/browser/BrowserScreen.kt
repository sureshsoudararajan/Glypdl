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

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.glypdl.android.service.browser.BrowserScripts
import com.glypdl.android.service.browser.GlypdlWebViewFactory
import com.glypdl.android.ui.browser.components.AddressBar
import com.glypdl.android.ui.browser.components.DetectedMediaSheet

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BrowserScreen(
    viewModel: BrowserViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAnalyze: (String) -> Unit,
    onNavigateToDownloads: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val detectedMediaList by viewModel.detectedMedia.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    BackHandler(enabled = true) {
        if (webViewRef?.canGoBack() == true) {
            webViewRef?.goBack()
        } else {
            onNavigateBack()
        }
    }

    // Handle desktop mode toggle
    LaunchedEffect(uiState.isDesktopMode) {
        webViewRef?.let { wv ->
            val defaultUa = WebSettings.getDefaultUserAgent(context)
            if (uiState.isDesktopMode) {
                wv.settings.userAgentString = GlypdlWebViewFactory.getDesktopUserAgent(defaultUa)
            } else {
                wv.settings.userAgentString = GlypdlWebViewFactory.sanitizeUserAgent(defaultUa)
            }
            wv.reload()
        }
    }

    // Handle snackbar notifications
    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            val result = snackbarHostState.showSnackbar(
                message = msg,
                actionLabel = "View",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                onNavigateToDownloads()
            }
            viewModel.clearSnackbarMessage()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                AddressBar(
                    url = uiState.inputUrl,
                    isLoading = uiState.isLoading,
                    isSecure = uiState.isSecure,
                    isDesktopMode = uiState.isDesktopMode,
                    onUrlChange = { viewModel.onInputUrlChange(it) },
                    onSubmitUrl = { target ->
                        viewModel.onSubmitInput(target)
                        val resolved = viewModel.resolveUrlOrSearch(target)
                        webViewRef?.loadUrl(resolved)
                    },
                    onReload = {
                        viewModel.clearError()
                        webViewRef?.reload()
                    },
                    onStop = { webViewRef?.stopLoading() },
                    onToggleDesktop = { viewModel.toggleDesktopMode() },
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, uiState.currentUrl)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Link"))
                    },
                    onOpenExternal = {
                        try {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(uiState.currentUrl))
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Could not open external browser", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCopyUrl = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("URL", uiState.currentUrl))
                        Toast.makeText(context, "Link copied to clipboard", Toast.LENGTH_SHORT).show()
                    }
                )

                if (uiState.isLoading) {
                    LinearProgressIndicator(
                        progress = { uiState.progress / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(3.dp),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { webViewRef?.goBack() },
                        enabled = uiState.canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }

                    IconButton(
                        onClick = { webViewRef?.goForward() },
                        enabled = uiState.canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }

                    IconButton(
                        onClick = {
                            viewModel.onSubmitInput("https://duckduckgo.com")
                            webViewRef?.loadUrl("https://duckduckgo.com")
                        }
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }

                    // Layer C: Download this page with yt-dlp
                    FilledTonalButton(
                        onClick = { onNavigateToAnalyze(uiState.currentUrl) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Download Page", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = detectedMediaList.isNotEmpty(),
                enter = scaleIn() + fadeIn(),
                exit = scaleOut() + fadeOut()
            ) {
                BadgedBox(
                    badge = {
                        Badge {
                            Text("${detectedMediaList.size}")
                        }
                    }
                ) {
                    FloatingActionButton(
                        onClick = { viewModel.showMediaSheet() },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ) {
                        Icon(Icons.Default.Download, contentDescription = "View detected media")
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    GlypdlWebViewFactory.createWebView(ctx).apply {
                        webViewRef = this

                        // Layer B: Add Javascript Interface for DOM reported media
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun reportMedia(jsonPayload: String) {
                                    post {
                                        viewModel.onDomMediaReported(jsonPayload)
                                    }
                                }
                            },
                            BrowserScripts.JS_INTERFACE_NAME
                        )

                        // If DOCUMENT_START_SCRIPT is supported, inject early
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                            try {
                                WebViewCompat.addDocumentStartJavaScript(
                                    this,
                                    BrowserScripts.INJECTION_SCRIPT,
                                    setOf("*")
                                )
                            } catch (_: Exception) {}
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                viewModel.onProgressChanged(newProgress)
                            }

                            override fun onReceivedTitle(view: WebView?, title: String?) {
                                title?.let { viewModel.onTitleReceived(it) }
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            // Layer A: Intercept network resource requests
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                request?.url?.let { uri ->
                                    val urlStr = uri.toString()
                                    val headers = request.requestHeaders ?: emptyMap()
                                    viewModel.onNetworkResourceIntercepted(urlStr, headers)
                                }
                                return null // Always allow WebView to load resource normally
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val urlStr = request?.url?.toString() ?: return false
                                val scheme = request.url.scheme?.lowercase() ?: return false

                                if (scheme == "http" || scheme == "https") {
                                    return false
                                }

                                try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlStr))
                                    ctx.startActivity(intent)
                                } catch (e: Exception) {}
                                return true
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let { viewModel.onPageStarted(it) }
                                view?.evaluateJavascript(BrowserScripts.INJECTION_SCRIPT, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                CookieManager.getInstance().flush()
                                view?.evaluateJavascript(BrowserScripts.INJECTION_SCRIPT, null)
                                url?.let {
                                    viewModel.onPageFinished(
                                        url = it,
                                        canBack = view?.canGoBack() == true,
                                        canForward = view?.canGoForward() == true
                                    )
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    viewModel.onErrorReceived(
                                        error?.description?.toString() ?: "Failed to connect to page"
                                    )
                                }
                            }

                            override fun onReceivedSslError(
                                view: WebView?,
                                handler: SslErrorHandler?,
                                error: SslError?
                            ) {
                                handler?.cancel()
                                viewModel.onSslError(error?.url ?: uiState.currentUrl)
                            }
                        }

                        loadUrl(uiState.currentUrl)
                    }
                }
            )

            // Error display overlay
            AnimatedVisibility(
                visible = uiState.errorMessage != null,
                modifier = Modifier.align(Alignment.Center)
            ) {
                uiState.errorMessage?.let { errorText ->
                    Card(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Unable to Load Page",
                                style = MaterialTheme.typography.titleMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = errorText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.clearError()
                                    webViewRef?.reload()
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry")
                            }
                        }
                    }
                }
            }
        }
    }

    // Detected Media BottomSheet
    if (uiState.isMediaSheetVisible) {
        DetectedMediaSheet(
            mediaList = detectedMediaList,
            onDownloadMedia = { media ->
                viewModel.downloadDetectedMedia(media)
            },
            onAnalyzeMedia = { media ->
                viewModel.hideMediaSheet()
                onNavigateToAnalyze(media.url)
            },
            onDismiss = { viewModel.hideMediaSheet() }
        )
    }

    // SSL Error Interstitial Dialog
    if (uiState.sslErrorDialogUrl != null) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSslDialog() },
            icon = {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Security Warning") },
            text = {
                Text(
                    "The security certificate for this site is not trusted or is invalid.\n\nTo protect your personal data and passwords, the connection was blocked."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissSslDialog()
                        if (webViewRef?.canGoBack() == true) {
                            webViewRef?.goBack()
                        } else {
                            onNavigateBack()
                        }
                    }
                ) {
                    Text("Go Back Safely")
                }
            }
        )
    }
}
