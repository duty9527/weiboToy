package com.duty.weibotoy.data.repository

interface AuthRepository {
    fun saveCredentials(cookie: String, groupId: String)
    fun getCredentials(): Pair<String, String>
    fun saveMobileCookie(cookie: String)
    fun getMobileCookie(): String
    fun getAllCookies(): String
    fun setActiveGroupId(groupId: String?)
    fun getActiveGroupId(): String?
}
