package com.example.weibochat.ui.weibo

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import android.widget.MediaController
import android.widget.VideoView
import java.util.Locale
import kotlin.math.abs
import android.app.DownloadManager
import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculateCentroidSize
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animate
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.WeiboTimelinePageInfo
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.data.WeiboPic
import com.example.weibochat.data.WeiboUrlStruct
import com.example.weibochat.data.WeiboComment
import com.example.weibochat.data.WeiboRepost
import com.example.weibochat.data.WeiboAttitude
import com.example.weibochat.theme.BubbleBg
import com.example.weibochat.theme.DarkBg
import com.example.weibochat.theme.DividerGrey
import com.example.weibochat.theme.TextGrey
import com.example.weibochat.theme.TextWhite
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeiboTimelineScreen(
    viewModel: WeiboTimelineViewModel,
    repository: DataRepository,
    showRoaming: Boolean = false,
    onShowRoamingChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val isLoadingMore by viewModel.isLoadingMore.collectAsState()
    val readStatusIds by viewModel.readStatusIds.collectAsState()
    
    var activePreviewImages by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    var activeVideoUrl by remember { mutableStateOf<String?>(null) }
    var activeWebUrl by remember { mutableStateOf<String?>(null) }
    var activeDetailStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    val unviewedStatuses = remember(uiState, readStatusIds) {
        val statuses = (uiState as? TimelineUiState.Success)?.statuses.orEmpty()
        statuses.filter { status ->
            val id = status.idstr ?: status.id?.toString()
            id == null || id !in readStatusIds
        }
    }
    val coroutineScope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current


    LaunchedEffect(Unit) {
        if (uiState is TimelineUiState.Loading) {
            viewModel.refresh()
        }
    }

    fun openStatusDetail(status: WeiboTimelineStatus) {
        activeDetailStatus = status
        val requestStatusId = statusId(status)
        if (requestStatusId.isNullOrBlank() || !shouldFetchLongText(status)) {
            isDetailLoading = false
            return
        }

        isDetailLoading = true
        coroutineScope.launch {
            val longText = repository.fetchWeiboStatusLongText(requestStatusId)
            if (activeDetailStatus?.let(::statusId) == requestStatusId) {
                if (!longText.isNullOrBlank()) {
                    activeDetailStatus = status.copy(
                        raw_text = longText,
                        text_raw = longText,
                        text = longText,
                        isLongText = false
                    )
                }
                isDetailLoading = false
            }
        }
    }

    fun handleWebClick(url: String) {
        val realUrl = getRealUrlIfWeiboRedirect(url)
        val normalized = normalizeWeiboUrl(realUrl)
        val host = runCatching { Uri.parse(normalized).host }.getOrNull()
        val isWeibo = host?.let { isAllowedWeiboHost(it) } == true

        if (isWeibo) {
            val statusId = extractWeiboStatusIdFromUrl(normalized)
            if (statusId != null) {
                isDetailLoading = true
                coroutineScope.launch {
                    val status = repository.fetchWeiboStatus(statusId)
                    isDetailLoading = false
                    if (status != null) {
                        openStatusDetail(status)
                    } else {
                        activeWebUrl = normalized
                    }
                }
            } else {
                activeWebUrl = normalized
            }
        } else {
            runCatching { uriHandler.openUri(normalized) }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        when (val state = uiState) {
            is TimelineUiState.Loading -> {
                WeiboSkeletonList()
            }
            is TimelineUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        color = Color(0xFFFCA5A5),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Button(
                        onClick = { viewModel.refresh() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316))
                    ) {
                        Text("重试", color = Color.White)
                    }
                }
            }
            is TimelineUiState.Success -> {
                val listState = rememberLazyListState()

                LaunchedEffect(viewModel) {
                    viewModel.scrollToTopEvents.collect {
                        listState.scrollToItem(0)
                    }
                }
                
                // Infinite Scroll / Load More
                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
                    }
                }
                
                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value) {
                        viewModel.loadMore()
                    }
                }

                // Save last viewed position when scroll stops, avoiding stale state and high-frequency writes
                val currentStatuses by rememberUpdatedState(state.statuses)
                LaunchedEffect(listState) {
                    snapshotFlow {
                        if (!listState.isScrollInProgress) {
                            val index = listState.firstVisibleItemIndex
                            val offset = listState.firstVisibleItemScrollOffset
                            val status = currentStatuses.getOrNull(index)
                            val statusId = status?.let { it.idstr ?: it.id?.toString() }
                            if (statusId != null && status.raw_text?.startsWith("__GAP__:") != true) {
                                Triple(statusId, index, offset)
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }.collect { triple ->
                        if (triple != null) {
                            viewModel.saveLastViewedWeibo(triple.first, triple.second, triple.third)
                        }
                    }
                }

                val pullToRefreshState = rememberPullToRefreshState()

                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() },
                    state = pullToRefreshState,
                    modifier = Modifier.fillMaxSize(),
                    indicator = {
                        PullToRefreshDefaults.Indicator(
                            state = pullToRefreshState,
                            isRefreshing = isRefreshing,
                            color = Color(0xFFF97316),
                            containerColor = BubbleBg,
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(state.statuses, key = { it.idstr ?: it.hashCode().toString() }) { status ->
                            val isGap = status.raw_text?.startsWith("__GAP__:") == true
                            if (isGap) {
                                val gapId = status.id ?: 0L
                                val loadingGaps by viewModel.loadingGaps.collectAsState()
                                val isGapLoading = loadingGaps.contains(gapId)
                                WeiboGapCard(
                                    gapStatus = status,
                                    isLoading = isGapLoading,
                                    onFillGap = { viewModel.fillGap(gapId) }
                                )
                            } else {
                                val statusId = status.idstr ?: status.id?.toString()
                                if (statusId != null) {
                                    LaunchedEffect(statusId) {
                                        viewModel.markAsRead(statusId)
                                    }
                                }
                                val isRead = statusId != null && readStatusIds.contains(statusId)
                                WeiboCard(
                                    status = status,
                                    isRead = isRead,
                                    onImageClick = { index, urls -> activePreviewImages = Pair(urls, index) },
                                    onVideoClick = { url -> activeVideoUrl = url },
                                    onWebClick = ::handleWebClick,
                                    onOpenDetail = { openStatusDetail(it) },
                                    onLikeClick = {
                                        val requestStatusId = statusId(status)
                                        if (requestStatusId != null) {
                                            viewModel.toggleLikeStatus(requestStatusId)
                                        }
                                    },

                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        
                        if (isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFFF97316)
                                    )
                                }
                            }
                        } else if (viewModel.canLoadMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 18.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    OutlinedButton(
                                        onClick = { viewModel.loadMore() },
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            contentColor = Color(0xFFF97316)
                                        ),
                                        border = BorderStroke(1.dp, Color(0x66F97316))
                                    ) {
                                        Text("加载更多")
                                    }
                                }
                            }
                        }
                    }

                    // Floating button to return to last viewed position
                    var hasScrolledToLastViewed by remember { mutableStateOf(false) }
                    val lastViewed = remember { viewModel.getLastViewedWeibo() }
                    
                    if (lastViewed != null && !hasScrolledToLastViewed && state.statuses.isNotEmpty() && listState.firstVisibleItemIndex == 0) {
                        val targetIndex = state.statuses.indexOfFirst { 
                            val id = it.idstr ?: it.id?.toString()
                            id == lastViewed.first 
                        }
                        if (targetIndex > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 16.dp),
                                contentAlignment = Alignment.TopCenter
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            listState.scrollToItem(targetIndex, lastViewed.third)
                                            hasScrolledToLastViewed = true
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.ArrowUpward,
                                            contentDescription = "回到上次阅读",
                                            tint = Color.White,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("回到上次阅读位置", color = Color.White, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        activeDetailStatus?.let { status ->
            WeiboDetailDialog(
                status = status,
                viewModel = viewModel,
                isLoadingLongText = isDetailLoading,
                onDismiss = {
                    activeDetailStatus = null
                    isDetailLoading = false
                },
                onImageClick = { index, urls -> activePreviewImages = Pair(urls, index) },
                onVideoClick = { url -> activeVideoUrl = url },
                onWebClick = ::handleWebClick,
                onOpenDetail = { openStatusDetail(it) }
            )
        }

        activePreviewImages?.let { (urls, index) ->
            ImagePreviewDialog(
                imageUrls = urls,
                initialIndex = index,
                onDismiss = { activePreviewImages = null }
            )
        }


        activeVideoUrl?.let { videoUrl ->
            VideoPreviewDialog(
                videoUrl = videoUrl,
                onDismiss = { activeVideoUrl = null }
            )
        }

        activeWebUrl?.let { webUrl ->
            WeiboWebDialog(
                url = webUrl,
                repository = repository,
                onDismiss = { activeWebUrl = null }
            )
        }

        if (showRoaming) {
            Dialog(
                onDismissRequest = { onShowRoamingChange(false) },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false
                )
            ) {
                RandomRoamingOverlay(
                    unviewedStatuses = unviewedStatuses,
                    readStatusIds = readStatusIds,
                    onDismiss = { onShowRoamingChange(false) },
                    onMarkAsRead = { viewModel.markAsRead(it) },
                    onMarkAllAsRead = { viewModel.markAllLoadedAsRead() },
                    onImageClick = { index, urls -> activePreviewImages = Pair(urls, index) },
                    onVideoClick = { url -> activeVideoUrl = url },
                    onWebClick = ::handleWebClick,
                    onOpenDetail = { openStatusDetail(it) }
                )
            }
        }
    }
}

