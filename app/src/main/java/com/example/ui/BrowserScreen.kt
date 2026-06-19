package com.example.ui

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(viewModel: MainViewModel) {
    val tabs by viewModel.tabs.collectAsStateWithLifecycle()
    val activeTabId by viewModel.activeTabId.collectAsStateWithLifecycle()
    val activeTab = tabs.find { it.id == activeTabId } ?: tabs.first()

    var urlInput by remember(activeTabId) { mutableStateOf(activeTab.url) }
    var webView: WebView? by remember { mutableStateOf(null) }
    val isDownloading by viewModel.isDownloading.collectAsStateWithLifecycle()

    var showTabSwitcher by remember { mutableStateOf(false) }
    var showEcoMenu by remember { mutableStateOf(false) }
    var loadProgress by remember { mutableFloatStateOf(0f) }

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
                                    webView?.reload()
                                    showEcoMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Lite Mode") },
                                onClick = {
                                    viewModel.toggleEcoMode(activeTab.id, EcoMode.LITE)
                                    webView?.reload()
                                    showEcoMenu = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Original Mode") },
                                onClick = {
                                    viewModel.toggleEcoMode(activeTab.id, EcoMode.ORIGINAL)
                                    webView?.reload()
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
                                    viewModel.updateActiveTab(formattedUrl, activeTab.title)
                                    webView?.loadUrl(formattedUrl)
                                }
                            }
                        ),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        )
                    )

                    IconButton(
                        onClick = { viewModel.downloadAndSavePage(webView?.url ?: activeTab.url) },
                        enabled = !isDownloading
                    ) {
                        if (isDownloading) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.AddCircle, contentDescription = "Save to Offline Vault")
                        }
                    }
                }
            }
        }

        if (loadProgress > 0f && loadProgress < 1f) {
            LinearProgressIndicator(
                progress = { loadProgress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }

        // Live WebView
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            return false
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            url?.let {
                                urlInput = it
                                viewModel.updateActiveTab(it, view?.title ?: "Unknown Title")
                                viewModel.addToHistory(it, view?.title ?: "Unknown Title")
                            }
                        }

                        override fun shouldInterceptRequest(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): WebResourceResponse? {
                            // Eco-Engine ON-THE-FLY STRIPPING:
                            val url = request?.url?.toString() ?: return super.shouldInterceptRequest(view, request)
                            val isLiteOrStrict = activeTab.ecoMode == EcoMode.LITE || activeTab.ecoMode == EcoMode.STRICT_TEXT
                            
                            // True Ad & Script Blocking in Lite/Strict modes
                            if (isLiteOrStrict) {
                                if (com.example.util.AdBlocker.isAdOrTracker(url)) {
                                    // Block and return empty response
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                            }
                            
                            val isStrict = activeTab.ecoMode == EcoMode.STRICT_TEXT
                            if (isStrict) {
                                if (com.example.util.AdBlocker.isHeavyMedia(url)) {
                                    return WebResourceResponse("text/plain", "UTF-8", null)
                                }
                            }
                            
                            // Let the system handle it normally if no blocks
                            return super.shouldInterceptRequest(view, request)
                        }
                    }
                    webChromeClient = object : android.webkit.WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            loadProgress = newProgress / 100f
                        }
                    }
                    webView = this
                }
            },
            update = { view ->
                webView = view
                
                // Update bindings safely
                view.settings.apply {
                    when (activeTab.ecoMode) {
                        EcoMode.STRICT_TEXT -> {
                            javaScriptEnabled = false
                            loadsImagesAutomatically = false
                            blockNetworkImage = true
                        }
                        EcoMode.LITE -> {
                            javaScriptEnabled = true // Required by many modern lite sites, but trackers are blocked above
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                        }
                        EcoMode.ORIGINAL -> {
                            javaScriptEnabled = true
                            loadsImagesAutomatically = true
                            blockNetworkImage = false
                            mediaPlaybackRequiresUserGesture = false
                        }
                    }
                }
                
                if (view.url != activeTab.url) {
                    view.loadUrl(activeTab.url)
                }
            },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )

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
                IconButton(onClick = { webView?.goBack() }, enabled = webView?.canGoBack() == true) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                IconButton(onClick = { webView?.goForward() }, enabled = webView?.canGoForward() == true) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                }
                if (loadProgress > 0f && loadProgress < 1f) {
                    IconButton(onClick = { webView?.stopLoading() }) {
                        Icon(androidx.compose.material.icons.Icons.Default.Close, contentDescription = "Stop")
                    }
                } else {
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
                
                var showMoreMenu by remember { mutableStateOf(false) }
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
                                // For now, just logging or showing a simple toast
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Bookmarks") },
                            onClick = {
                                showMoreMenu = false
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showTabSwitcher = true }) {
                    Text(tabs.size.toString(), style = MaterialTheme.typography.titleMedium)
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
                        containerColor = if (tab.id == activeTabId) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tab.title, style = MaterialTheme.typography.titleMedium, maxLines = 1)
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

