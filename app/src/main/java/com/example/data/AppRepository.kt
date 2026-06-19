package com.example.data

import kotlinx.coroutines.flow.Flow

class AppRepository(private val offlinePageDao: OfflinePageDao) {
    val allPages: Flow<List<OfflinePage>> = offlinePageDao.getAllPages()

    suspend fun insert(page: OfflinePage) {
        offlinePageDao.insertPage(page)
    }

    suspend fun delete(id: Int) {
        offlinePageDao.deletePage(id)
    }
}
