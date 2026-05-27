package com.example.weibochat.data

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

interface DataRepository {
    val allMessages: Flow<List<Message>>
    suspend fun sendMessage(content: String, senderName: String = "我", contextId: Long? = 1): Long
    suspend fun getMessagesByContextId(contextId: Long): List<Message>
    suspend fun getUserContextMessages(senderName: String, timestamp: String): List<Message>
    fun saveCredentials(cookie: String, groupId: String)
    fun getCredentials(): Pair<String, String>
    fun getAllCookies(): String
    suspend fun fetchOlderMessages(maxMid: Long): Boolean
    suspend fun fetchContacts(): List<WeiboContact>
    fun setActiveGroupId(groupId: String?)
    fun getActiveGroupId(): String?
    fun saveBlockedKeywords(keywords: String)
    fun getBlockedKeywordsString(): String
    fun getBlockedKeywordsList(): List<String>
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
        maxId: Long? = null
    ): WeiboTimelineResponse?
    suspend fun fetchWeiboStatusLongText(statusId: String): String?
    suspend fun fetchWeiboStatus(statusId: String): WeiboTimelineStatus?
    suspend fun fetchWeiboComments(statusId: String, maxId: Long?, maxIdType: Int): WeiboCommentsResponse?
    suspend fun fetchWeiboCommentChildren(commentId: Long, maxId: Long, maxIdType: Int): WeiboCommentChildrenResponse?
    suspend fun fetchWeiboReposts(statusId: String, page: Int): WeiboRepostsResponse?
    suspend fun fetchWeiboAttitudes(statusId: String, page: Int): WeiboAttitudesResponse?
    suspend fun likeWeiboStatus(statusId: String): Boolean
    suspend fun unlikeWeiboStatus(statusId: String): Boolean
}

class DefaultDataRepository(context: Context) : DataRepository {
    private val dbHelper = MessageDbHelper(context)
    private val apiClient = WeiboApiClient(context)
    private val sharedPrefs: SharedPreferences = context.getSharedPreferences("weibo_prefs", Context.MODE_PRIVATE)
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _databaseUpdates = MutableSharedFlow<Unit>(replay = 1)
    private val activeGroupIdFlow = MutableStateFlow<String?>(null)
    private var isPolling = false
    
    init {
        _databaseUpdates.tryEmit(Unit)
        startPeriodicSync()
    }

    override val allMessages: Flow<List<Message>> =
        combine(
            activeGroupIdFlow,
            _databaseUpdates.onStart { emit(Unit) }
        ) { groupId, _ -> groupId }
            .map { groupId ->
                if (groupId != null) queryMessagesByGroupId(groupId) else emptyList()
            }
            .flowOn(Dispatchers.IO)

