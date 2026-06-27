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
                } else {
                    val cleanCss = "<style>header, footer, nav, aside, iframe, .ads, .advertisement, [class*='banner'], .cookie-banner, .popup { display: none !important; } body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; line-height: 1.6; padding: 20px; max-width: 800px; margin: 0 auto; } img { max-width: 100%; height: auto; }</style>"
                    val finalHtml = if (htmlContent.contains("<head>", ignoreCase = true)) {
                        htmlContent.replaceFirst(Regex("(?i)<head>"), "<head>$cleanCss")
                    } else if (htmlContent.startsWith("<?xml") || htmlContent.startsWith("<html") || htmlContent.startsWith("<!DOCTYPE")) {
                        val insertionPoint = htmlContent.indexOf("<html>") + 6
                        if (insertionPoint > 5) {
                            htmlContent.substring(0, insertionPoint) + "<head>$cleanCss</head>" + htmlContent.substring(insertionPoint)
                        } else {
                            htmlContent + cleanCss
                        }
                    } else {
                        "<html><head><meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">$cleanCss</head><body>$htmlContent</body></html>"
                    }
                    webView.loadDataWithBaseURL(baseUrl, finalHtml, "text/html", "UTF-8", null)
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}
