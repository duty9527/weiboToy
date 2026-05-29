package com.duty.weibotoy.ui.weibo.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.duty.weibotoy.data.WeiboTimelinePageInfo
import com.duty.weibotoy.data.WeiboTimelineStatus
import com.duty.weibotoy.theme.TextGrey
import com.duty.weibotoy.theme.TextWhite
import com.duty.weibotoy.ui.weibo.isAllowedWeiboHost

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

@Composable
fun WeiboMediaGrid(
    mediaItems: List<MediaItem>,
    onImageClick: (Int, List<String>) -> Unit,
    onVideoClick: (String) -> Unit
) {
    val count = mediaItems.size
    if (count == 0) return

    val imageUrls = remember(mediaItems) { mediaItems.map { it.largeUrl } }
    val cornerRadius = 4.dp

    @Composable
    fun MediaCell(item: MediaItem, index: Int, modifier: Modifier, showPlayButton: Boolean = true) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(cornerRadius))
                .clickable {
                    if (item.isLivePhoto && !item.videoSrc.isNullOrBlank()) {
                        onVideoClick(item.videoSrc + "##" + item.largeUrl + "##live")
                    } else if (item.isVideo && !item.videoSrc.isNullOrBlank()) {
                        onVideoClick(item.videoSrc + "##" + item.largeUrl)
                    } else {
                        onImageClick(index, imageUrls)
                    }
                }
        ) {
            AsyncImage(
                model = item.largeUrl,
                contentDescription = if (item.isVideo) "Video" else "Image",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            if (item.isLivePhoto) {
                LivePhotoBadge(modifier = Modifier.align(Alignment.BottomEnd))
            } else if (item.isVideo && showPlayButton) {
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
    }

    if (count == 1) {
        val item = mediaItems.first()
        if (item.isVideo) {
            Box(
                modifier = Modifier
                    .wrapContentSize(Alignment.CenterStart)
                    .clip(RoundedCornerShape(cornerRadius))
                    .clickable {
                        if (!item.videoSrc.isNullOrBlank()) {
                            if (item.isLivePhoto) {
                                onVideoClick(item.videoSrc + "##" + item.largeUrl + "##live")
                            } else {
                                onVideoClick(item.videoSrc + "##" + item.largeUrl)
                            }
                        }
                    }
            ) {
                AsyncImage(
                    model = item.largeUrl,
                    contentDescription = if (item.isLivePhoto) "Live Photo" else "Video",
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .heightIn(max = 360.dp),
                    contentScale = ContentScale.Crop
                )
                if (item.isLivePhoto) {
                    LivePhotoBadge(modifier = Modifier.align(Alignment.BottomEnd))
                } else {
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
            }
        } else {
            val image = item
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
                    .clickable { onImageClick(0, imageUrls) }
            ) {
                AsyncImage(
                    model = image.largeUrl,
                    contentDescription = "Image",
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
        }
    } else if (count <= 3) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            for (i in 0 until count) {
                MediaCell(
                    item = mediaItems[i],
                    index = i,
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
                        val isLastVisible = index == maxVisible - 1 && remaining > 0

                        if (index < maxVisible) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .clip(RoundedCornerShape(cornerRadius))
                                    .clickable {
                                        if (isLastVisible) {
                                            onImageClick(maxVisible, imageUrls)
                                        } else if (mediaItems[index].isVideo && !mediaItems[index].videoSrc.isNullOrBlank()) {
                                            if (mediaItems[index].isLivePhoto) {
                                                onVideoClick(mediaItems[index].videoSrc!! + "##" + mediaItems[index].largeUrl + "##live")
                                            } else {
                                                onVideoClick(mediaItems[index].videoSrc!! + "##" + mediaItems[index].largeUrl)
                                            }
                                        } else {
                                            onImageClick(index, imageUrls)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = mediaItems[index].largeUrl,
                                    contentDescription = if (mediaItems[index].isVideo) "Video $index" else "Image $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (mediaItems[index].isLivePhoto) {
                                    LivePhotoBadge(modifier = Modifier.align(Alignment.BottomEnd))
                                } else if (mediaItems[index].isVideo && !isLastVisible) {
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
                                    .clickable {
                                        if (isLastVisible) {
                                            onImageClick(maxVisible, imageUrls)
                                        } else if (mediaItems[index].isVideo && !mediaItems[index].videoSrc.isNullOrBlank()) {
                                            if (mediaItems[index].isLivePhoto) {
                                                onVideoClick(mediaItems[index].videoSrc!! + "##" + mediaItems[index].largeUrl + "##live")
                                            } else {
                                                onVideoClick(mediaItems[index].videoSrc!! + "##" + mediaItems[index].largeUrl)
                                            }
                                        } else {
                                            onImageClick(index, imageUrls)
                                        }
                                    }
                            ) {
                                AsyncImage(
                                    model = mediaItems[index].largeUrl,
                                    contentDescription = if (mediaItems[index].isVideo) "Video $index" else "Image $index",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                                if (mediaItems[index].isLivePhoto) {
                                    LivePhotoBadge(modifier = Modifier.align(Alignment.BottomEnd))
                                } else if (mediaItems[index].isVideo && !isLastVisible) {
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

@Composable
fun AppleLiveIcon(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier.size(14.dp)) {
        val center = androidx.compose.ui.geometry.Offset(size.width / 2, size.height / 2)
        // 1. Center dot
        drawCircle(
            color = Color.White,
            radius = size.width * 0.12f,
            center = center
        )
        // 2. Middle ring
        drawCircle(
            color = Color.White,
            radius = size.width * 0.24f,
            center = center,
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.dp.toPx())
        )
        // 3. Outer dotted ring
        val dotCount = 24
        val outerRadius = size.width * 0.44f
        for (i in 0 until dotCount) {
            val angle = (i * 2 * Math.PI / dotCount).toFloat()
            val x = center.x + outerRadius * kotlin.math.cos(angle)
            val y = center.y + outerRadius * kotlin.math.sin(angle)
            drawCircle(
                color = Color.White.copy(alpha = 0.75f),
                radius = 0.5.dp.toPx(),
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}

@Composable
fun LivePhotoBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .background(Color(0x66000000), CircleShape)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        AppleLiveIcon()
    }
}
