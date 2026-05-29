package com.duty.weibotoy.data.repository

import com.duty.weibotoy.data.Message
import com.duty.weibotoy.data.WeiboContact
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface MessageRepository {
    val allMessages: Flow<List<Message>>
    fun getMessagesPagingData(): Flow<PagingData<Message>>
    suspend fun sendMessage(content: String, senderName: String = "我", contextId: Long? = 1): Long
    suspend fun getMessagesByContextId(contextId: Long): List<Message>
    suspend fun getUserContextMessages(senderName: String, timestamp: String): List<Message>
    suspend fun fetchOlderMessages(maxMid: Long): Boolean
    suspend fun loadOlderLocalMessages(groupId: String, beforeTimestamp: String, limit: Int = 500): List<Message>
    suspend fun fetchContacts(): List<WeiboContact>
    suspend fun getMessageContext(message: Message): List<Message>
    suspend fun syncMessagesUntil(targetTimeMillis: Long): Int
}
