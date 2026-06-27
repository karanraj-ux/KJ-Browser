package com.example.util

import android.util.Log
import android.util.LruCache
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object FamilyDnsChecker {
    private val client = OkHttpClient()
    private val cache = LruCache<String, Boolean>(2000)

    fun isDomainBlocked(host: String): Boolean {
        val cached = cache.get(host)
        if (cached != null) return cached

        try {
            val request = Request.Builder()
                .url("https://family.cloudflare-dns.com/dns-query?name=$host&type=A")
                .header("accept", "application/dns-json")
                .build()

            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return false
                val json = JSONObject(body)
                val answers = json.optJSONArray("Answer")
                if (answers != null) {
                    for (i in 0 until answers.length()) {
                        val answer = answers.getJSONObject(i)
                        val data = answer.optString("data")
                        // Cloudflare Family DNS returns 0.0.0.0 for blocked adult domains
                        if (data == "0.0.0.0") {
                            cache.put(host, true)
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("FamilyDns", "DNS Check failed", e)
        }
        
        cache.put(host, false)
        return false
    }
}
