package com.example.weibochat.ui.weibo.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.weibochat.data.WeiboTimelinePageInfo
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.data.WeiboUrlStruct
import com.example.weibochat.theme.TextWhite
import com.example.weibochat.ui.weibo.isAllowedWeiboHost
import java.util.Locale
import androidx.compose.foundation.gestures.detectTapGestures

internal const val DETAIL_LINK_TARGET = "__weibo_status_detail__"

internal data class ParsedWeiboText(
    val annotatedString: AnnotatedString,
    val inlineContent: Map<String, InlineTextContent> = emptyMap()
)

internal fun formatCount(count: Int): String {
    if (count <= 0) return "0"
    if (count < 1000) return count.toString()
    if (count < 10000) return String.format(Locale.getDefault(), "%.1fk", count / 1000f)
    return String.format(Locale.getDefault(), "%.1f万", count / 10000f)
}

internal fun extractUrlFromWeiboRedirect(url: String): String {
    if (url.contains("sinaurl") && url.contains("u=")) {
        val match = Regex("[?&]u=([^&]+)").find(url)
        if (match != null) {
            val uVal = match.groupValues[1]
            return runCatching { java.net.URLDecoder.decode(uVal, "UTF-8") }.getOrDefault(uVal)
        }
    }
    return url
}

internal fun statusId(status: WeiboTimelineStatus): String? {
    return status.idstr?.takeIf { it.isNotBlank() && it != "0" }
        ?: status.id?.toString()?.takeIf { it.isNotBlank() && it != "0" }
}

internal fun statusMblogId(status: WeiboTimelineStatus): String? {
    return status.mblogid?.takeIf { it.isNotBlank() && it != "0" }
        ?: status.page_info?.page_url?.let { extractWeiboStatusIdFromUrl(it) }
        ?: status.url_struct
            ?.asSequence()
            ?.mapNotNull { item ->
                item.long_url?.let { extractWeiboStatusIdFromUrl(it) }
                    ?: item.ori_url?.let { extractWeiboStatusIdFromUrl(it) }
                    ?: item.short_url?.let { extractWeiboStatusIdFromUrl(it) }
                    ?: item.page_info?.page_url?.let { extractWeiboStatusIdFromUrl(it) }
            }
            ?.firstOrNull()
}

internal fun statusDetailUrl(status: WeiboTimelineStatus): String? {
    return (statusMblogId(status) ?: statusId(status))?.let { "https://m.weibo.cn/status/$it" }
}

internal fun openInWeiboApp(
    context: Context,
    status: WeiboTimelineStatus,
    fallback: () -> Unit
) {
    val targetId = statusMblogId(status) ?: statusId(status)
    if (targetId == null) {
        fallback()
        return
    }

    try {
        val uri = Uri.parse("sinaweibo://detail?mblogid=$targetId")
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        fallback()
    }
}

internal fun shouldShowDetailButton(status: WeiboTimelineStatus): Boolean {
    val text = status.text ?: status.text_raw ?: status.raw_text ?: ""
    return text.contains("全文") || shouldFetchLongText(status)
}

internal fun shouldFetchLongText(status: WeiboTimelineStatus): Boolean {
    return status.isLongText == true
}

internal fun isFullTextLink(linkText: String, href: String): Boolean {
    return linkText.contains("全文") || href.contains("全文")
}

internal fun AnnotatedString.Builder.stylePattern(
    text: String,
    regex: Regex,
    color: Color
) {
    regex.findAll(text).forEach { match ->
        val start = match.range.first
        val end = match.range.last + 1
        addStyle(
            SpanStyle(color = color, fontWeight = FontWeight.Bold),
            start,
            end
        )
    }
}

internal fun stripHtml(value: String): String {
    return value.replace(Regex("<[^>]*>"), "")
}

internal fun decodeHtml(value: String): String {
    return value
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
}

internal fun codePointToString(code: Int): String {
    return runCatching { String(Character.toChars(code)) }.getOrDefault("")
}

internal fun extractImageUrlFromWeiboUrl(url: String): String? {
    if (url.contains("u=")) {
        val uri = Uri.parse(url)
        val uParam = uri.getQueryParameter("u")
        if (!uParam.isNullOrBlank()) {
            return uParam
        }
    }

    val match = Regex("[?&]u=([^&]+)").find(url)
    if (match != null) {
        val uVal = match.groupValues[1]
        val decodedUVal = runCatching { java.net.URLDecoder.decode(uVal, "UTF-8") }.getOrDefault(uVal)
        if (decodedUVal.startsWith("http://") || decodedUVal.startsWith("https://")) {
            return decodedUVal
        }
    }

    val decodedUrl = runCatching { java.net.URLDecoder.decode(url, "UTF-8") }.getOrDefault(url)
    val isDirectImage = decodedUrl.contains("sinaimg.cn") ||
                        decodedUrl.endsWith(".jpg", ignoreCase = true) ||
                        decodedUrl.endsWith(".jpeg", ignoreCase = true) ||
                        decodedUrl.endsWith(".png", ignoreCase = true) ||
                        decodedUrl.endsWith(".gif", ignoreCase = true) ||
                        decodedUrl.endsWith(".webp", ignoreCase = true)
    if (isDirectImage) {
        return decodedUrl
    }
    return null
}

