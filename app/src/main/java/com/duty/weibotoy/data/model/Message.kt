package com.duty.weibotoy.data

data class Message(
    val id: Long = 0,
    val timestamp: String,
    val senderName: String,
    val groupSuffix: String = "茧房建筑师协会",
    val content: String,
    val contextId: Long? = null,
    val imageUrl: String? = null,
    val linkTitle: String? = null,
    val linkDesc: String? = null,
    val linkImg: String? = null,
    val linkUrl: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val groupId: String? = null,
    val parentMsgId: Long? = null
)

data class QuoteLayer(
    val senderName: String?,
    val text: String,
    val cleanText: String = text
)

data class ParsedMessage(
    val immediateText: String,
    val cleanImmediateText: String,
    val quoteLayers: List<QuoteLayer>
)

data class TempParsed(
    val immediateText: String,
    val parent: TempParsed?,
    val senderName: String?
)
