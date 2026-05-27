package com.example.weibochat.data

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

fun cleanWeiboHtmlText(content: String): String {
    var result = content
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<img\\b[^>]*\\balt\\s*=\\s*([\"'])(.*?)\\1[^>]*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))) {
            decodeHtmlEntities(it.groupValues[2])
        }
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("<[^>]+>"), "")

    repeat(2) {
        result = decodeHtmlEntities(result)
    }

    return result
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{2,}"), "\n")
        .trim()
}

private fun decodeHtmlEntities(content: String): String {
    return content
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let { code ->
                codePointToString(code)
            } ?: match.value
        }
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.let { code ->
                codePointToString(code)
            } ?: match.value
        }
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
}

private fun codePointToString(code: Int): String {
    return try {
        String(Character.toChars(code))
    } catch (e: IllegalArgumentException) {
        ""
    }
}

private val quoteLayerRegex = Regex("^(?:@([^\\s:]+)(?::\\s*|\\s+))?「([\\s\\S]+)」\\s*\\n- -[ -]*\\s*\\n([\\s\\S]+)$")
private val senderRegex = Regex("^@([^\\s:]+):\\s*([\\s\\S]+)$")
private val recipientRegex = Regex("^@([^\\s:]+)(?::\\s*|\\s+)([\\s\\S]*)$")

fun parseRecursively(text: String): TempParsed {
    val trimmed = cleanWeiboHtmlText(text).trim()
    val match = quoteLayerRegex.matchEntire(trimmed)
    if (match != null) {
        val sender = match.groupValues[1].takeIf { it.isNotEmpty() }
        val quotedBody = match.groupValues[2].trim()
        val replyText = match.groupValues[3].trim()
        return TempParsed(
            immediateText = replyText,
            parent = parseRecursively(quotedBody),
            senderName = sender
        )
    } else {
        val senderMatch = senderRegex.matchEntire(trimmed)
        if (senderMatch != null) {
            val sender = senderMatch.groupValues[1].trim()
            val body = senderMatch.groupValues[2].trim()
            return TempParsed(
                immediateText = body,
                parent = null,
                senderName = sender
            )
        } else {
            return TempParsed(
                immediateText = trimmed,
                parent = null,
                senderName = null
            )
        }
    }
}

fun extractRecipient(text: String): Pair<String?, String> {
    val match = recipientRegex.matchEntire(text.trim())
    if (match != null) {
        val user = match.groupValues[1].trim().trimEnd(':')
        val rest = match.groupValues[2].trim()
        return Pair(user, rest)
    }
    return Pair(null, text)
}

fun cleanRootSymbols(text: String): String {
    var t = text.trim()
    while (t.startsWith("「") && t.endsWith("」")) {
        t = t.substring(1, t.length - 1).trim()
    }
    return t
}

fun isSystemKeyword(text: String): Boolean {
    val t = text.trim()
    return t == "图片" || t == "微博" || t.contains("图片") || t.contains("微博")
}

val parsedMessageCache = java.util.concurrent.ConcurrentHashMap<String, ParsedMessage>()
val quoteIdLookupCache = java.util.concurrent.ConcurrentHashMap<String, Long>()

fun parseMessageContent(content: String): ParsedMessage {
    return parsedMessageCache.getOrPut(content) {
        val temp = parseRecursively(content)
        
        val layers = mutableListOf<QuoteLayer>()
        var p = temp
        while (p.parent != null) {
            layers.add(QuoteLayer(senderName = p.parent.senderName, text = p.parent.immediateText))
            p = p.parent
        }
        
        val reversedLayers = layers.asReversed().toMutableList()
        val L = reversedLayers.size
        var currentText = temp.immediateText
        var cleanRootText = temp.immediateText
        
        for (i in L - 1 downTo 0) {
            val (recipient, restText) = extractRecipient(currentText)
            if (reversedLayers[i].senderName == null) {
                val layerCleanTemp = cleanRootSymbols(reversedLayers[i].text)
                if (!isSystemKeyword(layerCleanTemp)) {
                    reversedLayers[i] = reversedLayers[i].copy(senderName = recipient)
                }
            }
            if (i == L - 1) {
                cleanRootText = restText
            } else {
                reversedLayers[i + 1] = reversedLayers[i + 1].copy(cleanText = restText)
            }
            currentText = reversedLayers[i].text
        }
        
        if (L > 0) {
            val (recipient0, restText0) = extractRecipient(currentText)
            reversedLayers[0] = reversedLayers[0].copy(cleanText = restText0)
            if (reversedLayers[0].senderName == null) {
                if (!isSystemKeyword(reversedLayers[0].cleanText)) {
                    reversedLayers[0] = reversedLayers[0].copy(senderName = recipient0)
                }
            }
        } else {
            val (_, restText) = extractRecipient(temp.immediateText)
            cleanRootText = restText
        }
        
        for (i in 0 until L) {
            reversedLayers[i] = reversedLayers[i].copy(
                cleanText = cleanRootSymbols(reversedLayers[i].cleanText)
            )
        }
        cleanRootText = cleanRootSymbols(cleanRootText)
        
        ParsedMessage(
            immediateText = temp.immediateText,
            cleanImmediateText = cleanRootText,
            quoteLayers = reversedLayers
        )
    }
}


