package com.example.weibochat.ui.weibo

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.theme.BubbleBg
import com.example.weibochat.theme.DarkBg
import com.example.weibochat.theme.DividerGrey
import com.example.weibochat.ui.weibo.TimelineUiState
import com.example.weibochat.ui.weibo.components.*
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
    val statusComments by viewModel.statusComments.collectAsState()
    val pagedStatuses = viewModel.timelinePagingData.collectAsLazyPagingItems()
    val loadedStatuses = pagedStatuses.itemSnapshotList.items

    var activeMediaPreview by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var activeWebUrl by remember { mutableStateOf<String?>(null) }
    var activeDetailStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }
    val unviewedStatuses = remember(loadedStatuses, readStatusIds) {
        loadedStatuses.filter { status ->
            val isGap = status.raw_text?.startsWith("__GAP__:") == true
            val id = status.idstr ?: status.id?.toString()
            !isGap && (id == null || id !in readStatusIds)
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

    LaunchedEffect(loadedStatuses) {
        viewModel.updateLoadedStatuses(loadedStatuses)
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
        when {
            pagedStatuses.loadState.refresh is LoadState.Loading && pagedStatuses.itemCount == 0 -> {
                WeiboSkeletonList()
            }
            uiState is TimelineUiState.Error && pagedStatuses.itemCount == 0 -> {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = (uiState as TimelineUiState.Error).message,
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
            else -> {
                val listState = rememberLazyListState()

                LaunchedEffect(viewModel) {
                    viewModel.scrollToTopEvents.collect {
                        listState.scrollToItem(0)
                    }
                }

                val shouldLoadMore = remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                        lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
                    }
                }

                LaunchedEffect(shouldLoadMore.value) {
                    if (shouldLoadMore.value && pagedStatuses.loadState.append !is LoadState.Loading) {
                        viewModel.loadMore()
                    }
                }

                val currentStatuses by rememberUpdatedState(loadedStatuses)
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
                        items(
                            count = pagedStatuses.itemCount,
                            key = { index -> pagedStatuses.peek(index)?.let { it.idstr ?: it.id?.toString() } ?: "weibo_placeholder_$index" }
                        ) { index ->
                            val status = pagedStatuses[index]
                            if (status != null) {
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
                                        LaunchedEffect(statusId) {
                                            viewModel.loadCommentsForStatus(statusId)
                                        }
                                    }
                                    val isRead = statusId != null && readStatusIds.contains(statusId)
                                    val comments = statusId?.let { statusComments[it] }.orEmpty()
                                    WeiboCard(
                                        status = status,
                                        isRead = isRead,
                                        comments = comments,
                                        onImageClick = { imageIndex, urls ->
                                            activeMediaPreview = resolveImagePreview(status, imageIndex, urls)
                                        },
                                        onVideoClick = { url ->
                                            activeMediaPreview = resolveVideoPreview(status, url)
                                        },
                                        onWebClick = ::handleWebClick,
                                        onOpenDetail = { openStatusDetail(it) },
                                        onLikeClick = {
                                            val requestStatusId = statusId(status)
                                            if (requestStatusId != null) {
                                                viewModel.toggleLikeStatus(requestStatusId)
                                            }
                                        }
                                    )
                                }
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

                    var hasScrolledToLastViewed by remember { mutableStateOf(false) }
                    val lastViewed = remember { viewModel.getLastViewedWeibo() }

                    if (lastViewed != null && !hasScrolledToLastViewed && loadedStatuses.isNotEmpty() && listState.firstVisibleItemIndex == 0) {
                        val targetIndex = loadedStatuses.indexOfFirst {
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
                onImageClick = { index, urls ->
                    activeMediaPreview = resolveImagePreview(status, index, urls)
                },
                onVideoClick = { url ->
                    activeMediaPreview = resolveVideoPreview(status, url)
                },
                onWebClick = ::handleWebClick,
                onOpenDetail = { openStatusDetail(it) }
            )
        }

        activeMediaPreview?.let { (mediaList, index) ->
            WeiboMediaPreviewDialog(
                mediaItems = mediaList,
                initialIndex = index,
                repository = repository,
                onDismiss = { activeMediaPreview = null }
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
                    onImageClick = { index, urls ->
                        val clickedUrl = urls.getOrNull(index) ?: ""
                        val allStatuses = loadedStatuses
                        val resolved = findStatusByImageUrl(allStatuses, clickedUrl)
                        activeMediaPreview = resolved ?: Pair(urls.map { MediaItem(thumbnailUrl = it, largeUrl = it) }, index)
                    },
                    onVideoClick = { url ->
                        val allStatuses = loadedStatuses
                        val resolved = findStatusByVideoUrl(allStatuses, url)
                        if (resolved != null) {
                            activeMediaPreview = resolved
                        } else {
                            val isLive = url.endsWith("##live")
                            val cleanUrl = if (isLive) url.removeSuffix("##live") else url
                            val parts = cleanUrl.split("##")
                            val videoUrl = parts[0]
                            val coverUrl = parts.getOrNull(1)
                            val item = MediaItem(
                                thumbnailUrl = coverUrl ?: "",
                                largeUrl = coverUrl ?: "",
                                isVideo = true,
                                isLivePhoto = isLive,
                                videoSrc = videoUrl
                            )
                            activeMediaPreview = Pair(listOf(item), 0)
                        }
                    },
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
