package com.duty.weibotoy.data.repository

import com.duty.weibotoy.data.BlockedKeywordRule

interface SettingRepository {
    fun saveBlockedKeywords(keywords: String)
    fun getBlockedKeywordsString(): String
    fun getBlockedKeywordsList(): List<String>
    fun getBlockedKeywordRules(): List<BlockedKeywordRule>
    fun saveBlockedKeywordRules(rules: List<BlockedKeywordRule>)
    fun saveBlockedUsers(users: String)
    fun getBlockedUsersString(): String
    fun getBlockedUsersList(): List<String>
    fun isMessageBlocked(sender: String, content: String): Boolean
    fun saveReadPosition(groupId: String, index: Int, offset: Int)
    fun getReadPosition(groupId: String): Pair<Int, Int>?
}
