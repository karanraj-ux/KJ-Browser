package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.OfflinePage
import com.example.network.EcoAIEngine
import com.example.network.WebFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

enum class EcoMode(val title: String) {
    STRICT_TEXT("Extreme Battery (Text Only)"),
    LITE("Lite (Images On)"),
    ORIGINAL("Standard (Full Web)")
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "https://duckduckgo.com/lite",
    val title: String = "New Tab",
    val ecoMode: EcoMode = EcoMode.STRICT_TEXT,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isIncognito: Boolean = false,
    val isDesktopMode: Boolean = false,
    val isNativeReaderMode: Boolean = false,
    val isSleeping: Boolean = false,
    val nativeElements: List<com.example.network.ParsedElement>? = null,
    val webViewState: android.os.Bundle? = null,
    val backStack: List<String> = emptyList(),
    val forwardStack: List<String> = emptyList(),
    val reloadTrigger: Int = 0 // To trigger WebView reloading
)


class MainViewModel(private val repository: AppRepository) : ViewModel() {

    // --- Browser State ---
    private val _tabs = MutableStateFlow(listOf(BrowserTab()))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    val history: StateFlow<List<com.example.data.BrowserHistory>> = repository.history
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val bookmarks: StateFlow<List<com.example.data.Bookmark>> = repository.bookmarks
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- AI State ---
    private val _quickAnswer = MutableStateFlow<String?>(null)
    val quickAnswer: StateFlow<String?> = _quickAnswer.asStateFlow()

    private val _isQuickAnswerLoading = MutableStateFlow(false)
    val isQuickAnswerLoading: StateFlow<Boolean> = _isQuickAnswerLoading.asStateFlow()

    private val _pageSummary = MutableStateFlow<String?>(null)
    val pageSummary: StateFlow<String?> = _pageSummary.asStateFlow()

    private val _isSummarizing = MutableStateFlow(false)
    val isSummarizing: StateFlow<Boolean> = _isSummarizing.asStateFlow()
    
    // --- Stats ---
    val blockedAds = com.example.util.StatsManager.blockedAds
    val blockedVideos = com.example.util.StatsManager.blockedVideos
    val blockedImages = com.example.util.StatsManager.blockedImages

    init {
        // Initial tab is ready, WebView will load it when active
    }