internal fun handleWeiboLinkClick(
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onWebClick: (String) -> Unit
) {
    val realUrl = getRealUrlIfWeiboRedirect(url)
    val normalized = normalizeWeiboUrl(realUrl)
    val host = Uri.parse(normalized).host
    val isWeibo = host?.let { isAllowedWeiboHost(it) } == true
    if (isWeibo) {
        onWebClick(normalized)
    } else {
        runCatching { uriHandler.openUri(normalized) }
    }
}

internal fun getRealUrlIfWeiboRedirect(url: String): String {
    if (url.contains("sinaurl") && url.contains("u=")) {
        val uri = runCatching { Uri.parse(url) }.getOrNull()
        val uParam = uri?.getQueryParameter("u")
        if (!uParam.isNullOrBlank()) {
            return uParam
        }
    }
    return url
}

internal fun formatWeiboCreatedAt(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    // Custom logic to format time representation
    val parts = createdAt.split(" ")
    return if (parts.size >= 4) {
        "${parts[1]} ${parts[2]} ${parts[3]}"
    } else {
        val regex = Regex("\\d{2}:\\d{2}:\\d{2}")
        regex.find(createdAt)?.value ?: createdAt
    }
}

internal fun extractTimeCapsule(createdAt: String): String {
    if (createdAt.isBlank()) return ""
    val timeWithSecondsRegex = Regex("(\\d{2}):(\\d{2}):\\d{2}")
    val match = timeWithSecondsRegex.find(createdAt)
    if (match != null) {
        return "${match.groupValues[1]}:${match.groupValues[2]}"
    }
    val timeRegex = Regex("(\\d{2}):(\\d{2})")
    val shortMatch = timeRegex.find(createdAt)
    if (shortMatch != null) {
        return shortMatch.value
    }
    return createdAt
}

