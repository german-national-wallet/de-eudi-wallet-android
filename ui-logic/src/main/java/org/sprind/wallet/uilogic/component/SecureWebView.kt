/*
 * Copyright (c) 2026 European Commission
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.sprind.wallet.uilogic.component

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri

/**
 * Shows one web page and nothing else.
 *
 * The page is read-only by construction: scripts, storage, file access and cookies are off, and any
 * navigation away from the host of [url] is refused rather than followed, so a redirect or a link
 * cannot turn this into a browser. Pages are only loaded over https.
 */
@Composable
fun SecureWebView(
    url: String,
    modifier: Modifier = Modifier,
) {
    val fontScale = LocalConfiguration.current.fontScale

    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(Color.WHITE)
                isVerticalScrollBarEnabled = true
                harden()
                // The page does not follow the system font size by itself, so the setting is passed
                // on as a text zoom.
                settings.textZoom = (fontScale * PERCENT).toInt()
                webViewClient = SingleHostWebViewClient(host = url.toUri().host)
            }
        },
        update = { webView ->
            if (webView.url != url) {
                webView.loadUrl(url)
            }
        },
        onRelease = WebView::destroy,
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun WebView.harden() {
    CookieManager.getInstance().apply {
        setAcceptCookie(false)
        setAcceptThirdPartyCookies(this@harden, false)
    }
    settings.apply {
        javaScriptEnabled = false
        domStorageEnabled = false
        allowFileAccess = false
        allowContentAccess = false
        cacheMode = WebSettings.LOAD_NO_CACHE
        setGeolocationEnabled(false)
        mediaPlaybackRequiresUserGesture = true
        setSupportMultipleWindows(false)
        javaScriptCanOpenWindowsAutomatically = false
        safeBrowsingEnabled = true
    }
    clearCache(true)
    clearHistory()
    clearFormData()
}

/**
 * Refuses everything but https pages on [host], so the view cannot be navigated somewhere else.
 */
private class SingleHostWebViewClient(private val host: String?) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val target = request.url
        val allowed = target.scheme == HTTPS_SCHEME && target.host == host
        return !allowed
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
    }
}

private const val PERCENT = 100