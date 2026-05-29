package com.example.weibochat.ui.weibo.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.weibochat.data.WeiboComment
import com.example.weibochat.data.WeiboRepost
import com.example.weibochat.data.WeiboAttitude
import com.example.weibochat.data.WeiboCommentBadge
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.data.WeiboTimelinePageInfo
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Link
import com.example.weibochat.theme.BubbleBg
import com.example.weibochat.theme.DarkBg
import com.example.weibochat.theme.DividerGrey
import com.example.weibochat.theme.TextGrey
import com.example.weibochat.theme.TextWhite
import com.example.weibochat.ui.weibo.WeiboTimelineViewModel
import java.util.Locale

private fun Modifier.commentImageSize(width: Int?, height: Int?): Modifier {
    val ratio = if (width != null && height != null && width > 0 && height > 0) {
        (width.toFloat() / height.toFloat()).coerceIn(0.45f, 2.2f)
    } else {
        1.0f
    }
    return this
        .widthIn(max = 220.dp)
        .heightIn(max = 260.dp)
        .aspectRatio(ratio)
}

@Composable
fun WeiboDetailDialog(
    status: WeiboTimelineStatus,
    viewModel: WeiboTimelineViewModel,
    isLoadingLongText: Boolean,
    onDismiss: () -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit,
    onOpenDetail: (WeiboTimelineStatus) -> Unit
) {
    val statusId = remember(status) { statusId(status) }
    val detailUrl = remember(statusId) { statusId?.let { "https://m.weibo.cn/status/$it" } }
    val context = LocalContext.current
    LaunchedEffect(statusId) {
        if (statusId != null) {
            viewModel.openStatusDetail(statusId)
        }
    }

    val comments by viewModel.detailComments.collectAsState()
    val commentNotice by viewModel.detailCommentNotice.collectAsState()
    val childCommentsState by viewModel.childCommentsUiState.collectAsState()
    val reposts by viewModel.detailReposts.collectAsState()
    val attitudes by viewModel.detailAttitudes.collectAsState()
    val activeTab by viewModel.detailTab.collectAsState()
    val fetchedDetailStatus by viewModel.detailStatus.collectAsState()
    val displayStatus = remember(status, fetchedDetailStatus, statusId) {
        fetchedDetailStatus
            ?.takeIf { statusId(it) == statusId }
            ?: status
    }

    val isCommentsLoading by viewModel.isDetailCommentsLoading.collectAsState()
    val isRepostsLoading by viewModel.isDetailRepostsLoading.collectAsState()
    val isAttitudesLoading by viewModel.isDetailAttitudesLoading.collectAsState()
    val detailListState = rememberLazyListState()

    BackHandler(enabled = true) {
        if (childCommentsState != null) {
            viewModel.closeCommentChildren()
        } else {
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = {
            if (childCommentsState != null) {
                viewModel.closeCommentChildren()
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BubbleBg)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (childCommentsState != null) {
                            viewModel.closeCommentChildren()
                        } else {
                            onDismiss()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = TextWhite
                    )
                }
                Text(
                    text = if (childCommentsState != null) "评论回复" else "微博详情",
                    color = TextWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                if (childCommentsState == null) {
                    IconButton(
                        onClick = {
                            if (detailUrl != null) {
                                openInWeiboApp(context, displayStatus) {
                                    onWebClick(detailUrl)
                                }
                            }
                        },
                        enabled = detailUrl != null
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                            contentDescription = "打开微博 App",
                            tint = if (detailUrl != null) TextWhite else TextGrey
                        )
                    }
                }
            }

            val childState = childCommentsState
            if (childState != null) {
                ChildCommentsPage(
                    state = childState,
                    viewModel = viewModel,
                    onWebClick = onWebClick,
                    onImageClick = onImageClick,
                    modifier = Modifier.weight(1f)
                )
            } else {
                LazyColumn(
                    state = detailListState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    item {
                        if (isLoadingLongText) {
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF97316),
                                trackColor = Color(0x22FFFFFF)
                            )
                        }
                        WeiboDetailContent(
                            status = displayStatus,
                            viewModel = viewModel,
                            onImageClick = onImageClick,
                            onVideoClick = onVideoClick,
                            onWebClick = onWebClick,
                            onOpenDetail = onOpenDetail
                        )
                    }

                    item {
                        DetailTabBar(
                            status = displayStatus,
                            activeTab = activeTab,
                            onTabSelected = { viewModel.selectDetailTab(it) }
                        )
                    }

                    when (activeTab) {
                        WeiboTimelineViewModel.DetailTab.COMMENT -> {
                            if (comments.isEmpty() && isCommentsLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFF97316))
                                    }
                                }
                            } else if (comments.isEmpty()) {
                                item {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            commentNotice?.message ?: "还没有评论，快来抢沙发吧~",
                                            color = TextGrey,
                                            fontSize = 14.sp
                                        )
                                        val actionUrl = commentNotice?.actionUrl
                                        if (!actionUrl.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(12.dp))
                                            TextButton(onClick = { onWebClick(actionUrl) }) {
                                                Text("去验证")
                                            }
                                        }
                                    }
                                }
                            } else {
                                items(comments) { comment ->
                                    CommentItem(
                                        comment = comment,
                                        onWebClick = onWebClick,
                                        onImageClick = onImageClick,
                                        onOpenChildren = { viewModel.openCommentChildren(comment) }
                                    )
                                }

                                if (viewModel.canLoadMoreComments) {
                                    item {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadMoreComments()
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color(0xFFF97316)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        WeiboTimelineViewModel.DetailTab.REPOST -> {
                            if (reposts.isEmpty() && isRepostsLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFF97316))
                                    }
                                }
                            } else if (reposts.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("还没有人转发这篇微博~", color = TextGrey, fontSize = 14.sp)
                                    }
                                }
                            } else {
                                items(reposts) { repost ->
                                    RepostItem(
                                        repost = repost,
                                        onWebClick = onWebClick,
                                        onImageClick = onImageClick
                                    )
                                }

                                if (viewModel.canLoadMoreReposts) {
                                    item {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadMoreReposts()
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color(0xFFF97316)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        WeiboTimelineViewModel.DetailTab.LIKE -> {
                            if (attitudes.isEmpty() && isAttitudesLoading) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(32.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator(color = Color(0xFFF97316))
                                    }
                                }
                            } else if (attitudes.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(48.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("还没有人点赞，快来点赞支持一下吧~", color = TextGrey, fontSize = 14.sp)
                                    }
                                }
                            } else {
                                items(attitudes) { attitude ->
                                    AttitudeItem(attitude = attitude)
                                }

                                if (viewModel.canLoadMoreAttitudes) {
                                    item {
                                        LaunchedEffect(Unit) {
                                            viewModel.loadMoreAttitudes()
                                        }
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(16.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(24.dp),
                                                color = Color(0xFFF97316)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (childCommentsState == null) {
                HorizontalDivider(color = DividerGrey, thickness = 0.5.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BubbleBg)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(36.dp)
                            .background(DarkBg, RoundedCornerShape(18.dp))
                            .clickable { /* write comment */ }
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text("写评论...", color = TextGrey, fontSize = 14.sp)
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    IconButton(onClick = { /* repost */ }) {
                        Icon(
                            imageVector = Icons.Outlined.Share,
                            contentDescription = "转发",
                            tint = TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    val isLiked = displayStatus.liked == true
                    val scale by animateFloatAsState(
                        targetValue = if (isLiked) 1.3f else 1.0f,
                        animationSpec = tween(durationMillis = 200),
                        label = "detailLikeScale"
                    )
                    IconButton(
                        onClick = {
                            if (statusId != null) {
                                viewModel.toggleLikeStatus(statusId)
                            }
                        },
                        modifier = Modifier.scale(if (isLiked) scale else 1f)
                    ) {
                        Icon(
                            imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = "点赞",
                            tint = if (isLiked) Color(0xFFEF4444) else TextWhite,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DetailTabBar(
    status: WeiboTimelineStatus,
    activeTab: WeiboTimelineViewModel.DetailTab,
    onTabSelected: (WeiboTimelineViewModel.DetailTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BubbleBg)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        data class TabDef(
            val type: WeiboTimelineViewModel.DetailTab,
            val icon: androidx.compose.ui.graphics.vector.ImageVector,
            val count: Int
        )

        val tabs = listOf(
            TabDef(WeiboTimelineViewModel.DetailTab.REPOST, Icons.Outlined.Share, status.reposts_count ?: 0),
            TabDef(WeiboTimelineViewModel.DetailTab.COMMENT, Icons.Outlined.ChatBubbleOutline, status.comments_count ?: 0),
            TabDef(WeiboTimelineViewModel.DetailTab.LIKE, Icons.Outlined.FavoriteBorder, status.attitudes_count ?: 0)
        )

        tabs.forEach { tab ->
            val isSelected = activeTab == tab.type
            val displayCount = remember(tab.count) { formatCount(tab.count) }
            Row(
                modifier = Modifier
                    .clickable { onTabSelected(tab.type) }
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFF97316) else TextGrey,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = displayCount,
                    color = if (isSelected) Color(0xFFF97316) else TextGrey,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun CommentBadges(
    badges: List<WeiboCommentBadge>?,
    fontSize: TextUnit,
    includeAuthorBadges: Boolean = false
) {
    val badgeHeight = with(LocalDensity.current) { fontSize.toDp() * 1.0f }
    badges.orEmpty()
        .filter { includeAuthorBadges || shouldShowCommentBadge(it) }
        .forEach { badge ->
            val picUrl = badge.pic_url ?: return@forEach
            val ratio = badge.length?.takeIf { it > 0.0 } ?: 1.0
            AsyncImage(
                model = picUrl,
                contentDescription = badge.name,
                modifier = Modifier.size(width = (badgeHeight.value * ratio).dp, height = badgeHeight),
                contentScale = ContentScale.Fit
            )
        }
}

private fun shouldShowCommentBadge(badge: WeiboCommentBadge): Boolean {
    val text = listOfNotNull(badge.name, badge.scheme, badge.pic_url)
        .joinToString(" ")
        .lowercase(Locale.ROOT)
    return text.contains("loyal") ||
            text.contains("fans") ||
            text.contains("fan") ||
            text.contains("粉丝") ||
            text.contains("铁粉") ||
            text.contains("comment_author") ||
            text.contains("mblog_author") ||
            text.contains("comment_badge") ||
            text.contains("博主") ||
            text.contains("author")
}

@Composable
fun ChildCommentsPage(
    state: WeiboTimelineViewModel.ChildCommentsUiState,
    viewModel: WeiboTimelineViewModel,
    onWebClick: (String) -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .background(BubbleBg),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        item {
            CommentItem(
                comment = state.root,
                onWebClick = onWebClick,
                onImageClick = onImageClick,
                onOpenChildren = {},
                showReplyEntry = false,
                showPreviewReplies = false
            )
        }

        if (state.isLoading && state.replies.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFF97316))
                }
            }
        } else if (state.replies.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("暂无回复", color = TextGrey, fontSize = 14.sp)
                }
            }
        } else {
            items(state.replies) { reply ->
                ChildCommentItem(
                    reply = reply,
                    onWebClick = onWebClick,
                    onImageClick = onImageClick,
                    modifier = Modifier.padding(start = 22.dp, end = 14.dp)
                )
            }
        }

        if (state.canLoadMore) {
            item {
                LaunchedEffect(state.replies.size) {
                    viewModel.loadMoreCommentChildren()
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = Color(0xFFF97316)
                    )
                }
            }
        }
    }
}

@Composable
fun ChildCommentItem(
    reply: WeiboComment,
    onWebClick: (String) -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
            .padding(vertical = 8.dp)
    ) {
        AsyncImage(
            model = reply.user?.avatar_large ?: reply.user?.profile_image_url ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
            contentDescription = null,
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .border(0.5.dp, Color(0x22FFFFFF), CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = reply.user?.screen_name ?: "新浪用户",
                    color = if (reply.is_mblog_author == true) Color(0xFFF97316) else TextWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                CommentBadges(
                    badges = reply.comment_badge,
                    fontSize = 13.sp,
                    includeAuthorBadges = reply.is_mblog_author == true
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            WeiboRichText(
                html = reply.text ?: "",
                fontSize = 14,
                lineHeight = 19,
                detailUrl = null,
                onImageClick = onImageClick,
                onWebClick = onWebClick,
                onDetailClick = {}
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val metaText = buildString {
                    val timeStr = formatWeiboCreatedAt(reply.created_at ?: "")
                    if (timeStr.isNotBlank()) append(timeStr)
                    val cleanSource = reply.source.orEmpty().trim()
                    if (cleanSource.isNotBlank()) {
                        if (isNotEmpty()) append("  ")
                        append(if (cleanSource.startsWith("来自")) cleanSource else "来自 $cleanSource")
                    }
                }
                Text(metaText, color = TextGrey, fontSize = 11.sp, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Share, contentDescription = "转发", tint = TextGrey, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(22.dp))
                Icon(Icons.Outlined.ChatBubbleOutline, contentDescription = "评论", tint = TextGrey, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(22.dp))
                val totalLikes = reply.like_counts ?: reply.like_count ?: reply.like_num ?: 0
                Icon(Icons.Outlined.FavoriteBorder, contentDescription = "赞", tint = TextGrey, modifier = Modifier.size(17.dp))
                if (totalLikes > 0) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(formatCount(totalLikes), color = TextGrey, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: WeiboComment,
    onWebClick: (String) -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    onOpenChildren: () -> Unit,
    showReplyEntry: Boolean = true,
    showPreviewReplies: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(durationMillis = 180))
            .background(BubbleBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        AsyncImage(
            model = comment.user?.avatar_large ?: comment.user?.profile_image_url ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(0.5.dp, Color(0x22FFFFFF), CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = comment.user?.screen_name ?: "新浪用户",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        CommentBadges(
                            badges = comment.comment_badge,
                            fontSize = 14.sp,
                            includeAuthorBadges = comment.is_mblog_author == true
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    val metaText = buildString {
                        if (comment.floor_number != null && comment.floor_number > 0) {
                            append("${comment.floor_number}楼")
                        }
                        val timeStr = formatWeiboCreatedAt(comment.created_at ?: "")
                        if (timeStr.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(timeStr)
                        }
                        val cleanSource = comment.source.orEmpty().trim()
                        if (cleanSource.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            if (cleanSource.startsWith("来自")) {
                                append(cleanSource)
                            } else {
                                append("来自 $cleanSource")
                            }
                        }
                    }
                    if (metaText.isNotBlank()) {
                        Text(
                            text = metaText,
                            color = TextGrey,
                            fontSize = 10.sp
                        )
                    }
                }

                val totalLikes = comment.like_counts ?: comment.like_count ?: comment.like_num ?: 0
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.FavoriteBorder,
                        contentDescription = "赞",
                        tint = TextGrey,
                        modifier = Modifier.size(13.dp)
                    )
                    if (totalLikes > 0) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = formatCount(totalLikes),
                            color = TextGrey,
                            fontSize = 11.sp
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            WeiboRichText(
                html = comment.text ?: "",
                fontSize = 14,
                lineHeight = 20,
                detailUrl = null,
                onImageClick = onImageClick,
                onWebClick = onWebClick,
                onDetailClick = {}
            )

            val commentPic = comment.pic
            if (commentPic != null) {
                val imageUrl = commentPic.large?.url ?: commentPic.url
                if (!imageUrl.isNullOrBlank()) {
                    val width = commentPic.large?.width ?: commentPic.bmiddle?.width
                    val height = commentPic.large?.height ?: commentPic.bmiddle?.height
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .animateContentSize(animationSpec = tween(durationMillis = 180))
                            .clickable { onImageClick(0, listOf(imageUrl)) }
                    ) {
                        AsyncImage(
                            model = commentPic.url ?: imageUrl,
                            contentDescription = "评论图片",
                            modifier = Modifier.commentImageSize(width, height),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            val replies = comment.comments
            val totalReplies = comment.total_number ?: replies.orEmpty().size
            val previewReplies = replies.orEmpty().take(1)
            if (showPreviewReplies && previewReplies.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .animateContentSize(animationSpec = tween(durationMillis = 180)),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    previewReplies.forEach { reply ->
                        ChildCommentItem(
                            reply = reply,
                            onWebClick = onWebClick,
                            onImageClick = onImageClick
                        )
                    }
                }
            }
            if (showReplyEntry && totalReplies > 0) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "共${totalReplies}条回复  >",
                    color = Color(0xFF93A8C7),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(start = 36.dp)
                        .clickable { onOpenChildren() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerGrey, thickness = 0.5.dp)
        }
    }
}

@Composable
fun RepostItem(
    repost: WeiboRepost,
    onWebClick: (String) -> Unit,
    onImageClick: (Int, List<String>) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BubbleBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        AsyncImage(
            model = repost.user?.avatar_large ?: repost.user?.profile_image_url ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(0.5.dp, Color(0x22FFFFFF), CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = repost.user?.screen_name ?: "新浪用户",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatWeiboCreatedAt(repost.created_at ?: ""),
                color = TextGrey,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(6.dp))
            WeiboRichText(
                html = repost.text ?: "",
                fontSize = 14,
                lineHeight = 20,
                detailUrl = null,
                onImageClick = onImageClick,
                onWebClick = onWebClick,
                onDetailClick = {}
            )
            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = DividerGrey, thickness = 0.5.dp)
        }
    }
}

@Composable
fun AttitudeItem(
    attitude: WeiboAttitude
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BubbleBg)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = attitude.user?.avatar_large ?: attitude.user?.profile_image_url ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .border(0.5.dp, Color(0x22FFFFFF), CircleShape),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = attitude.user?.screen_name ?: "新浪用户",
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = formatWeiboCreatedAt(attitude.created_at ?: ""),
                color = TextGrey,
                fontSize = 10.sp
            )
        }

        Icon(
            imageVector = Icons.Filled.Favorite,
            contentDescription = "赞",
            tint = Color(0xFFEF4444),
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun WeiboDetailContent(
    status: WeiboTimelineStatus,
    viewModel: WeiboTimelineViewModel,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit,
    onOpenDetail: (WeiboTimelineStatus) -> Unit
) {
    val user = status.user
    val screenName = user?.screen_name ?: "新浪用户"
    val avatarUrl = user?.avatar_large ?: user?.profile_image_url
    val sourceClean = status.source?.replace(Regex("<[^>]*>"), "") ?: ""

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(BubbleBg)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = avatarUrl ?: "https://tvax1.sinaimg.cn/crop.0.0.100.100.180/default_avatar.jpg",
                contentDescription = screenName,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color(0x33FFFFFF), CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = screenName,
                    color = TextWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatWeiboCreatedAt(status.created_at ?: ""),
                        color = TextGrey,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                    if (sourceClean.isNotBlank()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = sourceClean,
                            color = TextGrey,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        val rawHtml = status.text ?: status.text_raw ?: status.raw_text ?: ""
        val combinedRepostChain = remember(rawHtml, status.retweeted_status) {
            val (mainHtml, statusChain) = parseWeiboRepostChain(rawHtml)
            val retweet = status.retweeted_status
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
        val locationName = remember(status) { getStatusLocation(status) }
        if (mainHtml.isNotBlank()) {
            WeiboRichText(
                html = mainHtml,
                fontSize = 16,
                lineHeight = 24,
                detailUrl = statusDetailUrl(status),
                urlStruct = status.url_struct,
                locationName = locationName,
                isMarkdown = status.md_render_mark == 1 || status.md_render_mark == 2 || status.style_config?.md_render_mark == 1 || status.style_config?.md_render_mark == 2,
                onImageClick = onImageClick,
                onWebClick = onWebClick,
                onDetailClick = { onOpenDetail(status) },
                statusPageInfo = status.page_info
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

        val isRetweet = status.retweeted_status != null
        WeiboMediaAndLinksSection(
            status = status,
            isRetweet = isRetweet,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            onWebClick = onWebClick
        )

        status.retweeted_status?.let { retweet ->
            Spacer(modifier = Modifier.height(12.dp))
            RetweetedBlock(
                retweet = retweet,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onWebClick = onWebClick,
                onOpenDetail = onOpenDetail
            )
        }

        val location = remember(status) { getStatusLocation(status) }
        if (!location.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "定位",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = location,
                    color = Color(0xFF60A5FA),
                    fontSize = 12.sp
                )
            }
        }
    }
}
