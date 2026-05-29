package com.example.weibochat.data.repository

import com.example.weibochat.data.DEFAULT_WEIBO_TIMELINE_LIST_ID
import com.example.weibochat.data.WeiboTimelineResponse
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.data.WeiboCommentsResponse
import com.example.weibochat.data.WeiboCommentChildrenResponse
import com.example.weibochat.data.WeiboRepostsResponse
import com.example.weibochat.data.WeiboAttitudesResponse
import androidx.paging.PagingData
import kotlinx.coroutines.flow.Flow

interface TimelineRepository {
    suspend fun fetchFriendsTimeline(
        listId: String = DEFAULT_WEIBO_TIMELINE_LIST_ID,
        maxId: Long? = null,
        sinceId: Long? = null
    ): WeiboTimelineResponse?
    suspend fun fetchWeiboStatusLongText(statusId: String): String?
    suspend fun fetchWeiboStatus(statusId: String): WeiboTimelineStatus?
    suspend fun fetchWeiboComments(statusId: String, maxId: Long?, maxIdType: Int): WeiboCommentsResponse?
    suspend fun fetchWeiboCommentChildren(commentId: Long, maxId: Long, maxIdType: Int): WeiboCommentChildrenResponse?
    suspend fun fetchWeiboReposts(statusId: String, page: Int): WeiboRepostsResponse?
    suspend fun fetchWeiboAttitudes(statusId: String, page: Int): WeiboAttitudesResponse?
    suspend fun likeWeiboStatus(statusId: String): Boolean
    suspend fun unlikeWeiboStatus(statusId: String): Boolean
    fun markWeiboStatusAsRead(statusId: String)
    fun getReadWeiboStatusIds(): Set<String>
    fun getLocalTimeline(): Flow<List<WeiboTimelineStatus>>
    fun getLocalTimelinePagingData(): Flow<PagingData<WeiboTimelineStatus>>
    suspend fun syncNewTimeline(): Result<Unit>
    suspend fun syncGap(gapId: Long): Result<Unit>
    suspend fun loadMoreTimeline(): Result<Unit>
    fun saveLastViewedWeibo(statusId: String, index: Int, offset: Int)
    fun getLastViewedWeibo(): Triple<String, Int, Int>?
}
