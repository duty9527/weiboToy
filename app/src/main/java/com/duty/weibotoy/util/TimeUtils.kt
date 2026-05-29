package com.duty.weibotoy.util

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object TimeUtils {
    private val DEFAULT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    private val WEIBO_FORMATTER = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.US)
    private val ZONE_ID = ZoneId.systemDefault()

    fun formatDefault(epochMilli: Long): String {
        val instant = Instant.ofEpochMilli(epochMilli)
        val localDateTime = LocalDateTime.ofInstant(instant, ZONE_ID)
        return DEFAULT_FORMATTER.format(localDateTime)
    }

    fun parseDefault(dateStr: String): Long {
        val localDateTime = LocalDateTime.parse(dateStr, DEFAULT_FORMATTER)
        return localDateTime.atZone(ZONE_ID).toInstant().toEpochMilli()
    }

    fun parseWeiboCreatedAt(createdAt: String): Long {
        if (createdAt.isBlank()) return System.currentTimeMillis()
        return try {
            val zonedDateTime = ZonedDateTime.parse(createdAt, WEIBO_FORMATTER)
            zonedDateTime.toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
}
