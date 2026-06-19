package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppRepository
import com.example.data.OfflinePage
import com.example.network.WebFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

enum class EcoMode(val title: String) {
    STRICT_TEXT("Strict Text"),
    LITE("Lite Mode"),
    ORIGINAL("Original Mode")
}

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    val url: String = "https://lite.cnn.com",
    val title: String = "New Tab",
    val ecoMode: EcoMode = EcoMode.LITE
)

data class BrowserHistoryItem(
    val url: String,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)

class MainViewModel(private val repository: AppRepository) : ViewModel() {

    // --- Browser State ---
    private val _tabs = MutableStateFlow(listOf(BrowserTab()))
    val tabs: StateFlow<List<BrowserTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(_tabs.value.first().id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _history = MutableStateFlow<List<BrowserHistoryItem>>(emptyList())
    val history: StateFlow<List<BrowserHistoryItem>> = _history.asStateFlow()

    private val _bookmarks = MutableStateFlow<List<String>>(emptyList())
    val bookmarks: StateFlow<List<String>> = _bookmarks.asStateFlow()

    fun createNewTab(url: String = "about:blank") {
        val newTab = BrowserTab(url = url)
        _tabs.update { it + newTab }
        _activeTabId.value = newTab.id
    }

    fun switchTab(tabId: String) {
        _activeTabId.value = tabId
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

    fun updateActiveTab(url: String, title: String) {
        _tabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.id == _activeTabId.value) {
                    tab.copy(url = url, title = title)
                } else tab
            }
        }
    }

    fun toggleEcoMode(tabId: String, mode: EcoMode) {
        _tabs.update { currentTabs ->
            currentTabs.map { tab ->
                if (tab.id == tabId) tab.copy(ecoMode = mode) else tab
            }
        }
    }

    fun addToHistory(url: String, title: String) {
        if (url == "about:blank" || url.trim().isEmpty()) return
        _history.update { current ->
            val newItem = BrowserHistoryItem(url, title)
            listOf(newItem) + current.filter { it.url != url }.take(99)
        }
    }

    fun toggleBookmark(url: String) {
        _bookmarks.update { current ->
            if (current.contains(url)) current.filter { it != url }
            else current + url
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
            
            // First fetch the HTML just slightly to get the title, or we can use Jsoup to fetch directly but let's stick to our fetchTextOnly.
            val resultHtml = WebFetcher.fetchHtml(url)
            val resultText = WebFetcher.fetchTextOnly(url)
            
            if (resultText.isSuccess && resultHtml.isSuccess) {
                val title = extractTitle(resultHtml.getOrDefault(""), url)
                val textOnly = resultText.getOrDefault("")
                val newPage = OfflinePage(url = url, title = title, htmlContent = textOnly)
                repository.insert(newPage)
                _downloadStatus.value = "Saved eco text locally!"
            } else {
                _downloadStatus.value = "Error: Failed to fetch url."
            }
            
            _isDownloading.value = false
        }
    }

    fun deletePage(id: Int) {
        viewModelScope.launch {
            repository.delete(id)
        }
    }

    fun clearStatus() {
        _downloadStatus.value = null
    }

    private fun extractTitle(html: String, fallbackUrl: String): String {
        val titleRegex = "<title>(.*?)</title>".toRegex(RegexOption.IGNORE_CASE)
        val matchResult = titleRegex.find(html)
        return matchResult?.groupValues?.getOrNull(1)?.trim() ?: fallbackUrl
    }
}
