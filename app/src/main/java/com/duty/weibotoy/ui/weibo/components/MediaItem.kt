package com.duty.weibotoy.ui.weibo.components

import com.duty.weibotoy.data.WeiboTimelinePageInfo

data class MediaItem(
    val thumbnailUrl: String,
    val largeUrl: String,
    val width: Int? = null,
    val height: Int? = null,
    val isVideo: Boolean = false,
    val isLivePhoto: Boolean = false,
    val videoSrc: String? = null,
    val duration: Int? = null
)

internal data class MediaItemInfo(
    val url: String,
    val title: String,
    val isImage: Boolean,
    val isVideo: Boolean,
    val pageInfo: WeiboTimelinePageInfo?
)
