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
import android.graphics.Bitmap
import android.graphics.Color
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
 *
 * @param url the page to read.
 * @param modifier applied to the view.
 * @param onLoadingChanged reports whether a page is on its way, so the caller can cover the empty
 *   view while it arrives. It is called with false once the page has rendered and also when it
 *   fails, so a page that never arrives does not leave a loader spinning.
 */
@Composable
fun SecureWebView(
    url: String,
    modifier: Modifier = Modifier,
    onLoadingChanged: (Boolean) -> Unit = {},
) {
    val fontScale = LocalConfiguration.current.fontScale
    // The client is built once, in the factory, so it reads the current callback rather than the
    // one that happened to be in place then.
    val loadingListener by rememberUpdatedState(onLoadingChanged)
    // The address the view was last asked for. Comparing against the view's *own* url instead would
    // ask again whenever a redirect settled on a different one, and the page would load forever.
    val requestedUrl = remember { mutableStateOf<String?>(null) }

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
                webViewClient = SingleHostWebViewClient(
                    host = url.toUri().host,
                    onLoadingChanged = { loadingListener(it) },
                )
            }
        },
        update = { webView ->
            if (requestedUrl.value != url) {
                requestedUrl.value = url
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
 * Refuses everything but https pages on [host], so the view cannot be navigated somewhere else, and
 * reports through [onLoadingChanged] whether a page is on its way.
 */
private class SingleHostWebViewClient(
    private val host: String?,
    private val onLoadingChanged: (Boolean) -> Unit,
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest,
    ): Boolean {
        val target = request.url
        val allowed = target.scheme == HTTPS_SCHEME && target.host == host
        return !allowed
    }

    override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
        onLoadingChanged(true)
    }

    override fun onPageFinished(view: WebView, url: String?) {
        onLoadingChanged(false)
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        error: WebResourceError,
    ) {
        // Only the page itself decides the loading state; a missing image on it does not.
        if (request.isForMainFrame) {
            onLoadingChanged(false)
        }
    }

    private companion object {
        const val HTTPS_SCHEME = "https"
    }
}

private const val PERCENT = 100