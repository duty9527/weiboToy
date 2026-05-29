package com.example.weibochat.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.gson.Gson
import coil.request.ImageRequest
import coil.request.CachePolicy

interface DataRepository {
    val allMessages: Flow<List<Message>>
    suspend fun sendMessage(content: String, senderName: String = "我", contextId: Long? = 1): Long
    suspend fun getMessagesByContextId(contextId: Long): List<Message>
    suspend fun getUserContextMessages(senderName: String, timestamp: String): List<Message>
    fun saveCredentials(cookie: String, groupId: String)
    fun getCredentials(): Pair<String, String>
    fun saveMobileCookie(cookie: String)
    fun getMobileCookie(): String
    fun getAllCookies(): String
    suspend fun fetchOlderMessages(maxMid: Long): Boolean
    suspend fun loadOlderLocalMessages(groupId: String, beforeTimestamp: String, limit: Int = 500): List<Message>
    suspend fun fetchContacts(): List<WeiboContact>
    fun setActiveGroupId(groupId: String?)
    fun getActiveGroupId(): String?
    fun saveBlockedKeywords(keywords: String)
    fun getBlockedKeywordsString(): String
    fun getBlockedKeywordsList(): List<String>
    fun getBlockedKeywordRules(): List<BlockedKeywordRule>
    fun saveBlockedKeywordRules(rules: List<BlockedKeywordRule>)
    fun saveBlockedUsers(users: String)
    fun getBlockedUsersString(): String
    fun getBlockedUsersList(): List<String>
    fun isMessageBlocked(sender: String, content: String): Boolean
    fun saveReadPosition(groupId: String, index: Int, offset: Int)
    fun getReadPosition(groupId: String): Pair<Int, Int>?
    suspend fun getMessageContext(message: Message): List<Message>
    suspend fun syncMessagesUntil(targetTimeMillis: Long): Int
    suspend fun fetchFriendsTimeline(
        listId: String = DEFAULT_WEIBO_TIMELINE_LIST_ID,
        maxId: Long? = null,
        sinceId: Long? = null
    ): WeiboTimelineResponse?
    suspend fun fetchWeiboStatusLongText(statusId: String): String?
    suspend fun fetchWeiboStatus(statusId: String): WeiboTimelineStatus?
    suspend fun fetchWeiboComments(statusId: String, maxId: Long?, maxIdType: Int): WeiboCommentsResponse?
    suspend fun fetchWeiboCommentChildren(commentId: Long, maxId: Long, maxIdType: Int): WeiboCommentChildrenResponse?
    suspend fun fetchWeiboReposts(statusId: String, page: Int): WeiboRepostsResponse?
    suspend fun fetchWeiboAttitudes(statusId: String, page: Int): WeiboAttitudesResponse?
    suspend fun likeWeiboStatus(statusId: String): Boolean
    suspend fun unlikeWeiboStatus(statusId: String): Boolean
    fun markWeiboStatusAsRead(statusId: String)
    fun getReadWeiboStatusIds(): Set<String>
    fun getLocalTimeline(): Flow<List<WeiboTimelineStatus>>
    suspend fun syncNewTimeline(): Result<Unit>
    suspend fun syncGap(gapId: Long): Result<Unit>
    suspend fun loadMoreTimeline(): Result<Unit>
    fun saveLastViewedWeibo(statusId: String, index: Int, offset: Int)
    fun getLastViewedWeibo(): Triple<String, Int, Int>?
}

class DefaultDataRepository(private val context: Context) : DataRepository {
    companion object {
        private const val MESSAGE_LOAD_LIMIT = 5000
    }

    private val database = WeiboDatabase.create(context)
    private val messageDao = database.messageDao()
    private val weiboDao = database.weiboDao()
    private val apiClient = WeiboApiClient(context)
    private val sharedPrefs: SharedPreferences = migrateToEncryptedPrefs(
        context, "weibo_prefs", "weibo_encrypted_prefs"
    )
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _databaseUpdates = MutableSharedFlow<Unit>(replay = 1)
    private val _weiboDatabaseUpdates = MutableSharedFlow<Unit>(replay = 1)
    private val activeGroupIdFlow = MutableStateFlow<String?>(null)
    private var isPolling = false
    
    init {
        _databaseUpdates.tryEmit(Unit)
        startPeriodicSync()
    }