@Composable
fun WeiboSkeletonList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        repeat(3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = BubbleBg),
                shape = RoundedCornerShape(0.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .background(DividerGrey, CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Box(
                                modifier = Modifier
                                    .size(120.dp, 16.dp)
                                    .background(DividerGrey, RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .size(80.dp, 12.dp)
                                    .background(DividerGrey, RoundedCornerShape(3.dp))
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .background(DividerGrey, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(14.dp)
                            .background(DividerGrey, RoundedCornerShape(4.dp))
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .background(DividerGrey, RoundedCornerShape(8.dp))
                    )
                }
            }
        }
    }
}
@Composable
fun WeiboCardContent(
    status: WeiboTimelineStatus,
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
        // Header Row: Avatar, ScreenName, Meta Details
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
                
                // Orange Verified V badge for key accounts or verified users
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

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = screenName,
                    color = if (screenName.contains("科技") || screenName.contains("人民网") || screenName.contains("爱范儿") || screenName.contains("公园")) Color(0xFFF97316) else TextWhite,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatWeiboCreatedAt(createdAt),
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

        Spacer(modifier = Modifier.height(10.dp))

        val locationName = remember(contentStatus) { getStatusLocation(contentStatus) }
        WeiboRichText(
            html = contentStatus.text ?: contentStatus.text_raw ?: contentStatus.raw_text ?: "",
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

        val isRetweet = contentStatus.retweeted_status != null
        WeiboMediaAndLinksSection(
            status = contentStatus,
            isRetweet = isRetweet,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            onWebClick = onWebClick
        )

        // Retweeted status block if present
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

        val location = remember(contentStatus) { getStatusLocation(contentStatus) }
        if (!location.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
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

        Spacer(modifier = Modifier.height(14.dp))
        HorizontalDivider(color = DividerGrey, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(8.dp))

        // Action Row: Repost, Comment, Like
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
                animationSpec = tween(durationMillis = 200)
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
fun WeiboCard(
    status: WeiboTimelineStatus,
    isRead: Boolean = false,
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
            .clickable { 
                onOpenDetail(contentStatus)
            },
        colors = CardDefaults.cardColors(containerColor = BubbleBg),
        shape = RoundedCornerShape(0.dp)
    ) {
        WeiboCardContent(
            status = status,
            onImageClick = onImageClick,
            onVideoClick = onVideoClick,
            onWebClick = onWebClick,
            onOpenDetail = onOpenDetail,
            onLikeClick = onLikeClick
        )
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
    val detailUrl = statusDetailUrl(retweet)
    
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
            val locationName = remember(retweet) { getStatusLocation(retweet) }
            val isMarkdown = retweet.md_render_mark == 1 || retweet.md_render_mark == 2 || retweet.style_config?.md_render_mark == 1 || retweet.style_config?.md_render_mark == 2
            
            if (isMarkdown) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color(0xFFF97316), fontWeight = FontWeight.Bold)) {
                                append("@$screenName: ")
                            }
                        },
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    WeiboMarkdownRenderer(
                        text = retweet.text ?: retweet.text_raw ?: retweet.raw_text ?: "",
                        detailUrl = detailUrl,
                        urlStruct = retweet.url_struct,
                        locationName = locationName,
                        onImageClick = onImageClick,
                        onWebClick = onWebClick,
                        onDetailClick = { onOpenDetail(retweet) },
                        fontSize = 14,
                        lineHeight = 20,
                        statusPageInfo = retweet.page_info
                    )
                }
            } else {
                val parsedRetweetText = parseWeiboHtmlText(
                    retweet.text ?: retweet.text_raw ?: retweet.raw_text ?: "",
                    detailUrl,
                    retweet.url_struct,
                    locationName,
                    isMarkdown = false,
                    statusPageInfo = retweet.page_info
                )
                val annotatedText = buildAnnotatedString {
                    withStyle(style = SpanStyle(color = Color(0xFFF97316), fontWeight = FontWeight.Bold)) {
                        append("@$screenName: ")
                    }
                    append(parsedRetweetText.annotatedString)
                }
                
                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                
                Text(
                    text = annotatedText,
                    style = LocalTextStyle.current.copy(
                        color = TextWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    ),
                    inlineContent = parsedRetweetText.inlineContent,
                    onTextLayout = { layoutResult = it },
                    modifier = Modifier.pointerInput(annotatedText, detailUrl) {
                        detectLinkTaps(
                            annotatedString = annotatedText,
                            layoutResult = layoutResult,
                            detailUrl = detailUrl,
                            uriHandler = uriHandler,
                            onDetailClick = { onOpenDetail(retweet) },
                            onImageClick = onImageClick,
                            onWebClick = onWebClick
                        )
                    }
                )
            }

            // Retweeted images if present
            val retweetImages = getStatusImages(retweet)
            if (retweetImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                WeiboImageGrid(
                    images = retweetImages,
                    onImageClick = onImageClick
                )
            }

            val pageInfo = getStatusPageInfo(retweet)
            if (pageInfo != null) {
                Spacer(modifier = Modifier.height(8.dp))
                val isVideo = pageInfo.type == "video" || pageInfo.media_info != null || pageInfo.object_type == "video"
                if (isVideo) {
                    WeiboVideoPreview(
                        pageInfo = pageInfo,
                        onVideoClick = onVideoClick,
                        onWebClick = onWebClick
                    )
                } else {
                    WeiboPageInfoCard(
                        pageInfo = pageInfo,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onWebClick = onWebClick
                    )
                }
            }

            val location = remember(retweet) { getStatusLocation(retweet) }
            if (!location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
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
}

