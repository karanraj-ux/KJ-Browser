package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException

object WebFetcher {
    private val client = OkHttpClient()

    suspend fun fetchHtml(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val validUrl = ensureValidUrl(url)
            val request = Request.Builder()
                .url(validUrl)
                .header("User-Agent", "Mozilla/5.0 OfflineHub/1.0 (Mobile; Battery-Saver)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(IOException("Unexpected code $response"))
                } else {
                    val html = response.body?.string() ?: ""
                    Result.success(html)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchTextOnly(url: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val validUrl = ensureValidUrl(url)
            val request = Request.Builder()
                .url(validUrl)
                .header("User-Agent", "Mozilla/5.0 OfflineHub/1.0 (Mobile; Battery-Saver)")
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Result.failure(IOException("Unexpected code $response"))
                } else {
                    val html = response.body?.string() ?: ""
                    // Use Jsoup to completely strip out JS, CSS, images, and keep only meaningful text
                    val document = Jsoup.parse(html)
                    // Removing unnecessary bloat tags explicitly
                    document.select("script, style, link, img, picture, video, iframe, nav, footer, header").remove()
                    val cleanText = document.text()
                    Result.success(cleanText)
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ensureValidUrl(url: String): String {
        return if (!url.startsWith("http://") && !url.startsWith("https://")) {
            "https://$url"
        } else {
            url
        }
    }
}
