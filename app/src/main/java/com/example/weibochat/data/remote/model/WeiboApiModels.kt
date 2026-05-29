package com.example.weibochat.data

class WeiboRiskControlException(message: String) : java.io.IOException(message)

const val DEFAULT_WEIBO_TIMELINE_LIST_ID = "110006355013357"

data class WeiboUser(
    val id: Long,
    val screen_name: String,
    val profile_image_url: String?
)

data class WeiboUrlInfo(
    val url_short: String?,
    val url_long: String?,
    val title: String?,
    val description: String?
)

data class WeiboStatusUser(
    val screen_name: String?
)

data class WeiboUrlStatus(
    val text: String?,
    val thumbnail_pic: String?,
    val original_pic: String?,
    val user: WeiboStatusUser?
)

data class WeiboUrlObject(
    val url_ori: String?,
    val info: WeiboUrlInfo?,
    val status: WeiboUrlStatus?
)

data class WeiboMessageAnnotations(
    val video_pic_fid: Long? = null
)

data class WeiboMessage(
    val id: Long,
    val content: String,
    val from_uid: Long,
    val time: Long,
    val from_user: WeiboUser?,
    val media_type: Int? = null,
    val fids: List<Long>? = null,
    val url_objects: List<WeiboUrlObject>? = null,
    val annotations: WeiboMessageAnnotations? = null
)

data class WeiboGroupMessagesResponse(
    val result: Boolean,
    val last_read_mid: Long?,
    val messages: List<WeiboMessage>?
)

data class WeiboSendMessageResponse(
    val result: Boolean,
    val id: Long?,
    val mid: Long?,
    val time: Long?,
    val ts: Long?,
    val content: String?,
    val from_uid: Long?,
    val from_user: WeiboUser?
)

// QR Code Login Data Models
data class WeiboQrCodeData(
    val qrid: String,
    val image: String
)

data class WeiboQrCodeResponse(
    val retcode: Int,
    val msg: String?,
    val data: WeiboQrCodeData?
)

data class WeiboQrStatusData(
    val alt: String?
)

data class WeiboQrStatusResponse(
    val retcode: Int,
    val msg: String?,
    val data: WeiboQrStatusData?
)

data class WeiboSsoLoginResponse(
    val retcode: Int,
    val uid: String?,
    val crossDomainUrlList: List<String>?
)

// Mobile group chat response models (used internally by WeiboApiClient)
internal data class WeiboMobileGroupMessagesResponse(
    val ok: Int?,
    val msg: String?,
    val data: WeiboMobileGroupMessagesData?
)

internal data class WeiboMobileGroupMessagesData(
    val msgs: List<WeiboMobileGroupMessage>?,
    val users: Map<String, WeiboMobileGroupUser>?,
    val last_read_mid: Long?,
    val title: String?,
    val ts: Long?
)

internal data class WeiboMobileGroupMessage(
    val id: Long?,
    val created_at: Long?,
    val sender_id: Long?,
    val sender_screen_name: String?,
    val text: String?,
    val media_type: Int?,
    val type: Int?,
    val fids: List<Long>?,
    val url_objects: List<WeiboUrlObject>?,
    val annotations: WeiboMessageAnnotations? = null
)

internal data class WeiboMobileGroupUser(
    val id: Long?,
    val screen_name: String?,
    val profile_image_url: String?,
    val avatar_large: String?
)
