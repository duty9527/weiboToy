package com.example.weibochat.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson

class SharedPreferencesCookieJar(private val context: Context) : okhttp3.CookieJar {
    private val sharedPrefs = migrateToEncryptedPrefs(
        context,
        "weibo_cookies_prefs",
        "weibo_cookies_encrypted_prefs"
    )
    private val gson = Gson()
    private val cookieStore = java.util.concurrent.ConcurrentHashMap<String, MutableList<okhttp3.Cookie>>()

    init {
        loadFromPrefs()
    }

    private fun loadFromPrefs() {
        try {
            val json = sharedPrefs.getString("cookies_json", null)
            if (!json.isNullOrBlank()) {
                val type = object : com.google.gson.reflect.TypeToken<Map<String, List<SerializableCookie>>>() {}.type
                val serialized: Map<String, List<SerializableCookie>> = gson.fromJson(json, type)
                serialized.forEach { (host, list) ->
                    cookieStore[host] = list.map { it.toOkHttpCookie() }.toMutableList()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveToPrefs() {
        try {
            val serialized = cookieStore.mapValues { entry ->
                entry.value.map { SerializableCookie.fromOkHttpCookie(it) }
            }
            val json = gson.toJson(serialized)
            sharedPrefs.edit().putString("cookies_json", json).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getFlatCookieString(): String {
        val flatMap = mutableMapOf<String, String>()
        cookieStore.values.forEach { list ->
            list.forEach { cookie ->
                flatMap[cookie.name] = cookie.value
            }
        }
        return flatMap.map { "${it.key}=${it.value}" }.joinToString("; ")
    }

    fun seedCookies(cookieStr: String) {
        if (cookieStr.isBlank()) return
        loadFromPrefs()

        val cookiesMap = mutableMapOf<String, String>()
        cookieStr.split(";").forEach {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                cookiesMap[parts[0].trim()] = parts[1].trim()
            }
        }

        val sinaKeys = setOf("tgc", "ALC", "ALF", "LT")
        val weiboComCookies = mutableListOf<okhttp3.Cookie>()
        val weiboCnCookies = mutableListOf<okhttp3.Cookie>()
        val sinaComCookies = mutableListOf<okhttp3.Cookie>()

        cookiesMap.forEach { (name, value) ->
            val builder = okhttp3.Cookie.Builder()
                .name(name)
                .value(value)
                .path("/")
                .expiresAt(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)

            if (name in sinaKeys) {
                builder.domain("login.sina.com.cn")
                sinaComCookies.add(builder.build())
            } else {
                builder.domain("weibo.com")
                weiboComCookies.add(builder.build())

                val builderCn = okhttp3.Cookie.Builder()
                    .name(name)
                    .value(value)
                    .path("/")
                    .expiresAt(System.currentTimeMillis() + 30L * 24 * 3600 * 1000)
                    .domain("weibo.cn")
                weiboCnCookies.add(builderCn.build())
            }
        }

        synchronized(this) {
            if (weiboComCookies.isNotEmpty()) {
                mergeCookies("weibo.com", weiboComCookies)
            }
            if (weiboCnCookies.isNotEmpty()) {
                mergeCookies("weibo.cn", weiboCnCookies)
            }
            if (sinaComCookies.isNotEmpty()) {
                mergeCookies("login.sina.com.cn", sinaComCookies)
            }
            promoteAuthCookiesForWeiboHosts()
            saveToPrefs()
        }
    }

    private fun promoteAuthCookiesForWeiboHosts() {
        val authNames = setOf("SUB", "SUBP", "ALF", "SSOLoginState")
        val authCookies = cookieStore.values
            .flatten()
            .filter { it.name in authNames }
            .distinctBy { it.name }

        if (authCookies.isEmpty()) return

        val expiresAt = System.currentTimeMillis() + 30L * 24 * 3600 * 1000
        val targetHosts = listOf("weibo.com", "weibo.cn", "login.sina.com.cn", "passport.weibo.com")

        targetHosts.forEach { host ->
            val hostCookies = authCookies.map { cookie ->
                okhttp3.Cookie.Builder()
                    .name(cookie.name)
                    .value(cookie.value)
                    .path("/")
                    .expiresAt(if (cookie.expiresAt > System.currentTimeMillis()) cookie.expiresAt else expiresAt)
                    .domain(host)
                    .apply {
                        if (cookie.secure) secure()
                        if (cookie.httpOnly) httpOnly()
                    }
                    .build()
            }
            mergeCookies(host, hostCookies)
        }
    }

    private fun mergeCookies(host: String, cookies: List<okhttp3.Cookie>) {
        val currentCookies = cookieStore.getOrPut(host) { mutableListOf() }
        cookies.forEach { newCookie ->
            currentCookies.removeAll { it.name == newCookie.name && it.path == newCookie.path }
            currentCookies.add(newCookie)
        }
    }

    @Synchronized
    fun clear() {
        cookieStore.clear()
        sharedPrefs.edit().remove("cookies_json").apply()
        val mainPrefs = migrateToEncryptedPrefs(context, "weibo_prefs", "weibo_encrypted_prefs")
        mainPrefs.edit().remove("cookie").remove("mobile_cookie").remove("group_id").apply()
    }

    @Synchronized
    override fun saveFromResponse(url: okhttp3.HttpUrl, cookies: List<okhttp3.Cookie>) {
        loadFromPrefs()
        mergeCookies(url.host, cookies)

        cookieStore[url.host]?.removeAll { it.expiresAt < System.currentTimeMillis() }
        saveToPrefs()
    }

    @Synchronized
    override fun loadForRequest(url: okhttp3.HttpUrl): List<okhttp3.Cookie> {
        loadFromPrefs()
        promoteAuthCookiesForWeiboHosts()
        val validCookies = mutableListOf<okhttp3.Cookie>()
        val currentTime = System.currentTimeMillis()

        cookieStore.forEach { (host, list) ->
            list.removeAll { it.expiresAt < currentTime }
            list.forEach { cookie ->
                if (cookie.matches(url)) {
                    validCookies.add(cookie)
                }
            }
        }
        return validCookies
    }
}