internal fun getStatusLocation(status: WeiboTimelineStatus): String? {
    val pageInfo = status.page_info
    if (pageInfo != null && pageInfo.type == "place") {
        val title = pageInfo.title?.takeIf { it.isNotBlank() }
            ?: pageInfo.page_title?.takeIf { it.isNotBlank() }
            ?: pageInfo.content1?.takeIf { it.isNotBlank() }
            ?: pageInfo.content2?.takeIf { it.isNotBlank() }
        if (!title.isNullOrBlank()) {
            return title
        }
    }

    val urlStructList = status.url_struct
    if (!urlStructList.isNullOrEmpty()) {
        for (urlObj in urlStructList) {
            val isLoc = urlObj.url_type_pic?.contains("location") == true ||
                        urlObj.short_url?.contains("location") == true ||
                        urlObj.long_url?.contains("location") == true ||
                        urlObj.long_url?.contains("230413") == true ||
                        urlObj.short_url?.contains("230413") == true ||
                        urlObj.long_url?.contains("100101") == true ||
                        urlObj.short_url?.contains("100101") == true ||
                        urlObj.page_id?.startsWith("100101") == true ||
                        urlObj.page_id?.startsWith("230413") == true ||
                        urlObj.page_info?.type == "place"
            if (isLoc) {
                val title = urlObj.page_info?.title?.takeIf { it.isNotBlank() }
                    ?: urlObj.page_info?.page_title?.takeIf { it.isNotBlank() }
                    ?: urlObj.url_title
                if (!title.isNullOrBlank()) {
                    return title
                }
            }
        }
    }

    val html = status.text ?: status.text_raw ?: status.raw_text ?: ""
    val linkRegex = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    for (match in linkRegex.findAll(html)) {
        val attrs = match.groupValues[1]
        val linkText = match.groupValues[2].replace(Regex("<[^>]+>"), "").trim()
        val href = Regex("\\bhref\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(2) ?: ""
        val isLoc = match.value.contains("location") || href.contains("location") || href.contains("230413") || href.contains("100101") || linkText.contains("定位") ||
                    (pageInfo != null && pageInfo.type == "place" && (
                        (pageInfo.page_url != null && href.contains(pageInfo.page_url.removePrefix("https:").removePrefix("http:").trim())) ||
                        (pageInfo.media_info?.h5_url != null && href.contains(pageInfo.media_info.h5_url.removePrefix("https:").removePrefix("http:").trim()))
                    ))
        if (isLoc && linkText.isNotBlank() && !linkText.contains("定位")) {
            return linkText
        }
    }

    return null
}

internal fun parseWeiboHtmlText(
    html: String,
    detailUrl: String? = null,
    urlStruct: List<WeiboUrlStruct>? = null,
    locationName: String? = null,
    isMarkdown: Boolean = false,
    statusPageInfo: WeiboTimelinePageInfo? = null
): ParsedWeiboText {
    val linkIconList = mutableListOf<String>()

    var processedHtml = html
    while (true) {
        val currentClean = processedHtml.replace(Regex("<[^>]+>"), "").trim()
        val match = Regex("#([^#\\n]+)#$").find(currentClean) ?: break
        val topicTitle = match.value
        val topicEscaped = Regex.escape(topicTitle)
        val endingTagRegex = Regex("(<a\\b[^>]*>\\s*${topicEscaped}\\s*</a>|${topicEscaped})\\s*$", RegexOption.IGNORE_CASE)
        val newHtml = processedHtml.replace(endingTagRegex, "").trim()
        if (newHtml == processedHtml) break
        processedHtml = newHtml
    }

    urlStruct?.forEach { urlObj ->
        val shortUrl = urlObj.short_url
        if (!shortUrl.isNullOrBlank()) {
            val cleanShort = shortUrl.removePrefix("https:").removePrefix("http:").removePrefix("//").trim()
            if (cleanShort.isNotBlank()) {
                val isVideo = urlObj.page_info?.type == "video" ||
                              urlObj.page_info?.media_info != null ||
                              urlObj.page_info?.object_type == "video" ||
                              urlObj.url_title?.contains("视频") == true ||
                              urlObj.url_title?.contains("直播") == true

                if (isVideo) {
                    val tagRegex = Regex("<a\\b[^>]*href=\"[^\"]*${Regex.escape(cleanShort)}[^\"]*\"[^>]*>.*?</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    processedHtml = processedHtml.replace(tagRegex, "")

                    val plainRegex = Regex("https?://\\Q$cleanShort\\E|//\\Q$cleanShort\\E|\\b\\Q$cleanShort\\E", RegexOption.IGNORE_CASE)
                    processedHtml = processedHtml.replace(plainRegex, "")
                } else {
                    val isLoc = urlObj.url_type_pic?.contains("location") == true ||
                                urlObj.short_url.contains("location") == true ||
                                urlObj.long_url?.contains("location") == true ||
                                urlObj.long_url?.contains("230413") == true ||
                                urlObj.short_url.contains("230413") == true ||
                                urlObj.long_url?.contains("100101") == true ||
                                urlObj.short_url.contains("100101") == true ||
                                urlObj.page_id?.startsWith("100101") == true ||
                                urlObj.page_id?.startsWith("230413") == true ||
                                urlObj.page_info?.type == "place"
                    val isTop = urlObj.url_type_pic?.contains("huati") == true ||
                                (urlObj.url_title?.startsWith("#") == true && urlObj.url_title.endsWith("#"))

                    if (isLoc || !isTop) {
                        val regex = Regex("https?://\\Q$cleanShort\\E|//\\Q$cleanShort\\E|\\b\\Q$cleanShort\\E", RegexOption.IGNORE_CASE)
                        processedHtml = processedHtml.replace(regex, "")
                    }
                }
            }
        }
    }

    processedHtml = processedHtml.replace(Regex("@[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博视频", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博视频", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("的微博视频", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("@[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博直播", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博直播", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("的微博直播", RegexOption.IGNORE_CASE), "")

    val linkRegex = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val imgRegex = Regex("<img\\b([^>]*)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    val emojiList = mutableListOf<Pair<String, String>>()
    processedHtml = imgRegex.replace(processedHtml) { match ->
        val attrs = match.groupValues[1]
        val alt = Regex("\\balt\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(2)
        val src = Regex("\\bsrc\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(2)
        if (alt != null && alt.startsWith("[") && alt.endsWith("]") && src != null) {
            emojiList.add(Pair(alt, src))
            "\uFFFC"
        } else {
            match.value
        }
    }

    var text = processedHtml
        .replace(brRegex, "\n")
        .replace(Regex("</p\\s*>", RegexOption.IGNORE_CASE), "\n")
        .replace(Regex("[ \\t]+\\n"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    if (!locationName.isNullOrBlank()) {
        val suffix = locationName.trim()
        if (text.endsWith(suffix)) {
            var prefix = text.substring(0, text.length - suffix.length).trim()
            prefix = prefix.removeSuffix("📍").removeSuffix("📍 ").removeSuffix("定位").removeSuffix("定位 ").trim()
            text = prefix
        }
    }

    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    var emojiCount = 0
    var iconCount = 0

    val annotatedString = buildAnnotatedString {
        fun appendTextSegment(segment: String) {
            var i = 0
            val len = segment.length
            while (i < len) {
                val char = segment[i]
                if (char == '\uFFFC') {
                    if (emojiCount < emojiList.size) {
                        val (alt, src) = emojiList[emojiCount]
                        val emojiId = "emoji_${emojiCount}"
                        appendInlineContent(emojiId, "\uFFFC")

                        inlineContentMap[emojiId] = InlineTextContent(
                            Placeholder(
                                width = 16.sp,
                                height = 16.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            AsyncImage(
                                model = src,
                                contentDescription = alt,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        emojiCount++
                    } else {
                        append("\uFFFC")
                    }
                    i++
                } else if (char == '\uFFFD') {
                    if (iconCount < linkIconList.size) {
                        val src = linkIconList[iconCount]
                        val iconId = "icon_${iconCount}"
                        appendInlineContent(iconId, "\uFFFD")

                        inlineContentMap[iconId] = InlineTextContent(
                            Placeholder(
                                width = 16.sp,
                                height = 16.sp,
                                placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                            )
                        ) {
                            AsyncImage(
                                model = src,
                                contentDescription = "link_icon",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                        iconCount++
                    } else {
                        append("\uFFFD")
                    }
                    i++
                } else {
                    var nextSpecial = segment.indexOfAny(charArrayOf('\uFFFC', '\uFFFD'), i)
                    if (nextSpecial == -1) nextSpecial = len
                    val chunk = segment.substring(i, nextSpecial)
                    if (isMarkdown) {
                        appendMarkdownStyledText(chunk)
                    } else {
                        append(chunk)
                    }
                    i = nextSpecial
                }
            }
        }

        var cursor = 0
        linkRegex.findAll(text).forEach { match ->
            val beforeText = stripHtml(text.substring(cursor, match.range.first))
            appendTextSegment(beforeText)

            val attrs = match.groupValues[1]
            val linkText = stripHtml(decodeHtml(match.groupValues[2])).trim()
            val rawHref = Regex("\\bhref\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
                .find(attrs)
                ?.groupValues
                ?.getOrNull(2)
                ?.let(::decodeHtml)
                .orEmpty()
            val href = extractUrlFromWeiboRedirect(rawHref)

            val cleanHref = href.removePrefix("https:").removePrefix("http:").trim()
            val isLocation = match.value.contains("location") ||
                             href.contains("location") ||
                             href.contains("230413") ||
                             href.contains("100101") ||
                             linkText.contains("定位") ||
                             (!locationName.isNullOrBlank() && (linkText == locationName || locationName.contains(linkText) || linkText.contains(locationName))) ||
                             (statusPageInfo != null && statusPageInfo.type == "place" && (
                                 (statusPageInfo.page_url != null && cleanHref.contains(statusPageInfo.page_url.removePrefix("https:").removePrefix("http:").trim())) ||
                                 (statusPageInfo.media_info?.h5_url != null && cleanHref.contains(statusPageInfo.media_info.h5_url.removePrefix("https:").removePrefix("http:").trim()))
                             )) ||
                             urlStruct?.any {
                                 val structShort = it.short_url?.removePrefix("https:")?.removePrefix("http:")?.trim() ?: ""
                                 val structLong = it.long_url?.removePrefix("https:")?.removePrefix("http:")?.trim() ?: ""
                                 ((structShort.isNotEmpty() && cleanHref.contains(structShort)) ||
                                  (structLong.isNotEmpty() && cleanHref.contains(structLong))) &&
                                 (it.url_type_pic?.contains("location") == true || it.page_id?.startsWith("100101") == true || it.page_id?.startsWith("230413") == true || it.page_info?.type == "place")
                             } == true

            if (!isLocation && linkText.isNotBlank()) {
                val hasIcon = match.groupValues[2].contains("<img")
                val iconUrl = if (hasIcon) {
                    Regex("<img\\b[^>]*src\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE)
                        .find(match.groupValues[2])
                        ?.groupValues?.get(2)
                } else {
                    null
                }

                if (iconUrl != null) {
                    linkIconList.add(iconUrl)
                    val start = length
                    appendTextSegment("\uFFFD")
                    appendTextSegment(linkText)
                    val end = length
                    val target = if (detailUrl != null && isFullTextLink(linkText, href)) DETAIL_LINK_TARGET else href
                    if (target.isNotBlank()) {
                        addStringAnnotation("URL", target, start, end)
                        addStyle(
                            SpanStyle(color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold),
                            start,
                            end
                        )
                    }
                } else {
                    val start = length
                    appendTextSegment(linkText)
                    val end = length
                    val target = if (detailUrl != null && isFullTextLink(linkText, href)) DETAIL_LINK_TARGET else href
                    if (target.isNotBlank()) {
                        addStringAnnotation("URL", target, start, end)
                        addStyle(
                            SpanStyle(color = Color(0xFF60A5FA), fontWeight = FontWeight.SemiBold),
                            start,
                            end
                        )
                    }
                }
            }
            cursor = match.range.last + 1
        }
        appendTextSegment(stripHtml(text.substring(cursor)))

        val builtText = this.toAnnotatedString().text
        stylePattern(builtText, Regex("#[^#\\n]+#"), Color(0xFFF97316))
        stylePattern(builtText, Regex("@[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+"), Color(0xFF60A5FA))
        Regex("https?://[^\\s]+").findAll(builtText).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            addStringAnnotation("URL", match.value, start, end)
            addStyle(
                SpanStyle(color = Color(0xFF10B981), fontWeight = FontWeight.SemiBold),
                start,
                end
            )
        }
        val trimmedBuilt = builtText.trim()
        if (detailUrl != null && trimmedBuilt.endsWith("全文")) {
            val start = builtText.lastIndexOf("全文")
            if (start != -1) {
                val end = start + 2
                addStringAnnotation("URL", DETAIL_LINK_TARGET, start, end)
                addStyle(
                    SpanStyle(color = Color(0xFFF97316), fontWeight = FontWeight.SemiBold),
                    start,
                    end
                )
            }
        }
    }

    return ParsedWeiboText(
        annotatedString = annotatedString,
        inlineContent = inlineContentMap
    )
}

internal enum class TableAlign {
    LEFT, CENTER, RIGHT
}

internal sealed class MarkdownBlock {
    data class Header(val level: Int, val text: String) : MarkdownBlock()
    data class Blockquote(val lines: List<String>) : MarkdownBlock()
    data class Paragraph(val text: String) : MarkdownBlock()
    data class BulletList(val items: List<String>) : MarkdownBlock()
    data class OrderedList(val items: List<String>) : MarkdownBlock()
    data class CodeBlock(val language: String, val content: String) : MarkdownBlock()
    data class Table(val headers: List<String>, val alignments: List<TableAlign>, val rows: List<List<String>>) : MarkdownBlock()
}

internal fun parseMarkdownBlocks(rawText: String): List<MarkdownBlock> {
    val processed = rawText
        .replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
        .replace("&gt;", ">")
        .replace("&lt;", "<")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")

    val lines = processed.split("\n")
    val blocks = mutableListOf<MarkdownBlock>()

    fun isTableSeparator(lineText: String): Boolean {
        val trimmed = lineText.trim()
        if (!trimmed.startsWith("|")) return false
        val clean = trimmed.replace("|", "").replace("-", "").replace(":", "").replace(" ", "")
        return clean.isEmpty() && trimmed.contains("-")
    }

    var i = 0
    while (i < lines.size) {
        val line = lines[i]

        if (line.trim().isEmpty()) {
            i++
            continue
        }

        if (line.trimStart().startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                val rawLine = lines[i].trimStart()
                val content = if (rawLine.length > 1 && rawLine[1] == ' ') {
                    rawLine.substring(2)
                } else {
                    rawLine.substring(1)
                }
                quoteLines.add(content)
                i++
            }
            blocks.add(MarkdownBlock.Blockquote(quoteLines))
            continue
        }

        if (line.trim().startsWith("```")) {
            val lang = line.trim().substring(3).trim()
            val contentLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                contentLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                i++
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, contentLines.joinToString("\n")))
            continue
        }

        val headerMatch = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line.trim())
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val text = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, text))
            i++
            continue
        }

        val nextLine = lines.getOrNull(i + 1)
        if (line.trim().startsWith("|") && nextLine != null && isTableSeparator(nextLine)) {
            val headerLine = line
            val separatorLine = nextLine

            fun extractTableCells(rowText: String): List<String> {
                val trimmed = rowText.trim()
                val parts = rowText.split("|").map { it.trim() }
                return if (trimmed.startsWith("|")) {
                    val dropLast = if (trimmed.endsWith("|")) 1 else 0
                    parts.drop(1).dropLast(dropLast)
                } else {
                    parts
                }
            }

            val headers = extractTableCells(headerLine)
            val sepCells = extractTableCells(separatorLine)
            val alignments = sepCells.map { cell ->
                val left = cell.startsWith(":")
                val right = cell.endsWith(":")
                when {
                    left && right -> TableAlign.CENTER
                    right -> TableAlign.RIGHT
                    else -> TableAlign.LEFT
                }
            }

            i += 2

            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trim().startsWith("|")) {
                val rowLine = lines[i]
                if (isTableSeparator(rowLine)) {
                    i++
                    continue
                }
                rows.add(extractTableCells(rowLine))
                i++
            }

            blocks.add(MarkdownBlock.Table(headers, alignments, rows))
            continue
        }

        if (line.trim().startsWith("- ") || line.trim().startsWith("* ") || line.trim().startsWith("+ ")) {
            val listItems = mutableListOf<String>()
            while (i < lines.size && (lines[i].trim().startsWith("- ") || lines[i].trim().startsWith("* ") || lines[i].trim().startsWith("+ "))) {
                val itemLine = lines[i].trim()
                listItems.add(itemLine.substring(2))
                i++
            }
            blocks.add(MarkdownBlock.BulletList(listItems))
            continue
        }

        val orderedMatch = Regex("^(\\d+)\\.\\s+(.*)$").matchEntire(line.trim())
        if (orderedMatch != null) {
            val listItems = mutableListOf<String>()
            while (i < lines.size && Regex("^\\d+\\.\\s+(.*)$").matches(lines[i].trim())) {
                val itemLine = lines[i].trim()
                val match = Regex("^\\d+\\.\\s+(.*)$").matchEntire(itemLine)
                if (match != null) {
                    listItems.add(match.groupValues[1])
                }
                i++
            }
            blocks.add(MarkdownBlock.OrderedList(listItems))
            continue
        }

        val paraLines = mutableListOf<String>()
        while (i < lines.size) {
            val curLine = lines[i]
            val curNextLine = lines.getOrNull(i + 1)
            val isTableStart = curLine.trimStart().startsWith("|") && curNextLine != null && isTableSeparator(curNextLine)

            val isOther = curLine.trimStart().startsWith(">") ||
                          curLine.trim().startsWith("```") ||
                          Regex("^(#{1,6})\\s+.*$").matches(curLine.trim()) ||
                          curLine.trim().startsWith("- ") || curLine.trim().startsWith("* ") || curLine.trim().startsWith("+ ") ||
                          Regex("^\\d+\\.\\s+.*$").matches(curLine.trim()) ||
                          isTableStart
            if (isOther || curLine.trim().isEmpty()) {
                break
            }
            paraLines.add(curLine)
            i++
        }
        blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString("\n")))
    }

    return blocks
}

internal fun AnnotatedString.Builder.appendMarkdownStyledText(text: String) {
    var i = 0
    val length = text.length
    while (i < length) {
        if (text[i] == '`') {
            val endIdx = text.indexOf('`', i + 1)
            if (endIdx != -1) {
                withStyle(SpanStyle(
                    fontFamily = FontFamily.Monospace,
                    background = Color(0x22FFFFFF),
                    color = Color(0xFF34D399)
                )) {
                    append(text.substring(i + 1, endIdx))
                }
                i = endIdx + 1
                continue
            }
        }

        if (i + 1 < length && text[i] == '*' && text[i + 1] == '*') {
            val endIdx = text.indexOf("**", i + 2)
            if (endIdx != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendMarkdownStyledText(text.substring(i + 2, endIdx))
                }
                i = endIdx + 2
                continue
            }
        }
        if (i + 1 < length && text[i] == '_' && text[i + 1] == '_') {
            val endIdx = text.indexOf("__", i + 2)
            if (endIdx != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    appendMarkdownStyledText(text.substring(i + 2, endIdx))
                }
                i = endIdx + 2
                continue
            }
        }

        if (text[i] == '*') {
            val endIdx = text.indexOf('*', i + 1)
            if (endIdx != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendMarkdownStyledText(text.substring(i + 1, endIdx))
                }
                i = endIdx + 1
                continue
            }
        }
        if (text[i] == '_') {
            val endIdx = text.indexOf('_', i + 1)
            if (endIdx != -1) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    appendMarkdownStyledText(text.substring(i + 1, endIdx))
                }
                i = endIdx + 1
                continue
            }
        }

        append(text[i].toString())
        i++
    }
}

