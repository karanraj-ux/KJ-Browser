package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(
    private val offlinePageDao: OfflinePageDao,
    private val browserHistoryDao: BrowserHistoryDao,
    private val bookmarkDao: BookmarkDao
) {
    val allPages: Flow<List<OfflinePage>> = offlinePageDao.getAllPages()
    val history: Flow<List<BrowserHistory>> = browserHistoryDao.getHistory()
    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.getBookmarks()

    suspend fun insertPage(page: OfflinePage) {
        offlinePageDao.insertPage(page)
    }

    suspend fun deletePage(id: Int) {
        offlinePageDao.deletePage(id)
    }

    suspend fun insertHistory(url: String, title: String) {
        browserHistoryDao.insert(BrowserHistory(url = url, title = title))
    }

    suspend fun clearHistory() {
        browserHistoryDao.clearHistory()
    }

    suspend fun toggleBookmark(url: String, title: String) {
        if (bookmarkDao.isBookmarked(url)) {
            bookmarkDao.deleteByUrl(url)
        } else {
            bookmarkDao.insert(Bookmark(url = url, title = title))
        }
    }
}
