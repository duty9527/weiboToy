package com.example.weibochat.ui.weibo

import android.net.Uri
import android.webkit.CookieManager

internal fun seedWeiboWebViewCookies(cookieString: String) {
    if (cookieString.isBlank()) return

    val cookieManager = CookieManager.getInstance()
    cookieManager.setAcceptCookie(true)

    cookieString
        .split(";")
        .map { it.trim() }
        .filter { it.contains("=") }
        .forEach { cookie ->
            weiboCookieHosts.forEach { host ->
                cookieManager.setCookie(host, "$cookie; Path=/")
            }
        }

    cookieManager.flush()
}

internal fun collectWeiboWebViewCookies(): String {
    val cookieManager = CookieManager.getInstance()
    val cookies = linkedMapOf<String, String>()

    weiboCookieHosts
        .mapNotNull { cookieManager.getCookie(it) }
        .flatMap { it.split(";") }
        .map { it.trim() }
        .filter { it.contains("=") }
        .forEach { cookie ->
            val parts = cookie.split("=", limit = 2)
            cookies[parts[0].trim()] = parts[1].trim()
        }

    return cookies.map { "${it.key}=${it.value}" }.joinToString("; ")
}

internal fun isAllowedWeiboHost(host: String): Boolean {
    val normalized = Uri.parse("https://$host").host ?: return false
    return normalized == "weibo.com" ||
        normalized.endsWith(".weibo.com") ||
        normalized == "weibo.cn" ||
        normalized.endsWith(".weibo.cn") ||
        normalized == "sina.com.cn" ||
        normalized.endsWith(".sina.com.cn") ||
        normalized == "sinaimg.cn" ||
        normalized.endsWith(".sinaimg.cn")
}

private val weiboCookieHosts = listOf(
    "https://weibo.com",
    "https://m.weibo.com",
    "https://m.weibo.cn",
    "https://weibo.cn",
    "https://login.sina.com.cn",
    "https://passport.weibo.com",
    "https://passport.weibo.cn"
)
