package com.duty.weibotoy.ui.weibo.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.duty.weibotoy.data.DataRepository
import com.duty.weibotoy.data.WeiboTimelineStatus
import com.duty.weibotoy.theme.BubbleBg
import com.duty.weibotoy.theme.DarkBg
import com.duty.weibotoy.theme.TextGrey
import com.duty.weibotoy.theme.TextWhite
import com.duty.weibotoy.ui.weibo.seedWeiboWebViewCookies
import kotlinx.coroutines.launch
import kotlin.math.abs

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
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            seedWeiboWebViewCookies(repository.getAllCookies())
                            android.webkit.WebView(context).apply {
                                webViewState.value = this
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
                                settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                cookieManager.setAcceptThirdPartyCookies(this, true)
                                webViewClient = object : android.webkit.WebViewClient() {
                                    override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                        isLoading = true
                                    }

                                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                        cookieManager.flush()
                                        isLoading = false
                                    }
                                }
                                tag = url
                                loadUrl(url)
                            }
                        },
                        update = { webView ->
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            seedWeiboWebViewCookies(repository.getAllCookies())
                            cookieManager.setAcceptThirdPartyCookies(webView, true)
                            webViewState.value = webView
                            if (webView.tag != url) {
                                webView.tag = url
                                webView.loadUrl(url)
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

    LaunchedEffect(isSwapping) {
        if (!isSwapping) return@LaunchedEffect
        val swipedUp = dragOffset.value < 0
        val newStatus = if (swipedUp) nextStatus else previousStatus
        if (newStatus == null) {
            dragOffset.animateTo(0f, spring())
            isSwapping = false
            return@LaunchedEffect
        }
        exitingStatus = currentStatus
        val exitTarget = if (swipedUp) -screenHeightPx.toFloat() else screenHeightPx.toFloat()
        val enterStart = if (swipedUp) screenHeightPx.toFloat() else -screenHeightPx.toFloat()
        enterOffset.snapTo(enterStart)
        launch { exitOffset.animateTo(exitTarget, tween(300, easing = FastOutSlowInEasing)) }
        launch { exitAlpha.animateTo(0f, tween(300)) }
        if (swipedUp) {
            previousStatus = currentStatus
            currentStatus = newStatus
        } else {
            currentStatus = newStatus
            previousStatus = null
        }
        dragOffset.snapTo(0f)
        enterOffset.animateTo(0f, tween(300, easing = FastOutSlowInEasing))
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
        val user = status.user
        val screenName = user?.screen_name ?: "新浪用户"
        val avatarUrl = user?.profile_image_url
        val createdAt = status.created_at ?: ""
        val sourceClean = status.source?.replace(Regex("<[^>]*>"), "") ?: ""
        val detailUrl = statusDetailUrl(status)

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
                    fontSize = 15,
                    lineHeight = 22,
                    detailUrl = detailUrl,
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
                Spacer(modifier = Modifier.height(10.dp))
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
                    .clickable(enabled = true, onClick = {})
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
