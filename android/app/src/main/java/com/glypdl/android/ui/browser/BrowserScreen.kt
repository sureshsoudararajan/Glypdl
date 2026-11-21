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
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.glypdl.android.data.model.MediaKind
import com.glypdl.android.service.browser.AdBlocker
import com.glypdl.android.service.browser.BrowserScripts
import com.glypdl.android.service.browser.GlypdlWebViewFactory
import com.glypdl.android.service.browser.MediaClassifier
import com.glypdl.android.ui.browser.components.*

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
    val bookmarks by viewModel.bookmarks.collectAsState()
    val history by viewModel.history.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var customView by remember { mutableStateOf<View?>(null) }
    var customViewCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    LaunchedEffect(Unit) {
        AdBlocker.init(context)
    }

    BackHandler(enabled = true) {
        if (customView != null) {
            customViewCallback?.onCustomViewHidden()
            customView = null
            customViewCallback = null
        } else if (uiState.isFindInPageVisible) {
            webViewRef?.clearMatches()
            viewModel.hideFindInPage()
        } else if (webViewRef?.canGoBack() == true) {
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

    // Handle active tab changes
    LaunchedEffect(uiState.activeTabId) {
        webViewRef?.let { wv ->
            if (uiState.isStartPage) {
                wv.loadUrl("about:blank")
            } else if (wv.url != uiState.currentUrl) {
                wv.loadUrl(uiState.currentUrl)
            }
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
                if (uiState.isFindInPageVisible) {
                    FindInPageBar(
                        query = uiState.findQuery,
                        currentIndex = uiState.findCurrentIndex,
                        totalMatches = uiState.findTotalMatches,
                        onQueryChange = { q ->
                            viewModel.onFindQueryChange(q)
                            webViewRef?.findAllAsync(q)
                        },
                        onNextMatch = { webViewRef?.findNext(true) },
                        onPrevMatch = { webViewRef?.findNext(false) },
                        onClose = {
                            webViewRef?.clearMatches()
                            viewModel.hideFindInPage()
                        }
                    )
                } else {
                    AddressBar(
                        url = uiState.inputUrl,
                        isLoading = uiState.isLoading,
                        isSecure = uiState.isSecure,
                        isDesktopMode = uiState.isDesktopMode,
                        isBookmarked = uiState.isBookmarked,
                        isIncognito = uiState.isIncognito,
                        isAdBlockEnabled = uiState.isAdBlockEnabled,
                        currentSearchEngine = uiState.currentSearchEngine,
                        onSearchEngineChange = { viewModel.setSearchEngine(it) },
                        onUrlChange = { viewModel.onInputUrlChange(it) },
                        onSubmitUrl = { target ->
                            viewModel.onSubmitInput(target)
                            val resolved = viewModel.resolveUrlOrSearch(target)
                            if (resolved != START_PAGE_URL) {
                                webViewRef?.loadUrl(resolved)
                            }
                        },
                        onReload = {
                            viewModel.clearError()
                            webViewRef?.reload()
                        },
                        onStop = { webViewRef?.stopLoading() },
                        onToggleDesktop = { viewModel.toggleDesktopMode() },
                        onToggleBookmark = { viewModel.toggleBookmarkCurrentPage() },
                        onToggleAdBlock = { viewModel.toggleAdBlock() },
                        onOpenBookmarksHistory = { viewModel.showBookmarksHistory() },
                        onFindInPage = {
                            viewModel.showFindInPage()
                            webViewRef?.setFindListener { activeMatchOrdinal, numberOfMatches, _ ->
                                viewModel.onFindResult(activeMatchOrdinal, numberOfMatches)
                            }
                        },
                        onClearBrowsingData = { viewModel.clearAllBrowsingData(context) },
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
                        },
                        onExitBrowser = onNavigateBack
                    )
                }

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
                            viewModel.navigateToStartPage()
                            webViewRef?.loadUrl("about:blank")
                        }
                    ) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }

                    // Tabs Count Button
                    IconButton(onClick = { viewModel.showTabSwitcher() }) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .border(
                                    width = 2.dp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    shape = RoundedCornerShape(7.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (uiState.tabs.size > 99) "99+" else "${uiState.tabs.size}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                fontSize = if (uiState.tabs.size >= 10) 10.sp else 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    // Layer C: Download this page with yt-dlp
                    FilledTonalButton(
                        onClick = {
                            val activeUrl = webViewRef?.url?.takeIf {
                                it.isNotBlank() && !it.startsWith("about:") && !it.startsWith("glypdl:")
                            } ?: uiState.currentUrl
                            onNavigateToAnalyze(activeUrl)
                        },
                        enabled = !uiState.isStartPage && (webViewRef?.url?.startsWith("http") == true || uiState.currentUrl.startsWith("http")),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Page", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = detectedMediaList.isNotEmpty() && !uiState.isStartPage,
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

                        // Long press link/image handler
                        setOnLongClickListener {
                            val result = hitTestResult
                            val extra = result.extra
                            if (!extra.isNullOrBlank()) {
                                val kind = MediaClassifier.classify(extra)
                                if (kind != null) {
                                    viewModel.onNetworkResourceIntercepted(extra, emptyMap())
                                    Toast.makeText(ctx, "Media link captured", Toast.LENGTH_SHORT).show()
                                    return@setOnLongClickListener true
                                }
                            }
                            false
                        }

                        // Layer B: Add Javascript Interface for DOM reported media and SPA URL changes
                        addJavascriptInterface(
                            object {
                                @JavascriptInterface
                                fun reportMedia(jsonPayload: String) {
                                    post {
                                        viewModel.onDomMediaReported(jsonPayload)
                                    }
                                }

                                @JavascriptInterface
                                fun reportUrlChange(newUrl: String, title: String?) {
                                    post {
                                        viewModel.onUrlChangedViaJs(newUrl, title)
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

                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                customView = view
                                customViewCallback = callback
                            }

                            override fun onHideCustomView() {
                                customView = null
                                customViewCallback = null
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            // Layer A: Intercept network resource requests
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                if (request == null) return null
                                // CRITICAL: Never block or intercept main frame navigation (prevents blank white screen)
                                if (request.isForMainFrame) {
                                    return null
                                }

                                val uri = request.url ?: return null
                                val urlStr = uri.toString()

                                // Block ads and intrusive tracking for subresources
                                if (uiState.isAdBlockEnabled && AdBlocker.isAd(urlStr)) {
                                    return WebResourceResponse(
                                        "text/plain",
                                        "UTF-8",
                                        java.io.ByteArrayInputStream(ByteArray(0))
                                    )
                                }

                                val headers = request.requestHeaders ?: emptyMap()
                                viewModel.onNetworkResourceIntercepted(urlStr, headers)
                                return null
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

                        if (!uiState.isStartPage) {
                            loadUrl(uiState.currentUrl)
                        }
                    }
                }
            )

            // Custom Glypdl Start Page (Opera-style Home)
            if (uiState.isStartPage) {
                GlypdlStartPage(
                    currentSearchEngine = uiState.currentSearchEngine,
                    onSearchEngineChange = { viewModel.setSearchEngine(it) },
                    onSubmitQuery = { query ->
                        val resolved = viewModel.resolveUrlOrSearch(query)
                        viewModel.onSubmitInput(query)
                        if (resolved != START_PAGE_URL) {
                            webViewRef?.loadUrl(resolved)
                        }
                    },
                    onOpenBookmarks = { viewModel.showBookmarksHistory(initialTab = 0) },
                    onOpenHistory = { viewModel.showBookmarksHistory(initialTab = 1) },
                    onOpenDownloads = onNavigateToDownloads,
                    modifier = Modifier.fillMaxSize()
                )
            }

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
                if (media.variants.isNotEmpty()) {
                    viewModel.onSelectQualityMedia(media)
                } else {
                    viewModel.downloadDetectedMedia(media)
                }
            },
            onAnalyzeMedia = { media ->
                viewModel.hideMediaSheet()
                onNavigateToAnalyze(media.pageUrl.ifBlank { media.url })
            },
            onDismiss = { viewModel.hideMediaSheet() }
        )
    }

    // Quality & Rendition Picker Sheet
    uiState.selectedQualityMedia?.let { qualityMedia ->
        QualityPickerSheet(
            media = qualityMedia,
            onConfirmDownload = { request ->
                viewModel.enqueueDownloadRequest(request)
            },
            onAnalyzePage = { pageUrl ->
                onNavigateToAnalyze(pageUrl)
            },
            onDismiss = { viewModel.dismissQualityPicker() }
        )
    }

    // Tab Switcher BottomSheet
    if (uiState.isTabSwitcherVisible) {
        TabSwitcher(
            tabs = uiState.tabs,
            activeTabId = uiState.activeTabId,
            onSelectTab = { tabId -> viewModel.switchTab(tabId) },
            onCloseTab = { tabId -> viewModel.closeTab(tabId) },
            onNewTab = { viewModel.openNewTab() },
            onNewIncognitoTab = { viewModel.openNewTab(isIncognito = true) },
            onCloseAllTabs = { viewModel.closeAllTabs() },
            onDismiss = { viewModel.hideTabSwitcher() }
        )
    }

    // Bookmarks and History BottomSheet
    if (uiState.isBookmarksHistoryVisible) {
        BookmarksHistorySheet(
            initialTab = uiState.bookmarksHistoryInitialTab,
            bookmarks = bookmarks,
            history = history,
            onSelectUrl = { targetUrl ->
                viewModel.onSubmitInput(targetUrl)
                webViewRef?.loadUrl(viewModel.resolveUrlOrSearch(targetUrl))
                viewModel.hideBookmarksHistory()
            },
            onDeleteBookmark = { id -> viewModel.deleteBookmark(id) },
            onDeleteHistoryItem = { id -> viewModel.deleteHistoryItem(id) },
            onClearHistory = { viewModel.clearHistory() },
            onDismiss = { viewModel.hideBookmarksHistory() }
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

    // Fullscreen HTML5 Video Custom View Overlay
    if (customView != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { customView!! },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
