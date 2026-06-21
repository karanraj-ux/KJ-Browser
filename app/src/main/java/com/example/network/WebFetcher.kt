package com.example.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.dnsoverhttps.DnsOverHttps
import org.jsoup.Jsoup
import java.io.IOException
import java.net.InetAddress
import java.net.UnknownHostException

object WebFetcher {
    private val bootstrapClient = OkHttpClient.Builder().build()
    
    private val standardClient: OkHttpClient
        get() = buildClientWithDns(false)
        
    private val incognitoClient: OkHttpClient
        get() = buildClientWithDns(true)

    private fun buildClientWithDns(isIncognito: Boolean): OkHttpClient {
        val builder = OkHttpClient.Builder()
        val customDnsUrl = com.example.util.PowerUserSettings.customDns.value
        if (customDnsUrl.isNotBlank() && customDnsUrl.startsWith("http")) {
            val hosts = buildList {
                try { InetAddress.getByName("1.1.1.1")?.let { add(it) } } catch (e: Exception) {}
                try { InetAddress.getByName("8.8.8.8")?.let { add(it) } } catch (e: Exception) {}
            }
            val appDns = DnsOverHttps.Builder().client(bootstrapClient)
                .url(customDnsUrl.toHttpUrlOrNull()!!)
                .bootstrapDnsHosts(*hosts.toTypedArray())
                .build()
            builder.dns(appDns)
        }
        return builder.build()
    }

    suspend fun fetchHtml(url: String, isIncognito: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        try {
            val validUrl = ensureValidUrl(url)
            val request = Request.Builder()
                .url(validUrl)
                .header("User-Agent", "Mozilla/5.0 OfflineHub/1.0 (Mobile; Battery-Saver)")
                .build()

            val client = if (isIncognito) incognitoClient else standardClient
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

            standardClient.newCall(request).execute().use { response ->
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
