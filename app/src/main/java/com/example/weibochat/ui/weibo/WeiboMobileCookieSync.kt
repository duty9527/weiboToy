package com.example.weibochat.ui.weibo

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.weibochat.data.DataRepository

private const val WEIBO_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeiboMobileCookieSync(
    repository: DataRepository,
    onCookiesReady: () -> Unit,
    modifier: Modifier = Modifier
) {
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val hasReported = remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose {
            mainHandler.removeCallbacksAndMessages(null)
            webViewState.value?.apply {
                stopLoading()
                webViewClient = WebViewClient()
                removeAllViews()
                destroy()
            }
            webViewState.value = null
        }
    }

    AndroidView(
        modifier = modifier.size(1.dp),
        factory = { context ->
            seedWeiboWebViewCookies(repository.getAllCookies())
            WebView(context).apply {
                layoutParams = android.view.ViewGroup.LayoutParams(1, 1)
                visibility = android.view.View.INVISIBLE
                setBackgroundColor(0) // transparent
                
                webViewState.value = this
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.cacheMode = WebSettings.LOAD_DEFAULT
                settings.userAgentString = WEIBO_MOBILE_USER_AGENT
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        hasReported.value = false
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        CookieManager.getInstance().flush()
                        mainHandler.postDelayed({
                            if (isWeiboLoginUrl(url)) {
                                android.util.Log.w("WeiboMobileCookieSync", "Skip cookie sync from login page: $url")
                                return@postDelayed
                            }

                            val webViewCookie = collectWeiboWebViewCookies()
                            if (webViewCookie.isBlank()) return@postDelayed
                            if (!hasWeiboAuthCookie(webViewCookie)) {
                                android.util.Log.w("WeiboMobileCookieSync", "Skip cookie sync because WebView has no auth cookie")
                                return@postDelayed
                            }

                            val (_, groupId) = repository.getCredentials()
                            val mergedCookie = mergeCookieStrings(repository.getAllCookies(), webViewCookie)
                            repository.saveCredentials(mergedCookie, groupId)
                            if (!hasReported.value) {
                                hasReported.value = true
                                onCookiesReady()
                            }
                        }, 500)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val host = request?.url?.host ?: return false
                        return !isAllowedWeiboHost(host)
                    }
                }
                loadUrl("https://m.weibo.cn/")
            }
        },
        update = { webView ->
            seedWeiboWebViewCookies(repository.getAllCookies())
            webViewState.value = webView
        }
    )
}
