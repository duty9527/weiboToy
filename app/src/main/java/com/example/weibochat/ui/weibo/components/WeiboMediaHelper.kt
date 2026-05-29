package com.example.weibochat.ui.weibo.components

import android.net.Uri
import com.example.weibochat.data.WeiboTimelinePageInfo
import com.example.weibochat.data.WeiboTimelineStatus

fun extractLivePhotoVideoUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.contains("livephoto=")) {
        try {
            val uri = Uri.parse(url)
            val livePhotoParam = uri.getQueryParameter("livephoto")
            if (!livePhotoParam.isNullOrBlank()) {
                return livePhotoParam
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    return url
}

fun getStatusMedia(status: WeiboTimelineStatus): List<MediaItem> {
    val pics = status.pics
    if (!pics.isNullOrEmpty()) {
        return pics.map { pic ->
            val isLive = pic.type == "livephoto"
            val isVideo = pic.type == "video" || isLive
            val largeUrl = pic.large?.url ?: pic.url ?: ""
            val thumbUrl = pic.bmiddle?.url ?: pic.url ?: largeUrl
            val videoSrc = pic.videoSrc
            MediaItem(
                thumbnailUrl = thumbUrl,
                largeUrl = largeUrl,
                width = pic.large?.width ?: pic.bmiddle?.width,
                height = pic.large?.height ?: pic.bmiddle?.height,
                isVideo = isVideo,
                isLivePhoto = isLive,
                videoSrc = videoSrc,
                duration = pic.duration
            )
        }.filter { it.largeUrl.isNotBlank() }
    }

    val picIds = status.pic_ids.orEmpty()
    val picInfos = status.pic_infos
    if (picIds.isNotEmpty() && picInfos != null) {
        return picIds.mapNotNull { id ->
            val info = picInfos[id] ?: return@mapNotNull null
            val largeUrl = info.large?.url ?: info.bmiddle?.url ?: "https://wx3.sinaimg.cn/large/$id.jpg"
            val thumbUrl = info.bmiddle?.url ?: info.thumbnail?.url ?: largeUrl
            MediaItem(
                thumbnailUrl = thumbUrl,
                largeUrl = largeUrl,
                width = info.large?.width ?: info.bmiddle?.width ?: info.original?.width,
                height = info.large?.height ?: info.bmiddle?.height ?: info.original?.height
            )
        }
    }

    // Fallback: Check if page_info contains a video page
    val pageInfo = getStatusPageInfo(status)
    if (pageInfo != null) {
        val isVideoPage = pageInfo.type == "video" || pageInfo.media_info != null || pageInfo.object_type == "video"
        if (isVideoPage) {
            val videoUrl = directVideoUrl(pageInfo)
            if (!videoUrl.isNullOrBlank()) {
                val imageUrl = pageInfo.page_pic?.url ?: ""
                return listOf(
                    MediaItem(
                        thumbnailUrl = imageUrl,
                        largeUrl = imageUrl,
                        width = pageInfo.page_pic?.width,
                        height = pageInfo.page_pic?.height,
                        isVideo = true,
                        isLivePhoto = false,
                        videoSrc = normalizeWeiboUrl(videoUrl)
                    )
                )
            }
        }
    }

    return emptyList()
}

fun getStatusPageInfo(status: WeiboTimelineStatus): WeiboTimelinePageInfo? {
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

fun isTopicOrLocationPageInfo(pageInfo: WeiboTimelinePageInfo): Boolean {
    val isLoc = pageInfo.type == "place" || pageInfo.object_type == "place" || pageInfo.type == "location" || pageInfo.object_type == "location"
    val isTop = pageInfo.type == "topic" || pageInfo.object_type == "topic" ||
                 pageInfo.type == "search" || pageInfo.type == "search_topic" || pageInfo.object_type == "directory" || pageInfo.object_type == "search" ||
                 (pageInfo.content2?.contains("讨论") == true && pageInfo.content2.contains("阅读")) ||
                 (pageInfo.content1?.contains("讨论") == true && pageInfo.content1.contains("阅读"))
    return isLoc || isTop
}

fun directVideoUrl(pageInfo: WeiboTimelinePageInfo): String? {
    return pageInfo.media_info?.stream_url_hd
        ?: pageInfo.media_info?.stream_url
        ?: pageInfo.media_info?.mp4_720p_mp4
        ?: pageInfo.media_info?.mp4_hd_url
        ?: pageInfo.media_info?.mp4_sd_url
}

fun normalizeWeiboUrl(url: String): String {
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://m.weibo.cn$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        else -> "https://m.weibo.cn/$url"
    }
}
