package com.example.util

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

    fun isAdOrTracker(url: String): Boolean {
        // Fast URL parsing vs domain set
        return try {
            val uri = android.net.Uri.parse(url)
            val host = uri.host?.lowercase() ?: return false
            if (AD_DOMAINS.any { domain -> host == domain || host.endsWith(".$domain") }) return true
            
            // Common path patterns for trackers
            val path = uri.path?.lowercase() ?: ""
            if (path.contains("/ad/") || path.contains("/track") || path.contains("analytics.js") || path.contains("pixel.gif")) return true
            
            false
        } catch (e: Exception) {
            false
        }
    }
    
    fun isHeavyMedia(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return lowerUrl.endsWith(".mp4") || lowerUrl.endsWith(".avi") || lowerUrl.endsWith(".webm") || 
               lowerUrl.endsWith(".gif") || lowerUrl.endsWith(".mov")
    }
}
