package com.example.weibochat.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["group_id", "timestamp"]),
        Index(value = ["parent_msg_id"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "timestamp", defaultValue = "")
    val timestamp: String = "",

    @ColumnInfo(name = "sender_name", defaultValue = "")
    val senderName: String = "",

    @ColumnInfo(name = "group_suffix", defaultValue = "")
    val groupSuffix: String = "",

    @ColumnInfo(name = "content", defaultValue = "")
    val content: String = "",

    @ColumnInfo(name = "context_id")
    val contextId: Long? = null,

    @ColumnInfo(name = "image_url")
    val imageUrl: String? = null,

    @ColumnInfo(name = "link_title")
    val linkTitle: String? = null,

    @ColumnInfo(name = "link_desc")
    val linkDesc: String? = null,

    @ColumnInfo(name = "link_img")
    val linkImg: String? = null,

    @ColumnInfo(name = "link_url")
    val linkUrl: String? = null,

    @ColumnInfo(name = "file_url")
    val fileUrl: String? = null,

    @ColumnInfo(name = "file_name")
    val fileName: String? = null,

    @ColumnInfo(name = "group_id", defaultValue = "4761715839862414")
    val groupId: String = "4761715839862414",

    @ColumnInfo(name = "parent_msg_id")
    val parentMsgId: Long? = null
)

fun MessageEntity.toMessage(): Message = Message(
    id = id,
    timestamp = timestamp,
    senderName = senderName,
    groupSuffix = groupSuffix,
    content = content,
    contextId = contextId,
    imageUrl = imageUrl,
    linkTitle = linkTitle,
    linkDesc = linkDesc,
    linkImg = linkImg,
    linkUrl = linkUrl,
    fileUrl = fileUrl,
    fileName = fileName,
    groupId = groupId,
    parentMsgId = parentMsgId
)

fun Message.toEntity(): MessageEntity = MessageEntity(
    id = id,
    timestamp = timestamp,
    senderName = senderName,
    groupSuffix = groupSuffix,
    content = content,
    contextId = contextId,
    imageUrl = imageUrl,
    linkTitle = linkTitle,
    linkDesc = linkDesc,
    linkImg = linkImg,
    linkUrl = linkUrl,
    fileUrl = fileUrl,
    fileName = fileName,
    groupId = groupId ?: "4761715839862414",
    parentMsgId = parentMsgId
)
