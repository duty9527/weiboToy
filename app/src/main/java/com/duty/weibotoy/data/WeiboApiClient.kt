package com.duty.weibotoy.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import android.content.Context

private const val WEIBO_DESKTOP_USER_AGENT =
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
private const val WEIBO_MOBILE_USER_AGENT =
    "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

class WeiboApiClient(private val context: Context) {
    private val cookieJar = SharedPreferencesCookieJar(context)
    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()
    private val ssoClient = client.newBuilder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .writeTimeout(5, TimeUnit.SECONDS)
        .callTimeout(8, TimeUnit.SECONDS)
        .build()
    private val noRedirectClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .build()
    private val gson = com.google.gson.GsonBuilder()
        .registerTypeAdapterFactory(WeiboCommentListTypeAdapterFactory())
        .create()

    // Session hydration cache - avoid redundant HTTP requests before every API call
    private var lastTimelineHydrationTime = 0L
    private var lastGroupChatHydrationTime = 0L
    private val hydrationCacheMs = 5 * 60 * 1000L // 5 minutes

    fun clearSession() {
        cookieJar.clear()
        lastTimelineHydrationTime = 0L
        lastGroupChatHydrationTime = 0L
    }

    fun getStoredCookieString(seedCookie: String = ""): String {
        cookieJar.seedCookies(seedCookie)
        return cookieJar.getFlatCookieString()
    }

    private fun checkResponse(response: okhttp3.Response, bodyString: String?) {
        val finalUrl = response.request.url.toString()
        if (finalUrl.contains("passport.weibo.com") || finalUrl.contains("passport.weibo.cn") || finalUrl.contains("security") || finalUrl.contains("verify") || finalUrl.contains("unisec")) {
            if (finalUrl.contains("passport.weibo")) {
                throw WeiboRiskControlException("会话已失效或需要重新登录")
            } else {
                throw WeiboRiskControlException("微博安全验证 (被风控)，请在浏览器中登录微博完成验证")
            }
        }
        if (bodyString != null) {
            val contentType = response.header("Content-Type").orEmpty()
            val trimmedBody = bodyString.trim()
            val isHtml = contentType.contains("text/html") || trimmedBody.contains("<html") || trimmedBody.contains("<!DOCTYPE")
            if (isHtml) {
                if (trimmedBody.contains("passport") || trimmedBody.contains("security") || trimmedBody.contains("verify") || trimmedBody.contains("验证")) {
                    throw WeiboRiskControlException("微博安全验证 (被风控)，请在浏览器中登录微博完成验证")
                } else {
                    throw WeiboRiskControlException("会话已失效或需要重新登录")
                }
            }
        }
    }

