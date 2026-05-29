package com.example.weibochat.data

// Contacts / Groups Data Models
data class WeiboContactUser(
    val id: Long,
    val idstr: String,
    val name: String,
    val type: Int,
    val group_type: Int?,
    val profile_image_url: String?,
    val round_profile_image_url: String?
)

data class WeiboContactMessage(
    val text: String?,
    val created_at: String?,
    val sender_screen_name: String?
)

data class WeiboContact(
    val unread_count: Int,
    val user: WeiboContactUser?,
    val message: WeiboContactMessage?
)

data class WeiboContactsResponse(
    val contacts: List<WeiboContact>?
)