    private fun mergeCookieStrings(firstCookieString: String, secondCookieString: String): String {
        val cookies = linkedMapOf<String, String>()
        listOf(firstCookieString, secondCookieString).forEach { cookieString ->
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

    private fun getMobileApiCookie(): String {
        return getCredentials().first
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override val allMessages: Flow<List<Message>> =
        activeGroupIdFlow.flatMapLatest { groupId ->
            if (groupId != null) {
                messageDao.getMessagesByGroupIdFlow(groupId, MESSAGE_LOAD_LIMIT)
                    .map { entities -> filterBlocked(entities.asReversed().map { it.toMessage() }) }
            } else {
                flowOf(emptyList())
            }
        }.flowOn(Dispatchers.IO)

    private suspend fun findParentMessageId(
        groupId: String,
        content: String,
        replyMessageId: Long
    ): Long? {
        val parsed = parseMessageContent(content)
        val lastQuote = parsed.quoteLayers.lastOrNull() ?: return null

        val targetSender = lastQuote.senderName
        val targetText = lastQuote.cleanText

        val cleanTargetText = targetText.trim()
        val cleanUser = targetSender?.removePrefix("@")?.trim()

        var resolvedId: Long? = null

        // 1. 如果有指定的发送者，先查询该发送者的消息
        if (cleanUser != null) {
            val candidates = messageDao.getMessagesBySenderBefore(groupId, cleanUser, replyMessageId, 100)
            for (msg in candidates) {
                val parsedMsg = parseMessageContent(msg.content)
                val msgImmediateText = parsedMsg.immediateText.trim()

                if (msg.imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                    resolvedId = msg.id; break
                }
                if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                    (msg.linkUrl != null || msg.linkTitle != null || msg.content.contains("weibo.com") || msg.content.contains("t.cn"))) {
                    resolvedId = msg.id; break
                }
                if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText == cleanTargetText) {
                    resolvedId = msg.id; break
                }
                if (cleanTargetText != "微博" && cleanTargetText != "图片" && cleanTargetText.length >= 2 &&
                    (msgImmediateText.contains(cleanTargetText) || cleanTargetText.contains(msgImmediateText))) {
                    resolvedId = msg.id; break
                }
                if (msg.linkTitle != null && cleanTargetText.contains(msg.linkTitle)) {
                    resolvedId = msg.id; break
                }
            }
        }

        if (resolvedId != null) return resolvedId

        // 2. 没有指定发送者，或指定发送者没有匹配成功
        val allCandidates = messageDao.getMessagesBefore(groupId, replyMessageId, 200)
        for (msg in allCandidates) {
            val parsedMsg = parseMessageContent(msg.content)
            val msgImmediateText = parsedMsg.immediateText.trim()

            if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText.isNotBlank() && msgImmediateText == cleanTargetText) {
                if (cleanUser == null || msg.senderName.trim().equals(cleanUser, ignoreCase = true)) {
                    resolvedId = msg.id; break
                }
            }

            if (cleanUser == null) {
                if (msg.imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                    resolvedId = msg.id; break
                }
                if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                    (msg.linkUrl != null || msg.linkTitle != null || msg.content.contains("weibo.com") || msg.content.contains("t.cn"))) {
                    resolvedId = msg.id; break
                }
            }
        }

        if (resolvedId != null) return resolvedId

        // 3. 兜底：直接找最近一条该发送者的消息
        if (cleanUser != null) {
            val fallback = messageDao.getMessagesBySenderBefore(groupId, cleanUser, replyMessageId, 1)
            resolvedId = fallback.firstOrNull()?.id
        }