    private fun parseJsonp(body: String): String {
        val startIndex = body.indexOf('(')
        val endIndex = body.lastIndexOf(')')
        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
            return body.substring(startIndex + 1, endIndex)
        }
        return body
    }

    suspend fun fetchQrCode(): WeiboQrCodeResponse? = withContext(Dispatchers.IO) {
        val url = "https://login.sina.com.cn/sso/qrcode/image?entry=sso&size=180&callback=STK_1"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Referer", "https://weibo.com/")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonStr = parseJsonp(body)
                return@withContext gson.fromJson(jsonStr, WeiboQrCodeResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun checkQrStatus(qrid: String): WeiboQrStatusResponse? = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val url = "https://login.sina.com.cn/sso/qrcode/check?entry=sso&qrid=$qrid&callback=STK_$timestamp"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Referer", "https://weibo.com/")
            .build()
        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonStr = parseJsonp(body)
                return@withContext gson.fromJson(jsonStr, WeiboQrStatusResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun performSsoLogin(alt: String): String? = withContext(Dispatchers.IO) {
        val encodedAlt = java.net.URLEncoder.encode(alt, "UTF-8")
        val url = "https://login.sina.com.cn/sso/login.php?entry=sso&returntype=TEXT&crossdomain=1&alt=$encodedAlt"
        val request = Request.Builder()
            .url(url)
            .get()
            .addHeader("User-Agent", WEIBO_DESKTOP_USER_AGENT)
            .addHeader("Referer", "https://weibo.com/")
            .build()
        try {
            ssoClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                
                val body = response.body?.string() ?: return@withContext null
                val loginResponse = gson.fromJson(body, WeiboSsoLoginResponse::class.java)
                
                val crossDomainUrls = loginResponse.crossDomainUrlList
                if (!crossDomainUrls.isNullOrEmpty()) {
                    for (cUrl in crossDomainUrls) {
                        val cRequest = Request.Builder()
                            .url(cUrl)
                            .get()
                            .addHeader("User-Agent", WEIBO_DESKTOP_USER_AGENT)
                            .addHeader("Referer", "https://weibo.com/")
                            .build()
                        try {
                            ssoClient.newCall(cRequest).execute().use { cResponse ->
                                // CookieJar handles it
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("WeiboApiClient", "SSO cross-domain cookie request failed: $cUrl", e)
                        }
                    }
                }

                val flatCookie = cookieJar.getFlatCookieString()
                val finalCookie = if (loginResponse.uid != null) {
                    "uid=${loginResponse.uid}; $flatCookie"
                } else {
                    flatCookie
                }
                return@withContext if (finalCookie.isNotBlank()) finalCookie else null
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun queryContacts(cookie: String): WeiboContactsResponse? = withContext(Dispatchers.IO) {
        var attempts = 0
        var lastResult: WeiboContactsResponse? = null
        while (attempts < 3) {
            attempts++
            try {
                cookieJar.seedCookies(cookie)
                hydrateTimelineMobileSession() // Ensure mobile session cookie / tokens are refreshed
                val url = "https://m.weibo.cn/message/msglist?page=1"
                val request = Request.Builder()
                    .url(url)
                    .get()
                    .addHeader("Accept", "application/json, text/plain, */*")
                    .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
                    .addHeader("Referer", "https://m.weibo.cn/message")
                    .addHeader("X-Requested-With", "XMLHttpRequest")
                    .addHeader("MWeibo-Pwa", "1")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string()
                    checkResponse(response, body)
                    if (!response.isSuccessful) {
                        val code = response.code
                        if (code == 403 || code == 418) {
                            throw WeiboRiskControlException("微博风控限制 (HTTP $code)，请稍后再试或通过浏览器解除限制")
                        }
                    } else {
                        if (body != null) {
                            try {
                                val root = gson.fromJson(body, com.google.gson.JsonObject::class.java)
                                if (root != null && root.has("ok") && root.get("ok")?.asInt != 1) {
                                    val msg = root.get("msg")?.asString ?: ""
                                    val errno = root.get("errno")?.asString ?: ""
                                    if (msg.contains("频繁") || msg.contains("异常") || msg.contains("风控") || msg.contains("繁忙") || errno == "20003" || errno == "100005" || msg.contains("未登录") || msg.contains("登录") || msg.contains("login")) {
                                        if (msg.contains("未登录") || msg.contains("登录") || msg.contains("login") || errno == "20003") {
                                            throw WeiboRiskControlException("会话已失效或需要重新登录")
                                        } else {
                                            throw WeiboRiskControlException("微博接口受限 ($msg)，请稍后再试")
                                        }
                                    }
                                }
                            } catch (jsonEx: Exception) {
                                if (jsonEx is WeiboRiskControlException) {
                                    throw jsonEx
                                }
                                // Ignore and let parseMobileContacts handle
                            }

                            val parsed = parseMobileContacts(body)
                            if (!parsed.contacts.isNullOrEmpty()) {
                                return@withContext parsed
                            }
                            lastResult = parsed
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is WeiboRiskControlException) {
                    throw e
                }
                android.util.Log.e("WeiboApiClient", "queryContacts attempt $attempts failed: ${e.message}")
            }
            if (attempts < 3) {
                kotlinx.coroutines.delay(800L * attempts)
            }
        }
        return@withContext lastResult
    }

    private fun parseMobileContacts(bodyString: String): WeiboContactsResponse {
        val root = gson.fromJson(bodyString, com.google.gson.JsonObject::class.java)
        if (root.get("ok")?.asInt != 1) {
            return WeiboContactsResponse(contacts = emptyList())
        }

        fun jsonObject(element: com.google.gson.JsonElement?): com.google.gson.JsonObject? {
            return if (element != null && element.isJsonObject) element.asJsonObject else null
        }

        fun stringValue(obj: com.google.gson.JsonObject?, name: String): String? {
            val value = obj?.get(name) ?: return null
            return if (value.isJsonNull) null else value.asString
        }

        fun intValue(obj: com.google.gson.JsonObject?, name: String): Int? {
            val value = obj?.get(name) ?: return null
            return if (value.isJsonNull) null else value.asInt
        }

        val data = root.get("data")
        val items = when {
            data == null || data.isJsonNull -> emptyList()
            data.isJsonArray -> data.asJsonArray.mapNotNull { jsonObject(it) }
            data.isJsonObject -> data.asJsonObject.entrySet().mapNotNull { jsonObject(it.value) }
            else -> emptyList()
        }

        val contacts = items.mapNotNull { item ->
            val user = jsonObject(item.get("user"))
            val groupId = extractMobileGroupId(stringValue(item, "scheme")) ?: return@mapNotNull null
            val name = stringValue(user, "screen_name")
                ?: stringValue(user, "name")
                ?: groupId.toString()
            WeiboContact(
                unread_count = intValue(item, "unread") ?: 0,
                user = WeiboContactUser(
                    id = groupId,
                    idstr = stringValue(user, "idstr") ?: groupId.toString(),
                    name = name,
                    type = 2,
                    group_type = null,
                    profile_image_url = stringValue(user, "profile_image_url") ?: stringValue(user, "avatar_large"),
                    round_profile_image_url = stringValue(user, "round_profile_image_url")
                ),
                message = WeiboContactMessage(
                    text = stringValue(item, "text")?.let(::cleanWeiboHtmlText),
                    created_at = stringValue(item, "created_at"),
                    sender_screen_name = null
                )
            )
        }
        return WeiboContactsResponse(contacts = contacts)
    }

    private fun extractMobileGroupId(scheme: String?): Long? {
        if (scheme.isNullOrBlank()) return null
        return Regex("[?&]gid=([^&]+)")
            .find(scheme)
            ?.groupValues
            ?.getOrNull(1)
            ?.toLongOrNull()
    }

    suspend fun queryMessages(
        groupId: String,
        cookie: String,
        maxMid: Long = 0L,
        count: Int = 50,
        onCookieUpdated: ((String) -> Unit)? = null,
        source: String = "209678993"
    ): WeiboGroupMessagesResponse? = withContext(Dispatchers.IO) {
        cookieJar.seedCookies(cookie)
        hydrateWebGroupChatSession()

        val urlBuilder = "https://api.weibo.com/webim/groupchat/query_messages.json".toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
        urlBuilder.addQueryParameter("convert_emoji", "1")
        urlBuilder.addQueryParameter("query_sender", "1")
        urlBuilder.addQueryParameter("count", count.toString())
        urlBuilder.addQueryParameter("id", groupId)
        urlBuilder.addQueryParameter("max_mid", maxMid.coerceAtLeast(0L).toString())
        urlBuilder.addQueryParameter("source", source)

        val finalUrl = urlBuilder.build()
        val request = Request.Builder()
            .url(finalUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_DESKTOP_USER_AGENT)
            .addHeader("Referer", "https://api.weibo.com/chat")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .build()

        android.util.Log.d("WeiboApiClient", "queryMessages request url: $finalUrl")
        val sentCookies = cookieJar.loadForRequest(finalUrl)
        android.util.Log.d("WeiboApiClient", "queryMessages request cookies sent: ${sentCookies.joinToString("; ") { "${it.name}=${it.value}" }}")

        try {
            client.newCall(request).execute().use { response ->
                android.util.Log.d("WeiboApiClient", "queryMessages response code: ${response.code}")
                android.util.Log.d("WeiboApiClient", "queryMessages response headers: ${response.headers.toMultimap()}")
                
                val bodyString = response.body?.string()
                checkResponse(response, bodyString)
                
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) {
                    android.util.Log.e("WeiboApiClient", "queryMessages response not successful. Code: ${response.code}")
                    return@withContext null
                }
                if (bodyString == null) return@withContext null
                android.util.Log.d("WeiboApiClient", "queryMessages response body: $bodyString")
                return@withContext parseMobileGroupMessages(bodyString)
            }
        } catch (e: Exception) {
            if (e is WeiboRiskControlException) {
                throw e
            }
            android.util.Log.e("WeiboApiClient", "queryMessages Exception", e)
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun hydrateWebGroupChatSession() {
        val now = System.currentTimeMillis()
        if (now - lastGroupChatHydrationTime < hydrationCacheMs) return
        val pageUrl = "https://api.weibo.com/chat"
        val request = Request.Builder()
            .url(pageUrl)
            .get()
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("User-Agent", WEIBO_DESKTOP_USER_AGENT)
            .addHeader("Referer", "https://weibo.com/")
            .build()

        try {
            noRedirectClient.newCall(request).execute().use { response ->
                response.body?.close()
                val code = response.code
                if (code == 302 || code == 307 || code == 308) {
                    val location = response.header("Location").orEmpty()
                    if (location.contains("passport") || location.contains("login") || location.contains("signin")) {
                        android.util.Log.w("WeiboApiClient", "Web group chat session request redirected to login page: $location")
                        return
                    }
                } else if (!response.isSuccessful) {
                    android.util.Log.w("WeiboApiClient", "Web group chat session request failed: code=$code, url=$pageUrl")
                    return
                }
                lastGroupChatHydrationTime = System.currentTimeMillis()
            }
        } catch (e: Exception) {
            android.util.Log.w("WeiboApiClient", "Web group chat session request failed", e)
        }
    }

    private fun parseMobileGroupMessages(bodyString: String): WeiboGroupMessagesResponse {
        try {
            val desktopResponse = gson.fromJson(bodyString, WeiboGroupMessagesResponse::class.java)
            if (desktopResponse != null && (desktopResponse.result || desktopResponse.messages != null)) {
                val cleanedMessages = desktopResponse.messages?.map { msg ->
                    msg.copy(content = cleanWeiboHtmlText(msg.content))
                }
                return desktopResponse.copy(messages = cleanedMessages)
            }
        } catch (e: Exception) {
            android.util.Log.w("WeiboApiClient", "Failed to parse as desktop response, falling back to mobile", e)
        }

        val response = gson.fromJson(bodyString, WeiboMobileGroupMessagesResponse::class.java)
        val data = response.data
        val users = data?.users.orEmpty()
        val messages = data?.msgs.orEmpty().mapNotNull { msg ->
            val id = msg.id ?: return@mapNotNull null
            val senderId = msg.sender_id ?: 0L
            val user = users[senderId.toString()]
            val screenName = msg.sender_screen_name
                ?: user?.screen_name
                ?: "新浪用户"
            val createdAt = msg.created_at ?: 0L
            WeiboMessage(
                id = id,
                content = cleanWeiboHtmlText(msg.text.orEmpty()),
                from_uid = senderId,
                time = if (createdAt > 10_000_000_000L) createdAt / 1000 else createdAt,
                from_user = WeiboUser(
                    id = senderId,
                    screen_name = screenName,
                    profile_image_url = user?.profile_image_url ?: user?.avatar_large
                ),
                media_type = msg.media_type,
                fids = msg.fids,
                url_objects = msg.url_objects,
                annotations = msg.annotations
            )
        }

        return WeiboGroupMessagesResponse(
            result = response.ok == 1,
            last_read_mid = data?.last_read_mid,
            messages = messages
        )
    }

    private fun hydrateMobileGroupChatSession(groupId: String) {
        val pageUrl = "https://m.weibo.cn/message/chat?gid=$groupId&name=msgbox"
        val request = Request.Builder()
            .url(pageUrl)
            .get()
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/")
            .build()

        try {
            noRedirectClient.newCall(request).execute().use { response ->
                response.body?.close()
                val code = response.code
                if (code == 302 || code == 307 || code == 308) {
                    val location = response.header("Location").orEmpty()
                    if (location.contains("passport") || location.contains("login") || location.contains("signin")) {
                        android.util.Log.w("WeiboApiClient", "Group chat page session request redirected to login page: $location")
                    }
                } else if (!response.isSuccessful) {
                    android.util.Log.w("WeiboApiClient", "Group chat page session request failed: code=$code, url=$pageUrl")
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("WeiboApiClient", "Group chat page session request failed", e)
        }
    }

    suspend fun sendMessage(
        groupId: String,
        content: String,
        cookie: String,
        onCookieUpdated: ((String) -> Unit)? = null,
        source: String = "209678993"
    ): WeiboSendMessageResponse? = withContext(Dispatchers.IO) {
        cookieJar.seedCookies(cookie)
        val formBody = FormBody.Builder()
            .add("setTimeout", "50")
            .add("content", content)
            .add("id", groupId)
            .add("media_type", "0")
            .add("annotations", """{"webchat":1,"clientid":"${System.currentTimeMillis()}"}""")
            .add("is_encoded", "0")
            .add("source", source)
            .build()

        val request = Request.Builder()
            .url("https://api.weibo.com/webim/groupchat/send_message.json")
            .post(formBody)
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .addHeader("Referer", "https://weibo.com/")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val bodyString = response.body?.string()
                checkResponse(response, bodyString)

                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) return@withContext null
                if (bodyString == null) return@withContext null
                return@withContext gson.fromJson(bodyString, WeiboSendMessageResponse::class.java)
            }
        } catch (e: Exception) {
            if (e is WeiboRiskControlException) {
                throw e
            }
            e.printStackTrace()
            return@withContext null
        }
    }

    suspend fun fetchFriendsTimeline(
        cookie: String,
        listId: String = DEFAULT_WEIBO_TIMELINE_LIST_ID,
        maxId: Long? = null,
        sinceId: Long? = null,
        onCookieUpdated: ((String) -> Unit)? = null
    ): WeiboTimelineResponse? = withContext(Dispatchers.IO) {
        var attempts = 0
        var lastResult: WeiboTimelineResponse? = null
        while (attempts < 3) {
            attempts++
            try {
                cookieJar.seedCookies(cookie)
                if (!hydrateTimelineMobileSession()) {
                    return@withContext WeiboTimelineResponse(
                        statuses = emptyList(),
                        since_id = null,
                        max_id = null,
                        total_number = null,
                        ok = -100,
                        msg = "微博登录态已失效，请重新登录后再试"
                    )
                }

                val urlBuilder = "https://m.weibo.cn/feed/friends".toHttpUrlOrNull()?.newBuilder()
                if (urlBuilder != null) {
                    if (maxId != null && maxId > 0L) {
                        urlBuilder.addQueryParameter("max_id", maxId.toString())
                    }
                    if (sinceId != null && sinceId > 0L) {
                        urlBuilder.addQueryParameter("since_id", sinceId.toString())
                    }

                    val targetUrl = urlBuilder.build()
                    val cookies = cookieJar.loadForRequest(targetUrl)
                    val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value

                    val requestBuilder = Request.Builder()
                        .url(targetUrl)
                        .get()
                        .addHeader("Accept", "application/json, text/plain, */*")
                        .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
                        .addHeader("Referer", "https://m.weibo.cn/")
                        .addHeader("X-Requested-With", "XMLHttpRequest")
                        .addHeader("MWeibo-Pwa", "1")

                    if (!xsrfToken.isNullOrBlank()) {
                        requestBuilder.addHeader("x-xsrf-token", xsrfToken)
                    }
                    val request = requestBuilder.build()

                    client.newCall(request).execute().use { response ->
                        val bodyString = response.body?.string()
                        checkResponse(response, bodyString)
                        
                        if (response.isSuccessful) {
                            if (bodyString != null) {
                                val timelineResponse = parseMobileFriendsTimelineResponse(bodyString)
                                if (timelineResponse.ok != -100 && timelineResponse.url?.contains("login.php") != true && !timelineResponse.statuses.isNullOrEmpty()) {
                                    val newCookie = cookieJar.getFlatCookieString()
                                    if (newCookie != cookie && onCookieUpdated != null) {
                                        onCookieUpdated(newCookie)
                                    }
                                    return@withContext timelineResponse
                                }
                                lastResult = timelineResponse
                                android.util.Log.w("WeiboApiClient", "Timeline response not usable: ok=${timelineResponse.ok}, statuses size=${timelineResponse.statuses?.size}")
                            }
                        } else {
                            android.util.Log.w("WeiboApiClient", "Timeline response error code: ${response.code}")
                        }
                    }
                }
            } catch (e: Exception) {
                if (e is WeiboRiskControlException) {
                    throw e
                }
                android.util.Log.e("WeiboApiClient", "fetchFriendsTimeline attempt $attempts failed", e)
            }
            if (attempts < 3) {
                kotlinx.coroutines.delay(800L * attempts)
            }
        }
        return@withContext lastResult
    }

    private fun parseMobileFriendsTimelineResponse(bodyString: String): WeiboTimelineResponse {
        val mobileResponse = gson.fromJson(bodyString, WeiboMobileFriendsTimelineResponse::class.java)
        val data = mobileResponse.data
        return WeiboTimelineResponse(
            statuses = data?.statuses.orEmpty(),
            since_id = data?.since_id,
            max_id = data?.max_id ?: data?.next_cursor,
            total_number = data?.total_number,
            ok = mobileResponse.ok,
            url = null
        )
    }

    suspend fun fetchStatusLongText(
        cookie: String,
        statusId: String,
        onCookieUpdated: ((String) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext null

        cookieJar.seedCookies(cookie)
        hydrateTimelineMobileSession()

        val targetUrl = "https://m.weibo.cn/statuses/extend".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("id", statusId)
            ?.build()
            ?: return@withContext null

        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("MWeibo-Pwa", "1")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("WeiboApiClient", "Status extend request failed: code=${response.code}, url=$targetUrl")
                    return@withContext null
                }

                val bodyString = response.body?.string() ?: return@withContext null
                val extendResponse = gson.fromJson(bodyString, WeiboStatusExtendResponse::class.java)
                if (extendResponse.ok != 1) {
                    android.util.Log.w("WeiboApiClient", "Status extend response not ok: ${bodyString.take(500)}")
                    return@withContext null
                }

                val newCookie = cookieJar.getFlatCookieString()
                if (newCookie != cookie && onCookieUpdated != null) {
                    onCookieUpdated(newCookie)
                }

                return@withContext extendResponse.data?.longTextContent
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error fetching status long text", e)
            return@withContext null
        }
    }

    suspend fun fetchStatusShow(
        cookie: String,
        statusId: String,
        onCookieUpdated: ((String) -> Unit)? = null
    ): WeiboTimelineStatus? = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext null

        cookieJar.seedCookies(cookie)
        hydrateTimelineMobileSession()

        val targetUrl = "https://m.weibo.cn/statuses/show".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("id", statusId)
            ?.build()
            ?: return@withContext null

        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("MWeibo-Pwa", "1")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    android.util.Log.w("WeiboApiClient", "Status show request failed: code=${response.code}, url=$targetUrl")
                    return@withContext null
                }

                val bodyString = response.body?.string() ?: return@withContext null
                val root = gson.fromJson(bodyString, com.google.gson.JsonObject::class.java)
                if (root.get("ok")?.asInt != 1) {
                    android.util.Log.w("WeiboApiClient", "Status show response not ok: ${bodyString.take(500)}")
                    return@withContext null
                }

                val dataObj = root.getAsJsonObject("data") ?: return@withContext null
                val status = gson.fromJson(dataObj, WeiboTimelineStatus::class.java)

                val newCookie = cookieJar.getFlatCookieString()
                if (newCookie != cookie && onCookieUpdated != null) {
                    onCookieUpdated(newCookie)
                }

                return@withContext status
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error fetching status show", e)
            return@withContext null
        }
    }

    private fun hydrateTimelineMobileSession(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastTimelineHydrationTime < hydrationCacheMs) return true
        val pageUrl = "https://m.weibo.cn/"
        val request = Request.Builder()
            .url(pageUrl)
            .get()
            .addHeader("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/")
            .build()

        try {
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() // Consume body
                val finalUrl = response.request.url.toString()
                if (finalUrl.contains("passport") || finalUrl.contains("login") || finalUrl.contains("signin")) {
                    android.util.Log.w("WeiboApiClient", "Timeline mobile page session redirected to login page: $finalUrl")
                    return false
                }
                if (!response.isSuccessful) {
                    android.util.Log.w("WeiboApiClient", "Timeline mobile page session failed: code=${response.code}, url=$finalUrl")
                    return false
                }
                lastTimelineHydrationTime = System.currentTimeMillis()
                return true
            }
        } catch (e: Exception) {
            android.util.Log.w("WeiboApiClient", "Timeline mobile page session request failed", e)
            return false
        }
    }

    suspend fun fetchWeiboComments(
        cookie: String,
        statusId: String,
        maxId: Long? = null,
        maxIdType: Int = 0,
        onCookieUpdated: ((String) -> Unit)? = null
    ): WeiboCommentsResponse? = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext null
        cookieJar.seedCookies(cookie)
        hydrateTimelineMobileSession()

        val urlBuilder = "https://m.weibo.cn/comments/hotflow".toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
        urlBuilder.addQueryParameter("id", statusId)
        urlBuilder.addQueryParameter("mid", statusId)
        urlBuilder.addQueryParameter("max_id_type", maxIdType.toString())
        if (maxId != null && maxId > 0L) {
            urlBuilder.addQueryParameter("max_id", maxId.toString())
        }

        val targetUrl = urlBuilder.build()
        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("MWeibo-Pwa", "1")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(500)
                    android.util.Log.w("WeiboApiClient", "Comments request failed: code=${response.code}, url=$targetUrl, body=$errorBody")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val commentsResponse = gson.fromJson(bodyString, WeiboCommentsResponse::class.java)
                if (commentsResponse.ok != 1) {
                    android.util.Log.w("WeiboApiClient", "Comments response not ok: ${bodyString.take(500)}")
                }
                return@withContext commentsResponse
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error fetching weibo comments", e)
            return@withContext null
        }
    }

    suspend fun fetchWeiboCommentChildren(
        cookie: String,
        commentId: Long,
        maxId: Long = 0L,
        maxIdType: Int = 0,
        onCookieUpdated: ((String) -> Unit)? = null
    ): WeiboCommentChildrenResponse? = withContext(Dispatchers.IO) {
        if (commentId <= 0L) return@withContext null
        cookieJar.seedCookies(cookie)
        hydrateTimelineMobileSession()

        val targetUrl = "https://m.weibo.cn/comments/hotFlowChild".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("cid", commentId.toString())
            ?.addQueryParameter("max_id", maxId.toString())
            ?.addQueryParameter("max_id_type", maxIdType.toString())
            ?.build()
            ?: return@withContext null

        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value
        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("MWeibo-Pwa", "1")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string()?.take(500)
                    android.util.Log.w("WeiboApiClient", "Comment children request failed: code=${response.code}, url=$targetUrl, body=$errorBody")
                    return@withContext null
                }
                val bodyString = response.body?.string() ?: return@withContext null
                val childResponse = gson.fromJson(bodyString, WeiboCommentChildrenResponse::class.java)
                if (childResponse.ok != 1) {
                    android.util.Log.w("WeiboApiClient", "Comment children response not ok: ${bodyString.take(500)}")
                }
                return@withContext childResponse
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error fetching weibo comment children", e)
            return@withContext null
        }
    }

    suspend fun fetchWeiboReposts(
        cookie: String,
        statusId: String,
        page: Int = 1,
        onCookieUpdated: ((String) -> Unit)? = null
    ): WeiboRepostsResponse? = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext null
        cookieJar.seedCookies(cookie)

        val urlBuilder = "https://m.weibo.cn/api/statuses/repostTimeline".toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
        urlBuilder.addQueryParameter("id", statusId)
        urlBuilder.addQueryParameter("page", page.toString())

        val targetUrl = urlBuilder.build()
        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("MWeibo-Pwa", "1")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(bodyString, WeiboRepostsResponse::class.java)
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error fetching weibo reposts", e)
            return@withContext null
        }
    }

    suspend fun fetchWeiboAttitudes(
        cookie: String,
        statusId: String,
        page: Int = 1,
        onCookieUpdated: ((String) -> Unit)? = null
    ): WeiboAttitudesResponse? = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext null
        cookieJar.seedCookies(cookie)

        val urlBuilder = "https://m.weibo.cn/api/attitudes/show".toHttpUrlOrNull()?.newBuilder() ?: return@withContext null
        urlBuilder.addQueryParameter("id", statusId)
        urlBuilder.addQueryParameter("page", page.toString())

        val targetUrl = urlBuilder.build()
        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .get()
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")
            .addHeader("MWeibo-Pwa", "1")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) return@withContext null
                val bodyString = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(bodyString, WeiboAttitudesResponse::class.java)
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error fetching weibo attitudes", e)
            return@withContext null
        }
    }

    suspend fun likeStatus(
        cookie: String,
        statusId: String,
        onCookieUpdated: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext false
        cookieJar.seedCookies(cookie)

        val targetUrl = "https://m.weibo.cn/api/attitudes/create".toHttpUrlOrNull() ?: return@withContext false
        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value

        val formBody = FormBody.Builder()
            .add("id", statusId)
            .add("attitude", "heart")
            .build()

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(formBody)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) return@withContext false
                val bodyString = response.body?.string() ?: return@withContext false
                val commonResponse = gson.fromJson(bodyString, WeiboCommonResponse::class.java)
                return@withContext commonResponse.ok == 1
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error liking status", e)
            return@withContext false
        }
    }

    suspend fun unlikeStatus(
        cookie: String,
        statusId: String,
        onCookieUpdated: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        if (statusId.isBlank()) return@withContext false
        cookieJar.seedCookies(cookie)

        val targetUrl = "https://m.weibo.cn/api/attitudes/destroy".toHttpUrlOrNull() ?: return@withContext false
        val cookies = cookieJar.loadForRequest(targetUrl)
        val xsrfToken = cookies.find { it.name == "XSRF-TOKEN" }?.value

        val formBody = FormBody.Builder()
            .add("id", statusId)
            .build()

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(formBody)
            .addHeader("Accept", "application/json, text/plain, */*")
            .addHeader("User-Agent", WEIBO_MOBILE_USER_AGENT)
            .addHeader("Referer", "https://m.weibo.cn/status/$statusId")
            .addHeader("X-Requested-With", "XMLHttpRequest")

        if (!xsrfToken.isNullOrBlank()) {
            requestBuilder.addHeader("x-xsrf-token", xsrfToken)
        }

        try {
            client.newCall(requestBuilder.build()).execute().use { response ->
                if (response.isSuccessful) {
                    val newCookie = cookieJar.getFlatCookieString()
                    if (newCookie != cookie && onCookieUpdated != null) {
                        onCookieUpdated(newCookie)
                    }
                }
                if (!response.isSuccessful) return@withContext false
                val bodyString = response.body?.string() ?: return@withContext false
                val commonResponse = gson.fromJson(bodyString, WeiboCommonResponse::class.java)
                return@withContext commonResponse.ok == 1
            }
        } catch (e: Exception) {
            android.util.Log.e("WeiboApiClient", "Error unliking status", e)
            return@withContext false
        }
    }
}


