package com.example.weibochat.ui.main

import com.example.weibochat.data.DataRepository
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Test

import com.example.weibochat.data.Message
import com.example.weibochat.data.WeiboContact
import com.example.weibochat.data.WeiboTimelineResponse
import com.example.weibochat.data.WeiboTimelineStatus

class MainScreenViewModelTest {
  @Test
  fun uiState_initiallyLoading() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }

  @Test
  fun uiState_onItemSaved_isDisplayed() = runTest {
    val viewModel = MainScreenViewModel(FakeMyModelRepository())
    assertEquals(viewModel.uiState.first(), MainScreenUiState.Loading)
  }
}

private class FakeMyModelRepository : DataRepository {
  override val allMessages: Flow<List<Message>> = flow { emit(emptyList()) }
  override suspend fun sendMessage(content: String, senderName: String, contextId: Long?): Long = 0L
  override suspend fun getMessagesByContextId(contextId: Long): List<Message> = emptyList()
  override suspend fun getUserContextMessages(senderName: String, timestamp: String): List<Message> = emptyList()
  override fun saveCredentials(cookie: String, groupId: String) {}
  override fun getCredentials(): Pair<String, String> = Pair("", "")
  override fun saveMobileCookie(cookie: String) {}
  override fun getMobileCookie(): String = ""
  override fun getAllCookies(): String = ""
  override suspend fun fetchOlderMessages(maxMid: Long): Boolean = false
  override suspend fun fetchContacts(): List<WeiboContact> = emptyList()
  override fun setActiveGroupId(groupId: String?) {}
  override fun getActiveGroupId(): String? = null
  override fun saveBlockedKeywords(keywords: String) {}
  override fun getBlockedKeywordsString(): String = ""
  override fun getBlockedKeywordsList(): List<String> = emptyList()
  override fun saveBlockedUsers(users: String) {}
  override fun getBlockedUsersString(): String = ""
  override fun getBlockedUsersList(): List<String> = emptyList()
  override fun isMessageBlocked(sender: String, content: String): Boolean = false
  override fun saveReadPosition(groupId: String, index: Int, offset: Int) {}
  override fun getReadPosition(groupId: String): Pair<Int, Int>? = null
  override suspend fun getMessageContext(message: Message): List<Message> = emptyList()
  override suspend fun syncMessagesUntil(targetTimeMillis: Long): Int = 0
  override suspend fun fetchFriendsTimeline(
    listId: String,
    maxId: Long?
  ): WeiboTimelineResponse? = null
  override suspend fun fetchWeiboStatusLongText(statusId: String): String? = null
  override suspend fun fetchWeiboStatus(statusId: String): WeiboTimelineStatus? = null
  override suspend fun fetchWeiboComments(statusId: String, maxId: Long?, maxIdType: Int): com.example.weibochat.data.WeiboCommentsResponse? = null
  override suspend fun fetchWeiboCommentChildren(commentId: Long, maxId: Long, maxIdType: Int): com.example.weibochat.data.WeiboCommentChildrenResponse? = null
  override suspend fun fetchWeiboReposts(statusId: String, page: Int): com.example.weibochat.data.WeiboRepostsResponse? = null
  override suspend fun fetchWeiboAttitudes(statusId: String, page: Int): com.example.weibochat.data.WeiboAttitudesResponse? = null
  override suspend fun likeWeiboStatus(statusId: String): Boolean = false
  override suspend fun unlikeWeiboStatus(statusId: String): Boolean = false
}
