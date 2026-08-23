package com.auriqo.music.ui.screens.settings

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.auriqo.music.security.isAllowedDiscordWebViewUrl
import com.auriqo.music.security.isDiscordTokenPage

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DiscordTokenWebView(
    onTokenExtracted: (String) -> Unit
) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                )
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                
                addJavascriptInterface(object : Any() {
                    @JavascriptInterface
                    fun onToken(token: String?) {
                        if (!token.isNullOrBlank() && token != "null") {
                            val cleanToken = token.replace("\"", "")
                            if (cleanToken.isNotBlank()) {
                                post { onTokenExtracted(cleanToken) }
                            }
                        }
                    }
                }, "DiscordInterface")

                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) {
                        super.onPageFinished(view, url)
                        if (isDiscordTokenPage(url)) {
                            view.evaluateJavascript(
                                """
                                (function() {
                                    try {
                                        var iframe = document.createElement('iframe');
                                        document.head.append(iframe);
                                        var pd = Object.getOwnPropertyDescriptor(iframe.contentWindow, 'localStorage');
                                        iframe.remove();
                                        Object.defineProperty(window, 'localStorage', pd);
                                        var t = window.localStorage.getItem('token');
                                        if (t) {
                                            DiscordInterface.onToken(t);
                                        }
                                    } catch(e) {
                                        DiscordInterface.onToken(null);
                                    }
                                })();
                                """.trimIndent(),
                                null
                            )
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView,
                        request: android.webkit.WebResourceRequest,
                    ): Boolean = !isAllowedDiscordWebViewUrl(request.url.toString())

                    override fun onPageStarted(
                        view: WebView,
                        url: String,
                        favicon: android.graphics.Bitmap?,
                    ) {
                        super.onPageStarted(view, url, favicon)
                        if (url != "about:blank" && !isAllowedDiscordWebViewUrl(url)) {
                            view.stopLoading()
                            view.loadUrl("about:blank")
                        }
                    }
                }
                loadUrl("https://discord.com/login")
            }
        },
        update = { webView ->
            // Update block if needed
        }
    )
}
