package com.example.weibochat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE group_id = :groupId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesByGroupIdDesc(groupId: String, limit: Int = 5000): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE group_id = :groupId ORDER BY timestamp DESC LIMIT :limit")
    fun getMessagesByGroupIdFlow(groupId: String, limit: Int = 5000): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE group_id = :groupId ORDER BY timestamp DESC")
    fun getMessagesPagingSource(groupId: String): PagingSource<Int, MessageEntity>

    @Query("SELECT * FROM messages WHERE group_id = :groupId AND timestamp < :beforeTimestamp ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getOlderMessages(groupId: String, beforeTimestamp: String, limit: Int = 500): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE context_id = :contextId ORDER BY timestamp ASC")
    suspend fun getMessagesByContextId(contextId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE sender_name = :senderName AND group_id = :groupId AND timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp ASC")
    suspend fun getUserContextMessages(senderName: String, groupId: String, startTime: String, endTime: String): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessageById(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE group_id = :groupId AND sender_name = :senderName AND id < :beforeId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesBySenderBefore(groupId: String, senderName: String, beforeId: Long, limit: Int): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE group_id = :groupId AND id < :beforeId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getMessagesBefore(groupId: String, beforeId: Long, limit: Int): List<MessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(message: MessageEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplaceAll(messages: List<MessageEntity>)

    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE id = :id)")
    suspend fun existsById(id: Long): Boolean

    @Query("SELECT image_url, link_title, file_url FROM messages WHERE id = :id")
    suspend fun getMediaFields(id: Long): MediaFields?
}
