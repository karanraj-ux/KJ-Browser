package com.example.network

import android.content.Context
import android.util.Log
import android.webkit.WebView
import java.io.File

class OfflineCacheService(private val context: Context) {
    private val cacheDir = File(context.filesDir, "auto_offline_cache")

    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }

    private fun getCacheFile(url: String): File {
        val fileName = "auto_cache_${url.hashCode()}.mht"
        return File(cacheDir, fileName)
    }

    fun autoCachePage(webView: WebView, url: String) {
        try {
            val file = getCacheFile(url)
            webView.saveWebArchive(file.absolutePath)
        } catch (e: Exception) {
            Log.e("OfflineCacheService", "Error caching url", e)
        }
    }

    fun getOfflineArchivePath(url: String): String? {
        val file = getCacheFile(url)
        return if (file.exists()) {
            file.absolutePath
        } else {
            null
        }
    }

    fun getCacheSize(): Long {
        return cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
    }

    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
}