    private fun cursorToMessage(cursor: android.database.Cursor): Message {
        val id = cursor.getLong(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_ID))
        val timestamp = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_TIMESTAMP)) ?: ""
        val senderName = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_SENDER_NAME)) ?: "匿名"
        val groupSuffix = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_GROUP_SUFFIX)) ?: "群聊"
        val content = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_CONTENT)) ?: ""
        val contextIdVal = if (cursor.isNull(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_CONTEXT_ID))) {
            null
        } else {
            cursor.getLong(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_CONTEXT_ID))
        }
        val imageUrl = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_IMAGE_URL))
        val linkTitle = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_LINK_TITLE))
        val linkDesc = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_LINK_DESC))
        val linkImg = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_LINK_IMG))
        val linkUrl = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_LINK_URL))
        val fileUrl = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_FILE_URL))
        val fileName = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_FILE_NAME))
        val gId = cursor.getString(cursor.getColumnIndexOrThrow(MessageDbHelper.COLUMN_GROUP_ID))

        val parentMsgIdCol = cursor.getColumnIndex(MessageDbHelper.COLUMN_PARENT_MSG_ID)
        val parentMsgIdVal = if (parentMsgIdCol != -1 && !cursor.isNull(parentMsgIdCol)) {
            cursor.getLong(parentMsgIdCol)
        } else {
            null
        }

        return Message(
            id = id,
            timestamp = timestamp,
            senderName = senderName,
            groupSuffix = groupSuffix,
            content = content,
            contextId = contextIdVal,
            imageUrl = imageUrl,
            linkTitle = linkTitle,
            linkDesc = linkDesc,
            linkImg = linkImg,
            linkUrl = linkUrl,
            fileUrl = fileUrl,
            fileName = fileName,
            groupId = gId,
            parentMsgId = parentMsgIdVal
        )
    }

    private fun findParentMessageId(
        db: SQLiteDatabase,
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

        // 1. 如果有指定的发送者，我们先查询该发送者的消息，按照时间倒序
        if (cleanUser != null) {
            val cursor = db.query(
                MessageDbHelper.TABLE_NAME,
                arrayOf(
                    MessageDbHelper.COLUMN_ID,
                    MessageDbHelper.COLUMN_SENDER_NAME,
                    MessageDbHelper.COLUMN_CONTENT,
                    MessageDbHelper.COLUMN_IMAGE_URL,
                    MessageDbHelper.COLUMN_LINK_TITLE,
                    MessageDbHelper.COLUMN_LINK_URL
                ),
                "${MessageDbHelper.COLUMN_GROUP_ID} = ? AND ${MessageDbHelper.COLUMN_SENDER_NAME} = ? AND ${MessageDbHelper.COLUMN_ID} < ?",
                arrayOf(groupId, cleanUser, replyMessageId.toString()),
                null, null,
                "${MessageDbHelper.COLUMN_TIMESTAMP} DESC",
                "100"
            )
            try {
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(0)
                    val msgContent = cursor.getString(2) ?: ""
                    val imageUrl = cursor.getString(3)
                    val linkTitle = cursor.getString(4)
                    val linkUrl = cursor.getString(5)

                    val parsedMsg = parseMessageContent(msgContent)
                    val msgImmediateText = parsedMsg.immediateText.trim()

                    // A1: Image message match
                    if (imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                        resolvedId = id
                        break
                    }
                    // A2: Link/Weibo message match
                    if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                        (linkUrl != null || linkTitle != null || msgContent.contains("weibo.com") || msgContent.contains("t.cn"))) {
                        resolvedId = id
                        break
                    }
                    // A3: Exact reply text match
                    if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText == cleanTargetText) {
                        resolvedId = id
                        break
                    }
                    // A4: Substring match
                    if (cleanTargetText != "微博" && cleanTargetText != "图片" && cleanTargetText.length >= 2 &&
                        (msgImmediateText.contains(cleanTargetText) || cleanTargetText.contains(msgImmediateText))) {
                        resolvedId = id
                        break
                    }
                    // A5: Link title match
                    if (linkTitle != null && cleanTargetText.contains(linkTitle)) {
                        resolvedId = id
                        break
                    }
                }
            } finally {
                cursor.close()
            }
        }

        if (resolvedId != null) return resolvedId

        // 2. 没有指定发送者，或者指定发送者没有匹配成功，但在数据库中存在完全文本一致的消息
        val cursorAll = db.query(
            MessageDbHelper.TABLE_NAME,
            arrayOf(
                MessageDbHelper.COLUMN_ID,
                MessageDbHelper.COLUMN_SENDER_NAME,
                MessageDbHelper.COLUMN_CONTENT,
                MessageDbHelper.COLUMN_IMAGE_URL,
                MessageDbHelper.COLUMN_LINK_TITLE,
                MessageDbHelper.COLUMN_LINK_URL
            ),
            "${MessageDbHelper.COLUMN_GROUP_ID} = ? AND ${MessageDbHelper.COLUMN_ID} < ?",
            arrayOf(groupId, replyMessageId.toString()),
            null, null,
            "${MessageDbHelper.COLUMN_TIMESTAMP} DESC",
            "200"
        )
        try {
            while (cursorAll.moveToNext()) {
                val id = cursorAll.getLong(0)
                val senderName = cursorAll.getString(1) ?: ""
                val msgContent = cursorAll.getString(2) ?: ""
                val imageUrl = cursorAll.getString(3)
                val linkTitle = cursorAll.getString(4)
                val linkUrl = cursorAll.getString(5)

                val parsedMsg = parseMessageContent(msgContent)
                val msgImmediateText = parsedMsg.immediateText.trim()

                if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText.isNotBlank() && msgImmediateText == cleanTargetText) {
                    if (cleanUser == null || senderName.trim().equals(cleanUser, ignoreCase = true)) {
                        resolvedId = id
                        break
                    }
                }

                if (cleanUser == null) {
                    if (imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                        resolvedId = id
                        break
                    }
                    if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                        (linkUrl != null || linkTitle != null || msgContent.contains("weibo.com") || msgContent.contains("t.cn"))) {
                        resolvedId = id
                        break
                    }
                }
            }
        } finally {
            cursorAll.close()
        }

        if (resolvedId != null) return resolvedId

        // 3. 兜底：如果指定了发送者，直接找最近一条该发送者的消息
        if (cleanUser != null) {
            val cursorSender = db.query(
                MessageDbHelper.TABLE_NAME,
                arrayOf(MessageDbHelper.COLUMN_ID),
                "${MessageDbHelper.COLUMN_GROUP_ID} = ? AND ${MessageDbHelper.COLUMN_SENDER_NAME} = ? AND ${MessageDbHelper.COLUMN_ID} < ?",
                arrayOf(groupId, cleanUser, replyMessageId.toString()),
                null, null,
                "${MessageDbHelper.COLUMN_TIMESTAMP} DESC",
                "1"
            )
            try {
                if (cursorSender.moveToFirst()) {
                    resolvedId = cursorSender.getLong(0)
                }
            } finally {
                cursorSender.close()
            }
        }

        return resolvedId
    }

    private fun createContentValues(db: SQLiteDatabase, wm: WeiboMessage, groupId: String): ContentValues {
        val values = ContentValues().apply {
            put(MessageDbHelper.COLUMN_ID, wm.id)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            val timeStr = sdf.format(Date(wm.time * 1000))
            put(MessageDbHelper.COLUMN_TIMESTAMP, timeStr)
            put(MessageDbHelper.COLUMN_SENDER_NAME, wm.from_user?.screen_name ?: "新浪用户")
            put(MessageDbHelper.COLUMN_GROUP_SUFFIX, "群聊")
            put(MessageDbHelper.COLUMN_CONTENT, wm.content)
            put(MessageDbHelper.COLUMN_CONTEXT_ID, 1L)
            put(MessageDbHelper.COLUMN_GROUP_ID, groupId)

            // Image parsing
            if (wm.media_type == 1 && !wm.fids.isNullOrEmpty()) {
                val fid = wm.fids.first()
                val imgUrl = "https://upload.api.weibo.com/2/mss/msget_thumbnail?fid=$fid&high=480&width=480&size=480,480&source=209678993"
                put(MessageDbHelper.COLUMN_IMAGE_URL, imgUrl)
            } else {
                putNull(MessageDbHelper.COLUMN_IMAGE_URL)
            }

            // Link preview parsing
            if (!wm.url_objects.isNullOrEmpty()) {
                val urlObj = wm.url_objects.first()
                val status = urlObj.status
                val info = urlObj.info
                val title = status?.user?.screen_name?.let { "微博：@$it" } ?: info?.title
                val desc = status?.text ?: info?.description
                val img = status?.thumbnail_pic ?: status?.original_pic
                val url = info?.url_long ?: urlObj.url_ori ?: wm.content

                if (title != null || desc != null) {
                    put(MessageDbHelper.COLUMN_LINK_TITLE, title)
                    put(MessageDbHelper.COLUMN_LINK_DESC, desc)
                    put(MessageDbHelper.COLUMN_LINK_IMG, img)
                    put(MessageDbHelper.COLUMN_LINK_URL, url)
                } else {
                    putNull(MessageDbHelper.COLUMN_LINK_TITLE)
                    putNull(MessageDbHelper.COLUMN_LINK_DESC)
                    putNull(MessageDbHelper.COLUMN_LINK_IMG)
                    putNull(MessageDbHelper.COLUMN_LINK_URL)
                }
            } else {
                putNull(MessageDbHelper.COLUMN_LINK_TITLE)
                putNull(MessageDbHelper.COLUMN_LINK_DESC)
                putNull(MessageDbHelper.COLUMN_LINK_IMG)
                putNull(MessageDbHelper.COLUMN_LINK_URL)
            }

            // File parsing
            if (wm.media_type == 5 && !wm.fids.isNullOrEmpty()) {
                val fid = wm.fids.first()
                val fileUrl = "https://upload.api.weibo.com/2/mss/msget?fid=$fid&source=209678993"
                put(MessageDbHelper.COLUMN_FILE_URL, fileUrl)
                put(MessageDbHelper.COLUMN_FILE_NAME, wm.content)
            } else {
                putNull(MessageDbHelper.COLUMN_FILE_URL)
                putNull(MessageDbHelper.COLUMN_FILE_NAME)
            }

            // Parse and associate parent message ID
            val parentId = findParentMessageId(db, groupId, wm.content, wm.id)
            if (parentId != null) {
                put(MessageDbHelper.COLUMN_PARENT_MSG_ID, parentId)
            } else {
                putNull(MessageDbHelper.COLUMN_PARENT_MSG_ID)
            }
        }
        return values
    }

    private fun shouldUpdateMessage(db: SQLiteDatabase, wm: WeiboMessage): Boolean {
        try {
            val cursor = db.query(
                MessageDbHelper.TABLE_NAME,
                arrayOf(MessageDbHelper.COLUMN_IMAGE_URL, MessageDbHelper.COLUMN_LINK_TITLE, MessageDbHelper.COLUMN_FILE_URL),
                "${MessageDbHelper.COLUMN_ID} = ?",
                arrayOf(wm.id.toString()),
                null, null, null
            )
            if (cursor.count == 0) {
                cursor.close()
                return true
            }
            cursor.moveToFirst()
            val currentImg = cursor.getString(0)
            val currentLinkTitle = cursor.getString(1)
            val currentFileUrl = cursor.getString(2)
            cursor.close()

            if (wm.media_type == 1 && !wm.fids.isNullOrEmpty() && currentImg.isNullOrEmpty()) {
                return true
            }
            if (!wm.url_objects.isNullOrEmpty() && currentLinkTitle.isNullOrEmpty()) {
                return true
            }
            if (wm.media_type == 5 && !wm.fids.isNullOrEmpty() && currentFileUrl.isNullOrEmpty()) {
                return true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }

    private fun upsertApiMessages(
        db: SQLiteDatabase,
        groupId: String,
        messages: List<WeiboMessage>,
        updateExisting: Boolean = true
    ): Int {
        var changed = 0
        db.beginTransaction()
        try {
            for (wm in messages) {
                val exists = isMessageExists(wm.id)
                val needsUpdate = updateExisting && exists && shouldUpdateMessage(db, wm)
                if (!exists || needsUpdate) {
                    val values = createContentValues(db, wm, groupId)
                    db.insertWithOnConflict(
                        MessageDbHelper.TABLE_NAME,
                        null,
                        values,
                        android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                    )
                    changed++
                }
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return changed
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
                        val db = dbHelper.writableDatabase
                        val changed = upsertApiMessages(db, targetGroupId, response.messages)
                        if (changed > 0) {
                            _databaseUpdates.emit(Unit)
                        }
                    }
                }
                delay(60000) // Poll every 60 seconds
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
                    val db = dbHelper.writableDatabase
                    val changed = upsertApiMessages(db, targetGroupId, response.messages)
                    if (changed > 0) {
                        _databaseUpdates.emit(Unit)
                    }
                }
            }
        }
    }

    private fun isMessageExists(id: Long): Boolean {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MessageDbHelper.TABLE_NAME,
            arrayOf(MessageDbHelper.COLUMN_ID),
            "${MessageDbHelper.COLUMN_ID} = ?",
            arrayOf(id.toString()),
            null, null, null
        )
        val exists = cursor.count > 0
        cursor.close()
        return exists
    }

    private fun queryMessagesByGroupId(groupId: String): List<Message> {
        try {
            val db = dbHelper.readableDatabase
            val cursor = db.query(
                MessageDbHelper.TABLE_NAME,
                null,
                "${MessageDbHelper.COLUMN_GROUP_ID} = ?",
                arrayOf(groupId),
                null, null,
                "${MessageDbHelper.COLUMN_TIMESTAMP} ASC"
            )
            val messages = mutableListOf<Message>()
            with(cursor) {
                while (moveToNext()) {
                    messages.add(cursorToMessage(this))
                }
                close()
            }
            return filterBlocked(messages)
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
                
                val db = dbHelper.writableDatabase
                val values = ContentValues().apply {
                    put(MessageDbHelper.COLUMN_ID, mid)
                    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
                    val timeStr = sdf.format(Date(responseTime * 1000))
                    put(MessageDbHelper.COLUMN_TIMESTAMP, timeStr)
                    put(MessageDbHelper.COLUMN_SENDER_NAME, response.from_user?.screen_name ?: "我")
                    put(MessageDbHelper.COLUMN_GROUP_SUFFIX, "群聊")
                    put(MessageDbHelper.COLUMN_CONTENT, content)
                    if (contextId != null) {
                        put(MessageDbHelper.COLUMN_CONTEXT_ID, contextId)
                    } else {
                        putNull(MessageDbHelper.COLUMN_CONTEXT_ID)
                    }
                    putNull(MessageDbHelper.COLUMN_IMAGE_URL)
                    putNull(MessageDbHelper.COLUMN_LINK_TITLE)
                    putNull(MessageDbHelper.COLUMN_LINK_DESC)
                    putNull(MessageDbHelper.COLUMN_LINK_IMG)
                    putNull(MessageDbHelper.COLUMN_LINK_URL)
                    putNull(MessageDbHelper.COLUMN_FILE_URL)
                    putNull(MessageDbHelper.COLUMN_FILE_NAME)
                    put(MessageDbHelper.COLUMN_GROUP_ID, targetGroupId)
                    
                    val parentId = findParentMessageId(db, targetGroupId, content, mid)
                    if (parentId != null) {
                        put(MessageDbHelper.COLUMN_PARENT_MSG_ID, parentId)
                    } else {
                        putNull(MessageDbHelper.COLUMN_PARENT_MSG_ID)
                    }
                }
                db.insertWithOnConflict(
                    MessageDbHelper.TABLE_NAME,
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
                _databaseUpdates.emit(Unit)
                return@withContext mid
            }
        }
        
        // Fallback: Local insert for offline/simulation mode
        val mid = System.currentTimeMillis()
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put(MessageDbHelper.COLUMN_ID, mid)
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            put(MessageDbHelper.COLUMN_TIMESTAMP, sdf.format(Date()))
            put(MessageDbHelper.COLUMN_SENDER_NAME, senderName)
            put(MessageDbHelper.COLUMN_GROUP_SUFFIX, "群聊")
            put(MessageDbHelper.COLUMN_CONTENT, content)
            if (contextId != null) {
                put(MessageDbHelper.COLUMN_CONTEXT_ID, contextId)
            } else {
                putNull(MessageDbHelper.COLUMN_CONTEXT_ID)
            }
            putNull(MessageDbHelper.COLUMN_IMAGE_URL)
            putNull(MessageDbHelper.COLUMN_LINK_TITLE)
            putNull(MessageDbHelper.COLUMN_LINK_DESC)
            putNull(MessageDbHelper.COLUMN_LINK_IMG)
            putNull(MessageDbHelper.COLUMN_LINK_URL)
            putNull(MessageDbHelper.COLUMN_FILE_URL)
            putNull(MessageDbHelper.COLUMN_FILE_NAME)
            put(MessageDbHelper.COLUMN_GROUP_ID, targetGroupId.ifBlank { "4761715839862414" })
            
            val parentId = findParentMessageId(db, targetGroupId.ifBlank { "4761715839862414" }, content, mid)
            if (parentId != null) {
                put(MessageDbHelper.COLUMN_PARENT_MSG_ID, parentId)
            } else {
                putNull(MessageDbHelper.COLUMN_PARENT_MSG_ID)
            }
        }
        db.insertWithOnConflict(
            MessageDbHelper.TABLE_NAME,
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
        _databaseUpdates.emit(Unit)
        return@withContext mid
    }

    override suspend fun getMessagesByContextId(contextId: Long): List<Message> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.query(
            MessageDbHelper.TABLE_NAME,
            null,
            "${MessageDbHelper.COLUMN_CONTEXT_ID} = ?",
            arrayOf(contextId.toString()),
            null, null,
            "${MessageDbHelper.COLUMN_TIMESTAMP} ASC"
        )
        val messages = mutableListOf<Message>()
        with(cursor) {
            while (moveToNext()) {
                messages.add(cursorToMessage(this))
            }
            close()
        }
        filterBlocked(messages)
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
                val db = dbHelper.writableDatabase
                val changed = upsertApiMessages(db, targetGroupId, response.messages)
                if (changed > 0) {
                    _databaseUpdates.emit(Unit)
                }
                return@withContext changed > 0
            }
        }
        return@withContext false
    }

    override suspend fun getUserContextMessages(senderName: String, timestamp: String): List<Message> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val messages = mutableListOf<Message>()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        try {
            val date = sdf.parse(timestamp) ?: Date()
            val cal = Calendar.getInstance().apply { time = date }
            cal.add(Calendar.HOUR_OF_DAY, -1)
            val startTime = sdf.format(cal.time)
            cal.add(Calendar.HOUR_OF_DAY, 2)
            val endTime = sdf.format(cal.time)

            val targetGroupId = activeGroupIdFlow.value ?: getCredentials().second
            val cursor = db.query(
                MessageDbHelper.TABLE_NAME,
                null,
                "${MessageDbHelper.COLUMN_SENDER_NAME} = ? AND ${MessageDbHelper.COLUMN_GROUP_ID} = ? AND ${MessageDbHelper.COLUMN_TIMESTAMP} BETWEEN ? AND ?",
                arrayOf(senderName, targetGroupId, startTime, endTime),
                null, null,
                "${MessageDbHelper.COLUMN_TIMESTAMP} ASC"
            )
            with(cursor) {
                while (moveToNext()) {
                    messages.add(cursorToMessage(this))
                }
                close()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        filterBlocked(messages)
    }

    override suspend fun fetchContacts(): List<WeiboContact> {
        val cookie = getCredentials().first
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
        maxId: Long?
    ): WeiboTimelineResponse? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchFriendsTimeline(
                cookie = cookie,
                listId = listId,
                maxId = maxId,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboStatusLongText(statusId: String): String? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchStatusLongText(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboStatus(statusId: String): WeiboTimelineStatus? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchStatusShow(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboComments(statusId: String, maxId: Long?, maxIdType: Int): WeiboCommentsResponse? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchWeiboComments(
                cookie = cookie,
                statusId = statusId,
                maxId = maxId,
                maxIdType = maxIdType,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboCommentChildren(commentId: Long, maxId: Long, maxIdType: Int): WeiboCommentChildrenResponse? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && commentId > 0L) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchWeiboCommentChildren(
                cookie = cookie,
                commentId = commentId,
                maxId = maxId,
                maxIdType = maxIdType,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboReposts(statusId: String, page: Int): WeiboRepostsResponse? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchWeiboReposts(
                cookie = cookie,
                statusId = statusId,
                page = page,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun fetchWeiboAttitudes(statusId: String, page: Int): WeiboAttitudesResponse? {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.fetchWeiboAttitudes(
                cookie = cookie,
                statusId = statusId,
                page = page,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return null
    }

    override suspend fun likeWeiboStatus(statusId: String): Boolean {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.likeStatus(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
                }
            )
        }
        return false
    }

    override suspend fun unlikeWeiboStatus(statusId: String): Boolean {
        val cookie = getCredentials().first
        if (cookie.isNotBlank() && statusId.isNotBlank()) {
            val activeGroupIdVal = getActiveGroupId() ?: getCredentials().second
            return apiClient.unlikeStatus(
                cookie = cookie,
                statusId = statusId,
                onCookieUpdated = { newCookie ->
                    saveCredentials(newCookie, activeGroupIdVal)
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
        sharedPrefs.edit().putString("blocked_keywords", keywords).apply()
        _databaseUpdates.tryEmit(Unit)
    }

    override fun getBlockedKeywordsString(): String {
        return sharedPrefs.getString("blocked_keywords", "撤回了一条消息,红包,🧧,系统消息,群规") ?: "撤回了一条消息,红包,🧧,系统消息,群规"
    }

    override fun getBlockedKeywordsList(): List<String> {
        val s = getBlockedKeywordsString()
        if (s.isBlank()) return emptyList()
        return s.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun saveBlockedUsers(users: String) {
        sharedPrefs.edit().putString("blocked_users", users).apply()
        _databaseUpdates.tryEmit(Unit)
    }

    override fun getBlockedUsersString(): String {
        return sharedPrefs.getString("blocked_users", "") ?: ""
    }

    override fun getBlockedUsersList(): List<String> {
        val s = getBlockedUsersString()
        if (s.isBlank()) return emptyList()
        return s.split(Regex("[,，]")).map { it.trim() }.filter { it.isNotEmpty() }
    }

    override fun isMessageBlocked(sender: String, content: String): Boolean {
        val blockedUsers = getBlockedUsersList()
        val blockedKeywords = getBlockedKeywordsList()
        if (sender.isNotBlank() && blockedUsers.any { it.equals(sender, ignoreCase = true) }) {
            return true
        }
        if (blockedKeywords.any { content.contains(it) }) {
            return true
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
        val writeDb = dbHelper.writableDatabase
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

            if (response == null || !response.result || response.messages.isNullOrEmpty()) {
                break
            }

            val list = response.messages
            val changed = upsertApiMessages(writeDb, targetGroupId, list)
            totalSynced += changed
            if (changed > 0) {
                _databaseUpdates.emit(Unit)
            }

            val oldestMsg = list.minByOrNull { it.time } ?: break
            val oldestTimeMillis = oldestMsg.time * 1000
            val oldestId = list.minOf { it.id }

            if (!seenPageOldest.add(oldestId) || oldestId >= currentMaxMid && currentMaxMid > 0L) {
                android.util.Log.w("WeiboChat", "History sync stopped because pagination did not move: currentMaxMid=$currentMaxMid, oldestId=$oldestId")
                break
            }

            if (oldTimeMillisToBreak(oldestTimeMillis, targetTimeMillis)) {
                break
            }

            currentMaxMid = oldestId
        }

        return@withContext totalSynced
    }

    private fun oldTimeMillisToBreak(oldestTimeMillis: Long, targetTimeMillis: Long): Boolean {
        return oldestTimeMillis <= targetTimeMillis
    }
}
