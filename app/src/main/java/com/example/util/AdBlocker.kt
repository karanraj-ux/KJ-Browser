package com.example.util

import kotlinx.coroutines.launch
import kotlinx.coroutines.DelicateCoroutinesApi

object AdBlocker {
    private val AD_DOMAINS = setOf(
        "doubleclick.net",
        "googleadservices.com",
        "googlesyndication.com",
        "adsystem.com",
        "advertising.com",
        "scorecardresearch.com",
        "quantserve.com",
        "outbrain.com",
        "taboola.com",
        "criteo.com",
        "amazon-adsystem.com",
        "adnxs.com",
        "rubiconproject.com",
        "openx.net",
        "adsrvr.org",
        "turn.com",
        "moatads.com",
        "adsafeprotected.com",
        "pubmatic.com",
        "yieldmanager.com",
        "revsci.net",
        "bluekai.com",
        "exelator.com",
        "krxd.net",
        "mathtag.com",
        "dotomi.com",
        "casalemedia.com",
        "serving-sys.com",
        "rlcdn.com",
        "imrworldwide.com",
        "agkn.com",
        "specificclick.net",
        "vindicosuite.com",
        "exponential.com",
        "tribalfusion.com",
        "zedo.com",
        "media.net",
        "bidswitch.net",
        "lijit.com",
        "sitescout.com",
        "betrad.com",
        "sharethis.com",
        "addthis.com",
        "tynt.com",
        "demdex.net",
        "tapad.com",
        "everesttech.net",
        "atdmt.com",
        "nexus.ensighten.com",
        "omtrdc.net",
        "2o7.net",
        "fls.doubleclick.net",
        "gstatic.com/ads",
        "facebook.com/tr",
        "bing.com/bat.js",
        "snap.licdn.com/li.lms-analytics",
        "tiktok.com/api/ad",
        "reddit.com/api/v1/telemetry",
        "t.co/i/ads",
        "ads-twitter.com",
        "analytics.twitter.com"
    )

    private val ADULT_DOMAINS = setOf(
        "pornhub.com",
        "xvideos.com",
        "xnxx.com",
        "xhamster.com",
        "youporn.com",
        "redtube.com",
        "spankbang.com",
        "eporner.com",
        "chaturbate.com",
        "bongacams.com",
        "livejasmin.com",
        "xhamsterlive.com",
        "stripchat.com",
        "adultwork.com",
        "onlyfans.com", // debatable but often blocked for focus
        "fansly.com"
    )

    private val dynamicBlockedDomains = mutableSetOf<String>()
    private val dynamicSubstringRules = mutableSetOf<String>()

    fun init() {
        refreshSubscriptions()
    }

    @OptIn(DelicateCoroutinesApi::class)
    fun refreshSubscriptions() {
        val urls = PowerUserSettings.adBlockSubscriptions.value
        if (urls.isEmpty()) {
            dynamicBlockedDomains.clear()
            dynamicSubstringRules.clear()
            return
        }

        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val newDomains = mutableSetOf<String>()
            val newSubstrings = mutableSetOf<String>()
            for (url in urls) {
                try {
                    val client = okhttp3.OkHttpClient()
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = client.newCall(request).execute()
                    if (response.isSuccessful) {
                        response.body?.string()?.lines()?.forEach { line ->
                            val cleanLine = line.trim()
                            if (cleanLine.isNotBlank() && !cleanLine.startsWith("!")) {
                                if (cleanLine.startsWith("||") && cleanLine.endsWith("^")) {
                                    // EasyList domain rule: ||example.com^
                                    newDomains.add(cleanLine.substring(2, cleanLine.length - 1).lowercase())
                                } else if (cleanLine.startsWith("||")) {
                                    newDomains.add(cleanLine.substring(2).lowercase())
                                } else if (!cleanLine.startsWith("#") && !cleanLine.startsWith("@@") && cleanLine.length > 3) {
                                    // Hosts format support
                                    val parts = cleanLine.split(Regex("\\s+"))
                                    if (parts.size >= 2 && (parts[0] == "0.0.0.0" || parts[0] == "127.0.0.1")) {
                                        newDomains.add(parts[1].lowercase())
                                    } else if (parts.size == 1 && !cleanLine.contains("/") && !cleanLine.contains("*")) { 
                                        newDomains.add(parts[0].lowercase())
                                    } else if (!cleanLine.startsWith("[")) { // skip tags like [Adblock Plus 2.0]
                                        // Basic substring rule fallback
                                        newSubstrings.add(cleanLine.replace("^", "").lowercase())
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("AdBlocker", "Failed to fetch list $url", e)
                }
            }
            dynamicBlockedDomains.clear()
            dynamicBlockedDomains.addAll(newDomains)
            dynamicSubstringRules.clear()
            dynamicSubstringRules.addAll(newSubstrings)
        }
    }

    fun isAdOrTracker(url: String): Boolean {
        // Fast URL parsing vs domain set
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            
            // Check exact and suffix matches in O(1) by hashing segments
            var currentHost = host
            while (currentHost.isNotEmpty()) {
                if (AD_DOMAINS.contains(currentHost) || dynamicBlockedDomains.contains(currentHost)) {
                    return true
                }
                val dotIndex = currentHost.indexOf('.')
                if (dotIndex == -1) break
                currentHost = currentHost.substring(dotIndex + 1)
            }
            
            // Substring rules (limit checks to prevent jank on huge lists)
            val lowerUrl = url.lowercase()
            // We only check the first 2000 rules if it's huge, to maintain 60fps
            var checks = 0
            for (rule in dynamicSubstringRules) {
                if (lowerUrl.contains(rule)) return true
                checks++
                if (checks > 2000) break
            }
            
            // Check custom domains from PowerUserSettings
            val customDomainsStr = PowerUserSettings.customAdBlockList.value
            if (customDomainsStr.isNotBlank()) {
                val customDomains = customDomainsStr.split(",").map { it.trim().lowercase() }
                if (customDomains.any { domain -> host == domain || host.endsWith(".$domain") }) return true
            }

            // Common path patterns for trackers
            val path = uri.path?.lowercase() ?: ""
            if (path.contains("/ad/") || path.contains("/track") || path.contains("analytics.js") || path.contains("pixel.gif")) return true
            
            false
        } catch (e: Exception) {
            false
        }
    }

    fun isAdultContent(url: String): Boolean {
        return try {
            val host = android.net.Uri.parse(url).host?.lowercase() ?: return false
            if (ADULT_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }) {
                return true
            }
            // Real-time DNS check via Cloudflare Family DNS (1.1.1.3)
            return com.example.util.FamilyDnsChecker.isDomainBlocked(host)
        } catch (e: Exception) {
            false
        }
    }
    
    fun isHeavyMedia(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".avi") || lowerUrl.endsWith(".webm") || 
               lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".mov") || lowerUrl.endsWith(".mkv") ||
               lowerUrl.endsWith(".m3u8") || lowerUrl.endsWith(".ts") || lowerUrl.endsWith(".mp3") ||
               lowerUrl.contains("youtube.com/embed/") || lowerUrl.contains("player.vimeo.com") ||
               lowerUrl.contains("youtube.com/api/") || lowerUrl.contains("video.")
    }
}
