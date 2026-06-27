package com.example.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.border

// ...

// Jump to Omnibox area

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewCompat
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewFeature
import com.example.util.AdBlocker
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: MainViewModel, onNavigate: (String) -> Unit = {}) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    var urlInput by remember(activeTabId, activeTab.url) { mutableStateOf(activeTab.url) }
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showEcoMenu by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    var showClipboardVault by remember { mutableStateOf(false) }
    
    val quickAnswer by viewModel.quickAnswer.collectAsStateWithLifecycle()
    val isQuickAnswerLoading by viewModel.isQuickAnswerLoading.collectAsStateWithLifecycle()
    val pageSummary by viewModel.pageSummary.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()

    var activeWebView by remember { mutableStateOf<WebView?>(null) }
    
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activeWebView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    activeWebView?.onPause()
                    activeWebView?.pauseTimers()
                }
                Lifecycle.Event.ON_RESUME, Lifecycle.Event.ON_START -> {
                    activeWebView?.onResume()
                    activeWebView?.resumeTimers()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(urlInput) {
        if (urlInput.isBlank() || (!urlInput.contains(" ") && !urlInput.endsWith("?"))) {
            viewModel.clearQuickAnswer()
            return@LaunchedEffect
        }
        kotlinx.coroutines.delay(800)
        viewModel.fetchQuickAnswer(urlInput)
    }

    if (showTabSwitcher) {
        TabSwitcherScreen(
            tabs = tabs,
            activeTabId = activeTabId,
            onCloseTab = { viewModel.closeTab(it) },
            onSelectTab = { 
                viewModel.switchTab(it)
                showTabSwitcher = false 
            },
            onNewTab = { 
                viewModel.createNewTab()
                showTabSwitcher = false
            },
            onNewIncognitoTab = {
                viewModel.createNewTab(isIncognito = true)
                showTabSwitcher = false
            },
            onCloseSwitcher = { showTabSwitcher = false }
        )
        return
    }

    var uiVisible by remember { mutableStateOf(true) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Omnibox Bar
        androidx.compose.animation.AnimatedVisibility(visible = uiVisible) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 2.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 8.dp)
                    ) {
                    IconButton(onClick = { viewModel.loadUrlInActiveTab("https://duckduckgo.com/lite") }) {
                        Icon(Icons.Default.Home, contentDescription = "Home")
                    }

                    // Compact Address Bar
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp)
                        ) {
                            androidx.compose.foundation.text.BasicTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Go,
                                    keyboardType = KeyboardType.Uri
                                ),
                                keyboardActions = KeyboardActions(
                                    onGo = {
                                        if (urlInput.isNotBlank()) {
                                            val formattedUrl = if (urlInput.startsWith("http")) urlInput else {
                                                if (urlInput.contains(".") && !urlInput.contains(" ")) "https://$urlInput"
                                                else "https://duckduckgo.com/lite/?q=${urlInput.replace(" ", "+")}"
                                            }
                                            viewModel.loadUrlInActiveTab(formattedUrl)
                                        }
                                    }
                                ),
                                decorationBox = { innerTextField ->
                                    if (urlInput.isEmpty()) {
                                        Text("Search or type URL", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    innerTextField()
                                }
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(24.dp)
                            .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(4.dp))
                            .clickable { showTabSwitcher = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            tabs.size.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    var showMoreMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                        }
                        DropdownMenu(
                            expanded = showMoreMenu,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("New Tab") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.createNewTab()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Incognito Tab") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.createNewTab(isIncognito = true)
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Refresh") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.reloadActiveTab()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Summarize Page") },
                                onClick = {
                                    showMoreMenu = false
                                    activeWebView?.evaluateJavascript("(function() { return document.body.innerText; })();") { innerText ->
                                        if (!innerText.isNullOrBlank() && innerText != "null") {
                                            viewModel.summarizeActivePageText(innerText)
                                        } else {
                                            viewModel.summarizeActivePageText("No text found on this page.")
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Save to Offline Vault") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.downloadAndSavePage(activeTab.url)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Command Palette") },
                                onClick = {
                                    showMoreMenu = false
                                    showCommandPalette = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Toggle Desktop Mode") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.toggleDesktopMode(activeTab.id)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Open in System Browser") },
                                onClick = {
                                    showMoreMenu = false
                                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(activeTab.url))
                                    context.startActivity(intent)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Sleep Background Tabs") },
                                onClick = {
                                    showMoreMenu = false
                                    viewModel.sleepBackgroundTabs()
                                    android.widget.Toast.makeText(context, "Suspended inactive tabs to save memory", android.widget.Toast.LENGTH_SHORT).show()
                                }
                            )
                            Divider()
                            DropdownMenuItem(
                                text = { Text("Offline Vault") },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate("offline_vault")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Quick Answer") },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate("quick_answer")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Efficiency") },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate("system_info")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = {
                                    showMoreMenu = false
                                    onNavigate("power_settings")
                                }
                            )
                        }
                    }
                }
                
                if (isQuickAnswerLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!quickAnswer.isNullOrBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(quickAnswer!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
                        }
                    }
                }
            }
        }

        if (activeTab.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        if (activeTab.url.contains("duckduckgo.com")) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).clickable {
                    val query = activeTab.url.substringAfter("q=").substringBefore("&")
                    viewModel.loadUrlInActiveTab("https://www.google.com/search?q=$query")
                },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Not finding what you need?", style = MaterialTheme.typography.bodyMedium)
                    Text("Search Google ↗", color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        } // End of AnimatedVisibility

        // Browser Screen Content
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (activeTab.isNativeReaderMode && activeTab.nativeElements != null) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(activeTab.nativeElements!!) { element ->
                        when (element) {
                            is com.example.network.ParsedElement.TextElement -> {
                                val style = when (element.type) {
                                    com.example.network.TextType.H1 -> MaterialTheme.typography.headlineLarge
                                    com.example.network.TextType.H2 -> MaterialTheme.typography.headlineMedium
                                    com.example.network.TextType.H3 -> MaterialTheme.typography.headlineSmall
                                    com.example.network.TextType.H4, com.example.network.TextType.H5, com.example.network.TextType.H6 -> MaterialTheme.typography.titleLarge
                                    com.example.network.TextType.BLOCKQUOTE -> MaterialTheme.typography.bodyLarge.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.secondary)
                                    com.example.network.TextType.CODE -> MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceVariant)
                                    else -> MaterialTheme.typography.bodyLarge
                                }
                                val color = if (element.link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground
                                Text(
                                    text = element.text,
                                    style = style,
                                    color = color,
                                    modifier = Modifier.padding(vertical = 4.dp).let {
                                        if (element.link != null) it.clickable { viewModel.loadUrlInActiveTab(element.link) } else it
                                    }
                                )
                            }
                            is com.example.network.ParsedElement.ImageElement -> {
                                if (activeTab.ecoMode != EcoMode.STRICT_TEXT) {
                                    coil.compose.AsyncImage(
                                        model = element.url,
                                        contentDescription = element.alt,
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        if (activeTab.isIncognito) {
                            if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                                val profileStore = ProfileStore.getInstance()
                                val profile = profileStore.getOrCreateProfile("incognito")
                                WebViewCompat.setProfile(this, profile.name)
                            }
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                url?.let {
                                    if (it != activeTab.url) {
                                        viewModel.updateActiveTabTitleAndLoading(it, "Loading...", true)
                                    }
                                }
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val urlStr = request?.url?.toString() ?: return false
                                val domain = request?.url?.host?.replace("www.", "") ?: ""
                                
                                val redirects = com.example.util.PowerUserSettings.urlRedirects.value
                                for ((matchDomain, replacement) in redirects) {
                                    if (domain.contains(matchDomain)) {
                                        val newUrl = urlStr.replace(request.url.host!!, replacement)
                                        view?.loadUrl(newUrl)
                                        return true
                                    }
                                }
                                return false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                url?.let {
                                    val title = view?.title ?: it
                                    viewModel.updateActiveTabTitleAndLoading(it, title, false)
                                    
                                    val urlDomain = android.net.Uri.parse(it).host?.replace("www.", "") ?: ""
                                    val userStyles = com.example.util.PowerUserSettings.userStyles.value
                                    val styleStr = userStyles[urlDomain]
                                    if (styleStr != null) {
                                        val base64Style = android.util.Base64.encodeToString(styleStr.toByteArray(), android.util.Base64.NO_WRAP)
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var style = document.createElement('style');
                                                style.innerHTML = decodeURIComponent(escape(window.atob('$base64Style')));
                                                document.head.appendChild(style);
                                            })();
                                        """.trimIndent(), null)
                                    }

                                    val userScripts = com.example.util.PowerUserSettings.userScripts.value
                                    val scriptStr = userScripts[urlDomain]
                                    if (scriptStr != null) {
                                        val base64Script = android.util.Base64.encodeToString(scriptStr.toByteArray(), android.util.Base64.NO_WRAP)
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var script = document.createElement('script');
                                                script.innerHTML = decodeURIComponent(escape(window.atob('$base64Script')));
                                                document.head.appendChild(script);
                                            })();
                                        """.trimIndent(), null)
                                    }

                                    // AMOLED Pure Black Injection
                                    view?.evaluateJavascript("""
                                        (function() {
                                            var style = document.createElement('style');
                                            style.innerHTML = '* { background-color: #000000 !important; color: #E0E0E0 !important; border-color: #333333 !important; } ' +
                                                              'a { color: #82B1FF !important; } ' + 
                                                              'img, picture, video, canvas, svg { background-color: transparent !important; }';
                                            document.head.appendChild(style);
                                        })();
                                    """.trimIndent(), null)

                                    if (activeTab.ecoMode == EcoMode.LITE) {
                                        // Tap to reveal images logic
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var style = document.createElement('style');
                                                style.innerHTML = 'img:not(.revealed), picture:not(.revealed), video:not(.revealed) { filter: blur(20px) !important; transition: filter 0.3s; } ' +
                                                                  '.revealed { filter: none !important; }';
                                                document.head.appendChild(style);

                                                document.addEventListener('click', function(e) {
                                                    if(e.target && (e.target.nodeName === 'IMG' || e.target.nodeName === 'PICTURE')) {
                                                        e.target.classList.add('revealed');
                                                    }
                                                });
                                            })();
                                        """.trimIndent(), null)
                                    }
                                    
                                    val offlineService = com.example.network.OfflineCacheService(context)
                                    if (view != null && !it.startsWith("file://")) {
                                        offlineService.autoCachePage(view, it)
                                    }
                                }
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: android.webkit.WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    val urlStr = request.url.toString()
                                    val offlineService = com.example.network.OfflineCacheService(context)
                                    val archivePath = offlineService.getOfflineArchivePath(urlStr)
                                    if (archivePath != null) {
                                        view?.loadUrl("file://$archivePath")
                                    } else {
                                        viewModel.updateActiveTabTitleAndLoading(urlStr, "Offline", false, "You are offline and this page is not cached.")
                                        view?.loadDataWithBaseURL(null, "<html><body style='font-family:sans-serif;padding:24px;text-align:center;'><h2>You are offline</h2><p>This page is not available in your offline cache.</p></body></html>", "text/html", "UTF-8", null)
                                    }
                                }
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val requestUrl = request?.url?.toString() ?: return null

                                val isLiteOrStrict = activeTab.ecoMode == EcoMode.LITE || activeTab.ecoMode == EcoMode.STRICT_TEXT
                                
                                if (AdBlocker.isAdultContent(requestUrl)) {
                                    return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                }

                                if (isLiteOrStrict) {
                                    if (AdBlocker.isAdOrTracker(requestUrl)) {
                                        com.example.util.StatsManager.incrementBlockedAds()
                                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    }
                                    if (AdBlocker.isHeavyMedia(requestUrl)) {
                                        com.example.util.StatsManager.incrementBlockedVideos()
                                        if (requestUrl.contains("youtube.com/embed") || requestUrl.contains("vimeo") || requestUrl.contains("iframe")) {
                                            val html = """
                                                <html><body style="background:#eeeeee;display:flex;justify-content:center;align-items:center;height:100%;margin:0;border-radius:8px;">
                                                <div style="font-family:sans-serif;color:#666;text-align:center;font-weight:bold;">Video Blocked for Focus</div>
                                                </body></html>
                                            """.trimIndent()
                                            return WebResourceResponse("text/html", "UTF-8", ByteArrayInputStream(html.toByteArray()))
                                        }
                                        return WebResourceResponse("text/plain", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    }
                                }

                                if (activeTab.ecoMode == EcoMode.STRICT_TEXT) {
                                    if (requestUrl.endsWith(".jpg") || requestUrl.endsWith(".png") || requestUrl.endsWith(".webp") || requestUrl.endsWith(".jpeg") || requestUrl.endsWith(".gif")) {
                                        com.example.util.StatsManager.incrementBlockedImages()
                                        return WebResourceResponse("image/png", "UTF-8", ByteArrayInputStream(ByteArray(0)))
                                    }
                                }

                                return super.shouldInterceptRequest(view, request)
                            }
                        }
                        webChromeClient = object : android.webkit.WebChromeClient() {}
                    }
                },
                update = { webView ->
                    activeWebView = webView
                    webView.settings.apply {
                        javaScriptEnabled = !com.example.util.PowerUserSettings.isJsDisabledForUrl(activeTab.url)
                        domStorageEnabled = true
                        
                        // Desktop Mode Logic
                        if (activeTab.isDesktopMode) {
                            userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        } else {
                            userAgentString = android.webkit.WebSettings.getDefaultUserAgent(context)
                            useWideViewPort = false
                            loadWithOverviewMode = false
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                        }

                        if (activeTab.isIncognito) {
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            // Set a private cache path or clear data
                        } else {
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                    }
                    
                    val tagMap = webView.tag as? MutableMap<String, Any> ?: mutableMapOf()
                    val loadedTabId = tagMap["tabId"] as? String
                    
                    if (loadedTabId != activeTab.id) {
                        if (loadedTabId != null) {
                            val bundle = android.os.Bundle()
                            webView.saveState(bundle)
                            viewModel.saveWebViewState(loadedTabId, bundle)
                        }
                        
                        tagMap["tabId"] = activeTab.id
                        webView.tag = tagMap
                        
                        if (activeTab.webViewState != null) {
                            webView.restoreState(activeTab.webViewState!!)
                        } else {
                            webView.loadUrl(activeTab.url)
                        }
                    } else {
                        // Handle navigation loading and reloading for the SAME tab
                        val lastReloadTrigger = tagMap["reloadTrigger"] as? Int ?: 0
                        if (webView.url != activeTab.url && webView.url != null && activeTab.url != "about:blank") {
                            webView.loadUrl(activeTab.url)
                        } else if (activeTab.reloadTrigger > lastReloadTrigger) {
                            webView.reload()
                        }
                        tagMap["reloadTrigger"] = activeTab.reloadTrigger
                        webView.tag = tagMap
                    }
                    
                    if (activeTab.isIncognito && (tagMap["incognitoCleared"] as? Boolean != true)) {
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
                            val profile = ProfileStore.getInstance().getOrCreateProfile("incognito")
                            profile.cookieManager.removeAllCookies(null)
                        } else {
                            android.webkit.CookieManager.getInstance().removeAllCookies(null)
                        }
                        tagMap["incognitoCleared"] = true
                        webView.tag = tagMap
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            }
            
            // Fullscreen Toggle Button
            IconButton(
                onClick = { uiVisible = !uiVisible },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .size(40.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f), shape = RoundedCornerShape(20.dp))
            ) {
                Icon(
                    if (uiVisible) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle Fullscreen",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        // Bottom Navigation Controls removed in favor of top dropdown menu

    }

    if (showCommandPalette) {
        var commandText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCommandPalette = false },
            title = { Text("Command Palette") },
            text = {
                OutlinedTextField(
                    value = commandText,
                    onValueChange = { commandText = it },
                    placeholder = { Text("> Disable JS") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val cmd = commandText.trim().lowercase().removePrefix(">").trim()
                    when {
                        cmd == "disable js" -> {
                            com.example.util.PowerUserSettings.toggleJsForDomain(activeTab.url)
                            activeWebView?.reload()
                        }
                        cmd == "summarize" -> {
                            activeWebView?.evaluateJavascript("(function() { return document.body.innerText; })();") { innerText ->
                                if (!innerText.isNullOrBlank() && innerText != "null") {
                                    viewModel.summarizeActivePageText(innerText)
                                } else {
                                    viewModel.summarizeActivePageText("No text found on this page.")
                                }
                            }
                        }
                        cmd == "lite mode" -> {
                            viewModel.toggleEcoMode(activeTab.id, EcoMode.LITE)
                        }
                        cmd == "strict mode" -> {
                            viewModel.toggleEcoMode(activeTab.id, EcoMode.STRICT_TEXT)
                        }
                    }
                    showCommandPalette = false
                }) {
                    Text("Run")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCommandPalette = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (isSummarizing || pageSummary != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearPageSummary() },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = "AI", tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Page Summary")
                }
            },
            text = {
                if (isSummarizing) {
                    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Text(pageSummary ?: "")
                }
            },
            confirmButton = {
                TextButton(onClick = { viewModel.clearPageSummary() }) {
                    Text("Got it")
                }
            }
        )
    }

    if (showClipboardVault) {
        val sheetState = rememberModalBottomSheetState()
        val vaultText by com.example.util.PowerUserSettings.vaultText.collectAsStateWithLifecycle()
        var currentText by remember { mutableStateOf(vaultText) }
        
        ModalBottomSheet(
            onDismissRequest = { 
                com.example.util.PowerUserSettings.setVaultText(currentText)
                showClipboardVault = false 
            },
            sheetState = sheetState
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth()) {
                Text("Context Clipboard (Vault)", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text("Temporary scratchpad for copy/pasting. Saves locally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = currentText,
                    onValueChange = { currentText = it },
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    placeholder = { Text("Paste things here while browsing...") },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { 
                        com.example.util.PowerUserSettings.setVaultText(currentText)
                        showClipboardVault = false 
                    },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Save & Close")
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabSwitcherScreen(
    tabs: List<BrowserTab>,
    activeTabId: String,
    onCloseTab: (String) -> Unit,
    onSelectTab: (String) -> Unit,
    onNewTab: () -> Unit,
    onNewIncognitoTab: () -> Unit,
    onCloseSwitcher: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tabs") },
                navigationIcon = {
                    IconButton(onClick = onCloseSwitcher) {
                        Icon(Icons.Default.Close, contentDescription = "Close Tab Switcher")
                    }
                },
                actions = {
                    IconButton(onClick = onNewIncognitoTab) {
                        Icon(Icons.Default.Add, contentDescription = "New Incognito Tab", tint = MaterialTheme.colorScheme.tertiary)
                    }
                    IconButton(onClick = onNewTab) {
                        Icon(Icons.Default.Add, contentDescription = "New Tab")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(tabs) { tab ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectTab(tab.id) },
                    colors = CardDefaults.cardColors(
                        containerColor = if (tab.id == activeTabId) {
                            if (tab.isIncognito) MaterialTheme.colorScheme.tertiaryContainer 
                            else MaterialTheme.colorScheme.primaryContainer
                        } else {
                            if (tab.isIncognito) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.7f)
                            else MaterialTheme.colorScheme.surfaceVariant
                        }
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            val titleText = buildString {
                                if (tab.isIncognito) append("🕵️ ")
                                append(tab.title)
                                if (tab.isSleeping) append(" 💤")
                            }
                            Text(titleText, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(tab.url, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        IconButton(onClick = { onCloseTab(tab.id) }) {
                            Icon(Icons.Default.Close, contentDescription = "Close Tab")
                        }
                    }
                }
            }
        }
    }
}

