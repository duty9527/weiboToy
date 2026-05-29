package com.example.weibochat.ui.weibo.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.weibochat.data.WeiboTimelinePageInfo
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.data.WeiboUrlStruct
import com.example.weibochat.data.WeiboComment
import com.example.weibochat.theme.BubbleBg
import com.example.weibochat.theme.DividerGrey
import com.example.weibochat.theme.TextGrey
import com.example.weibochat.theme.TextWhite
import android.net.Uri

@Composable
fun WeiboCard(
    status: WeiboTimelineStatus,
    isRead: Boolean = false,
    comments: List<WeiboComment> = emptyList(),
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit,
    onOpenDetail: (WeiboTimelineStatus) -> Unit,
    onLikeClick: () -> Unit
) {
    val contentStatus = status
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .clickable {
                onOpenDetail(contentStatus)
            },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0C)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, Color(0xFF2E2E2E))
    ) {
        WeiboCardContent(
            status = status,
            comments = comments,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            onWebClick = onWebClick,
            onOpenDetail = onOpenDetail,
            onLikeClick = onLikeClick
        )
    }
}

@Composable
fun WeiboCardContent(
    status: WeiboTimelineStatus,
    comments: List<WeiboComment>,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit,
    onOpenDetail: (WeiboTimelineStatus) -> Unit,
    onLikeClick: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val contentStatus = status
    val user = status.user
    val screenName = user?.screen_name ?: "新浪用户"
    val avatarUrl = user?.profile_image_url
    val createdAt = status.created_at ?: ""
    val sourceClean = status.source?.replace(Regex("<[^>]*>"), "") ?: ""
    val detailUrl = statusDetailUrl(contentStatus)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(42.dp)) {
                AsyncImage(
                    model = avatarUrl ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
                    contentDescription = screenName,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .border(1.dp, Color(0x33FFFFFF), CircleShape),
                    contentScale = ContentScale.Crop
                )

                if (screenName.contains("科技") || screenName.contains("人民网") || screenName.contains("爱范儿") || screenName.contains("公园")) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .background(Color(0xFFF97316), CircleShape)
                            .border(1.dp, BubbleBg, CircleShape)
                            .align(Alignment.BottomEnd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "V",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))
            val locationName = remember(contentStatus) { getStatusLocation(contentStatus) }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = screenName,
                        color = if (screenName.contains("科技") || screenName.contains("人民网") || screenName.contains("爱范儿") || screenName.contains("公园")) Color(0xFFF97316) else TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    val timeText = extractTimeCapsule(createdAt)
                    if (timeText.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        WeiboTimeBadge(timeText = timeText)
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                val subtitleText = remember(sourceClean, locationName) {
                    buildString {
                        if (sourceClean.isNotBlank()) {
                            append(sourceClean)
                        }
                        if (!locationName.isNullOrBlank()) {
                            if (isNotEmpty()) append("  ")
                            append("发布于 ")
                            append(locationName)
                        }
                    }
                }
                if (subtitleText.isNotBlank()) {
                    Text(
                        text = subtitleText,
                        color = TextGrey,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(
                onClick = {},
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MoreHoriz,
                    contentDescription = "更多",
                    tint = TextGrey,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        val rawHtml = contentStatus.text ?: contentStatus.text_raw ?: contentStatus.raw_text ?: ""
        val combinedRepostChain = remember(rawHtml, contentStatus.retweeted_status) {
            val (mainHtml, statusChain) = parseWeiboRepostChain(rawHtml)
            val retweet = contentStatus.retweeted_status
            if (retweet != null) {
                val retweetText = retweet.text ?: retweet.text_raw ?: retweet.raw_text ?: ""
                val (retweetMain, retweetChainList) = parseWeiboRepostChain(retweetText)
                
                val list = mutableListOf<WeiboRepostChainItem>()
                list.addAll(statusChain)
                if (retweetChainList.isNotEmpty()) {
                    if (retweetMain.isNotBlank()) {
                        list.add(WeiboRepostChainItem(
                            username = retweet.user?.screen_name ?: "原作者",
                            htmlText = retweetMain
                        ))
                    }
                    for (i in 0 until retweetChainList.size - 1) {
                        list.add(retweetChainList[i])
                    }
                }
                Pair(mainHtml, list)
            } else {
                Pair(mainHtml, statusChain)
            }
        }
        val mainHtml = combinedRepostChain.first
        val repostChain = combinedRepostChain.second.filter { it.htmlText.isNotBlank() }
        val locationName = remember(contentStatus) { getStatusLocation(contentStatus) }
        if (mainHtml.isNotBlank()) {
            WeiboRichText(
                html = mainHtml,
                fontSize = 15,
                lineHeight = 22,
                detailUrl = detailUrl,
                urlStruct = contentStatus.url_struct,
                locationName = locationName,
                isMarkdown = contentStatus.md_render_mark == 1 || contentStatus.md_render_mark == 2 || contentStatus.style_config?.md_render_mark == 1 || contentStatus.style_config?.md_render_mark == 2,
                onImageClick = onImageClick,
                onWebClick = onWebClick,
                onDetailClick = { onOpenDetail(contentStatus) },
                statusPageInfo = contentStatus.page_info
            )
        }

        if (repostChain.isNotEmpty()) {
            if (mainHtml.isNotBlank()) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            NestedRepostChain(
                chainItems = repostChain,
                index = 0,
                onImageClick = onImageClick,
                onWebClick = onWebClick
            )
        }

        val isRetweet = contentStatus.retweeted_status != null
        WeiboMediaAndLinksSection(
            status = contentStatus,
            isRetweet = isRetweet,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            onWebClick = onWebClick
        )

        contentStatus.retweeted_status?.let { retweet ->
            Spacer(modifier = Modifier.height(10.dp))
            RetweetedBlock(
                retweet = retweet,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onWebClick = onWebClick,
                onOpenDetail = onOpenDetail
            )
        }

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = DividerGrey, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            ActionButton(
                icon = Icons.Outlined.Share,
                label = formatCount(status.reposts_count ?: 0),
                onClick = {}
            )
            ActionButton(
                icon = Icons.Outlined.ChatBubbleOutline,
                label = formatCount(status.comments_count ?: 0),
                onClick = {}
            )

            val isLiked = status.liked == true
            val likeCount = status.attitudes_count ?: 0
            val scale by animateFloatAsState(
                targetValue = if (isLiked) 1.3f else 1.0f,
                animationSpec = tween(durationMillis = 200),
                label = "likeScale"
            )

            Row(
                modifier = Modifier
                    .clickable {
                        onLikeClick()
                    }
                    .padding(8.dp)
                    .scale(if (isLiked) scale else 1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "点赞",
                    tint = if (isLiked) Color(0xFFEF4444) else TextGrey,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = formatCount(likeCount),
                    color = if (isLiked) Color(0xFFEF4444) else TextGrey,
                    fontSize = 12.sp
                )
            }
        }
    }
}

@Composable
fun WeiboGapCard(
    gapStatus: WeiboTimelineStatus,
    isLoading: Boolean,
    onFillGap: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isLoading) { onFillGap() },
        colors = CardDefaults.cardColors(containerColor = BubbleBg),
        shape = RoundedCornerShape(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 18.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (isLoading) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color(0xFFF97316)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "正在安全获取断档微博，请稍候...",
                        color = TextGrey,
                        fontSize = 13.sp
                    )
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "填充断档",
                        tint = Color(0xFFF97316),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "此处有未加载的微博，点击安全补齐",
                        color = Color(0xFFF97316),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun RetweetedBlock(
    retweet: WeiboTimelineStatus,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit,
    onOpenDetail: (WeiboTimelineStatus) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val screenName = retweet.user?.screen_name ?: "原作者"
    val avatarUrl = retweet.user?.profile_image_url
    val createdAt = retweet.created_at ?: ""
    val sourceClean = retweet.source?.replace(Regex("<[^>]*>"), "") ?: ""
    val locationName = remember(retweet) { getStatusLocation(retweet) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0x0FFFFFFF), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0x15FFFFFF), RoundedCornerShape(8.dp))
            .clickable {
                onOpenDetail(retweet)
            }
            .padding(10.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = avatarUrl ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
                    contentDescription = screenName,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .border(0.5.dp, Color(0x22FFFFFF), CircleShape),
                    contentScale = ContentScale.Crop
                )

                Spacer(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = screenName,
                            color = TextWhite,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val timeText = extractTimeCapsule(createdAt)
                        if (timeText.isNotBlank()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            WeiboTimeBadge(timeText = timeText, compact = true)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val subtitleText = remember(sourceClean, locationName) {
                        buildString {
                            if (sourceClean.isNotBlank()) {
                                append(sourceClean)
                            }
                            if (!locationName.isNullOrBlank()) {
                                if (isNotEmpty()) append("  ")
                                  append("发布于 ")
                                  append(locationName)
                            }
                        }
                    }
                    if (subtitleText.isNotBlank()) {
                        Text(
                            text = subtitleText,
                            color = TextGrey,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            val cardText = remember(retweet) {
                val retweetText = retweet.text ?: retweet.text_raw ?: retweet.raw_text ?: ""
                val (_, retweetChainList) = parseWeiboRepostChain(retweetText)
                if (retweetChainList.isNotEmpty()) {
                    retweetChainList.last().htmlText
                } else {
                    retweetText
                }
            }
            if (cardText.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                val detailUrl = statusDetailUrl(retweet)
                val locationName = remember(retweet) { getStatusLocation(retweet) }
                WeiboRichText(
                    html = cardText,
                    fontSize = 14,
                    lineHeight = 20,
                    detailUrl = detailUrl,
                    urlStruct = retweet.url_struct,
                    locationName = locationName,
                    isMarkdown = retweet.md_render_mark == 1 || retweet.md_render_mark == 2 || retweet.style_config?.md_render_mark == 1 || retweet.style_config?.md_render_mark == 2,
                    onImageClick = onImageClick,
                    onWebClick = onWebClick,
                    onDetailClick = { onOpenDetail(retweet) },
                    statusPageInfo = retweet.page_info
                )
            }

            val retweetMedia = remember(retweet) { getStatusMedia(retweet) }
            if (retweetMedia.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WeiboMediaGrid(
                    mediaItems = retweetMedia,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick
                )
            }

            val pageInfo = getStatusPageInfo(retweet)
            val isVideoPage = pageInfo?.type == "video" || pageInfo?.media_info != null || pageInfo?.object_type == "video"
            if (pageInfo != null && !isVideoPage && retweetMedia.isEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WeiboPageInfoCard(
                    pageInfo = pageInfo,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onWebClick = onWebClick
                )
            }
        }
    }
}

@Composable
private fun WeiboTimeBadge(
    timeText: String,
    compact: Boolean = false
) {
    val iconSize = if (compact) 10.dp else 12.dp
    val fontSize = if (compact) 9.sp else 11.sp
    val horizontalPadding = if (compact) 5.dp else 7.dp
    val verticalPadding = if (compact) 1.dp else 2.dp

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color(0x1AF97316))
            .border(0.5.dp, Color(0x33F97316), RoundedCornerShape(999.dp))
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.Schedule,
            contentDescription = null,
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(iconSize)
        )
        Spacer(modifier = Modifier.width(if (compact) 2.dp else 3.dp))
        Text(
            text = timeText,
            color = Color(0xFFE5E7EB),
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun WeiboDetailButton(
    label: String,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFF97316),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun WeiboRichText(
    html: String,
    fontSize: Int,
    lineHeight: Int,
    detailUrl: String?,
    urlStruct: List<WeiboUrlStruct>? = null,
    locationName: String? = null,
    isMarkdown: Boolean = false,
    onImageClick: ((Int, List<String>) -> Unit)? = null,
    onWebClick: ((String) -> Unit)? = null,
    onDetailClick: () -> Unit,
    statusPageInfo: WeiboTimelinePageInfo? = null
) {
    if (isMarkdown) {
        WeiboMarkdownRenderer(
            text = html,
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
    } else {
        val uriHandler = LocalUriHandler.current
        val parsed = remember(html, detailUrl, urlStruct, locationName, statusPageInfo) {
            parseWeiboHtmlText(html, detailUrl, urlStruct, locationName, isMarkdown = false, statusPageInfo = statusPageInfo)
        }

        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }

        Text(
            text = parsed.annotatedString,
            style = LocalTextStyle.current.copy(
                color = TextWhite,
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
}

@Composable
fun ActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TextGrey,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            color = TextGrey,
            fontSize = 12.sp
        )
    }
}

@Composable
internal fun WeiboMediaAndLinksSection(
    status: WeiboTimelineStatus,
    isRetweet: Boolean,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current

    val media = remember(status) { getStatusMedia(status) }
    if (media.isNotEmpty()) {
        Spacer(modifier = Modifier.height(10.dp))
        WeiboMediaGrid(
            mediaItems = media,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick
        )
    }

    if (isRetweet) {
        val linkItems = remember(status) {
            val items = mutableListOf<MediaItemInfo>()

            status.url_struct?.forEach { urlObj ->
                val url = urlObj.short_url ?: urlObj.long_url ?: urlObj.ori_url ?: ""
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
                val isTop = urlObj.url_type_pic?.contains("huati") == true ||
                            (urlObj.url_title?.startsWith("#") == true && urlObj.url_title.endsWith("#"))
                if (!isLoc && !isTop && url.isNotBlank()) {
                    if (items.none { it.url == url || (urlObj.long_url != null && it.url == urlObj.long_url) }) {
                        val url_title = urlObj.url_title ?: ""
                        val isImage = url_title.contains("图片") || url_title.contains("评论配图") || url_title.contains("图") ||
                                      urlObj.url_type_pic?.contains("photo") == true || urlObj.url_type_pic?.contains("pic") == true || urlObj.url_type_pic?.contains("image") == true
                        val isVideo = urlObj.page_info?.type == "video" || urlObj.page_info?.media_info != null ||
                                      url_title.contains("视频") || url_title.contains("直播")
                        items.add(MediaItemInfo(
                            url = url,
                            title = url_title,
                            isImage = isImage,
                            isVideo = isVideo,
                            pageInfo = urlObj.page_info
                        ))
                    }
                }
            }
            items
        }

        linkItems.forEach { item ->
            val isWeiboStatus = remember(item.url) { extractWeiboStatusIdFromUrl(item.url) != null }
            val displayIcon = when {
                item.isImage -> Icons.Default.Image
                isWeiboStatus -> Icons.Default.Description
                else -> Icons.Default.Link
            }
            val displayText = when {
                item.isImage -> "查看图片"
                isWeiboStatus -> "查看微博"
                else -> "查看链接"
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { handleWeiboLinkClick(item.url, uriHandler, onWebClick) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Icon(
                    imageVector = displayIcon,
                    contentDescription = displayText,
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = displayText,
                    color = Color(0xFF60A5FA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        val pageInfo = remember(status) { getStatusPageInfo(status) }
        val isVideoPage = pageInfo?.type == "video" || pageInfo?.media_info != null || pageInfo?.object_type == "video"
        if (pageInfo != null && !isVideoPage && media.isEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            WeiboPageInfoCard(
                pageInfo = pageInfo,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onWebClick = onWebClick
            )
        }
    }
}

internal fun extractWeiboStatusIdFromUrl(url: String): String? {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (!host.contains("weibo.com") && !host.contains("weibo.cn")) return null

    val pathSegments = uri.pathSegments ?: return null
    if (pathSegments.isEmpty()) return null

    val statusIdx = pathSegments.indexOf("status")
    if (statusIdx != -1 && statusIdx + 1 < pathSegments.size) {
        return pathSegments[statusIdx + 1]
    }
    val detailIdx = pathSegments.indexOf("detail")
    if (detailIdx != -1 && detailIdx + 1 < pathSegments.size) {
        return pathSegments[detailIdx + 1]
    }

    if (pathSegments.size >= 2) {
        val first = pathSegments[0]
        val second = pathSegments[1]
        if (first.all { it.isDigit() } && second.length >= 8) {
            return second
        }
    }

    for (segment in pathSegments) {
        if (segment.length >= 15 && segment.all { it.isDigit() }) {
            return segment
        }
        if (segment.length in 8..10 && segment.any { it.isLetter() } && segment.any { it.isDigit() }) {
            return segment
        }
    }

    return null
}

@Composable
fun NestedRepostChain(
    chainItems: List<WeiboRepostChainItem>,
    index: Int,
    onImageClick: (Int, List<String>) -> Unit,
    onWebClick: (String) -> Unit
) {
    if (index >= chainItems.size) return
    val visibleChainItems = chainItems.drop(index).filter { it.htmlText.isNotBlank() }
    if (visibleChainItems.isEmpty()) return

    val uriHandler = LocalUriHandler.current

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (item in visibleChainItems) {
            val parsed = remember(item.htmlText) {
                parseWeiboHtmlText(item.htmlText, isMarkdown = false)
            }
            val annotatedText = remember(item, parsed) {
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TextWhite, fontWeight = FontWeight.Bold)) {
                        append("@${item.username}: ")
                    }
                    append(parsed.annotatedString)
                }
            }
            var layoutResult by remember(item.htmlText) { mutableStateOf<TextLayoutResult?>(null) }
            Text(
                text = annotatedText,
                color = TextWhite,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                inlineContent = parsed.inlineContent,
                onTextLayout = { layoutResult = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(
                            color = Color(0xFF333333),
                            start = Offset(0f, 0f),
                            end = Offset(0f, size.height),
                            strokeWidth = 2.dp.toPx()
                        )
                    }
                    .padding(start = 10.dp, top = 3.dp, bottom = 3.dp)
                    .pointerInput(annotatedText) {
                        detectLinkTaps(
                            annotatedString = annotatedText,
                            layoutResult = layoutResult,
                            detailUrl = null,
                            uriHandler = uriHandler,
                            onDetailClick = {},
                            onImageClick = onImageClick,
                            onWebClick = onWebClick
                        )
                    }
            )
        }
    }
}
