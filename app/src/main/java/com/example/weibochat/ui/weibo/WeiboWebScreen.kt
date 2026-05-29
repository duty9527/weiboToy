package com.example.weibochat.ui.weibo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.activity.compose.BackHandler
import com.example.weibochat.data.DEFAULT_WEIBO_TIMELINE_LIST_ID
import com.example.weibochat.data.DataRepository
import com.example.weibochat.theme.DarkBg

private const val WEIBO_GROUP_URL = "https://weibo.com/mygroups?gid=$DEFAULT_WEIBO_TIMELINE_LIST_ID"
const val WEIBO_SEARCH_URL = "https://m.weibo.cn/search?containerid=231583"
private const val WEIBO_WEB_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeiboWebScreen(
    repository: DataRepository,
    reloadSignal: Int,
    initialUrl: String = WEIBO_GROUP_URL,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }

    val currentWebView = webViewState.value
    BackHandler(enabled = canGoBack || onBack != null) {
        if (currentWebView?.canGoBack() == true) {
            currentWebView.goBack()
        } else {
            onBack?.invoke()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewState.value?.apply {
                stopLoading()
                webViewClient = WebViewClient()
                removeAllViews()
                destroy()
            }
            webViewState.value = null
        }
    }

    LaunchedEffect(reloadSignal) {
        webViewState.value?.reload()
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                seedWeiboWebViewCookies(repository.getAllCookies())
                WebView(context).apply {
                    webViewState.value = this
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.cacheMode = WebSettings.LOAD_DEFAULT
                    settings.userAgentString = WEIBO_WEB_USER_AGENT
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            isLoading = true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            cookieManager.flush()
                            isLoading = false
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
                            val host = request?.url?.host ?: return false
                            return !isAllowedWeiboHost(host)
                        }
                    }
                    loadUrl(initialUrl)
                }
            },
            update = { webView ->
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                seedWeiboWebViewCookies(repository.getAllCookies())
                cookieManager.setAcceptThirdPartyCookies(webView, true)
                webViewState.value = webView
                if (webView.url.isNullOrBlank()) {
                    webView.loadUrl(initialUrl)
                }
            }
        )

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFF97316)
            )
        }
    }
}
