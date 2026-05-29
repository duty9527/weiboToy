package com.duty.weibotoy.data

// Timeline Data Models
data class WeiboTimelineUser(
    val id: Long?,
    val idstr: String?,
    val screen_name: String?,
    val profile_image_url: String?,
    val avatar_large: String?
)

data class WeiboPicObject(
    val url: String?,
    val width: Int?,
    val height: Int?
)

data class WeiboPicInfo(
    val thumbnail: WeiboPicObject?,
    val bmiddle: WeiboPicObject?,
    val large: WeiboPicObject?,
    val original: WeiboPicObject?
)

data class WeiboTimelineMediaInfo(
    val stream_url: String?,
    val stream_url_hd: String?,
    val mp4_sd_url: String?,
    val mp4_hd_url: String?,
    val mp4_720p_mp4: String?,
    val h5_url: String?
)

data class WeiboTimelinePageInfo(
    val type: String?,
    val title: String?,
    val page_title: String?,
    val content1: String?,
    val content2: String?,
    val page_url: String?,
    val object_type: String?,
    val page_pic: WeiboPicObject?,
    val media_info: WeiboTimelineMediaInfo?
)

data class WeiboPic(
    val pid: String?,
    val url: String?,
    val large: WeiboPicObject?,
    val bmiddle: WeiboPicObject?,
    val type: String? = null,
    @com.google.gson.annotations.SerializedName("videoSrc")
    val videoSrc: String? = null,
    val duration: Int? = null
)

data class WeiboUrlStruct(
    val url_title: String?,
    val url_type_pic: String?,
    val ori_url: String?,
    val short_url: String?,
    val long_url: String?,
    val page_id: String?,
    val page_info: WeiboTimelinePageInfo?
)

data class WeiboStyleConfig(
    val remove_blank_line_flag: Int? = null,
    val md_render_mark: Int? = null
)

data class WeiboTimelineStatus(
    val id: Long?,
    val idstr: String?,
    val created_at: String?,
    val raw_text: String?,
    val text_raw: String?,
    val text: String?,
    val source: String?,
    val isLongText: Boolean?,
    val user: WeiboTimelineUser?,
    val pic_ids: List<String>?,
    val pic_infos: Map<String, WeiboPicInfo>?,
    val retweeted_status: WeiboTimelineStatus?,
    val page_info: WeiboTimelinePageInfo?,
    val reposts_count: Int?,
    val comments_count: Int?,
    val attitudes_count: Int?,
    val pics: List<WeiboPic>? = null,
    val url_struct: List<WeiboUrlStruct>? = null,
    val md_render_mark: Int? = null,
    val style_config: WeiboStyleConfig? = null,
    val liked: Boolean? = null,
    val mblogid: String? = null
)

data class WeiboTimelineResponse(
    val statuses: List<WeiboTimelineStatus>?,
    val since_id: Long?,
    val max_id: Long?,
    val total_number: Int?,
    val ok: Int? = null,
    val url: String? = null,
    val msg: String? = null
)

// Mobile timeline response models (used internally by WeiboApiClient)
internal data class WeiboMobileFriendsTimelineResponse(
    val ok: Int?,
    val http_code: Int?,
    val msg: String?,
    val data: WeiboMobileFriendsTimelineData?
)

internal data class WeiboMobileFriendsTimelineData(
    val statuses: List<WeiboTimelineStatus>?,
    val since_id: Long?,
    val max_id: Long?,
    val next_cursor: Long?,
    val total_number: Int?,
    val has_unread: Int?
)

// Status extend response models
internal data class WeiboStatusExtendResponse(
    val ok: Int?,
    val msg: String?,
    val data: WeiboStatusExtendData?
)

internal data class WeiboStatusExtendData(
    val longTextContent: String?
)
