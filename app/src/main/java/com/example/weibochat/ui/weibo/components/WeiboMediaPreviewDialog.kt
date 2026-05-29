package com.example.weibochat.ui.weibo.components

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.view.ViewGroup
import android.widget.Toast
import android.widget.VideoView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.SubcomposeAsyncImage
import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.WeiboTimelineStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import kotlin.math.abs

fun resolveImagePreview(
    status: WeiboTimelineStatus,
    index: Int,
    urls: List<String>
): Pair<List<MediaItem>, Int> {
    val clickedUrl = urls.getOrNull(index)
    val candidateMedia = listOfNotNull(status, status.retweeted_status)
        .map { getStatusMedia(it) }

    if (clickedUrl != null) {
        candidateMedia.forEach { mediaList ->
            val matchedIndex = mediaList.indexOfFirst {
                it.largeUrl == clickedUrl || it.thumbnailUrl == clickedUrl
            }
            if (matchedIndex != -1) {
                return Pair(mediaList, matchedIndex)
            }
        }
    }

    val fallbackList = if (urls.isNotEmpty()) {
        urls.map { url -> MediaItem(thumbnailUrl = url, largeUrl = url) }
    } else {
        candidateMedia.firstOrNull { it.isNotEmpty() }.orEmpty()
    }
    if (fallbackList.isEmpty()) {
        return Pair(emptyList(), 0)
    }
    return Pair(fallbackList, index.coerceIn(fallbackList.indices))
}

fun resolveVideoPreview(
    status: WeiboTimelineStatus,
    rawUrl: String
): Pair<List<MediaItem>, Int> {
    val isLive = rawUrl.endsWith("##live")
    val cleanUrl = if (isLive) rawUrl.removeSuffix("##live") else rawUrl
    val parts = cleanUrl.split("##")
    val videoUrl = parts[0]
    
    val candidateMedia = listOfNotNull(status, status.retweeted_status)
        .map { getStatusMedia(it) }

    candidateMedia.forEach { mediaList ->
        val index = mediaList.indexOfFirst {
            it.videoSrc == videoUrl || it.videoSrc?.contains(videoUrl) == true || videoUrl.contains(it.videoSrc ?: "___")
        }
        if (index != -1) {
            return Pair(mediaList, index)
        }
    }

    val fallbackList = candidateMedia.firstOrNull { it.isNotEmpty() }.orEmpty()
    return Pair(fallbackList, 0)
}

fun findStatusByImageUrl(statuses: List<WeiboTimelineStatus>, url: String): Pair<List<MediaItem>, Int>? {
    for (status in statuses) {
        val media = getStatusMedia(status)
        val idx = media.indexOfFirst { it.largeUrl == url || it.thumbnailUrl == url }
        if (idx != -1) {
            return Pair(media, idx)
        }
        val retweet = status.retweeted_status
        if (retweet != null) {
            val retweetMedia = getStatusMedia(retweet)
            val rIdx = retweetMedia.indexOfFirst { it.largeUrl == url || it.thumbnailUrl == url }
            if (rIdx != -1) {
                return Pair(retweetMedia, rIdx)
            }
        }
    }
    return null
}

fun findStatusByVideoUrl(statuses: List<WeiboTimelineStatus>, rawUrl: String): Pair<List<MediaItem>, Int>? {
    val isLive = rawUrl.endsWith("##live")
    val cleanUrl = if (isLive) rawUrl.removeSuffix("##live") else rawUrl
    val parts = cleanUrl.split("##")
    val videoUrl = parts[0]

    for (status in statuses) {
        val media = getStatusMedia(status)
        val idx = media.indexOfFirst {
            it.videoSrc == videoUrl || it.videoSrc?.contains(videoUrl) == true || videoUrl.contains(it.videoSrc ?: "___")
        }
        if (idx != -1) {
            return Pair(media, idx)
        }
        val retweet = status.retweeted_status
        if (retweet != null) {
            val retweetMedia = getStatusMedia(retweet)
            val rIdx = retweetMedia.indexOfFirst {
                it.videoSrc == videoUrl || it.videoSrc?.contains(videoUrl) == true || videoUrl.contains(it.videoSrc ?: "___")
            }
            if (rIdx != -1) {
                return Pair(retweetMedia, rIdx)
            }
        }
    }
    return null
}

