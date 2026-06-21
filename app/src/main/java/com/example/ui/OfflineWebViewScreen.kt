package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient

@Composable
fun OfflineWebViewScreen(htmlContent: String, baseUrl: String) {
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = false
                    settings.allowFileAccess = true
                }
            },
            update = { webView ->
                if (htmlContent.startsWith("file://")) {
                    // It's local path
                    webView.loadUrl(htmlContent)
                } else if (htmlContent.startsWith("<?xml") || htmlContent.startsWith("<html") || htmlContent.startsWith("<!DOCTYPE")) {
                    webView.loadDataWithBaseURL(baseUrl, htmlContent, "text/html", "UTF-8", null)
                } else {
                    val fallbackHtml = "<html><body style='font-family: sans-serif; padding:16px;'><h3>Source: $baseUrl</h3><p>$htmlContent</p></body></html>"
                    webView.loadDataWithBaseURL(baseUrl, fallbackHtml, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