@Composable
internal fun WeiboMarkdownRenderer(
    text: String,
    detailUrl: String?,
    urlStruct: List<WeiboUrlStruct>?,
    locationName: String?,
    onImageClick: ((Int, List<String>) -> Unit)?,
    onWebClick: ((String) -> Unit)?,
    onDetailClick: () -> Unit,
    fontSize: Int,
    lineHeight: Int,
    statusPageInfo: WeiboTimelinePageInfo? = null
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Header -> {
                    val headerSize = when (block.level) {
                        1 -> fontSize + 4
                        2 -> fontSize + 3
                        3 -> fontSize + 2
                        else -> fontSize + 1
                    }
                    val parsed = remember(block.text, detailUrl, urlStruct, locationName, statusPageInfo) {
                        parseWeiboHtmlText(block.text, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                    }
                    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                        text = parsed.annotatedString,
                        style = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = headerSize.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = (headerSize * 1.4f).sp
                        ),
                        inlineContent = parsed.inlineContent,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier.pointerInput(parsed.annotatedString, detailUrl) {
                            detectLinkTaps(
                                annotatedString = parsed.annotatedString,
                                layoutResult = layoutResult,
                                detailUrl = detailUrl,
                                uriHandler = uriHandler,
                                onDetailClick = onDetailClick,
                                onImageClick = onImageClick,
                                onWebClick = onWebClick
                            )
                        }
                    )
                }
                is MarkdownBlock.Blockquote -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x0AFFFFFF), RoundedCornerShape(4.dp))
                            .padding(vertical = 4.dp, horizontal = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(IntrinsicSize.Max)
                                .background(Color(0xFF94A3B8))
                                .align(Alignment.CenterVertically)
                        )
                        Spacer(modifier = Modifier.width(8.dp))

                        val quoteText = block.lines.joinToString("\n")
                        val parsed = remember(quoteText, detailUrl, urlStruct, locationName, statusPageInfo) {
                            parseWeiboHtmlText(quoteText, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                        }
                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        Text(
                            text = parsed.annotatedString,
                            style = LocalTextStyle.current.copy(
                                color = Color(0xFFCBD5E1),
                                fontSize = fontSize.sp,
                                lineHeight = lineHeight.sp,
                                fontStyle = FontStyle.Italic
                            ),
                            inlineContent = parsed.inlineContent,
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(parsed.annotatedString, detailUrl) {
                                    detectLinkTaps(
                                        annotatedString = parsed.annotatedString,
                                        layoutResult = layoutResult,
                                        detailUrl = detailUrl,
                                        uriHandler = uriHandler,
                                        onDetailClick = onDetailClick,
                                        onImageClick = onImageClick,
                                        onWebClick = onWebClick
                                    )
                                }
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    val parsed = remember(block.text, detailUrl, urlStruct, locationName, statusPageInfo) {
                        parseWeiboHtmlText(block.text, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                    }
                    var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                    Text(
                        text = parsed.annotatedString,
                        style = LocalTextStyle.current.copy(
                            color = Color.White,
                            fontSize = fontSize.sp,
                            lineHeight = lineHeight.sp
                        ),
                        inlineContent = parsed.inlineContent,
                        onTextLayout = { layoutResult = it },
                        modifier = Modifier.pointerInput(parsed.annotatedString, detailUrl) {
                            detectLinkTaps(
                                annotatedString = parsed.annotatedString,
                                layoutResult = layoutResult,
                                detailUrl = detailUrl,
                                uriHandler = uriHandler,
                                onDetailClick = onDetailClick,
                                onImageClick = onImageClick,
                                onWebClick = onWebClick
                            )
                        }
                    )
                }
                is MarkdownBlock.BulletList -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.items.forEach { item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "•",
                                    color = Color(0xFF94A3B8),
                                    fontSize = fontSize.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                val parsed = remember(item, detailUrl, urlStruct, locationName, statusPageInfo) {
                                    parseWeiboHtmlText(item, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                                }
                                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                Text(
                                    text = parsed.annotatedString,
                                    style = LocalTextStyle.current.copy(
                                        color = Color.White,
                                        fontSize = fontSize.sp,
                                        lineHeight = lineHeight.sp
                                    ),
                                    inlineContent = parsed.inlineContent,
                                    onTextLayout = { layoutResult = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .pointerInput(parsed.annotatedString, detailUrl) {
                                            detectLinkTaps(
                                                annotatedString = parsed.annotatedString,
                                                layoutResult = layoutResult,
                                                detailUrl = detailUrl,
                                                uriHandler = uriHandler,
                                                onDetailClick = onDetailClick,
                                                onImageClick = onImageClick,
                                                onWebClick = onWebClick
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.OrderedList -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        block.items.forEachIndexed { index, item ->
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    text = "${index + 1}.",
                                    color = Color(0xFF94A3B8),
                                    fontSize = fontSize.sp,
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                val parsed = remember(item, detailUrl, urlStruct, locationName, statusPageInfo) {
                                    parseWeiboHtmlText(item, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                                }
                                var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                                Text(
                                    text = parsed.annotatedString,
                                    style = LocalTextStyle.current.copy(
                                        color = Color.White,
                                        fontSize = fontSize.sp,
                                        lineHeight = lineHeight.sp
                                    ),
                                    inlineContent = parsed.inlineContent,
                                    onTextLayout = { layoutResult = it },
                                    modifier = Modifier
                                        .weight(1f)
                                        .pointerInput(parsed.annotatedString, detailUrl) {
                                            detectLinkTaps(
                                                annotatedString = parsed.annotatedString,
                                                layoutResult = layoutResult,
                                                detailUrl = detailUrl,
                                                uriHandler = uriHandler,
                                                onDetailClick = onDetailClick,
                                                onImageClick = onImageClick,
                                                onWebClick = onWebClick
                                            )
                                        }
                                )
                            }
                        }
                    }
                }
                is MarkdownBlock.CodeBlock -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0x1F000000), RoundedCornerShape(6.dp))
                            .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
                            .padding(8.dp)
                    ) {
                        if (block.language.isNotBlank()) {
                            Text(
                                text = block.language,
                                color = Color(0xFF10B981),
                                fontSize = (fontSize - 3).sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                        Text(
                            text = block.content,
                            style = LocalTextStyle.current.copy(
                                color = Color(0xFF34D399),
                                fontFamily = FontFamily.Monospace,
                                fontSize = (fontSize - 1).sp,
                                lineHeight = (lineHeight - 2).sp
                            )
                        )
                    }
                }
                is MarkdownBlock.Table -> {
                    WeiboTableRenderer(
                        headers = block.headers,
                        alignments = block.alignments,
                        rows = block.rows,
                        detailUrl = detailUrl,
                        urlStruct = urlStruct,
                        locationName = locationName,
                        onImageClick = onImageClick,
                        onWebClick = onWebClick,
                        onDetailClick = onDetailClick,
                        fontSize = fontSize,
                        lineHeight = lineHeight,
                        statusPageInfo = statusPageInfo
                    )
                }
            }
        }
    }
}

