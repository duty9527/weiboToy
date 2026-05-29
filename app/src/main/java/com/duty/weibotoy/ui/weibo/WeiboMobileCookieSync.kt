package com.duty.weibotoy.ui.weibo

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceError
import android.webkit.WebResourceResponse
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
import com.duty.weibotoy.data.DataRepository

private const val WEIBO_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WeiboMobileCookieSync(
    repository: DataRepository,
    onCookiesReady: () -> Unit,
    onSyncBlocked: ((String) -> Unit)? = null,
    maxWaitMillis: Long = 15_000L,
    modifier: Modifier = Modifier
) {
    android.util.Log.d("WeiboMobileCookieSync", "WeiboMobileCookieSync Composable function entered")
    val webViewState = remember { mutableStateOf<WebView?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val hasReported = remember { mutableStateOf(false) }
    val syncStartedAt = remember { mutableStateOf(System.currentTimeMillis()) }

    DisposableEffect(Unit) {
        android.util.Log.d("WeiboMobileCookieSync", "DisposableEffect: initialized")
        onDispose {
            android.util.Log.d("WeiboMobileCookieSync", "DisposableEffect: disposing WebView")
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
            android.util.Log.d("WeiboMobileCookieSync", "AndroidView factory: creating WebView. Current cookies length: ${repository.getAllCookies().length}")
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
                    override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                        android.util.Log.d("WeiboMobileCookieSync", "onPageStarted: url=$url")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        android.util.Log.d("WeiboMobileCookieSync", "onPageFinished: url=$url")
                        CookieManager.getInstance().flush()
                        mainHandler.postDelayed({
                            val webViewCookie = collectWeiboWebViewCookies()
                            val hasAuthCookie = hasWeiboAuthCookie(webViewCookie)
                            val isLoginPage = isWeiboLoginUrl(url)

                            android.util.Log.d("WeiboMobileCookieSync", "Processing cookies: isLoginPage=$isLoginPage, hasAuthCookie=$hasAuthCookie, cookiesLength=${webViewCookie.length}")

                            if (webViewCookie.isNotBlank() && hasAuthCookie && !isLoginPage) {
                                val mergedCookie = mergeCookieStrings(repository.getAllCookies(), webViewCookie)
                                android.util.Log.i("WeiboMobileCookieSync", "Mobile cookie successfully synchronized and merged! length=${mergedCookie.length}")
                                repository.saveMobileCookie(mergedCookie)
                                if (!hasReported.value) {
                                    hasReported.value = true
                                    android.util.Log.i("WeiboMobileCookieSync", "Triggering onCookiesReady callback")
                                    onCookiesReady()
                                }
                                return@postDelayed
                            }

                            val elapsed = System.currentTimeMillis() - syncStartedAt.value
                            if (elapsed < maxWaitMillis) {
                                if (isLoginPage) {
                                    android.util.Log.d(
                                        "WeiboMobileCookieSync",
                                        "Currently on login/SSO redirect page, waiting for auto-redirect. url=$url"
                                    )
                                } else {
                                    android.util.Log.w(
                                        "WeiboMobileCookieSync",
                                        "Mobile cookie not ready yet, retrying page load. url=$url, hasAuthCookie=$hasAuthCookie"
                                    )
                                    view?.postDelayed({ 
                                        android.util.Log.d("WeiboMobileCookieSync", "Retrying loadUrl: https://m.weibo.cn/")
                                        view.loadUrl("https://m.weibo.cn/") 
                                    }, 1_000L)
                                }
                                return@postDelayed
                            }

                            val reason = if (isLoginPage) {
                                "微博移动端会话仍然要求登录"
                            } else {
                                "没有拿到微博移动端登录 Cookie"
                            }
                            android.util.Log.w("WeiboMobileCookieSync", "Mobile cookie sync blocked: $reason, url=$url")
                            onSyncBlocked?.invoke(reason)
                        }, 500)
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val urlStr = request?.url?.toString() ?: ""
                        val host = request?.url?.host ?: ""
                        val allowed = isAllowedWeiboHost(host)
                        android.util.Log.d("WeiboMobileCookieSync", "shouldOverrideUrlLoading: url=$urlStr, host=$host, allowed=$allowed")
                        return !allowed
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        android.util.Log.w("WeiboMobileCookieSync", "onReceivedError: url=${request?.url}, code=${error?.errorCode}, desc=${error?.description}")
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        android.util.Log.w("WeiboMobileCookieSync", "onReceivedHttpError: url=${request?.url}, status=${errorResponse?.statusCode}, phrase=${errorResponse?.reasonPhrase}")
                    }
                }
                android.util.Log.d("WeiboMobileCookieSync", "Starting initial loadUrl: https://m.weibo.cn/")
                loadUrl("https://m.weibo.cn/")
            }
        },
        update = { webView ->
            android.util.Log.d("WeiboMobileCookieSync", "AndroidView update: current cookies length: ${repository.getAllCookies().length}")
            seedWeiboWebViewCookies(repository.getAllCookies())
            webViewState.value = webView
        }
    )
}