        return resolvedId
    }

    private suspend fun buildMessageEntity(wm: WeiboMessage, groupId: String): MessageEntity {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeStr = sdf.format(Date(wm.time * 1000))

        var imageUrl: String? = null
        if (wm.media_type == 1 && !wm.fids.isNullOrEmpty()) {
            val fid = wm.fids.first()
            imageUrl = "https://upload.api.weibo.com/2/mss/msget_thumbnail?fid=$fid&high=480&width=480&size=480,480&source=209678993"
        }

        var linkTitle: String? = null
        var linkDesc: String? = null
        var linkImg: String? = null
        var linkUrl: String? = null
        if (!wm.url_objects.isNullOrEmpty()) {
            val urlObj = wm.url_objects.first()
            val status = urlObj.status
            val info = urlObj.info
            val title = status?.user?.screen_name?.let { "微博：@$it" } ?: info?.title
            val desc = status?.text ?: info?.description
            val img = status?.thumbnail_pic ?: status?.original_pic
            val url = info?.url_long ?: urlObj.url_ori ?: wm.content
            if (title != null || desc != null) {
                linkTitle = title
                linkDesc = desc
                linkImg = img
                linkUrl = url
            }
        }

        var fileUrl: String? = null
        var fileName: String? = null
        if (wm.media_type == 5 && !wm.fids.isNullOrEmpty()) {
            val fid = wm.fids.first()
            fileUrl = "https://upload.api.weibo.com/2/mss/msget?fid=$fid&source=209678993"
            fileName = wm.content
        }

        if (wm.media_type == 10) {
            if (!wm.fids.isNullOrEmpty()) {
                val fid = wm.fids.first()
                fileUrl = "https://upload.api.weibo.com/2/mss/msget?fid=$fid&source=209678993"
                fileName = "video.mp4"
            }
            val coverFid = wm.annotations?.video_pic_fid
            if (coverFid != null) {
                imageUrl = "https://upload.api.weibo.com/2/mss/msget_thumbnail?fid=$coverFid&high=480&width=480&size=480,480&source=209678993"
            }
        }

        val parentId = findParentMessageId(groupId, wm.content, wm.id)

        return MessageEntity(
            id = wm.id,
            timestamp = timeStr,
            senderName = wm.from_user?.screen_name ?: "新浪用户",
            groupSuffix = "群聊",
            content = wm.content,
            contextId = 1L,
            imageUrl = imageUrl,
            linkTitle = linkTitle,
            linkDesc = linkDesc,
            linkImg = linkImg,
            linkUrl = linkUrl,
            fileUrl = fileUrl,
            fileName = fileName,
            groupId = groupId,
            parentMsgId = parentId
        )
    }

    private suspend fun shouldUpdateMessage(wm: WeiboMessage): Boolean {
        val existing = messageDao.getMediaFields(wm.id) ?: return true
        if (wm.media_type == 1 && !wm.fids.isNullOrEmpty() && existing.imageUrl.isNullOrEmpty()) return true
        if (!wm.url_objects.isNullOrEmpty() && existing.linkTitle.isNullOrEmpty()) return true
        if (wm.media_type == 5 && !wm.fids.isNullOrEmpty() && existing.fileUrl.isNullOrEmpty()) return true
        if (wm.media_type == 10 && existing.fileUrl.isNullOrEmpty()) return true
        return false
    }

    private suspend fun upsertApiMessages(
        groupId: String,
        messages: List<WeiboMessage>,
        updateExisting: Boolean = true
    ): Int {
        val toInsert = mutableListOf<MessageEntity>()
        for (wm in messages) {
            val exists = messageDao.existsById(wm.id)
            val needsUpdate = updateExisting && exists && shouldUpdateMessage(wm)
            if (!exists || needsUpdate) {
                toInsert.add(buildMessageEntity(wm, groupId))
            }
        }
        if (toInsert.isNotEmpty()) {
            messageDao.insertOrReplaceAll(toInsert)
        }
        return toInsert.size
    }

    private fun startPeriodicSync() {
        if (isPolling) return
        isPolling = true
        repositoryScope.launch {
            while (true) {
                val (cookie, credentialGroupId) = getCredentials()
                val targetGroupId = activeGroupIdFlow.value ?: credentialGroupId
                if (cookie.isNotBlank() && targetGroupId.isNotBlank()) {
                    val response = apiClient.queryMessages(
                        groupId = targetGroupId,
                        cookie = cookie,
                        maxMid = 0L,
                        onCookieUpdated = { newCookie ->
                            saveCredentials(newCookie, targetGroupId)
                        }
                    )
                    if (response != null && response.result && response.messages != null) {
                        val changed = upsertApiMessages(targetGroupId, response.messages)
                        if (changed > 0) {
                            _databaseUpdates.emit(Unit)
                        }
                    }
                }
                delay(60000)
            }
        }
    }

    private fun triggerSync() {
        repositoryScope.launch {
            val (cookie, credentialGroupId) = getCredentials()
            val targetGroupId = activeGroupIdFlow.value ?: credentialGroupId
            if (cookie.isNotBlank() && targetGroupId.isNotBlank()) {
                val response = apiClient.queryMessages(
                    groupId = targetGroupId,
                    cookie = cookie,
                    maxMid = 0L,
                    onCookieUpdated = { newCookie ->
                        saveCredentials(newCookie, targetGroupId)
                    }
                )
                if (response != null && response.result && response.messages != null) {
                    val changed = upsertApiMessages(targetGroupId, response.messages)
                    if (changed > 0) {
                        _databaseUpdates.emit(Unit)
                    }
                }
            }
        }
    }

    private suspend fun queryMessagesByGroupId(groupId: String): List<Message> {
        try {
            val entities = messageDao.getMessagesByGroupIdDesc(groupId, MESSAGE_LOAD_LIMIT)
            return entities.asReversed().map { it.toMessage() }.let { filterBlocked(it) }
        } catch (e: Exception) {
            android.util.Log.e("WeiboChat", "Error in queryMessagesByGroupId", e)
            throw e
        }
    }

    override suspend fun sendMessage(content: String, senderName: String, contextId: Long?): Long = withContext(Dispatchers.IO) {
        val (cookie, credentialGroupId) = getCredentials()
        val targetGroupId = activeGroupIdFlow.value ?: credentialGroupId
        if (cookie.isNotBlank() && targetGroupId.isNotBlank()) {
            val response = try {
                apiClient.sendMessage(
                    groupId = targetGroupId,
                    content = content,
                    cookie = cookie,
                    onCookieUpdated = { newCookie ->
                        saveCredentials(newCookie, targetGroupId)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("WeiboChat", "Failed to send message via API", e)
                null
            }
            if (response != null && response.result) {
                val mid = response.id ?: response.mid ?: System.currentTimeMillis()
                val responseTime = response.time ?: response.ts ?: (System.currentTimeMillis() / 1000)
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                val parentId = findParentMessageId(targetGroupId, content, mid)

                val entity = MessageEntity(
                    id = mid,
                    timestamp = sdf.format(Date(responseTime * 1000)),
                    senderName = response.from_user?.screen_name ?: "我",
                    groupSuffix = "群聊",
                    content = content,
                    contextId = contextId,
                    groupId = targetGroupId,
                    parentMsgId = parentId
                )
                messageDao.insertOrReplace(entity)
                _databaseUpdates.emit(Unit)
                return@withContext mid
            }
        }

        // Fallback: Local insert for offline/simulation mode
        val mid = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val fallbackGroupId = targetGroupId.ifBlank { "4761715839862414" }
        val parentId = findParentMessageId(fallbackGroupId, content, mid)

        val entity = MessageEntity(
            id = mid,
            timestamp = sdf.format(Date()),
            senderName = senderName,
            groupSuffix = "群聊",
            content = content,
            contextId = contextId,
            groupId = fallbackGroupId,
            parentMsgId = parentId
        )
        messageDao.insertOrReplace(entity)
        _databaseUpdates.emit(Unit)
        return@withContext mid
    }

    override suspend fun getMessagesByContextId(contextId: Long): List<Message> = withContext(Dispatchers.IO) {
        val entities = messageDao.getMessagesByContextId(contextId)
        filterBlocked(entities.map { it.toMessage() })
    }

    override fun saveCredentials(cookie: String, groupId: String) {
        if (cookie.isBlank() && groupId.isBlank()) {
            apiClient.clearSession()
        }
        sharedPrefs.edit()
            .putString("cookie", cookie)
            .putString("group_id", groupId)
            .apply()
        _databaseUpdates.tryEmit(Unit)
    }

    override fun getCredentials(): Pair<String, String> {
        val cookie = sharedPrefs.getString("cookie", "") ?: ""
        val groupId = sharedPrefs.getString("group_id", "") ?: ""
        return Pair(cookie, groupId)
    }

    override fun saveMobileCookie(cookie: String) {
        sharedPrefs.edit()
            .putString("cookie", cookie)
            .apply()
        _databaseUpdates.tryEmit(Unit)
    }

    override fun getMobileCookie(): String {
        return sharedPrefs.getString("cookie", "") ?: ""
    }

    override fun getAllCookies(): String {
        return apiClient.getStoredCookieString(getCredentials().first)
    }

    override suspend fun fetchOlderMessages(maxMid: Long): Boolean = withContext(Dispatchers.IO) {
        val cookie = getCredentials().first
        val targetGroupId = activeGroupIdFlow.value ?: getCredentials().second
        if (cookie.isNotBlank() && targetGroupId.isNotBlank()) {
            val response = apiClient.queryMessages(
                groupId = targetGroupId,
                cookie = cookie,
                maxMid = (maxMid - 1).coerceAtLeast(0L),
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, targetGroupId)
                }
            )
            if (response != null && response.result && response.messages != null && response.messages.isNotEmpty()) {
                val changed = upsertApiMessages(targetGroupId, response.messages)
                if (changed > 0) {
                    _databaseUpdates.emit(Unit)
                }
                return@withContext changed > 0
            }
        }
        return@withContext false
    }

    override suspend fun loadOlderLocalMessages(groupId: String, beforeTimestamp: String, limit: Int): List<Message> = withContext(Dispatchers.IO) {
        try {
            val entities = messageDao.getOlderMessages(groupId, beforeTimestamp, limit)
            return@withContext filterBlocked(entities.asReversed().map { it.toMessage() })
        } catch (e: Exception) {
            android.util.Log.e("WeiboChat", "Error in loadOlderLocalMessages", e)
            return@withContext emptyList()
        }
    }

    override suspend fun getUserContextMessages(senderName: String, timestamp: String): List<Message> = withContext(Dispatchers.IO) {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        try {
            val date = sdf.parse(timestamp) ?: Date()
            val cal = Calendar.getInstance().apply { time = date }
            cal.add(Calendar.HOUR_OF_DAY, -1)
            val startTime = sdf.format(cal.time)
            cal.add(Calendar.HOUR_OF_DAY, 2)
            val endTime = sdf.format(cal.time)

            val targetGroupId = activeGroupIdFlow.value ?: getCredentials().second
            val entities = messageDao.getUserContextMessages(senderName, targetGroupId, startTime, endTime)
            return@withContext filterBlocked(entities.map { it.toMessage() })
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext emptyList()
        }
    }

    override suspend fun fetchContacts(): List<WeiboContact> {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank()) {
            val response = apiClient.queryContacts(cookie)
            if (response != null && !response.contacts.isNullOrEmpty()) {
                return response.contacts
            }
        }
        return emptyList()
    }

    override suspend fun fetchFriendsTimeline(
        listId: String,
        maxId: Long?,
        sinceId: Long?
    ): WeiboTimelineResponse? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank()) {
            return apiClient.fetchFriendsTimeline(
                cookie = cookie,
                listId = listId,
                maxId = maxId,
                sinceId = sinceId,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboStatusLongText(statusId: String): String? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.fetchStatusLongText(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboStatus(statusId: String): WeiboTimelineStatus? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.fetchStatusShow(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboComments(statusId: String, maxId: Long?, maxIdType: Int): WeiboCommentsResponse? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.fetchWeiboComments(
                cookie = cookie,
                statusId = statusId,
                maxId = maxId,
                maxIdType = maxIdType,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboCommentChildren(commentId: Long, maxId: Long, maxIdType: Int): WeiboCommentChildrenResponse? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && commentId > 0L) {
            return apiClient.fetchWeiboCommentChildren(
                cookie = cookie,
                commentId = commentId,
                maxId = maxId,
                maxIdType = maxIdType,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboReposts(statusId: String, page: Int): WeiboRepostsResponse? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.fetchWeiboReposts(
                cookie = cookie,
                statusId = statusId,
                page = page,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboAttitudes(statusId: String, page: Int): WeiboAttitudesResponse? {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.fetchWeiboAttitudes(
                cookie = cookie,
                statusId = statusId,
                page = page,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return null
    }

    override suspend fun likeWeiboStatus(statusId: String): Boolean {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.likeStatus(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return false
    }

    override suspend fun unlikeWeiboStatus(statusId: String): Boolean {
        val cookie = getMobileApiCookie()
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            return apiClient.unlikeStatus(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveMobileCookie(newCookie)
                }
            )
        }
        return false
    }

    override fun setActiveGroupId(groupId: String?) {
        activeGroupIdFlow.value = groupId
        _databaseUpdates.tryEmit(Unit)
        if (groupId != null) {
            triggerSync()
        }
    }

    override fun getActiveGroupId(): String? {
        return activeGroupIdFlow.value
    }

    override fun saveBlockedKeywords(keywords: String) {
        val rules = keywords.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
            .map { BlockedKeywordRule(it, MatchMode.CONTAINS) }
        saveBlockedKeywordRules(rules)
    }

    override fun getBlockedKeywordsString(): String {
        return getBlockedKeywordRules().joinToString(",") { it.text }
    }

    override fun getBlockedKeywordsList(): List<String> {
        return getBlockedKeywordRules().map { it.text }
    }

    override fun getBlockedKeywordRules(): List<BlockedKeywordRule> {
        val gson = Gson()
        val json = sharedPrefs.getString("blocked_keywords_rules", null)
        if (json != null) {
            try {
                val type = object : com.google.gson.reflect.TypeToken<List<BlockedKeywordRule>>() {}.type
                val rules: List<BlockedKeywordRule> = gson.fromJson(json, type)
                if (rules.isNotEmpty()) return rules
            } catch (_: Exception) {}
        }
        // Migrate from old StringSet
        val oldSet = sharedPrefs.getStringSet("blocked_keywords_set", null)
        if (oldSet != null && oldSet.isNotEmpty()) {
            val rules = oldSet.map { BlockedKeywordRule(it, MatchMode.CONTAINS) }
            sharedPrefs.edit().putString("blocked_keywords_rules", gson.toJson(rules)).remove("blocked_keywords_set").apply()
            return rules
        }
        // Migrate from old comma-separated string
        val oldStr = sharedPrefs.getString("blocked_keywords", null)
        if (oldStr != null) {
            val rules = oldStr.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
                .map { BlockedKeywordRule(it, MatchMode.CONTAINS) }
            sharedPrefs.edit().putString("blocked_keywords_rules", gson.toJson(rules)).remove("blocked_keywords").apply()
            return rules
        }
        // Default
        return listOf("撤回了一条消息", "红包", "🧧", "系统消息", "群规")
            .map { BlockedKeywordRule(it, MatchMode.CONTAINS) }
    }

    override fun saveBlockedKeywordRules(rules: List<BlockedKeywordRule>) {
        val gson = Gson()
        sharedPrefs.edit().putString("blocked_keywords_rules", gson.toJson(rules)).apply()
        _databaseUpdates.tryEmit(Unit)
    }

    override fun saveBlockedUsers(users: String) {
        val set = users.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        sharedPrefs.edit().putStringSet("blocked_users_set", set).apply()
        _databaseUpdates.tryEmit(Unit)
    }

    override fun getBlockedUsersString(): String {
        val set = sharedPrefs.getStringSet("blocked_users_set", null)
        if (set != null) return set.joinToString(",")
        // Migrate from old comma-separated string
        val old = sharedPrefs.getString("blocked_users", null)
        if (old != null) {
            val migrated = old.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }.toSet()
            sharedPrefs.edit().putStringSet("blocked_users_set", migrated).remove("blocked_users").apply()
            return migrated.joinToString(",")
        }
        return ""
    }

    override fun getBlockedUsersList(): List<String> {
        val set = sharedPrefs.getStringSet("blocked_users_set", null)
        if (set != null) return set.toList()
        val s = getBlockedUsersString()
        if (s.isBlank()) return emptyList()
        return s.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun isMessageBlocked(sender: String, content: String): Boolean {
        val blockedUsers = getBlockedUsersList()
        if (sender.isNotBlank() && blockedUsers.any { it.equals(sender, ignoreCase = true) }) {
            return true
        }
        val rules = getBlockedKeywordRules()
        for (rule in rules) {
            if (rule.text.isBlank()) continue
            val matched = when (rule.mode) {
                MatchMode.CONTAINS -> content.contains(rule.text)
                MatchMode.EXACT -> content.trim() == rule.text
                MatchMode.REGEX -> try {
                    Regex(rule.text).containsMatchIn(content)
                } catch (_: Exception) { false }
            }
            if (matched) return true
        }
        return false
    }

    private fun filterBlocked(messages: List<Message>): List<Message> {
        return messages.filter { msg ->
            !isMessageBlocked(msg.senderName, msg.content)
        }
    }

    override fun saveReadPosition(groupId: String, index: Int, offset: Int) {
        sharedPrefs.edit()
            .putInt("read_index_$groupId", index)
            .putInt("read_offset_$groupId", offset)
            .apply()
    }

    override fun getReadPosition(groupId: String): Pair<Int, Int>? {
        if (!sharedPrefs.contains("read_index_$groupId")) return null
        val index = sharedPrefs.getInt("read_index_$groupId", 0)
        val offset = sharedPrefs.getInt("read_offset_$groupId", 0)
        return Pair(index, offset)
    }

    private fun getAncestorsChain(
        message: Message,
        allMessages: List<Message>,
        visited: MutableSet<Long>
    ): List<Message> {
        val parentId = message.parentMsgId ?: run {
            val parsed = parseMessageContent(message.content)
            val lastQuote = parsed.quoteLayers.lastOrNull() ?: return emptyList()
            val parentIdx = findQuotedMessageIndex(allMessages, message.id, lastQuote.senderName, lastQuote.cleanText)
            if (parentIdx != -1) allMessages[parentIdx].id else null
        } ?: return emptyList()

        if (parentId in visited) return emptyList()
        visited.add(parentId)

        val parent = allMessages.find { it.id == parentId }
        if (parent != null) {
            return getAncestorsChain(parent, allMessages, visited) + parent
        }
        return emptyList()
    }

    private fun getDescendantsChain(
        parent: Message,
        parentParsed: ParsedMessage,
        allMessages: List<Message>,
        visited: MutableSet<Long>
    ): List<Message> {
        val descendants = mutableListOf<Message>()
        
        // Find immediate children of the parent message
        val children = allMessages.filter { msg ->
            if (msg.id in visited) return@filter false
            val msgParsed = parseMessageContent(msg.content)
            matchesParent(msg, msgParsed, parent, parentParsed)
        }

        for (child in children) {
            visited.add(child.id)
            descendants.add(child)
            val childParsed = parseMessageContent(child.content)
            descendants.addAll(getDescendantsChain(child, childParsed, allMessages, visited))
        }

        return descendants
    }

    private fun matchesParent(
        candidate: Message,
        candidateParsed: ParsedMessage,
        parent: Message,
        parentParsed: ParsedMessage
    ): Boolean {
        if (candidate.id == parent.id) return false
        
        if (candidate.parentMsgId != null) {
            return candidate.parentMsgId == parent.id
        }
        
        val lastQuote = candidateParsed.quoteLayers.lastOrNull() ?: return false
        val cleanTargetText = lastQuote.cleanText.trim()
        val cleanUser = lastQuote.senderName?.removePrefix("@")?.trim()
        
        val senderMatches = cleanUser == null || parent.senderName.trim().equals(cleanUser, ignoreCase = true)
        if (!senderMatches) return false
        
        val parentImmediateText = parentParsed.cleanImmediateText.trim()
        
        return if (cleanTargetText == "图片" || cleanTargetText.contains("图片")) {
            parent.imageUrl != null
        } else if (cleanTargetText == "微博" || cleanTargetText.contains("微博")) {
            parent.linkUrl != null || parent.linkTitle != null || parent.content.contains("weibo.com") || parent.content.contains("t.cn")
        } else {
            parentImmediateText == cleanTargetText || 
            (cleanTargetText.length >= 2 && (parentImmediateText.contains(cleanTargetText) || cleanTargetText.contains(parentImmediateText)))
        }
    }

    override suspend fun getMessageContext(message: Message): List<Message> = withContext(Dispatchers.IO) {
        val targetGroupId = activeGroupIdFlow.value ?: getCredentials().second
        val allMessages = queryMessagesByGroupId(targetGroupId)

        // Pre-parse the target message
        val targetParsed = parseMessageContent(message.content)

        // 1. Get ancestors of the target message
        val visited = mutableSetOf<Long>()
        val ancestors = getAncestorsChain(message, allMessages, visited)

        // 2. Get descendants of the target message
        visited.clear()
        visited.add(message.id)
        val descendants = getDescendantsChain(message, targetParsed, allMessages, visited)

        val quoteChain = (ancestors + message + descendants).distinctBy { it.id }

        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val contextMessages = mutableListOf<Message>()

        // Add the quote chain (all referenced and referencing messages)
        contextMessages.addAll(quoteChain)

        // Only add nearby messages (+/- 2 hours) for the sender of the target (clicked) message
        val msgTime = try { sdf.parse(message.timestamp) } catch (e: Exception) { null }
        if (msgTime != null) {
            val cal = Calendar.getInstance().apply { time = msgTime }
            cal.add(Calendar.HOUR_OF_DAY, -2) // 2 hours before
            val startTimeStr = sdf.format(cal.time)
            cal.add(Calendar.HOUR_OF_DAY, 4) // 2 hours after (relative to startTimeStr)
            val endTimeStr = sdf.format(cal.time)

            val userMsgs = allMessages.filter {
                it.senderName == message.senderName &&
                it.timestamp >= startTimeStr &&
                it.timestamp <= endTimeStr
            }
            contextMessages.addAll(userMsgs)
        }

        return@withContext contextMessages.distinctBy { it.id }.sortedBy { it.timestamp }
    }

    override suspend fun syncMessagesUntil(targetTimeMillis: Long): Int = withContext(Dispatchers.IO) {
        val (cookie, credentialGroupId) = getCredentials()
        val targetGroupId = activeGroupIdFlow.value ?: credentialGroupId
        if (cookie.isBlank() || targetGroupId.isBlank()) return@withContext 0

        var currentMaxMid = 0L
        var totalSynced = 0
        var loopCount = 0
        val maxLoops = 200
        val seenPageOldest = mutableSetOf<Long>()

        while (loopCount < maxLoops) {
            loopCount++
            val requestMaxMid = (currentMaxMid - 1).coerceAtLeast(0L)
            val response = try {
                apiClient.queryMessages(
                    groupId = targetGroupId,
                    cookie = cookie,
                    maxMid = requestMaxMid,
                    count = 50,
                    onCookieUpdated = { newCookie ->
                        saveCredentials(newCookie, targetGroupId)
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("WeiboChat", "Error querying messages in syncMessagesUntil", e)
                null
            }

            if (response == null || !response.result || response.messages.isNullOrEmpty()) break

            val list = response.messages
            val changed = upsertApiMessages(targetGroupId, list)
            totalSynced += changed
            if (changed > 0) _databaseUpdates.emit(Unit)

            val oldestMsg = list.minByOrNull { it.time } ?: break
            val oldestTimeMillis = oldestMsg.time * 1000
            val oldestId = list.minOf { it.id }

            if (!seenPageOldest.add(oldestId) || oldestId >= currentMaxMid && currentMaxMid > 0L) break
            if (oldestTimeMillis <= targetTimeMillis) break

            currentMaxMid = oldestId
        }

        return@withContext totalSynced
    }

    override fun markWeiboStatusAsRead(statusId: String) {
        val current = getReadWeiboStatusIds().toMutableSet()
        if (current.add(statusId)) {
            if (current.size > 1000) {
                val list = current.toList()
                val pruned = list.drop(current.size - 1000).toSet()
                sharedPrefs.edit().putStringSet("read_weibo_ids", pruned).apply()
            } else {
                sharedPrefs.edit().putStringSet("read_weibo_ids", current).apply()
            }
        }
    }

    override fun getReadWeiboStatusIds(): Set<String> {
        return sharedPrefs.getStringSet("read_weibo_ids", emptySet()) ?: emptySet()
    }

    override fun getLocalTimeline(): Flow<List<WeiboTimelineStatus>> {
        return weiboDao.getAllWeibosFlow()
            .map { entities -> entities.toTimelineStatuses() }
            .flowOn(Dispatchers.IO)
    }

    private fun List<WeiboEntity>.toTimelineStatuses(): List<WeiboTimelineStatus> {
        val gson = Gson()
        return mapNotNull { entity ->
            if (entity.isGap == 1) {
                WeiboTimelineStatus(
                    id = entity.id,
                    idstr = entity.id.toString(),
                    created_at = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US).format(Date(entity.createdAtLong)),
                    raw_text = "__GAP__:${entity.gapSinceId}:${entity.gapMaxId}",
                    text_raw = "__GAP__:${entity.gapSinceId}:${entity.gapMaxId}",
                    text = "__GAP__:${entity.gapSinceId}:${entity.gapMaxId}",
                    source = null, isLongText = false, user = null,
                    pic_ids = null, pic_infos = null, retweeted_status = null,
                    page_info = null, reposts_count = null, comments_count = null, attitudes_count = null
                )
            } else {
                try {
                    gson.fromJson(entity.contentJson, WeiboTimelineStatus::class.java)
                } catch (e: Exception) {
                    e.printStackTrace()
                    null
                }
            }
        }
    }

    override suspend fun syncNewTimeline(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val localNewestId = weiboDao.getNewestWeiboId() ?: 0L
            val response = fetchFriendsTimeline(sinceId = localNewestId)
                ?: return@withContext Result.failure(Exception("Failed to fetch timeline from network"))
            if (response.ok != 1) {
                return@withContext Result.failure(Exception(response.msg ?: "Network response not OK"))
            }

            val statuses = response.statuses.orEmpty()
            if (statuses.isNotEmpty()) {
                val oldestStatus = statuses.minByOrNull { it.id ?: it.idstr?.toLongOrNull() ?: Long.MAX_VALUE }
                val oldestId = oldestStatus?.let { it.id ?: it.idstr?.toLongOrNull() }

                if (localNewestId > 0L && oldestId != null && oldestId > localNewestId && statuses.size >= 15) {
                    val gapId = oldestId - 1
                    val gapCreatedAtLong = parseWeiboCreatedAt(oldestStatus.created_at) - 1000L
                    weiboDao.insertOrReplace(WeiboEntity(
                        id = gapId,
                        createdAtLong = gapCreatedAtLong,
                        contentJson = "",
                        isGap = 1,
                        gapSinceId = localNewestId,
                        gapMaxId = oldestId - 1
                    ))
                }

                insertWeiboStatuses(statuses)
                prefetchImages(statuses)
                pruneOldWeibos()

                _weiboDatabaseUpdates.emit(Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncGap(gapId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val gap = weiboDao.getGapById(gapId)
                ?: return@withContext Result.failure(Exception("Gap not found"))
            val gapSinceId = gap.gapSinceId ?: 0L
            val gapMaxId = gap.gapMaxId ?: 0L
            if (gapSinceId == 0L || gapMaxId == 0L) return@withContext Result.failure(Exception("Gap not found"))

            var currentMaxId = gapMaxId
            var loopCount = 0
            val maxLoops = 10

            while (currentMaxId > gapSinceId && loopCount < maxLoops) {
                loopCount++
                if (loopCount > 1) delay(3000)

                val response = fetchFriendsTimeline(maxId = currentMaxId) ?: break
                if (response.ok != 1 || response.statuses.isNullOrEmpty()) break

                val statuses = response.statuses
                insertWeiboStatuses(statuses)
                prefetchImages(statuses)

                val oldestFetchedId = statuses.minOfOrNull { it.id ?: it.idstr?.toLongOrNull() ?: Long.MAX_VALUE } ?: Long.MAX_VALUE
                if (oldestFetchedId >= currentMaxId) break
                currentMaxId = oldestFetchedId - 1

                if (currentMaxId > gapSinceId) {
                    weiboDao.updateGapMaxId(gapId, currentMaxId)
                } else {
                    weiboDao.deleteById(gapId)
                }
                _weiboDatabaseUpdates.emit(Unit)
            }

            if (currentMaxId <= gapSinceId) {
                weiboDao.deleteById(gapId)
                _weiboDatabaseUpdates.emit(Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun loadMoreTimeline(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val oldestId = weiboDao.getOldestWeiboId() ?: 0L
            if (oldestId == 0L) return@withContext syncNewTimeline()
            val response = fetchFriendsTimeline(maxId = oldestId - 1)
                ?: return@withContext Result.failure(Exception("Failed to fetch older timeline posts"))
            if (response.ok != 1) {
                return@withContext Result.failure(Exception(response.msg ?: "Network response not OK"))
            }
            val statuses = response.statuses.orEmpty()
            if (statuses.isNotEmpty()) {
                insertWeiboStatuses(statuses)
                prefetchImages(statuses)
                _weiboDatabaseUpdates.emit(Unit)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private suspend fun insertWeiboStatuses(statuses: List<WeiboTimelineStatus>) {
        val gson = Gson()
        val entities = statuses.mapNotNull { status ->
            val statusId = status.id ?: status.idstr?.toLongOrNull() ?: return@mapNotNull null
            WeiboEntity(
                id = statusId,
                createdAtLong = parseWeiboCreatedAt(status.created_at),
                contentJson = gson.toJson(status),
                isGap = 0
            )
        }
        if (entities.isNotEmpty()) {
            weiboDao.insertOrReplaceAll(entities)
        }
    }

    private fun parseWeiboCreatedAt(createdAt: String?): Long {
        if (createdAt.isNullOrBlank()) return System.currentTimeMillis()
        return try {
            val parser = SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
            parser.parse(createdAt)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }

    private fun prefetchImages(statuses: List<WeiboTimelineStatus>) {
        val urls = mutableListOf<String>()
        for (status in statuses) {
            status.user?.profile_image_url?.let { urls.add(it) }
            status.pics?.forEach { pic ->
                pic.large?.url?.let { urls.add(it) }
            }
            status.retweeted_status?.pics?.forEach { pic ->
                pic.large?.url?.let { urls.add(it) }
            }
        }
        try {
            val loader = coil.Coil.imageLoader(context)
            urls.distinct().forEach { url ->
                val request = ImageRequest.Builder(context)
                    .data(url)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                loader.enqueue(request)
            }
        } catch (e: Exception) {
            // Ignore Coil initialization exceptions in tests or if loader isn't ready
            e.printStackTrace()
        }
    }

    private suspend fun pruneOldWeibos() {
        val sevenDaysAgo = System.currentTimeMillis() - (7L * 24 * 60 * 60 * 1000)
        weiboDao.deleteOlderThan(sevenDaysAgo)
        val count = weiboDao.countNonGap()
        if (count > 2000) {
            weiboDao.deleteOldest(count - 2000)
        }
    }

    override fun saveLastViewedWeibo(statusId: String, index: Int, offset: Int) {
        sharedPrefs.edit()
            .putString("last_viewed_weibo_id", statusId)
            .putInt("last_viewed_weibo_index", index)
            .putInt("last_viewed_weibo_offset", offset)
            .apply()
    }

    override fun getLastViewedWeibo(): Triple<String, Int, Int>? {
        val id = sharedPrefs.getString("last_viewed_weibo_id", null) ?: return null
        val index = sharedPrefs.getInt("last_viewed_weibo_index", 0)
        val offset = sharedPrefs.getInt("last_viewed_weibo_offset", 0)
        return Triple(id, index, offset)
    }
}
