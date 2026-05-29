package com.duty.weibotoy.ui.weibo

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
                val uri = Uri.parse(host)
                val hostStr = uri.host ?: ""
                val domainAttr = when {
                    hostStr.endsWith("weibo.com") -> "; Domain=.weibo.com"
                    hostStr.endsWith("weibo.cn") -> "; Domain=.weibo.cn"
                    hostStr.endsWith("sina.com.cn") -> "; Domain=.sina.com.cn"
                    hostStr.endsWith("sina.cn") -> "; Domain=.sina.cn"
                    else -> ""
                }
                cookieManager.setCookie(host, "$cookie$domainAttr; Path=/")
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

internal fun mergeCookieStrings(existingCookieString: String, newCookieString: String): String {
    val cookies = linkedMapOf<String, String>()
    listOf(existingCookieString, newCookieString).forEach { cookieString ->
        cookieString
            .split(";")
            .map { it.trim() }
            .filter { it.contains("=") }
            .forEach { cookie ->
                val parts = cookie.split("=", limit = 2)
                cookies[parts[0].trim()] = parts[1].trim()
            }
    }
    return cookies.map { "${it.key}=${it.value}" }.joinToString("; ")
}

internal fun hasWeiboAuthCookie(cookieString: String): Boolean {
    val names = cookieString
        .split(";")
        .mapNotNull { cookie ->
            cookie.trim()
                .takeIf { it.contains("=") }
                ?.substringBefore("=")
                ?.trim()
        }
        .toSet()
    return "SUB" in names || "SSOLoginState" in names
}

internal fun isWeiboLoginUrl(url: String?): Boolean {
    if (url.isNullOrBlank()) return false
    if (url.contains("retcode=")) return true
    val host = Uri.parse(url).host ?: return false
    return host == "login.sina.com.cn" ||
        host.endsWith(".passport.weibo.com") ||
        host.endsWith(".passport.weibo.cn") ||
        host.contains("passport") ||
        host.contains("login")
}

internal fun isAllowedWeiboHost(host: String): Boolean {
    val normalized = Uri.parse("https://$host").host ?: return false
    return normalized == "weibo.com" ||
        normalized.endsWith(".weibo.com") ||
        normalized == "weibo.cn" ||
        normalized.endsWith(".weibo.cn") ||
        normalized == "sina.com.cn" ||
        normalized.endsWith(".sina.com.cn") ||
        normalized == "sina.cn" ||
        normalized.endsWith(".sina.cn") ||
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
    "https://passport.weibo.cn",
    "https://sina.cn",
    "https://login.sina.cn",
    "https://passport.sina.cn"
)