private suspend fun PointerInputScope.detectTransformGesturesConditional(
    panZoomLock: Boolean = false,
    canPan: () -> Boolean,
    onGesture: (centroid: Offset, pan: Offset, zoom: Float, rotation: Float) -> Unit
) {
    awaitEachGesture {
        var rotation = 0f
        var zoom = 1f
        var pan = Offset.Zero
        var pastTouchPoints = false
        val touchSlop = viewConfiguration.touchSlop
        var lockedToPanZoomRatio = false

        awaitFirstDown(requireUnconsumed = false)
        do {
            val event = awaitPointerEvent()
            var canceled = false
            if (event.changes.any { it.isConsumed }) {
                canceled = true
            } else {
                val zoomChange = event.calculateZoom()
                val rotationChange = event.calculateRotation()
                val panChange = event.calculatePan()

                if (!pastTouchPoints) {
                    zoom *= zoomChange
                    rotation += rotationChange
                    pan += panChange

                    val centroidSize = event.calculateCentroidSize(useCurrent = false)
                    val zoomMotion = abs(1 - zoom) * centroidSize
                    val rotationMotion = abs(rotation) * java.lang.Math.PI.toFloat() * centroidSize / 180f
                    val panMotion = pan.getDistance()

                    if (zoomMotion > touchSlop ||
                        rotationMotion > touchSlop ||
                        panMotion > touchSlop
                    ) {
                        pastTouchPoints = true
                    }
                }

                if (pastTouchPoints) {
                    val centroid = event.calculateCentroid(useCurrent = false)
                    val effectivePan = if (canPan()) panChange else Offset.Zero
                    val handled = rotationChange != 0f || zoomChange != 1f || effectivePan != Offset.Zero
                    if (handled) {
                        onGesture(centroid, effectivePan, zoomChange, rotationChange)
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

private fun downloadVideoToGallery(context: Context, url: String, cookie: String = "") {
    try {
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val uri = Uri.parse(url)
        val request = DownloadManager.Request(uri).apply {
            setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setTitle("保存微博视频")
            setDescription("正在下载视频...")

            if (cookie.isNotEmpty()) {
                addRequestHeader("Cookie", cookie)
                addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36")
                addRequestHeader("Referer", "https://m.weibo.cn/")
            }

            val fileName = url.substringAfterLast("/").substringBefore("?")
            val finalName = if (fileName.contains(".")) fileName else "$fileName.mp4"

            setDestinationInExternalPublicDir(Environment.DIRECTORY_MOVIES, finalName)
        }
        downloadManager.enqueue(request)
        Toast.makeText(context, "开始下载视频...", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "下载失败: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
    }
}

private fun formatTime(ms: Int): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
}

private fun Modifier.mediaHorizontalSwipe(
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
): Modifier {
    if (!enabled) return this

    return pointerInput(onPrevious, onNext) {
        var totalDrag = Offset.Zero
        detectDragGestures(
            onDragStart = {
                totalDrag = Offset.Zero
            },
            onDragEnd = {
                val horizontalEnough = abs(totalDrag.x) > 96f && abs(totalDrag.x) > abs(totalDrag.y) * 1.2f
                if (horizontalEnough) {
                    if (totalDrag.x > 0f) {
                        onPrevious()
                    } else {
                        onNext()
                    }
                }
                totalDrag = Offset.Zero
            },
            onDragCancel = {
                totalDrag = Offset.Zero
            },
            onDrag = { change, dragAmount ->
                totalDrag += dragAmount
                if (abs(totalDrag.x) > abs(totalDrag.y)) {
                    change.consume()
                }
            }
        )
    }
}

private fun downloadWithRedirects(
    urlStr: String,
    repository: DataRepository,
    targetFile: File,
    context: Context
): Boolean {
    var currentUrl = urlStr
    var redirects = 0
    val maxRedirects = 5
    val userAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    val referer = "https://m.weibo.cn/"
    val cookieString = repository.getAllCookies()

    while (redirects < maxRedirects) {
        try {
            val url = java.net.URL(currentUrl)
            val conn = url.openConnection() as java.net.HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Referer", referer)
            if (cookieString.isNotBlank()) {
                conn.setRequestProperty("Cookie", cookieString)
            }
            conn.connectTimeout = 15000
            conn.readTimeout = 15000

            val status = conn.responseCode
            if (status == java.net.HttpURLConnection.HTTP_MOVED_TEMP ||
                status == java.net.HttpURLConnection.HTTP_MOVED_PERM ||
                status == 307 || status == 308 || status == 302 || status == 301) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (newUrl.isNullOrBlank()) {
                    return false
                }
                currentUrl = if (newUrl.startsWith("http")) {
                    newUrl
                } else {
                    val base = java.net.URL(currentUrl)
                    java.net.URL(base, newUrl).toString()
                }
                redirects++
                continue
            }

            if (status == java.net.HttpURLConnection.HTTP_OK) {
                val tmpFile = File(context.cacheDir, targetFile.name + ".tmp")
                try {
                    conn.inputStream.use { input ->
                        tmpFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    if (tmpFile.exists() && tmpFile.length() > 0) {
                        if (targetFile.exists()) targetFile.delete()
                        val renamed = tmpFile.renameTo(targetFile)
                        if (renamed) return true
                    }
                } catch (e: java.lang.Exception) {
                    e.printStackTrace()
                } finally {
                    conn.disconnect()
                    if (tmpFile.exists()) tmpFile.delete()
                }
                return false
            } else {
                conn.disconnect()
                return false
            }
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
            return false
        }
    }
    return false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPage(
    videoUrl: String,
    coverUrl: String?,
    isCurrentPage: Boolean,
    repository: DataRepository,
    onPreviousMedia: () -> Unit,
    onNextMedia: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val videoViewState = remember(videoUrl) { mutableStateOf<VideoView?>(null) }
    var isVideoPrepared by remember { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableStateOf(0) }
    var duration by remember { mutableStateOf(0) }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(videoUrl) {
        onDispose {
            videoViewState.value?.apply {
                stopPlayback()
                setMediaController(null)
            }
            videoViewState.value = null
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            videoViewState.value?.pause()
            isPlaying = false
        } else {
            if (isVideoPrepared) {
                videoViewState.value?.start()
                isPlaying = true
            }
        }
    }

    LaunchedEffect(isPlaying, controlsVisible) {
        if (isPlaying && controlsVisible) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewState.value?.let { vv ->
                if (vv.isPlaying) {
                    currentPosition = vv.currentPosition
                    duration = vv.duration
                }
            }
            delay(200)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .mediaHorizontalSwipe(
                enabled = isCurrentPage,
                onPrevious = onPreviousMedia,
                onNext = onNextMedia
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        controlsVisible = !controlsVisible
                    }
                )
            }
    ) {
        if (isCurrentPage) {
            AndroidView(
                factory = { context ->
                    VideoView(context).apply {
                        videoViewState.value = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        val isGroupVideo = videoUrl.contains("mss/msget")
                        if (isGroupVideo) {
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
                                "Referer" to "https://m.weibo.cn/",
                                "Cookie" to repository.getAllCookies()
                            )
                            setVideoURI(Uri.parse(videoUrl), headers)
                        } else {
                            setVideoURI(Uri.parse(videoUrl))
                        }

                        setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = false
                            duration = mediaPlayer.duration
                            isVideoPrepared = true
                            isPlaying = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!isVideoPrepared) {
            if (!coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = "Cover Image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color(0xFFF97316)
            )
        }

        if (isVideoPrepared) {
            val controlsAlpha by animateFloatAsState(
                targetValue = if (controlsVisible) 1f else 0f,
                animationSpec = tween(durationMillis = 300),
                label = "controlsAlpha"
            )

            if (controlsAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = controlsAlpha }
                ) {
                    IconButton(
                        onClick = {
                            downloadVideoToGallery(context, videoUrl, repository.getAllCookies())
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(20.dp)
                            .background(Color(0x66000000), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = "下载",
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(64.dp)
                            .background(Color(0x80000000), CircleShape)
                            .clickable {
                                videoViewState.value?.let { vv ->
                                    if (vv.isPlaying) {
                                        vv.pause()
                                        isPlaying = false
                                    } else {
                                        vv.start()
                                        isPlaying = true
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "暂停" else "播放",
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color(0x99000000))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                    ) {
                        Slider(
                            value = currentPosition.toFloat(),
                            onValueChange = { newValue ->
                                currentPosition = newValue.toInt()
                                videoViewState.value?.seekTo(currentPosition)
                            },
                            valueRange = 0f..duration.toFloat().coerceAtLeast(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = Color(0xFFF97316),
                                activeTrackColor = Color(0xFFF97316),
                                inactiveTrackColor = Color(0x33FFFFFF)
                            ),
                            modifier = Modifier.fillMaxWidth(),
                            thumb = { _ ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .offset(x = 4.dp, y = 4.dp)
                                        .background(Color(0xFFF97316), CircleShape)
                                )
                            },
                            track = { _ ->
                                Canvas(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.dp)
                                ) {
                                    val width = size.width
                                    val height = size.height
                                    val fraction = currentPosition.toFloat() / duration.toFloat().coerceAtLeast(1f)
                                    val activeWidth = width * fraction

                                    drawRoundRect(
                                        color = Color(0x33FFFFFF),
                                        size = size,
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                    )

                                    drawRoundRect(
                                        color = Color(0xFFF97316),
                                        size = androidx.compose.ui.geometry.Size(activeWidth, height),
                                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx(), 1.dp.toPx())
                                    )
                                }
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentStr = formatTime(currentPosition)
                            val durationStr = formatTime(duration)
                            Text(
                                text = "$currentStr / $durationStr",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LivePhotoPage(
    videoUrl: String,
    coverUrl: String?,
    isCurrentPage: Boolean,
    repository: DataRepository,
    onPreviousMedia: () -> Unit,
    onNextMedia: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val videoViewState = remember(videoUrl) { mutableStateOf<VideoView?>(null) }
    var isVideoPrepared by remember { mutableStateOf(false) }
    var localVideoPath by remember { mutableStateOf<String?>(null) }
    var isDownloading by remember { mutableStateOf(true) }

    DisposableEffect(videoUrl) {
        onDispose {
            videoViewState.value?.apply {
                stopPlayback()
            }
            videoViewState.value = null
        }
    }

    LaunchedEffect(videoUrl) {
        isDownloading = true
        val downloadedFile = withContext(Dispatchers.IO) {
            try {
                val fileName = "live_photo_" + videoUrl.hashCode() + ".mov"
                val cacheFile = File(context.cacheDir, fileName)
                if (cacheFile.exists() && cacheFile.length() > 0) {
                    cacheFile
                } else {
                    val success = downloadWithRedirects(videoUrl, repository, cacheFile, context)
                    if (success && cacheFile.exists() && cacheFile.length() > 0) cacheFile else null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                null
            }
        }
        isDownloading = false
        if (downloadedFile != null) {
            localVideoPath = downloadedFile.absolutePath
        } else {
            localVideoPath = videoUrl
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage) {
            videoViewState.value?.pause()
        } else {
            if (isVideoPrepared) {
                videoViewState.value?.start()
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .mediaHorizontalSwipe(
                enabled = isCurrentPage,
                onPrevious = onPreviousMedia,
                onNext = onNextMedia
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onDismiss() })
            }
    ) {
        val currentPath = localVideoPath
        if (currentPath != null && isCurrentPage) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        videoViewState.value = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        if (currentPath.startsWith("http")) {
                            val headers = mapOf(
                                "User-Agent" to "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
                                "Referer" to "https://m.weibo.cn/",
                                "Cookie" to repository.getAllCookies()
                            )
                            setVideoURI(Uri.parse(currentPath), headers)
                        } else {
                            setVideoPath(currentPath)
                        }

                        setOnPreparedListener { mediaPlayer ->
                            mediaPlayer.isLooping = true
                            isVideoPrepared = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        if (!coverUrl.isNullOrBlank() && (!isVideoPrepared || isDownloading)) {
            AsyncImage(
                model = coverUrl,
                contentDescription = "Cover Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        if (isDownloading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .size(36.dp)
                    .align(Alignment.Center),
                color = Color(0xFFF97316),
                strokeWidth = 3.dp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeiboMediaPreviewDialog(
    mediaItems: List<MediaItem>,
    initialIndex: Int,
    repository: DataRepository,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val dragOffsetY = remember { Animatable(0f) }
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var imageActionMenuUrl by remember { mutableStateOf<String?>(null) }
    var imageActionMenuPage by remember { mutableStateOf(-1) }
    var originalPages by remember { mutableStateOf(setOf<Int>()) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val pagerState = rememberPagerState(initialPage = initialIndex) { mediaItems.size }

        LaunchedEffect(pagerState.currentPage) {
            scale = 1f
            offset = Offset.Zero
            dragOffsetY.snapTo(0f)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = (1f - (abs(dragOffsetY.value) / 1000f)).coerceIn(0f, 1f)))
                .offset { androidx.compose.ui.unit.IntOffset(0, dragOffsetY.value.toInt()) }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {},
                        onDragEnd = {
                            if (scale <= 1f && abs(dragOffsetY.value) > 300f) {
                                onDismiss()
                            } else {
                                coroutineScope.launch {
                                    dragOffsetY.animateTo(0f, spring())
                                }
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            if (scale <= 1f) {
                                change.consume()
                                coroutineScope.launch {
                                    dragOffsetY.snapTo(dragOffsetY.value + dragAmount)
                                }
                            }
                        }
                    )
                }
        ) {
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = scale <= 1f,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val item = mediaItems[page]
                val isCurrentPage = pagerState.currentPage == page

                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.isLivePhoto && !item.videoSrc.isNullOrBlank()) {
                        LivePhotoPage(
                            videoUrl = item.videoSrc,
                            coverUrl = item.largeUrl,
                            isCurrentPage = isCurrentPage,
                            repository = repository,
                            onPreviousMedia = {
                                if (page > 0) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(page - 1) }
                                }
                            },
                            onNextMedia = {
                                if (page < mediaItems.lastIndex) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(page + 1) }
                                }
                            },
                            onDismiss = onDismiss
                        )
                    } else if (item.isVideo && !item.videoSrc.isNullOrBlank()) {
                        VideoPage(
                            videoUrl = item.videoSrc,
                            coverUrl = item.largeUrl,
                            isCurrentPage = isCurrentPage,
                            repository = repository,
                            onPreviousMedia = {
                                if (page > 0) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(page - 1) }
                                }
                            },
                            onNextMedia = {
                                if (page < mediaItems.lastIndex) {
                                    coroutineScope.launch { pagerState.animateScrollToPage(page + 1) }
                                }
                            },
                            onDismiss = onDismiss
                        )
                    } else {
                        var isLongImage by remember(item.largeUrl) { mutableStateOf<Boolean?>(null) }
                        val imageModel = remember(item.largeUrl, originalPages) {
                            coil.request.ImageRequest.Builder(context)
                                .data(item.largeUrl)
                                .crossfade(true)
                                .apply {
                                    val cookie = repository.getAllCookies()
                                    if (cookie.isNotEmpty()) {
                                        addHeader("Cookie", cookie)
                                        addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                                        addHeader("Referer", "https://weibo.com/")
                                    }
                                    if (originalPages.contains(page)) {
                                        size(coil.size.Size.ORIGINAL)
                                    }
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
                                    detectTransformGesturesConditional(
                                        panZoomLock = true,
                                        canPan = { scale > 1f }
                                    ) { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 5f)
                                        scale = newScale
                                        if (scale > 1f && pan != Offset.Zero) {
                                            offset = Offset(
                                                x = offset.x + pan.x * scale,
                                                y = offset.y + pan.y * scale
                                            )
                                        } else if (scale <= 1f) {
                                            offset = Offset.Zero
                                        }
                                    }
                                }
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onTap = {
                                            if (scale <= 1f) onDismiss()
                                        },
                                        onDoubleTap = {
                                            if (scale > 1f) {
                                                scale = 1f
                                                offset = Offset.Zero
                                            } else {
                                                scale = 2.5f
                                            }
                                        },
                                        onLongPress = {
                                            imageActionMenuUrl = item.largeUrl
                                            imageActionMenuPage = page
                                        }
                                    )
                                }
                        ) {
                            val longImage = isLongImage
                            if (longImage == true) {
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
                                        modifier = Modifier.fillMaxWidth().wrapContentHeight()
                                            .graphicsLayer(
                                                scaleX = scale,
                                                scaleY = scale,
                                                translationX = offset.x,
                                                translationY = offset.y
                                            ),
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
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .align(Alignment.Center)
                                        .graphicsLayer(
                                            scaleX = scale,
                                            scaleY = scale,
                                            translationX = offset.x,
                                            translationY = offset.y
                                        ),
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
                        }
                    }
                }
            }

            if (mediaItems.size > 1) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 24.dp)
                        .background(Color(0x77000000), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "${pagerState.currentPage + 1}/${mediaItems.size}",
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
                    titleContentColor = Color.White,
                    textContentColor = Color.Gray,
                    title = { Text("图片操作", color = Color.White) },
                    text = { Text("选择对这张图片的操作", color = Color.Gray) },
                    confirmButton = {
                        TextButton(onClick = {
                            if (imageActionMenuPage != -1) {
                                originalPages = originalPages + imageActionMenuPage
                            }
                            imageActionMenuUrl = null
                        }) {
                            Text("查看原图", color = Color(0xFFF97316))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            downloadImageToGallery(context, url, repository.getAllCookies())
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
