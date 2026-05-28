package com.example.weibochat.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "weibos",
    indices = [
        Index(value = ["created_at_long"]),
        Index(value = ["is_gap"])
    ]
)
data class WeiboEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Long,

    @ColumnInfo(name = "created_at_long")
    val createdAtLong: Long = 0,

    @ColumnInfo(name = "content_json")
    val contentJson: String = "",

    @ColumnInfo(name = "is_read", defaultValue = "0")
    val isRead: Int = 0,

    @ColumnInfo(name = "is_gap", defaultValue = "0")
    val isGap: Int = 0,

    @ColumnInfo(name = "gap_since_id")
    val gapSinceId: Long? = null,

    @ColumnInfo(name = "gap_max_id")
    val gapMaxId: Long? = null
)

data class MediaFields(
    @ColumnInfo(name = "image_url") val imageUrl: String?,
    @ColumnInfo(name = "link_title") val linkTitle: String?,
    @ColumnInfo(name = "file_url") val fileUrl: String?
)