@Composable
internal fun WeiboTableRenderer(
    headers: List<String>,
    alignments: List<TableAlign>,
    rows: List<List<String>>,
    detailUrl: String?,
    urlStruct: List<WeiboUrlStruct>?,
    locationName: String?,
    onImageClick: ((Int, List<String>) -> Unit)?,
    onWebClick: ((String) -> Unit)?,
    onDetailClick: () -> Unit,
    fontSize: Int,
    lineHeight: Int,
    statusPageInfo: WeiboTimelinePageInfo? = null
) {
    val scrollState = rememberScrollState()
    val columnCount = headers.size

    val columnWidths = remember(headers, rows) {
        List(columnCount) { col ->
            var maxLen = headers.getOrNull(col)?.length ?: 0
            for (row in rows) {
                val cellText = row.getOrNull(col) ?: ""
                if (cellText.length > maxLen) {
                    maxLen = cellText.length
                }
            }
            (maxLen * 8 + 36).coerceIn(90, 240).dp
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x05FFFFFF), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0x1FFFFFFF), RoundedCornerShape(8.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0x15FFFFFF), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                    .border(0.5.dp, Color(0x33FFFFFF), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
            ) {
                headers.forEachIndexed { colIndex, headerText ->
                    val alignment = alignments.getOrNull(colIndex) ?: TableAlign.LEFT
                    Box(
                        modifier = Modifier
                            .width(columnWidths[colIndex])
                            .padding(8.dp),
                        contentAlignment = when (alignment) {
                            TableAlign.LEFT -> Alignment.CenterStart
                            TableAlign.CENTER -> Alignment.Center
                            TableAlign.RIGHT -> Alignment.CenterEnd
                        }
                    ) {
                        val parsed = remember(headerText, detailUrl, urlStruct, locationName, statusPageInfo) {
                            parseWeiboHtmlText(headerText, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                        }
                        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                        val uriHandler = LocalUriHandler.current
                        Text(
                            text = parsed.annotatedString,
                            style = LocalTextStyle.current.copy(
                                color = TextWhite,
                                fontSize = (fontSize - 1).sp,
                                fontWeight = FontWeight.Bold,
                                lineHeight = (lineHeight - 2).sp
                            ),
                            inlineContent = parsed.inlineContent,
                            onTextLayout = { layoutResult = it },
                            modifier = Modifier.pointerInput(parsed.annotatedString, detailUrl) {
                                detectLinkTaps(
                                    annotatedString = parsed.annotatedString,
                                    layoutResult = layoutResult,
                                    detailUrl = detailUrl,
                                    uriHandler = uriHandler,
                                    onDetailClick = onDetailClick,
                                    onImageClick = onImageClick,
                                    onWebClick = onWebClick
                                )
                            }
                        )
                    }
                }
            }

            rows.forEachIndexed { rowIndex, rowCells ->
                val bg = if (rowIndex % 2 == 0) Color(0x05FFFFFF) else Color(0x0DFFFFFF)
                Row(
                    modifier = Modifier
                        .background(bg)
                        .border(0.5.dp, Color(0x15FFFFFF))
                ) {
                    for (colIndex in 0 until columnCount) {
                        val cellText = rowCells.getOrNull(colIndex) ?: ""
                        val alignment = alignments.getOrNull(colIndex) ?: TableAlign.LEFT
                        Box(
                            modifier = Modifier
                                .width(columnWidths[colIndex])
                                .padding(8.dp),
                            contentAlignment = when (alignment) {
                                TableAlign.LEFT -> Alignment.CenterStart
                                TableAlign.CENTER -> Alignment.Center
                                TableAlign.RIGHT -> Alignment.CenterEnd
                            }
                        ) {
                            val parsed = remember(cellText, detailUrl, urlStruct, locationName, statusPageInfo) {
                                parseWeiboHtmlText(cellText, detailUrl, urlStruct, locationName, isMarkdown = true, statusPageInfo = statusPageInfo)
                            }
                            var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
                            val uriHandler = LocalUriHandler.current
                            Text(
                                text = parsed.annotatedString,
                                style = LocalTextStyle.current.copy(
                                    color = Color(0xFFE2E8F0),
                                    fontSize = (fontSize - 1).sp,
                                    lineHeight = (lineHeight - 2).sp
                                ),
                                inlineContent = parsed.inlineContent,
                                onTextLayout = { layoutResult = it },
                                modifier = Modifier.pointerInput(parsed.annotatedString, detailUrl) {
                                    detectLinkTaps(
                                        annotatedString = parsed.annotatedString,
                                        layoutResult = layoutResult,
                                        detailUrl = detailUrl,
                                        uriHandler = uriHandler,
                                        onDetailClick = onDetailClick,
                                        onImageClick = onImageClick,
                                        onWebClick = onWebClick
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

internal suspend fun PointerInputScope.detectLinkTaps(
    annotatedString: AnnotatedString,
    layoutResult: TextLayoutResult?,
    detailUrl: String?,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onDetailClick: () -> Unit,
    onImageClick: ((Int, List<String>) -> Unit)?,
    onWebClick: ((String) -> Unit)?
) {
    detectTapGestures(onTap = { pos ->
        layoutResult?.let { layout ->
            val offset = layout.getOffsetForPosition(pos)
            annotatedString
                .getStringAnnotations(tag = "URL", start = offset, end = offset)
                .firstOrNull()
                ?.item
                ?.let { target ->
                    if (target == DETAIL_LINK_TARGET) {
                        onDetailClick()
                    } else {
                        val imageUrl = extractImageUrlFromWeiboUrl(target)
                        if (imageUrl != null && onImageClick != null) {
                            onImageClick(0, listOf(imageUrl))
                        } else {
                            if (onWebClick != null) {
                                handleWeiboLinkClick(target, uriHandler, onWebClick)
                            } else {
                                runCatching { uriHandler.openUri(normalizeWeiboUrl(target)) }
                            }
                        }
                    }
                }
                ?: onDetailClick()
        }
    })
}

data class WeiboRepostChainItem(
    val username: String,
    val htmlText: String
)

fun parseWeiboRepostChain(html: String): Pair<String, List<WeiboRepostChainItem>> {
    if (html.isBlank()) return Pair("", emptyList())
    // Match intermediate repost tags in HTML like:
    // //<a href="/n/Username">@Username</a>:comment
    // or //@<a href="/n/Username">@Username</a>:comment
    // or plain text like:
    // //@Username:comment
    val regex = Regex("//\\s*@?\\s*(?:<a\\b[^>]*>@?([^<]+)</a>|@?([^:：/<]+))\\s*[:：]", RegexOption.IGNORE_CASE)
    val matches = regex.findAll(html).toList()
    if (matches.isEmpty()) {
        return Pair(html, emptyList())
    }
    
    val mainContent = html.substring(0, matches.first().range.first).trim()
    val chain = mutableListOf<WeiboRepostChainItem>()
    
    for (i in matches.indices) {
        val match = matches[i]
        val rawUsername = match.groupValues[1].takeIf { it.isNotEmpty() } 
            ?: match.groupValues[2]
        val username = rawUsername.replace(Regex("[\\r\\n\\s]+"), "")
        
        val start = match.range.last + 1
        val end = if (i + 1 < matches.size) matches[i + 1].range.first else html.length
        val itemText = html.substring(start, end).trim()
        
        chain.add(WeiboRepostChainItem(username, itemText))
    }
    
    return Pair(mainContent, chain)
}