@Composable
private fun WeiboDetailButton(
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

        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }

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
fun WeiboVideoPlayerPreview(
    thumbnailUrl: String?,
    videoUrl: String,
    onVideoClick: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .wrapContentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onVideoClick(normalizeWeiboUrl(videoUrl)) }
    ) {
        AsyncImage(
            model = thumbnailUrl.orEmpty(),
            contentDescription = "Video Thumbnail",
            modifier = Modifier
                .widthIn(max = 240.dp)
                .heightIn(max = 200.dp),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(48.dp)
                .background(Color(0x99000000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "播放视频",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
fun WeiboVideoGrid(
    videos: List<WeiboPic>,
    onVideoClick: (String) -> Unit
) {
    val count = videos.size
    val cornerRadius = 4.dp

    @Composable
    fun VideoCell(video: WeiboPic, modifier: Modifier) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .clickable { video.videoSrc?.let { onVideoClick(it) } }
        ) {
            AsyncImage(
                model = video.url ?: video.large?.url ?: "",
                contentDescription = "Video",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .background(Color(0x99000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }

    if (count == 1) {
        val video = videos.first()
        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.CenterStart)
                .clip(RoundedCornerShape(cornerRadius))
                .clickable { video.videoSrc?.let { onVideoClick(it) } }
        ) {
            AsyncImage(
                model = video.url ?: video.large?.url ?: "",
                contentDescription = "Video",
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .heightIn(max = 360.dp),
                contentScale = ContentScale.Crop
            )
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(48.dp)
                    .background(Color(0x99000000), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "播放",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    } else if (count <= 3) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (i in 0 until count) {
                VideoCell(
                    video = videos[i],
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f)
                )
            }
        }
    } else if (count <= 5) {
        val columns = 2
        val rows = 2
        val maxVisible = 4
        val remaining = count - maxVisible

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        if (index < maxVisible) {
                            val isLastVisible = index == maxVisible - 1 && remaining > 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .clickable { videos[index].videoSrc?.let { onVideoClick(it) } }
                            ) {
                                AsyncImage(
                                    model = videos[index].url ?: videos[index].large?.url ?: "",
                                    contentDescription = "Video $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(36.dp)
                                        .background(Color(0x99000000), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                if (isLastVisible) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x80000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$remaining",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } else {
        val maxVisible = when {
            count == 6 -> 6
            count == 9 -> 9
            count <= 8 -> 6
            else -> 9
        }
        val remaining = count - maxVisible
        val columns = 3
        val rows = (maxVisible + columns - 1) / columns

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        if (index < maxVisible) {
                            val isLastVisible = index == maxVisible - 1 && remaining > 0
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .clickable { videos[index].videoSrc?.let { onVideoClick(it) } }
                            ) {
                                AsyncImage(
                                    model = videos[index].url ?: videos[index].large?.url ?: "",
                                    contentDescription = "Video $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(36.dp)
                                        .background(Color(0x99000000), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "播放",
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                if (isLastVisible) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x80000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$remaining",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeiboVideoPreview(
    pageInfo: WeiboTimelinePageInfo,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit
) {
    val imageUrl = pageInfo.page_pic?.url
    val directVideoUrl = directVideoUrl(pageInfo)
    val targetUrl = directVideoUrl ?: pageInfo.page_url ?: pageInfo.media_info?.h5_url
    if (!targetUrl.isNullOrBlank()) {
        WeiboVideoPlayerPreview(
            thumbnailUrl = imageUrl,
            videoUrl = targetUrl,
            onVideoClick = { url ->
                if (!directVideoUrl.isNullOrBlank()) {
                    onVideoClick(url)
                } else {
                    onWebClick(url)
                }
            }
        )
    }
}

@Composable
fun WeiboPageInfoCard(
    pageInfo: WeiboTimelinePageInfo,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit
) {
    if (pageInfo.type == "article") {
        WeiboArticlePageCard(
            pageInfo = pageInfo,
            onImageClick = onImageClick,
            onWebClick = onWebClick
        )
        return
    }

    val uriHandler = LocalUriHandler.current
    val isVideo = pageInfo.type == "video" || pageInfo.media_info != null || pageInfo.object_type == "video"
    val directVideoUrl = directVideoUrl(pageInfo)
    val targetUrl = if (isVideo) {
        directVideoUrl
    } else {
        pageInfo.page_url ?: pageInfo.media_info?.h5_url
    }
    val imageUrl = pageInfo.page_pic?.url

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0x14FFFFFF))
            .border(0.5.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
            .clickable(enabled = !targetUrl.isNullOrBlank()) {
                if (isVideo && !directVideoUrl.isNullOrBlank()) {
                    onVideoClick(normalizeWeiboUrl(directVideoUrl))
                } else {
                    handleWeiboLinkClick(targetUrl.orEmpty(), uriHandler, onWebClick)
                }
            }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .size(width = 92.dp, height = 68.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .clickable {
                        if (isVideo && !directVideoUrl.isNullOrBlank()) {
                            onVideoClick(normalizeWeiboUrl(directVideoUrl))
                        } else {
                            onImageClick(0, listOf(imageUrl))
                        }
                    }
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = pageInfo.title ?: if (isVideo) "视频" else "链接",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                if (isVideo) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(34.dp)
                            .background(Color(0x99000000), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放视频",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = pageInfo.title?.takeIf { it.isNotBlank() } ?: if (isVideo) "视频" else "链接",
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val desc = pageInfo.content1?.takeIf { it.isNotBlank() }
                ?: pageInfo.content2?.takeIf { it.isNotBlank() }
            if (!desc.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = desc,
                    color = TextGrey,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun WeiboArticlePageCard(
    pageInfo: WeiboTimelinePageInfo,
    onImageClick: (Int, List<String>) -> Unit,
    onWebClick: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val targetUrl = pageInfo.page_url ?: pageInfo.media_info?.h5_url ?: ""
    val imageUrl = pageInfo.page_pic?.url

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.778f)
            .clip(RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0x18FFFFFF), RoundedCornerShape(8.dp))
            .clickable(enabled = targetUrl.isNotBlank()) {
                handleWeiboLinkClick(targetUrl, uriHandler, onWebClick)
            },
        colors = CardDefaults.cardColors(containerColor = Color(0x14FFFFFF))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = pageInfo.content1 ?: "文章配图",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }

            // Top gradient overlay to make top text readable
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color(0x66000000), Color.Transparent)
                        )
                    )
            )

            // Top-left: Author/Title
            val topTitle = pageInfo.title?.takeIf { it.isNotBlank() } ?: "文章"
            Text(
                text = topTitle,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            )

            // Top-right: Article indicator with Lightning/Flash icon
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FlashOn,
                    contentDescription = "文章",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = "文章",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Normal
                )
            }

            // Bottom Gradient Overlay and Title
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color(0x99000000), Color(0xCC000000))
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                val bottomTitle = pageInfo.content1?.takeIf { it.isNotBlank() } ?: ""
                if (bottomTitle.isNotBlank()) {
                    Text(
                        text = bottomTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

data class ImageItem(
    val thumbnailUrl: String,
    val largeUrl: String,
    val width: Int? = null,
    val height: Int? = null
)

private fun getStatusImages(status: WeiboTimelineStatus): List<ImageItem> {
    val pics = status.pics
    if (!pics.isNullOrEmpty()) {
        return pics.filter { it.type != "video" }.map { pic ->
            val largeUrl = pic.large?.url ?: pic.url ?: ""
            val thumbUrl = pic.bmiddle?.url ?: pic.url ?: largeUrl
            ImageItem(
                thumbnailUrl = thumbUrl,
                largeUrl = largeUrl,
                width = pic.large?.width ?: pic.bmiddle?.width,
                height = pic.large?.height ?: pic.bmiddle?.height
            )
        }.filter { it.largeUrl.isNotBlank() }
    }
    
    val picIds = status.pic_ids.orEmpty()
    val picInfos = status.pic_infos
    if (picIds.isNotEmpty() && picInfos != null) {
        return picIds.map { id ->
            val info = picInfos[id]
            val largeUrl = info?.large?.url ?: info?.bmiddle?.url ?: "https://wx3.sinaimg.cn/large/$id.jpg"
            val thumbUrl = info?.bmiddle?.url ?: info?.thumbnail?.url ?: largeUrl
            ImageItem(
                thumbnailUrl = thumbUrl,
                largeUrl = largeUrl,
                width = info?.large?.width ?: info?.bmiddle?.width ?: info?.original?.width,
                height = info?.large?.height ?: info?.bmiddle?.height ?: info?.original?.height
            )
        }
    }
    
    return emptyList()
}

private fun isTopicOrLocationPageInfo(pageInfo: WeiboTimelinePageInfo): Boolean {
    val url = pageInfo.page_url ?: pageInfo.media_info?.h5_url ?: ""
    val isLoc = url.contains("location") || url.contains("containerid=230413") || pageInfo.type == "location" || pageInfo.type == "place"
    val isTop = url.contains("huati") || url.contains("containerid=231522") || url.contains("containerid=100808") || 
                 pageInfo.type == "search" || pageInfo.type == "search_topic" || pageInfo.object_type == "directory" || pageInfo.object_type == "search" ||
                 (pageInfo.content2?.contains("讨论") == true && pageInfo.content2.contains("阅读")) ||
                 (pageInfo.content1?.contains("讨论") == true && pageInfo.content1.contains("阅读"))
    return isLoc || isTop
}

private fun getStatusPageInfo(status: WeiboTimelineStatus): WeiboTimelinePageInfo? {
    val pageInfo = status.page_info
    if (pageInfo != null && !isTopicOrLocationPageInfo(pageInfo)) return pageInfo
    
    // Fallback: search in url_struct
    val urlStructList = status.url_struct
    if (!urlStructList.isNullOrEmpty()) {
        for (urlObj in urlStructList) {
            val pi = urlObj.page_info
            if (pi != null && !isTopicOrLocationPageInfo(pi)) {
                // Prioritize video type page_info
                val isVideo = pi.type == "video" || pi.media_info != null || pi.object_type == "video"
                if (isVideo) {
                    return pi
                }
            }
        }
        for (urlObj in urlStructList) {
            val pi = urlObj.page_info
            if (pi != null && !isTopicOrLocationPageInfo(pi)) {
                return pi
            }
        }
    }
    return null
}

private fun getStatusLocation(status: WeiboTimelineStatus): String? {
    // 0. Check status.page_info if it's place
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

    // 1. Check in url_struct first
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
    
    // 2. Fallback: Parse from status text HTML
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

@Composable
fun WeiboImageGrid(
    images: List<ImageItem>,
    onImageClick: (Int, List<String>) -> Unit
) {
    val count = images.size
    val imageUrls = remember(images) { images.map { it.largeUrl } }
    val cornerRadius = 4.dp

    if (count == 1) {
        // Single image: left-aligned, natural aspect ratio, rounded corners
        val image = images.first()
        val imageUrl = image.largeUrl

        var loadedRatio by remember { mutableStateOf<Float?>(null) }
        val ratio = remember(image, loadedRatio) {
            if (image.width != null && image.height != null && image.width > 0 && image.height > 0) {
                if (isLongWeiboImage(image.width, image.height)) 0.75f else (image.width.toFloat() / image.height.toFloat()).coerceIn(0.45f, 2.4f)
            } else {
                loadedRatio ?: 1.0f
            }
        }

        Box(
            modifier = Modifier
                .wrapContentSize(Alignment.CenterStart)
                .clip(RoundedCornerShape(cornerRadius))
                .combinedClickable(
                    onClick = { onImageClick(0, imageUrls) }
                )
        ) {
            AsyncImage(
                model = image.thumbnailUrl.takeIf { it.isNotBlank() } ?: imageUrl,
                contentDescription = "Weibo Image",
                modifier = Modifier.weiboImageSize(ratio),
                contentScale = ContentScale.Crop,
                onSuccess = { state ->
                    val size = state.painter.intrinsicSize
                    if (size.width > 0 && size.height > 0) {
                        loadedRatio = (size.width / size.height).coerceIn(0.45f, 2.4f)
                    }
                }
            )
        }
    } else if (count <= 3) {
        // 2-3 images: uniform grid (2x1, 3x1)
        val columns = count
        val rows = 1

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        if (index < count) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .combinedClickable(
                                        onClick = { onImageClick(index, imageUrls) }
                                    )
                            ) {
                                AsyncImage(
                                    model = images[index].thumbnailUrl.takeIf { it.isNotBlank() } ?: images[index].largeUrl,
                                    contentDescription = "Weibo Image $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } else if (count == 4 || count == 5) {
        // 4-5 images: uniform 2x2 grid
        val columns = 2
        val rows = 2
        val maxVisible = 4
        val remaining = count - maxVisible

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        val isLastVisible = index == maxVisible - 1 && remaining > 0

                        if (index < maxVisible) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .combinedClickable(
                                        onClick = {
                                            if (isLastVisible) {
                                                onImageClick(maxVisible, imageUrls)
                                            } else {
                                                onImageClick(index, imageUrls)
                                            }
                                        },
                                    )
                            ) {
                                AsyncImage(
                                    model = images[index].thumbnailUrl.takeIf { it.isNotBlank() } ?: images[index].largeUrl,
                                    contentDescription = "Weibo Image $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                if (isLastVisible) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x80000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$remaining",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    } else {
        // 6+ images: 3-column grid
        val maxVisible = when {
            count == 6 -> 6
            count == 9 -> 9
            count <= 8 -> 6
            else -> 9
        }
        val remaining = count - maxVisible
        val columns = 3
        val rows = (maxVisible + columns - 1) / columns

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            for (r in 0 until rows) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    for (c in 0 until columns) {
                        val index = r * columns + c
                        val isLastVisible = index == maxVisible - 1 && remaining > 0

                        if (index < maxVisible) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .clickable {
                                        if (isLastVisible) {
                                            // Click "+x" opens the first hidden image
                                            onImageClick(maxVisible, imageUrls)
                                        } else {
                                            onImageClick(index, imageUrls)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = images[index].thumbnailUrl.takeIf { it.isNotBlank() } ?: images[index].largeUrl,
                                    contentDescription = "Weibo Image $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )

                                // "+x" overlay on last visible image
                                if (isLastVisible) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color(0x80000000)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "+$remaining",
                                            color = Color.White,
                                            fontSize = 22.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

private fun isLongWeiboImage(width: Int?, height: Int?): Boolean {
    if (width == null || height == null || width <= 0 || height <= 0) return false
    return height.toFloat() / width.toFloat() >= 2.4f
}

private fun Modifier.weiboGridImageSize(width: Int?, height: Int?): Modifier {
    val ratio = if (width != null && height != null && width > 0 && height > 0) {
        if (isLongWeiboImage(width, height)) 0.75f else (width.toFloat() / height.toFloat()).coerceIn(0.45f, 2.4f)
    } else {
        1.0f
    }
    return this.aspectRatio(ratio)
}

private fun Modifier.weiboImageSize(ratio: Float): Modifier {
    return this
        .widthIn(max = 280.dp)
        .heightIn(max = 360.dp)
        .aspectRatio(ratio)
}

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
fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
                                openInWeiboApp(context, detailUrl) {
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
                        status = status,
                        viewModel = viewModel,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onWebClick = onWebClick,
                        onOpenDetail = onOpenDetail
                    )
                }

                item {
                    DetailTabBar(
                        status = status,
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
                                RepostItem(repost = repost, onWebClick = onWebClick)
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
                // Fixed docked bottom bar
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

                    val isLiked = status.liked == true
                    val scale by animateFloatAsState(
                        targetValue = if (isLiked) 1.3f else 1.0f,
                        animationSpec = tween(durationMillis = 200)
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
            Box(
                modifier = Modifier
                    .clickable { onTabSelected(tab.type) }
                    .padding(horizontal = 24.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) Color(0xFFF97316) else TextGrey,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun CommentBadges(
    badges: List<com.example.weibochat.data.WeiboCommentBadge>?,
    fontSize: TextUnit,
    includeAuthorBadges: Boolean = false
) {
    val badgeHeight = with(LocalDensity.current) { fontSize.toDp() * 1.25f }
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

private fun shouldShowCommentBadge(badge: com.example.weibochat.data.WeiboCommentBadge): Boolean {
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
            Row(verticalAlignment = Alignment.CenterVertically) {
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
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
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
                    modifier = Modifier.clickable { /* Toggle comment like (optional visual state) */ }
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
 
            // Render comment image if present
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
 
            // Render nested reply comments (楼中楼)
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
    onWebClick: (String) -> Unit
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
                onImageClick = { _, _ -> },
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
    val uriHandler = LocalUriHandler.current
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
        val locationName = remember(status) { getStatusLocation(status) }
        WeiboRichText(
            html = status.text ?: status.text_raw ?: status.raw_text ?: "",
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

@Composable
fun ImagePreviewDialog(
    imageUrls: List<String>,
    initialIndex: Int,
    cookie: String = "",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var imageActionMenuUrl by remember { mutableStateOf<String?>(null) }
    var originalPage by remember { mutableStateOf(-1) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { imageUrls.size }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val imageUrl = imageUrls[page]
                ZoomableImage(
                    url = imageUrl,
                    cookie = cookie,
                    onDismiss = onDismiss,
                    onLongPress = {
                        imageActionMenuUrl = imageUrl
                    },
                    useOriginalSize = (page == originalPage)
                )
            }

            if (imageUrls.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color(0x77000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${imageUrls.size}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            imageActionMenuUrl?.let { url ->
                AlertDialog(
                    onDismissRequest = { imageActionMenuUrl = null },
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = TextWhite,
                    textContentColor = TextGrey,
                    title = { Text("图片操作") },
                    text = { Text("选择对这张图片的操作") },
                    confirmButton = {
                        TextButton(onClick = {
                            originalPage = pagerState.currentPage
                            imageActionMenuUrl = null
                        }) {
                            Text("查看原图", color = Color(0xFFF97316))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            downloadImageToGallery(context, url, cookie)
                            imageActionMenuUrl = null
                        }) {
                            Text("保存原图", color = Color(0xFFF97316))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun ZoomableImage(
    url: String,
    cookie: String = "",
    onDismiss: () -> Unit,
    onLongPress: () -> Unit,
    useOriginalSize: Boolean = false
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val context = LocalContext.current
    // null = unknown yet, true = tall image (use FillWidth + scroll), false = normal image (use Fit + center)
    var isLongImage by remember(url) { mutableStateOf<Boolean?>(null) }

    val imageModel = remember(url, cookie, useOriginalSize) {
        coil.request.ImageRequest.Builder(context)
            .data(url)
            .crossfade(true)
            .apply {
                if (cookie.isNotEmpty()) {
                    addHeader("Cookie", cookie)
                    addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    addHeader("Referer", "https://weibo.com/")
                }
                if (useOriginalSize) size(coil.size.Size.ORIGINAL)
            }
            .build()
    }

    val screenHeight = context.resources.displayMetrics.heightPixels
    val screenWidth = context.resources.displayMetrics.widthPixels
    val longImageThreshold = (screenHeight.toFloat() / screenWidth) * 1.2f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onDismiss() },
                    onDoubleTap = { pos ->
                        if (useOriginalSize && isLongImage != true) return@detectTapGestures
                        if (scale > 1f) {
                            scale = 1f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        } else {
                            scale = 2.5f
                        }
                    },
                    onLongPress = { pos ->
                        onLongPress()
                    }
                )
            }
    ) {
        if (useOriginalSize) {
            val longImage = isLongImage
            if (longImage == true) {
                // Long image: fill width, scrollable from top to bottom
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    SubcomposeAsyncImage(
                        model = imageModel,
                        contentDescription = "Zoomable Image",
                        contentScale = ContentScale.FillWidth,
                        loading = {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(color = Color(0xFFF97316))
                            }
                        },
                        error = {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("图片加载失败", color = TextGrey, fontSize = 14.sp)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().wrapContentHeight(),
                        onSuccess = { state ->
                            val intrinsicSize = state.painter.intrinsicSize
                            if (intrinsicSize.height > 0 && intrinsicSize.width > 0) {
                                val ratio = intrinsicSize.height / intrinsicSize.width
                                if (isLongImage == null) {
                                    isLongImage = ratio > longImageThreshold
                                }
                            }
                        }
                    )
                }
            } else {
                // Normal image: fit and center
                SubcomposeAsyncImage(
                    model = imageModel,
                    contentDescription = "Zoomable Image",
                    contentScale = ContentScale.Fit,
                    loading = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Color(0xFFF97316))
                        }
                    },
                    error = {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("图片加载失败", color = TextGrey, fontSize = 14.sp)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .align(Alignment.Center),
                    onSuccess = { state ->
                        val intrinsicSize = state.painter.intrinsicSize
                        if (intrinsicSize.height > 0 && intrinsicSize.width > 0) {
                            val ratio = intrinsicSize.height / intrinsicSize.width
                            if (isLongImage == null) {
                                isLongImage = ratio > longImageThreshold
                            }
                        }
                    }
                )
            }
        } else {
        SubcomposeAsyncImage(
            model = imageModel,
            contentDescription = "Zoomable Image",
            contentScale = ContentScale.Fit,
            loading = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFF97316))
                }
            },
            error = {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("图片加载失败", color = TextGrey, fontSize = 14.sp)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center)
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .pointerInput(Unit) {
                    detectTransformGesturesConditional(
                        panZoomLock = true,
                        canPan = { scale > 1f }
                    ) { centroid, pan, zoom, rotation ->
                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                        if (newScale > 1f) {
                            scale = newScale
                            offset = androidx.compose.ui.geometry.Offset(
                                x = offset.x + pan.x * scale,
                                y = offset.y + pan.y * scale
                            )
                        } else {
                            scale = 1f
                            offset = androidx.compose.ui.geometry.Offset.Zero
                        }
                    }
                }
        )
        }
    }
}

private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTransformGesturesConditional(
    panZoomLock: Boolean = false,
    canPan: () -> Boolean,
    onGesture: (centroid: androidx.compose.ui.geometry.Offset, pan: androidx.compose.ui.geometry.Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = androidx.compose.ui.geometry.Offset.Zero
        var pastTouchSlop = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoom = false

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            val canceled = event.changes.any { it.isConsumed }
            if (!canceled) {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchSlop) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = kotlin.math.abs(1 - zoom) * centroidSize
                    val rotationMotion = kotlin.math.abs(rotation) * kotlin.math.min(1f, centroidSize)
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchSlop = true
                        lockedToPanZoom = panZoomLock && rotationMotion < touchSlop
                    }
                }

                if (pastTouchSlop) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectiveRotation = if (lockedToPanZoom) 0f else rotationChange
                    if (effectiveRotation != 0f ||
                        zoomChange != 1f ||
                        panChange != androidx.compose.ui.geometry.Offset.Zero
                    ) {
                        onGesture(centroid, panChange, zoomChange, effectiveRotation)
                    }
                    
                    if (canPan() || zoomChange != 1f || rotationChange != 0f) {
                        event.changes.forEach {
                            if (it.positionChanged()) {
                                it.consume()
                            }
                        }
                    }
                }
            }
        } while (!canceled && event.changes.any { it.pressed })
    }
}

private fun downloadImageToGallery(context: Context, url: String, cookie: String = "") {
    try {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setTitle("保存微博原图")
            setDescription("正在保存图片到系统相册...")
            
            if (cookie.isNotEmpty()) {
                addRequestHeader("Cookie", cookie)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                addRequestHeader("Referer", "https://weibo.com/")
            }
            
            val fileName = url.substringAfterLast("/").substringBefore("?")
            val finalName = if (fileName.contains(".")) fileName else "$fileName.jpg"
            
            setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, finalName)
        }
        downloadManager.enqueue(request)
        Toast.makeText(context, "开始保存图片...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "保存失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

@Composable
fun VideoPreviewDialog(
    videoUrl: String,
    onDismiss: () -> Unit
) {
    val videoViewState = remember(videoUrl) { mutableStateOf<VideoView?>(null) }

    DisposableEffect(videoUrl) {
        onDispose {
            videoViewState.value?.apply {
                stopPlayback()
                setMediaController(null)
            }
            videoViewState.value = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        videoViewState.value = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val controller = MediaController(context)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setVideoURI(Uri.parse(videoUrl))
                        setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = false
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(24.dp)
                    .background(Color(0x55000000), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "关闭视频",
                    tint = Color.White
                )
            }
        }
    }
}

// Formats number to show "K" or "W" (万) for large numbers
private fun formatCount(count: Int): String {
    if (count <= 0) return "0"
    if (count < 1000) return count.toString()
    if (count < 10000) return String.format(Locale.getDefault(), "%.1fk", count / 1000f)
    return String.format(Locale.getDefault(), "%.1f万", count / 10000f)
}

internal data class ParsedWeiboText(
    val annotatedString: androidx.compose.ui.text.AnnotatedString,
    val inlineContent: Map<String, InlineTextContent> = emptyMap()
)

internal const val DETAIL_LINK_TARGET = "__weibo_status_detail__"

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

internal fun parseWeiboHtmlText(
    html: String,
    detailUrl: String? = null,
    urlStruct: List<WeiboUrlStruct>? = null,
    locationName: String? = null,
    isMarkdown: Boolean = false,
    statusPageInfo: WeiboTimelinePageInfo? = null
): ParsedWeiboText {
    val linkIconList = mutableListOf<String>()

    // Strip ending topics (one or more) from the end of the post text
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

    // 1. Preprocess raw HTML to strip short URLs that match the url_struct media items
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

    // Clean up any remaining "XXX的微博视频" or "XXX的微博直播" signatures
    processedHtml = processedHtml.replace(Regex("@[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博视频", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博视频", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("的微博视频", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("@[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博直播", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("[\\u4e00-\\u9fa5a-zA-Z0-9_\\-·]+\\s*的微博直播", RegexOption.IGNORE_CASE), "")
    processedHtml = processedHtml.replace(Regex("的微博直播", RegexOption.IGNORE_CASE), "")

    val linkRegex = Regex("<a\\b([^>]*)>(.*?)</a>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val imgRegex = Regex("<img\\b([^>]*)>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    val brRegex = Regex("<br\\s*/?>", RegexOption.IGNORE_CASE)

    // Extract and placeholder-ize all emoji images
    val emojiList = mutableListOf<Pair<String, String>>() // Pair(alt, src)
    processedHtml = imgRegex.replace(processedHtml) { match ->
        val attrs = match.groupValues[1]
        val alt = Regex("\\balt\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(2)
        val src = Regex("\\bsrc\\s*=\\s*([\"'])(.*?)\\1", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(2)
        if (alt != null && alt.startsWith("[") && alt.endsWith("]") && src != null) {
            val idx = emojiList.size
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

private fun directVideoUrl(pageInfo: WeiboTimelinePageInfo): String? {
    return pageInfo.media_info?.stream_url_hd
        ?: pageInfo.media_info?.stream_url
        ?: pageInfo.media_info?.mp4_720p_mp4
        ?: pageInfo.media_info?.mp4_hd_url
        ?: pageInfo.media_info?.mp4_sd_url
}

private fun statusDetailUrl(status: WeiboTimelineStatus): String? {
    return statusId(status)?.let { "https://m.weibo.cn/status/$it" }
}

private fun openInWeiboApp(
    context: Context,
    detailUrl: String,
    fallback: () -> Unit
) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(detailUrl)).apply {
        setPackage("com.sina.weibo")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        fallback()
    }
}

private fun statusId(status: WeiboTimelineStatus): String? {
    return status.idstr?.takeIf { it.isNotBlank() } ?: status.id?.toString()
}

private fun shouldShowDetailButton(status: WeiboTimelineStatus): Boolean {
    val raw = status.text ?: status.text_raw ?: status.raw_text ?: ""
    return status.isLongText == true || raw.contains("全文") || raw.contains("/status/")
}

private fun shouldFetchLongText(status: WeiboTimelineStatus): Boolean {
    val raw = status.text ?: status.text_raw ?: status.raw_text ?: ""
    return status.isLongText == true || raw.contains("全文")
}

private fun isFullTextLink(linkText: String, href: String): Boolean {
    return linkText.contains("全文") || href.contains("/status/")
}

private fun androidx.compose.ui.text.AnnotatedString.Builder.stylePattern(
    text: String,
    regex: Regex,
    color: Color
) {
    regex.findAll(text).forEach { match ->
        addStyle(
            SpanStyle(color = color, fontWeight = FontWeight.SemiBold),
            match.range.first,
            match.range.last + 1
        )
    }
}

private fun stripHtml(value: String): String {
    return decodeHtml(value.replace(Regex("<[^>]+>"), ""))
}

private fun decodeHtml(value: String): String {
    return value
        .replace(Regex("&#x([0-9a-fA-F]+);")) { match ->
            match.groupValues[1].toIntOrNull(16)?.let(::codePointToString).orEmpty()
        }
        .replace(Regex("&#(\\d+);")) { match ->
            match.groupValues[1].toIntOrNull()?.let(::codePointToString).orEmpty()
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
    return runCatching { String(Character.toChars(code)) }.getOrDefault("")
}

private fun normalizeWeiboUrl(url: String): String {
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://m.weibo.cn$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        else -> "https://m.weibo.cn/$url"
    }
}

private fun getRealUrlIfWeiboRedirect(url: String): String {
    if (url.contains("sinaurl") && url.contains("u=")) {
        val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
        val uParam = uri?.getQueryParameter("u")
        if (!uParam.isNullOrBlank()) {
            return uParam
        }
    }
    return url
}

private fun extractImageUrlFromWeiboUrl(url: String): String? {
    if (url.contains("u=")) {
        val uri = android.net.Uri.parse(url)
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

private fun handleWeiboLinkClick(
    url: String,
    uriHandler: androidx.compose.ui.platform.UriHandler,
    onWebClick: (String) -> Unit
) {
    val realUrl = getRealUrlIfWeiboRedirect(url)
    val normalized = normalizeWeiboUrl(realUrl)
    val host = android.net.Uri.parse(normalized).host
    val isWeibo = host?.let { isAllowedWeiboHost(it) } == true
    if (isWeibo) {
        onWebClick(normalized)
    } else {
        runCatching { uriHandler.openUri(normalized) }
    }
}

@Composable
fun WeiboWebDialog(
    url: String,
    repository: DataRepository,
    onDismiss: () -> Unit
) {
    val webViewState = remember(url) { mutableStateOf<android.webkit.WebView?>(null) }

    DisposableEffect(url) {
        onDispose {
            webViewState.value?.apply {
                stopLoading()
                webViewClient = android.webkit.WebViewClient()
                removeAllViews()
                destroy()
            }
            webViewState.value = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BubbleBg)
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "关闭",
                            tint = TextWhite
                        )
                    }
                    Text(
                        text = "微博网页",
                        color = TextWhite,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                }

                var isLoading by remember { mutableStateOf(true) }
                Box(modifier = Modifier.weight(1f)) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { context ->
                            seedWeiboWebViewCookies(repository.getAllCookies())
                            android.webkit.WebView(context).apply {
                                webViewState.value = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        android.webkit.CookieManager.getInstance().flush()
                                        isLoading = false
                                    }
                                }
                                loadUrl(url)
                            }
                        }
                    )

                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.Center),
                            color = Color(0xFFF97316)
                        )
                    }
                }
            }
            }
        }
    }
private fun formatWeiboCreatedAt(createdAt: String): String {
    try {
        val parser = java.text.SimpleDateFormat("EEE MMM dd HH:mm:ss Z yyyy", java.util.Locale.US)
        val date = parser.parse(createdAt) ?: return createdAt
        val formatter = java.text.SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
        return formatter.format(date)
    } catch (e: Exception) {
        val regex = Regex("\\d{2}:\\d{2}:\\d{2}")
        return regex.find(createdAt)?.value ?: createdAt
    }
}

private data class MediaItemInfo(
    val url: String,
    val title: String,
    val isImage: Boolean,
    val isVideo: Boolean,
    val pageInfo: WeiboTimelinePageInfo?
)

@Composable
private fun WeiboMediaAndLinksSection(
    status: WeiboTimelineStatus,
    isRetweet: Boolean,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit
) {
    val uriHandler = LocalUriHandler.current
    
    if (isRetweet) {
        val mediaItems = remember(status) {
            val items = mutableListOf<MediaItemInfo>()
            
            val pi = status.page_info
            if (pi != null && !isTopicOrLocationPageInfo(pi)) {
                val isVideo = pi.type == "video" || pi.media_info != null || pi.object_type == "video"
                val url = pi.page_url ?: pi.media_info?.h5_url ?: ""
                if (url.isNotBlank()) {
                    items.add(MediaItemInfo(
                        url = url,
                        title = pi.title ?: pi.content1 ?: "",
                        isImage = false,
                        isVideo = isVideo,
                        pageInfo = pi
                    ))
                }
            }
            
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

        mediaItems.forEach { item ->
            val isVideo = item.isVideo
            val isImage = item.isImage
            val pageInfo = item.pageInfo
            val direct = pageInfo?.let { directVideoUrl(it) }
            val targetUrl = if (isVideo && !direct.isNullOrBlank()) {
                direct
            } else {
                item.url
            }

            if (targetUrl.isNotBlank()) {
                if (isVideo) {
                    val imageUrl = pageInfo?.page_pic?.url
                    Spacer(modifier = Modifier.height(8.dp))
                    WeiboVideoPlayerPreview(
                        thumbnailUrl = imageUrl,
                        videoUrl = targetUrl,
                        onVideoClick = { url ->
                            if (!direct.isNullOrBlank()) {
                                onVideoClick(normalizeWeiboUrl(direct))
                            } else {
                                onWebClick(normalizeWeiboUrl(url))
                            }
                        }
                    )
                } else {
                    val isWeiboStatus = remember(targetUrl) { extractWeiboStatusIdFromUrl(targetUrl) != null }
                    val displayIcon = when {
                        isImage -> Icons.Default.Image
                        isWeiboStatus -> Icons.Default.Description
                        else -> Icons.Default.Link
                    }
                    val displayText = when {
                        isImage -> "查看图片"
                        isWeiboStatus -> "查看微博"
                        else -> "查看链接"
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable {
                                handleWeiboLinkClick(targetUrl, uriHandler, onWebClick)
                            }
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
            }
        }

        val cardImages = remember(status) { getStatusImages(status) }
        if (cardImages.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            val imageUrls = remember(cardImages) { cardImages.map { it.largeUrl } }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable { onImageClick(0, imageUrls) }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "查看图片",
                    tint = Color(0xFF60A5FA),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (imageUrls.size > 1) "查看图片 (${imageUrls.size}张)" else "查看图片",
                    color = Color(0xFF60A5FA),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    } else {
        val pageInfo = remember(status) { getStatusPageInfo(status) }
        val isVideoPage = pageInfo?.type == "video" || pageInfo?.media_info != null || pageInfo?.object_type == "video"

        if (isVideoPage) {
            val videoPics = remember(status) {
                status.pics?.filter { it.type == "video" && !it.videoSrc.isNullOrBlank() }.orEmpty()
            }
            if (videoPics.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                WeiboVideoGrid(
                    videos = videoPics,
                    onVideoClick = onVideoClick
                )
            } else if (pageInfo != null) {
                Spacer(modifier = Modifier.height(10.dp))
                WeiboVideoPreview(
                    pageInfo = pageInfo,
                    onVideoClick = onVideoClick,
                    onWebClick = onWebClick
                )
            }
        } else {
            if (pageInfo != null) {
                Spacer(modifier = Modifier.height(10.dp))
                WeiboPageInfoCard(
                    pageInfo = pageInfo,
                    onImageClick = onImageClick,
                    onVideoClick = onVideoClick,
                    onWebClick = onWebClick
                )
            }
            val cardImages = remember(status) { getStatusImages(status) }
            if (cardImages.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                WeiboImageGrid(
                    images = cardImages,
                    onImageClick = onImageClick
                )
            }
        }
    }
}

private fun extractWeiboStatusIdFromUrl(url: String): String? {
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (!host.contains("weibo.com") && !host.contains("weibo.cn")) return null
    
    val pathSegments = uri.pathSegments ?: return null
    if (pathSegments.isEmpty()) return null
    
    // case 1: /status/ID or /detail/ID
    val statusIdx = pathSegments.indexOf("status")
    if (statusIdx != -1 && statusIdx + 1 < pathSegments.size) {
        return pathSegments[statusIdx + 1]
    }
    val detailIdx = pathSegments.indexOf("detail")
    if (detailIdx != -1 && detailIdx + 1 < pathSegments.size) {
        return pathSegments[detailIdx + 1]
    }
    
    // case 2: /userID/bid or /userID/mid (where userID is numeric)
    if (pathSegments.size >= 2) {
        val first = pathSegments[0]
        val second = pathSegments[1]
        if (first.all { it.isDigit() } && second.length >= 8) {
            return second
        }
    }
    
    // case 3: fallback check for any segment that is numeric and long (>= 15 digits) or is a 9-char alphanumeric bid
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

private fun getEndingTopicUrl(status: WeiboTimelineStatus): String? {
    val text = status.text ?: status.text_raw ?: status.raw_text ?: ""
    val cleanText = text.replace(Regex("<[^>]+>"), "").trim()
    val match = Regex("#([^#\\n]+)#$").find(cleanText) ?: return null
    val topicTitle = match.value
    
    status.url_struct?.forEach { urlObj ->
        if (urlObj.url_title == topicTitle) {
            val url = urlObj.long_url ?: urlObj.short_url ?: urlObj.ori_url
            if (!url.isNullOrBlank()) {
                return url
            }
        }
    }
    
    val topicName = match.groupValues[1]
    val encoded = runCatching { java.net.URLEncoder.encode(topicName, "UTF-8") }.getOrDefault(topicName)
    return "https://m.weibo.cn/search?containerid=231522$encoded"
}

// Markdown blocks representation
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

// Block parser
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
        
        // Skip empty lines
        if (line.trim().isEmpty()) {
            i++
            continue
        }
        
        // Blockquote
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
        
        // Code block
        if (line.trim().startsWith("```")) {
            val lang = line.trim().substring(3).trim()
            val contentLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trim().startsWith("```")) {
                contentLines.add(lines[i])
                i++
            }
            if (i < lines.size) {
                i++ // skip closing ```
            }
            blocks.add(MarkdownBlock.CodeBlock(lang, contentLines.joinToString("\n")))
            continue
        }
        
        // Header
        val headerMatch = Regex("^(#{1,6})\\s+(.*)$").matchEntire(line.trim())
        if (headerMatch != null) {
            val level = headerMatch.groupValues[1].length
            val text = headerMatch.groupValues[2]
            blocks.add(MarkdownBlock.Header(level, text))
            i++
            continue
        }
        
        // Table block
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
            
            i += 2 // skip header and separator
            
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
        
        // Bullet list
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
        
        // Ordered list
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
        
        // Paragraph: collect consecutive non-empty lines
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

// Inline formatting parser
internal fun androidx.compose.ui.text.AnnotatedString.Builder.appendMarkdownStyledText(text: String) {
    var i = 0
    val length = text.length
    while (i < length) {
        // Monospace code
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
        
        // Bold
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
        
        // Italic
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

// Markdown Renderer
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
                    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                    Text(
                        text = parsed.annotatedString,
                        style = LocalTextStyle.current.copy(
                            color = TextWhite,
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
                        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
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
                    var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
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
                                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                Text(
                                    text = parsed.annotatedString,
                                    style = LocalTextStyle.current.copy(
                                        color = TextWhite,
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
                                var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
                                Text(
                                    text = parsed.annotatedString,
                                    style = LocalTextStyle.current.copy(
                                        color = TextWhite,
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
            // Header Row
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
                        var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
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
            
            // Body Rows
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
                            var layoutResult by remember { mutableStateOf<androidx.compose.ui.text.TextLayoutResult?>(null) }
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


// Link Tap Handler Helper
private suspend fun PointerInputScope.detectLinkTaps(
    annotatedString: androidx.compose.ui.text.AnnotatedString,
    layoutResult: androidx.compose.ui.text.TextLayoutResult?,
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

@Composable
fun RandomRoamingOverlay(
    unviewedStatuses: List<WeiboTimelineStatus>,
    readStatusIds: Set<String>,
    onDismiss: () -> Unit,
    onMarkAsRead: (String) -> Unit,
    onMarkAllAsRead: () -> Unit,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit,
    onWebClick: (String) -> Unit,
    onOpenDetail: (WeiboTimelineStatus) -> Unit
) {
    var currentStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    var previousStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    var nextStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    val dragOffset = remember { Animatable(0f) }
    val exitOffset = remember { Animatable(0f) }
    val exitAlpha = remember { Animatable(1f) }
    val enterOffset = remember { Animatable(0f) }
    val swipeThresholdPx = with(LocalDensity.current) { 100f.dp.toPx() }
    var isSwapping by remember { mutableStateOf(false) }
    var exitingStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(unviewedStatuses, currentStatus) {
        if (currentStatus == null && unviewedStatuses.isNotEmpty()) {
            currentStatus = unviewedStatuses.random()
        }
    }

    LaunchedEffect(currentStatus, unviewedStatuses) {
        if (currentStatus != null && unviewedStatuses.isNotEmpty()) {
            val pool = unviewedStatuses.filter { (it.idstr ?: it.id?.toString()) != (currentStatus?.idstr ?: currentStatus?.id?.toString()) }
            nextStatus = pool.randomOrNull()
        } else {
            nextStatus = null
        }
    }

    val configuration = LocalConfiguration.current
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx().toInt() }

    fun Modifier.swipeGestureModifier(): Modifier = this.pointerInput(Unit) {
        detectVerticalDragGestures(
            onDragStart = {},
            onDragEnd = {
                if (isSwapping) return@detectVerticalDragGestures
                val offset = dragOffset.value
                if (offset < -swipeThresholdPx) {
                    isSwapping = true
                    currentStatus?.let { s -> (s.idstr ?: s.id?.toString())?.let { onMarkAsRead(it) } }
                } else if (offset > swipeThresholdPx) {
                    isSwapping = true
                } else {
                    coroutineScope.launch { dragOffset.animateTo(0f, spring()) }
                }
            },
            onVerticalDrag = { change, dragAmount ->
                change.consume()
                coroutineScope.launch { dragOffset.snapTo(dragOffset.value + dragAmount) }
            }
        )
    }

    // When swipe threshold is met, animate cards continuously from drag position
    LaunchedEffect(isSwapping) {
        if (!isSwapping) return@LaunchedEffect
        val swipedUp = dragOffset.value < 0
        val oldStatus = currentStatus
        val newStatus = if (swipedUp) nextStatus else previousStatus
        if (newStatus == null) {
            // No card to swap to — spring back
            dragOffset.animateTo(0f, spring())
            isSwapping = false
            return@LaunchedEffect
        }
        // Capture old card status before state change
        exitingStatus = currentStatus
        val exitTarget = if (swipedUp) -screenHeightPx.toFloat() else screenHeightPx.toFloat()
        val enterStart = if (swipedUp) screenHeightPx.toFloat() else -screenHeightPx.toFloat()
        // Prepare enter animation starting position
        enterOffset.snapTo(enterStart)
        // Animate old card off-screen from current drag position
        launch { exitOffset.animateTo(exitTarget, tween(300, easing = FastOutSlowInEasing)) }
        launch { exitAlpha.animateTo(0f, tween(300)) }
        // Swap state — new card appears at enterStart (off-screen), old card captured in exitingCard
        if (swipedUp) {
            previousStatus = currentStatus
            currentStatus = newStatus
        } else {
            currentStatus = newStatus
            previousStatus = null
        }
        dragOffset.snapTo(0f)
        // Animate new card onto screen
        enterOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
        // Cleanup
        exitingStatus = null
        exitOffset.snapTo(0f)
        exitAlpha.snapTo(1f)
        isSwapping = false
    }

    @Composable
    fun RoamingCardContent(
        status: WeiboTimelineStatus,
        onImageClick: (Int, List<String>) -> Unit,
        onVideoClick: (String) -> Unit,
        onWebClick: (String) -> Unit,
        onOpenDetail: (WeiboTimelineStatus) -> Unit
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

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = screenName,
                        color = if (screenName.contains("科技") || screenName.contains("人民网") || screenName.contains("爱范儿") || screenName.contains("公园")) Color(0xFFF97316) else TextWhite,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatWeiboCreatedAt(createdAt),
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

            Spacer(modifier = Modifier.height(10.dp))

            val locationName = remember(contentStatus) { getStatusLocation(contentStatus) }
            WeiboRichText(
                html = contentStatus.text ?: contentStatus.text_raw ?: contentStatus.raw_text ?: "",
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

            val location = remember(contentStatus) { getStatusLocation(contentStatus) }
            if (!location.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
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

    @Composable
    fun RoamingCard(
        status: WeiboTimelineStatus,
        modifier: Modifier = Modifier,
        isInteractive: Boolean = true
    ) {
        Card(
            modifier = modifier,
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            border = BorderStroke(1.dp, Color(0x33FFFFFF)),
            elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(9.6.dp)
                                .background(Color(0xFFFF5F56), CircleShape)
                                .clickable(enabled = isInteractive) { onDismiss() }
                        )
                        Box(
                            modifier = Modifier
                                .size(9.6.dp)
                                .background(Color(0xFFFFBD2E), CircleShape)
                        )
                        Box(
                            modifier = Modifier
                                .size(9.6.dp)
                                .background(Color(0xFF27C93F), CircleShape)
                        )
                    }
                }

                HorizontalDivider(color = Color(0x20FFFFFF), thickness = 1.dp)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clipToBounds()
                ) {
                    RoamingCardContent(
                        status = status,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onWebClick = onWebClick,
                        onOpenDetail = onOpenDetail
                    )
                }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // Full-screen dark overlay to focus attention on the card
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xE6000000))
                .clickable(enabled = true, onClick = { onDismiss() })
                .swipeGestureModifier()
        )

        BackHandler {
            onDismiss()
        }

        if (unviewedStatuses.isEmpty() && currentStatus == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp)
                    .clickable(enabled = true, onClick = {}) // block clicking inside from closing
                    .background(Color(0xFF1E1E1E), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0x20FFFFFF), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "🎉 所有未看微博已随机漫游完毕！",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF97316)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("返回信息流", color = Color.White)
                }
            }
        } else {
            // Exiting card (old card continuing off-screen)
            exitingStatus?.let { exitStatus ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    RoamingCard(
                        status = exitStatus,
                        isInteractive = false,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .fillMaxHeight(0.62f)
                            .graphicsLayer {
                                translationY = exitOffset.value
                                alpha = exitAlpha.value
                            }
                    )
                }
            }
            // Current card (or entering card)
            currentStatus?.let { status ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    RoamingCard(
                        status = status,
                        isInteractive = true,
                        modifier = Modifier
                            .fillMaxWidth(0.88f)
                            .fillMaxHeight(0.62f)
                            .clickable { onOpenDetail(status) }
                            .graphicsLayer {
                                translationY = if (isSwapping) enterOffset.value else dragOffset.value
                                if (!isSwapping) {
                                    val progress = (abs(dragOffset.value) / swipeThresholdPx).coerceIn(0f, 1f)
                                    alpha = 1f - progress * 0.3f
                                    scaleX = 1f - progress * 0.05f
                                    scaleY = 1f - progress * 0.05f
                                }
                            }
                            .swipeGestureModifier()
                    )
                }
            }
        }
    }
}