fun findQuotedMessageIndex(
    messages: List<Message>,
    replyMessageId: Long,
    targetSender: String?,
    targetText: String
): Int {
    android.util.Log.d("WeiboChat", "findQuotedMessageIndex START: replyMessageId=$replyMessageId, targetSender=$targetSender, targetText=$targetText")
    val replyIdx = messages.indexOfFirst { it.id == replyMessageId }
    if (replyIdx == -1) {
        android.util.Log.d("WeiboChat", "findQuotedMessageIndex FAILED: replyMessageId not found in messages list")
        return -1
    }

    val cleanTargetText = targetText.trim()
    val cleanUser = targetSender?.removePrefix("@")?.trim()
    var resolvedIndex = -1

    // We search backwards from the reply message
    for (i in (replyIdx - 1) downTo 0) {
        val msg = messages[i]
        val parsedMsg = parseMessageContent(msg.content)
        val msgImmediateText = parsedMsg.immediateText.trim()

        // Case A: User matches (case-insensitive)
        if (cleanUser != null && msg.senderName.trim().equals(cleanUser, ignoreCase = true)) {
            // A1: Image message match
            if (msg.imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                resolvedIndex = i
                break
            }
            // A2: Link/Weibo message match
            if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                (msg.linkUrl != null || msg.linkTitle != null || msg.content.contains("weibo.com") || msg.content.contains("t.cn"))) {
                resolvedIndex = i
                break
            }
            // A3: Exact reply text match
            if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText == cleanTargetText) {
                resolvedIndex = i
                break
            }
            // A4: Substring match
            if (cleanTargetText != "微博" && cleanTargetText != "图片" && cleanTargetText.length >= 2 && 
                (msgImmediateText.contains(cleanTargetText) || cleanTargetText.contains(msgImmediateText))) {
                resolvedIndex = i
                break
            }
            // A5: Link title match
            if (msg.linkTitle != null && cleanTargetText.contains(msg.linkTitle)) {
                resolvedIndex = i
                break
            }
        }

        // Case B: No user specified, or user mismatch but exact text match (skip for system keywords)
        if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText.isNotBlank() && msgImmediateText == cleanTargetText) {
            if (cleanUser == null || msg.senderName.trim().equals(cleanUser, ignoreCase = true)) {
                resolvedIndex = i
                break
            }
        }

        // Case C: No user specified (cleanUser == null), but target is a system keyword (图片/微博)
        if (cleanUser == null) {
            if (msg.imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                resolvedIndex = i
                break
            }
            if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                (msg.linkUrl != null || msg.linkTitle != null || msg.content.contains("weibo.com") || msg.content.contains("t.cn"))) {
                resolvedIndex = i
                break
            }
        }
    }

    if (resolvedIndex == -1) {
        // Fallback: search the whole list
        for (i in messages.indices) {
            val msg = messages[i]
            val parsedMsg = parseMessageContent(msg.content)
            val msgImmediateText = parsedMsg.immediateText.trim()
            
            if (cleanUser != null && msg.senderName.trim().equals(cleanUser, ignoreCase = true)) {
                if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText == cleanTargetText) {
                    resolvedIndex = i
                    break
                }
                if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                    (msg.linkUrl != null || msg.linkTitle != null || msg.content.contains("weibo.com") || msg.content.contains("t.cn"))) {
                    resolvedIndex = i
                    break
                }
                if (msg.imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                    resolvedIndex = i
                    break
                }
            }
            
            if (cleanUser == null) {
                if (cleanTargetText != "微博" && cleanTargetText != "图片" && msgImmediateText.isNotBlank() && msgImmediateText == cleanTargetText) {
                    resolvedIndex = i
                    break
                }
                if (msg.imageUrl != null && (cleanTargetText == "图片" || cleanTargetText.contains("图片"))) {
                    resolvedIndex = i
                    break
                }
                if ((cleanTargetText == "微博" || cleanTargetText.contains("微博")) &&
                    (msg.linkUrl != null || msg.linkTitle != null || msg.content.contains("weibo.com") || msg.content.contains("t.cn"))) {
                    resolvedIndex = i
                    break
                }
            }
        }
    }

    if (resolvedIndex == -1 && cleanUser != null) {
        for (i in (replyIdx - 1) downTo 0) {
            if (messages[i].senderName.trim().equals(cleanUser, ignoreCase = true)) {
                resolvedIndex = i
                break
            }
        }
    }

    android.util.Log.d("WeiboChat", "findQuotedMessageIndex END: resolvedIndex=$resolvedIndex, msgId=${if (resolvedIndex != -1) messages[resolvedIndex].id else null}, sender=${if (resolvedIndex != -1) messages[resolvedIndex].senderName else null}")
    return resolvedIndex
}