    fun createNewTab(url: String = "https://duckduckgo.com/lite", isIncognito: Boolean = false) {
        val newTab = BrowserTab(url = url, isIncognito = isIncognito)
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
    }

    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
        wakeActiveTabIfNeeded()
    }

    fun switchToNextTab() {
        val currentTabs = _tabs.value
        val currentIndex = currentTabs.indexOfFirst { it.id == _activeTabId.value }
        if (currentIndex in 0 until currentTabs.lastIndex) {
            _activeTabId.value = currentTabs[currentIndex + 1].id
        }
    }

    fun switchToPreviousTab() {
        val currentTabs = _tabs.value
        val currentIndex = currentTabs.indexOfFirst { it.id == _activeTabId.value }
        if (currentIndex > 0) {
            _activeTabId.value = currentTabs[currentIndex - 1].id
        }
    }

    fun closeTab(tabId: String) {
        _tabs.update { currentTabs ->
            val newTabs = currentTabs.filter { it.id != tabId }
            if (newTabs.isEmpty()) listOf(BrowserTab()) else newTabs
        }
        if (_activeTabId.value == tabId) {
            _activeTabId.value = _tabs.value.last().id
        }
    }

    fun saveWebViewState(tabId: String, state: android.os.Bundle) {
        _tabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.id == tabId) tab.copy(webViewState = state) else tab
            }
        }
    }

    fun loadUrlInActiveTab(url: String) {
        val tab = _tabs.value.find { it.id == _activeTabId.value }
        if (tab != null && tab.url != url) {
            val newBackStack = tab.backStack + tab.url
            _tabs.update { tabs ->
                tabs.map { if (it.id == tab.id) it.copy(url = url, backStack = newBackStack, forwardStack = emptyList(), isLoading = true, isSleeping = false) else it }
            }
        }
    }
    
    fun sleepBackgroundTabs() {
        _tabs.update { tabs ->
            tabs.map { 
                if (it.id != _activeTabId.value && !it.isSleeping) {
                    it.copy(isSleeping = true, webViewState = null) // Free memory
                } else {
                    it
                }
            }
        }
    }

    fun wakeActiveTabIfNeeded() {
        _tabs.update { tabs ->
            tabs.map { 
                if (it.id == _activeTabId.value && it.isSleeping) {
                    it.copy(isSleeping = false, reloadTrigger = it.reloadTrigger + 1)
                } else {
                    it
                }
            }
        }
    }
    
    fun updateActiveTabTitleAndLoading(url: String, title: String, isLoading: Boolean, error: String? = null) {
        _tabs.update { tabs ->
            tabs.map { if (it.id == _activeTabId.value) it.copy(url = url, title = title, isLoading = isLoading, errorMessage = error) else it }
        }
        val tab = _tabs.value.find { it.id == _activeTabId.value }
        if (tab != null && !tab.isIncognito && !isLoading && error == null) {
            addToHistory(url, title)
        }
    }
    
    fun reloadActiveTab() {
        _tabs.update { tabs ->
            tabs.map { if (it.id == _activeTabId.value) it.copy(isLoading = true, reloadTrigger = it.reloadTrigger + 1) else it }
        }
    }
    
    fun canGoBack(): Boolean {
        val tab = _tabs.value.find { it.id == _activeTabId.value }
        return tab?.backStack?.isNotEmpty() == true
    }
    
    fun canGoForward(): Boolean {
        val tab = _tabs.value.find { it.id == _activeTabId.value }
        return tab?.forwardStack?.isNotEmpty() == true
    }
    
    fun goBack() {
        val tab = _tabs.value.find { it.id == _activeTabId.value }
        if (tab != null && tab.backStack.isNotEmpty()) {
            val prevUrl = tab.backStack.last()
            val newBackStack = tab.backStack.dropLast(1)
            val newForwardStack = tab.forwardStack + tab.url
            _tabs.update { tabs ->
                tabs.map { if (it.id == tab.id) it.copy(url = prevUrl, backStack = newBackStack, forwardStack = newForwardStack, isLoading = true) else it }
            }
        }
    }
    
    fun goForward() {
        val tab = _tabs.value.find { it.id == _activeTabId.value }
        if (tab != null && tab.forwardStack.isNotEmpty()) {
            val nextUrl = tab.forwardStack.last()
            val newForwardStack = tab.forwardStack.dropLast(1)
            val newBackStack = tab.backStack + tab.url
            _tabs.update { tabs ->
                tabs.map { if (it.id == tab.id) it.copy(url = nextUrl, backStack = newBackStack, forwardStack = newForwardStack, isLoading = true) else it }
            }
        }
    }

    fun toggleEcoMode(tabId: String, mode: EcoMode) {
        _tabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.id == tabId) tab.copy(ecoMode = mode, reloadTrigger = tab.reloadTrigger + 1) else tab
            }
        }
    }

    fun toggleDesktopMode(tabId: String) {
        _tabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.id == tabId) tab.copy(isDesktopMode = !tab.isDesktopMode, reloadTrigger = tab.reloadTrigger + 1) else tab
            }
        }
    }

    fun toggleNativeReaderMode(tabId: String, currentUrl: String, injectedHtml: String? = null) {
        _tabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.id == tabId) tab.copy(isNativeReaderMode = !tab.isNativeReaderMode, isLoading = !tab.isNativeReaderMode) else tab
            }
        }
        val tab = _tabs.value.find { it.id == tabId }
        if (tab != null && tab.isNativeReaderMode) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val html = if (!injectedHtml.isNullOrEmpty() && injectedHtml != "null") {
                        // evaluateJavascript returns a JSON string, we need to unescape it if it's quoted
                        var cleanHtml = injectedHtml
                        if (cleanHtml.startsWith("\"") && cleanHtml.endsWith("\"")) {
                            cleanHtml = cleanHtml.substring(1, cleanHtml.length - 1)
                                .replace("\\u003C", "<")
                                .replace("\\\"", "\"")
                                .replace("\\\\", "\\")
                                .replace("\\n", "\n")
                                .replace("\\r", "\r")
                                .replace("\\t", "\t")
                        }
                        cleanHtml
                    } else {
                        val client = okhttp3.OkHttpClient()
                        val request = okhttp3.Request.Builder().url(currentUrl).build()
                        val response = client.newCall(request).execute()
                        response.body?.string() ?: ""
                    }
                    val elements = com.example.network.DomParser.parsePageAsComponents(html, currentUrl, fetchImages = true)
                    withContext(Dispatchers.Main) {
                        _tabs.update { currentTabs ->
                            currentTabs.map { t ->
                                if (t.id == tabId) t.copy(nativeElements = elements, isLoading = false) else t
                            }
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        _tabs.update { currentTabs ->
                            currentTabs.map { t ->
                                if (t.id == tabId) t.copy(isLoading = false, errorMessage = "Failed to load reader mode: ${e.message}") else t
                            }
                        }
                    }
                }
            }
        }
    }

    fun addToHistory(url: String, title: String) {
        if (url == "about:blank" || url.trim().isEmpty() || url.startsWith("data:")) return
        viewModelScope.launch(Dispatchers.IO) {
            repository.insertHistory(url, title)
        }
    }

    fun toggleBookmark(url: String, title: String) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.toggleBookmark(url, title)
        }
    }

    // --- Offline Vault State ---

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val savedPages: StateFlow<List<OfflinePage>> = combine(
        repository.allPages,
        _searchQuery
    ) { pages, query ->
        if (query.isBlank()) {
            pages
        } else {
            pages.filter { page ->
                page.title.contains(query, ignoreCase = true) || 
                page.htmlContent.contains(query, ignoreCase = true) ||
                page.url.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _downloadStatus = MutableStateFlow<String?>(null)
    val downloadStatus: StateFlow<String?> = _downloadStatus.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun downloadAndSavePage(url: String) {
        if (url.isBlank()) return
        
        viewModelScope.launch {
            _isDownloading.value = true
            _downloadStatus.value = "Fetching Eco Format..."
            
            val resultText = WebFetcher.fetchTextOnly(url)
            
            if (resultText.isSuccess) {
                val textOnly = resultText.getOrDefault("")
                val newPage = OfflinePage(url = url, title = url, htmlContent = textOnly)
                repository.insertPage(newPage)
                _downloadStatus.value = "Saved eco text locally!"
            } else {
                _downloadStatus.value = "Error: Failed to fetch url."
            }
            
            _isDownloading.value = false
        }
    }

    fun deletePage(id: Int) {
        viewModelScope.launch {
            repository.deletePage(id)
        }
    }

    fun clearStatus() {
        _downloadStatus.value = null
    }

    // --- AI Methods ---
    private var searchJob: kotlinx.coroutines.Job? = null

    fun fetchQuickAnswer(query: String) {
        if (query.isBlank()) {
            clearQuickAnswer()
            return
        }
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _isQuickAnswerLoading.value = true
            _quickAnswer.value = null
            val answer = EcoAIEngine.getQuickAnswer(query)
            if (answer.isNotBlank()) {
                _quickAnswer.value = answer
            }
            _isQuickAnswerLoading.value = false
        }
    }

    fun clearQuickAnswer() {
        searchJob?.cancel()
        _quickAnswer.value = null
        _isQuickAnswerLoading.value = false
    }

    fun saveLocalArchive(url: String, title: String, filePath: String) {
        viewModelScope.launch {
            val newPage = OfflinePage(url = url, title = title, htmlContent = "file://$filePath")
            repository.insertPage(newPage)
            _downloadStatus.value = "Saved archive locally!"
        }
    }

    fun summarizeActivePageText(htmlContent: String) {
        viewModelScope.launch {
            _isSummarizing.value = true
            _pageSummary.value = null
            val res = EcoAIEngine.summarizePage(htmlContent)
            _pageSummary.value = res
            _isSummarizing.value = false
        }
    }

    fun clearPageSummary() {
        _pageSummary.value = null
    }
}

