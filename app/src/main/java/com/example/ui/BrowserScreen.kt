package com.example.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.viewinterop.AndroidView
import com.example.util.AdBlocker
import java.io.ByteArrayInputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    var urlInput by remember(activeTabId, activeTab.url) { mutableStateOf(activeTab.url) }
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showEcoMenu by remember { mutableStateOf(false) }
    var showCommandPalette by remember { mutableStateOf(false) }
    
    val quickAnswer by viewModel.quickAnswer.collectAsStateWithLifecycle()
    val isQuickAnswerLoading by viewModel.isQuickAnswerLoading.collectAsStateWithLifecycle()
    val pageSummary by viewModel.pageSummary.collectAsStateWithLifecycle()
    val isSummarizing by viewModel.isSummarizing.collectAsStateWithLifecycle()

    var activeWebView by remember { mutableStateOf<WebView?>(null) }

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

    Column(modifier = Modifier.fillMaxSize()) {
        // Omnibox Bar
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Eco Mode Output
                    Box {
                        IconButton(onClick = { showEcoMenu = true }) {
                            Icon(
                                imageVector = if (activeTab.ecoMode == EcoMode.ORIGINAL) Icons.Default.Info else Icons.Default.Star,
                                contentDescription = "Eco Mode",
                                tint = if (activeTab.ecoMode == EcoMode.ORIGINAL) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showEcoMenu,
                            onDismissRequest = { showEcoMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Strict Text Mode") },
                                onClick = {
                                    viewModel.toggleEcoMode(activeTab.id, EcoMode.STRICT_TEXT)
                                    showEcoMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lite Mode") },
                                onClick = {
                                    viewModel.toggleEcoMode(activeTab.id, EcoMode.LITE)
                                    showEcoMenu = false
                                }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        placeholder = { Text("Search or type URL") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        singleLine = true,
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
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    IconButton(
                        onClick = { viewModel.downloadAndSavePage(activeTab.url) },
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AddCircle, contentDescription = "Save to Offline Vault")
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

        // Native Rendered View
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
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
                        if (activeTab.isIncognito) {
                            cacheMode = WebSettings.LOAD_NO_CACHE
                            // Set a private cache path or clear data
                        } else {
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                    }

                    // Handle navigation loading and reloading
                    if (webView.url != activeTab.url) {
                        webView.loadUrl(activeTab.url)
                    } else if (activeTab.reloadTrigger > (webView.tag as? Int ?: 0)) {
                        webView.reload()
                    }
                    webView.tag = activeTab.reloadTrigger
                    
                    if (activeTab.isIncognito && webView.tag == null) {
                        webView.clearCache(true)
                        webView.clearHistory()
                        webView.clearFormData()
                        android.webkit.CookieManager.getInstance().setAcceptCookie(false)
                        webView.tag = -1
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Bottom Navigation Controls
        var dragX by remember { mutableFloatStateOf(0f) }
        BottomAppBar(
            modifier = Modifier.pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (dragX > 50) {
                            viewModel.switchToPreviousTab()
                        } else if (dragX < -50) {
                            viewModel.switchToNextTab()
                        }
                        dragX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        dragX += dragAmount
                    }
                )
            },
            actions = {
                IconButton(onClick = { viewModel.goBack() }, enabled = viewModel.canGoBack()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { viewModel.goForward() }, enabled = viewModel.canGoForward()) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                
                IconButton(onClick = { viewModel.reloadActiveTab() }) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                }
                
                var showMoreMenu by remember { mutableStateOf(false) }
                IconButton(onClick = {
                    activeWebView?.evaluateJavascript("(function() { return document.body.innerText; })();") { innerText ->
                        if (!innerText.isNullOrBlank() && innerText != "null") {
                            viewModel.summarizeActivePageText(innerText)
                        } else {
                            viewModel.summarizeActivePageText("No text found on this page.")
                        }
                    }
                }) {
                    Icon(Icons.Default.Star, contentDescription = "Summarize")
                }
                Box {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(androidx.compose.material.icons.Icons.Default.MoreVert, contentDescription = "More Options")
                    }
                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Browsing History") },
                            onClick = {
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Save Page (Offline Archive)") },
                            onClick = {
                                showMoreMenu = false
                                activeWebView?.let { view ->
                                    val fileName = "archive_${System.currentTimeMillis()}.mht"
                                    val filePath = java.io.File(context.filesDir, fileName).absolutePath
                                    view.saveWebArchive(filePath)
                                    viewModel.saveLocalArchive(activeTab.url, view.title ?: activeTab.url, filePath)
                                }
                            }
                        )
                        val isJsDisabled = com.example.util.PowerUserSettings.isJsDisabledForUrl(activeTab.url)
                        DropdownMenuItem(
                            text = { Text(if (isJsDisabled) "Enable JavaScript for site" else "Disable JavaScript (Fast mode)") },
                            onClick = {
                                showMoreMenu = false
                                com.example.util.PowerUserSettings.toggleJsForDomain(activeTab.url)
                                // Reload page to apply changes
                                activeWebView?.reload()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            },
            floatingActionButton = {
                Row {
                    FloatingActionButton(
                        onClick = { showCommandPalette = true },
                        modifier = Modifier.padding(end = 8.dp),
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Text(">", style = MaterialTheme.typography.titleMedium.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold))
                    }
                    FloatingActionButton(onClick = { showTabSwitcher = true }) {
                        Text(tabs.size.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        )
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
                            Text(if (tab.isIncognito) "🕵️ ${tab.title}" else tab.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
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

