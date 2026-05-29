package com.duty.weibotoy.ui.weibo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.duty.weibotoy.data.repository.TimelineRepository
import com.duty.weibotoy.data.DEFAULT_WEIBO_TIMELINE_LIST_ID
import com.duty.weibotoy.data.WeiboTimelineStatus
import com.duty.weibotoy.data.WeiboComment
import com.duty.weibotoy.data.WeiboRepost
import com.duty.weibotoy.data.WeiboAttitude
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch



sealed interface TimelineUiState {
    object Loading : TimelineUiState
    data class Success(val statuses: List<WeiboTimelineStatus>) : TimelineUiState
    data class Error(val message: String) : TimelineUiState
}

class WeiboTimelineViewModel(
    private val repository: TimelineRepository
) : ViewModel() {
    private val listId: String = DEFAULT_WEIBO_TIMELINE_LIST_ID


    private val _scrollToTopEvents = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val scrollToTopEvents = _scrollToTopEvents.asSharedFlow()

    fun triggerScrollToTopAndRefresh() {
        _scrollToTopEvents.tryEmit(Unit)
        refresh()
    }

    private val _uiState = MutableStateFlow<TimelineUiState>(TimelineUiState.Loading)
    val uiState: StateFlow<TimelineUiState> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    val canLoadMore: Boolean = true

    private val _readStatusIds = MutableStateFlow<Set<String>>(emptySet())
    val readStatusIds: StateFlow<Set<String>> = _readStatusIds.asStateFlow()

    private val _loadingGaps = MutableStateFlow<Set<Long>>(emptySet())
    val loadingGaps: StateFlow<Set<Long>> = _loadingGaps.asStateFlow()

    val timelinePagingData: Flow<PagingData<WeiboTimelineStatus>> =
        repository.getLocalTimelinePagingData().cachedIn(viewModelScope)

    private val _loadedStatuses = MutableStateFlow<List<WeiboTimelineStatus>>(emptyList())

    init {
        _readStatusIds.value = repository.getReadWeiboStatusIds()
    }

    fun updateLoadedStatuses(statuses: List<WeiboTimelineStatus>) {
        _loadedStatuses.value = statuses
        if (statuses.isNotEmpty()) {
            _uiState.value = TimelineUiState.Success(statuses)
        }
    }

    fun markAsRead(statusId: String) {
        viewModelScope.launch {
            repository.markWeiboStatusAsRead(statusId)
            _readStatusIds.value = repository.getReadWeiboStatusIds()
        }
    }

    fun markAllLoadedAsRead() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state is TimelineUiState.Success) {
                _loadedStatuses.value.forEach { status ->
                    val id = status.idstr ?: status.id?.toString()
                    if (id != null && status.raw_text?.startsWith("__GAP__:") != true) {
                        repository.markWeiboStatusAsRead(id)
                    }
                }
                _readStatusIds.value = repository.getReadWeiboStatusIds()
            }
        }
    }

    fun refresh() {
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        if (_uiState.value !is TimelineUiState.Success) {
            _uiState.value = TimelineUiState.Loading
        }
        viewModelScope.launch {
            val result = repository.syncNewTimeline()
            _isRefreshing.value = false
            if (result.isFailure) {
                val currentList = (uiState.value as? TimelineUiState.Success)?.statuses.orEmpty()
                if (currentList.isEmpty()) {
                    _uiState.value = TimelineUiState.Error(
                        result.exceptionOrNull()?.message ?: "没有拿到最新微博，请确认登录状态后重试"
                    )
                }
            }
        }
    }

    fun loadMore() {
        if (_isLoadingMore.value) return
        _isLoadingMore.value = true
        viewModelScope.launch {
            repository.loadMoreTimeline()
            _isLoadingMore.value = false
        }
    }

    fun fillGap(gapId: Long) {
        if (_loadingGaps.value.contains(gapId)) return
        _loadingGaps.value = _loadingGaps.value + gapId
        viewModelScope.launch {
            repository.syncGap(gapId)
            _loadingGaps.value = _loadingGaps.value - gapId
        }
    }

    fun saveLastViewedWeibo(statusId: String, index: Int, offset: Int) {
        repository.saveLastViewedWeibo(statusId, index, offset)
    }

    fun getLastViewedWeibo(): Triple<String, Int, Int>? {
        return repository.getLastViewedWeibo()
    }

    // Detail Page Tabs
    enum class DetailTab {
        REPOST, COMMENT, LIKE
    }

    data class DetailNotice(
        val message: String,
        val actionUrl: String? = null
    )

    data class ChildCommentsUiState(
        val root: WeiboComment,
        val replies: List<WeiboComment> = emptyList(),
        val totalNumber: Int = 0,
        val isLoading: Boolean = false,
        val canLoadMore: Boolean = false
    )

    private val _statusComments = MutableStateFlow<Map<String, List<WeiboComment>>>(emptyMap())
    val statusComments: StateFlow<Map<String, List<WeiboComment>>> = _statusComments.asStateFlow()

    fun loadCommentsForStatus(statusId: String) {
        if (statusId.isBlank() || _statusComments.value.containsKey(statusId)) return
        viewModelScope.launch {
            val response = repository.fetchWeiboComments(statusId, null, 0)
            if (response?.ok == 1) {
                val list = response.data?.data.orEmpty()
                _statusComments.value = _statusComments.value + (statusId to list)
            }
        }
    }

    private val _detailTab = MutableStateFlow(DetailTab.COMMENT)
    val detailTab: StateFlow<DetailTab> = _detailTab.asStateFlow()

    private val _detailComments = MutableStateFlow<List<WeiboComment>>(emptyList())
    val detailComments: StateFlow<List<WeiboComment>> = _detailComments.asStateFlow()

    private val _detailCommentNotice = MutableStateFlow<DetailNotice?>(null)
    val detailCommentNotice: StateFlow<DetailNotice?> = _detailCommentNotice.asStateFlow()

    private val _childCommentsUiState = MutableStateFlow<ChildCommentsUiState?>(null)
    val childCommentsUiState: StateFlow<ChildCommentsUiState?> = _childCommentsUiState.asStateFlow()

    private val _detailReposts = MutableStateFlow<List<WeiboRepost>>(emptyList())
    val detailReposts: StateFlow<List<WeiboRepost>> = _detailReposts.asStateFlow()

    private val _detailAttitudes = MutableStateFlow<List<WeiboAttitude>>(emptyList())
    val detailAttitudes: StateFlow<List<WeiboAttitude>> = _detailAttitudes.asStateFlow()

    private val _detailStatus = MutableStateFlow<WeiboTimelineStatus?>(null)
    val detailStatus: StateFlow<WeiboTimelineStatus?> = _detailStatus.asStateFlow()

    private val _isDetailCommentsLoading = MutableStateFlow(false)
    val isDetailCommentsLoading: StateFlow<Boolean> = _isDetailCommentsLoading.asStateFlow()

    private val _isDetailRepostsLoading = MutableStateFlow(false)
    val isDetailRepostsLoading: StateFlow<Boolean> = _isDetailRepostsLoading.asStateFlow()

    private val _isDetailAttitudesLoading = MutableStateFlow(false)
    val isDetailAttitudesLoading: StateFlow<Boolean> = _isDetailAttitudesLoading.asStateFlow()

    private var commentMaxId: Long? = null
    private var commentMaxIdType: Int = 0
    private var childCommentMaxId: Long = 0L
    private var childCommentMaxIdType: Int = 0
    private var repostPage: Int = 1
    private var attitudePage: Int = 1

    private var commentHasMore = true
    private var repostHasMore = true
    private var attitudeHasMore = true

    val canLoadMoreComments: Boolean get() = commentHasMore
    val canLoadMoreReposts: Boolean get() = repostHasMore
    val canLoadMoreAttitudes: Boolean get() = attitudeHasMore

    private var activeDetailStatusId: String? = null

    fun selectDetailTab(tab: DetailTab) {
        _detailTab.value = tab
        val statusId = activeDetailStatusId
        if (statusId != null) {
            when (tab) {
                DetailTab.COMMENT -> if (_detailComments.value.isEmpty()) loadComments(statusId, reset = true)
                DetailTab.REPOST -> if (_detailReposts.value.isEmpty()) loadReposts(statusId, reset = true)
                DetailTab.LIKE -> if (_detailAttitudes.value.isEmpty()) loadAttitudes(statusId, reset = true)
            }
        }
    }

    fun openStatusDetail(statusId: String) {
        activeDetailStatusId = statusId
        _detailTab.value = DetailTab.COMMENT

        _detailStatus.value = null
        _detailComments.value = emptyList()
        _detailReposts.value = emptyList()
        _detailAttitudes.value = emptyList()
        _childCommentsUiState.value = null

        commentMaxId = null
        commentMaxIdType = 0
        repostPage = 1
        attitudePage = 1

        commentHasMore = true
        repostHasMore = true
        attitudeHasMore = true

        viewModelScope.launch {
            val status = repository.fetchWeiboStatus(statusId)
            if (activeDetailStatusId == statusId && status != null) {
                _detailStatus.value = status
            }
        }
        loadComments(statusId, reset = true)
    }

    fun loadMoreComments() {
        val statusId = activeDetailStatusId ?: return
        if (_isDetailCommentsLoading.value || !commentHasMore) return
        loadComments(statusId, reset = false)
    }

    fun loadMoreReposts() {
        val statusId = activeDetailStatusId ?: return
        if (_isDetailRepostsLoading.value || !repostHasMore) return
        loadReposts(statusId, reset = false)
    }

    fun loadMoreAttitudes() {
        val statusId = activeDetailStatusId ?: return
        if (_isDetailAttitudesLoading.value || !attitudeHasMore) return
        loadAttitudes(statusId, reset = false)
    }

    fun openCommentChildren(root: WeiboComment) {
        val totalNumber = root.total_number ?: root.comments.orEmpty().size
        childCommentMaxId = 0L
        childCommentMaxIdType = 0
        _childCommentsUiState.value = ChildCommentsUiState(
            root = root,
            replies = emptyList(),
            totalNumber = totalNumber,
            isLoading = true,
            canLoadMore = false
        )
        loadCommentChildren(root, reset = true)
    }

    fun closeCommentChildren() {
        _childCommentsUiState.value = null
    }

    fun loadMoreCommentChildren() {
        val state = _childCommentsUiState.value ?: return
        if (state.isLoading || !state.canLoadMore) return
        loadCommentChildren(state.root, reset = false)
    }

    private fun loadComments(statusId: String, reset: Boolean) {
        if (reset) {
            commentMaxId = null
            commentMaxIdType = 0
            commentHasMore = true
            _detailCommentNotice.value = null
        }
        _isDetailCommentsLoading.value = true
        viewModelScope.launch {
            val response = repository.fetchWeiboComments(statusId, commentMaxId, commentMaxIdType)
            if (response?.ok == 1) {
                val list = response.data?.data.orEmpty()
                if (reset) {
                    _detailComments.value = list
                } else {
                    _detailComments.value = _detailComments.value + list
                }
                commentMaxId = response.data?.max_id
                commentMaxIdType = response.data?.max_id_type ?: 0
                commentHasMore = list.isNotEmpty() && commentMaxId != null && commentMaxId != 0L
                _detailCommentNotice.value = null
            } else {
                commentHasMore = false
                _detailCommentNotice.value = buildCommentNotice(response)
            }
            _isDetailCommentsLoading.value = false
        }
    }

    private fun buildCommentNotice(response: com.duty.weibotoy.data.WeiboCommentsResponse?): DetailNotice {
        val actionUrl = response?.url?.takeIf { it.isNotBlank() }
        if (response?.ok == -100 || actionUrl?.contains("/captcha/") == true) {
            return DetailNotice("微博要求完成验证后才能查看评论", actionUrl)
        }

        val message = response?.msg
            ?.takeIf { it.isNotBlank() }
            ?: "评论接口没有返回可用数据"
        return DetailNotice(message, actionUrl)
    }

    private fun loadCommentChildren(root: WeiboComment, reset: Boolean) {
        val rootId = root.id ?: return
        val currentState = _childCommentsUiState.value ?: return
        _childCommentsUiState.value = currentState.copy(isLoading = true)
        viewModelScope.launch {
            val response = repository.fetchWeiboCommentChildren(rootId, childCommentMaxId, childCommentMaxIdType)
            val currentReplies = if (reset) emptyList() else _childCommentsUiState.value?.replies.orEmpty()
            if (response?.ok == 1) {
                val pageReplies = response.data.orEmpty()
                val newReplies = pageReplies.filter { reply ->
                    val replyId = reply.id
                    replyId == null || currentReplies.none { it.id == replyId }
                }
                val replies = currentReplies + newReplies
                childCommentMaxId = response.max_id ?: 0L
                childCommentMaxIdType = response.max_id_type ?: 0
                _childCommentsUiState.value = currentState.copy(
                    replies = replies,
                    totalNumber = response.total_number ?: currentState.totalNumber,
                    isLoading = false,
                    canLoadMore = childCommentMaxId > 0L
                )
            } else {
                _childCommentsUiState.value = currentState.copy(
                    replies = currentReplies,
                    isLoading = false,
                    canLoadMore = false
                )
            }
        }
    }

    private fun loadReposts(statusId: String, reset: Boolean) {
        if (reset) {
            repostPage = 1
            repostHasMore = true
        }
        _isDetailRepostsLoading.value = true
        viewModelScope.launch {
            val response = repository.fetchWeiboReposts(statusId, repostPage)
            if (response?.ok == 1) {
                val list = response.data?.data.orEmpty()
                if (reset) {
                    _detailReposts.value = list
                } else {
                    _detailReposts.value = _detailReposts.value + list
                }
                repostPage++
                repostHasMore = list.isNotEmpty()
            } else {
                repostHasMore = false
            }
            _isDetailRepostsLoading.value = false
        }
    }

    private fun loadAttitudes(statusId: String, reset: Boolean) {
        if (reset) {
            attitudePage = 1
            attitudeHasMore = true
        }
        _isDetailAttitudesLoading.value = true
        viewModelScope.launch {
            val response = repository.fetchWeiboAttitudes(statusId, attitudePage)
            if (response?.ok == 1) {
                val list = response.data?.data.orEmpty()
                if (reset) {
                    _detailAttitudes.value = list
                } else {
                    _detailAttitudes.value = _detailAttitudes.value + list
                }
                attitudePage++
                attitudeHasMore = list.isNotEmpty()
            } else {
                attitudeHasMore = false
            }
            _isDetailAttitudesLoading.value = false
        }
    }

    fun toggleLikeStatus(statusId: String) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState is TimelineUiState.Success) {
                val statuses = _loadedStatuses.value.toMutableList()
                val index = statuses.indexOfFirst { (it.idstr ?: it.id?.toString()) == statusId }
                if (index != -1) {
                    val oldStatus = statuses[index]
                    val currentlyLiked = oldStatus.liked == true
                    val newLiked = !currentlyLiked
                    val countChange = if (newLiked) 1 else -1
                    val newCount = ((oldStatus.attitudes_count ?: 0) + countChange).coerceAtLeast(0)
                    val newStatus = oldStatus.copy(
                        liked = newLiked,
                        attitudes_count = newCount
                    )
                    statuses[index] = newStatus
                    _loadedStatuses.value = statuses
                    _uiState.value = TimelineUiState.Success(statuses)

                    val success = if (newLiked) {
                        repository.likeWeiboStatus(statusId)
                    } else {
                        repository.unlikeWeiboStatus(statusId)
                    }

                    if (!success) {
                        val revertState = _uiState.value
                        if (revertState is TimelineUiState.Success) {
                            val revertStatuses = _loadedStatuses.value.toMutableList()
                            val revertIndex = revertStatuses.indexOfFirst { (it.idstr ?: it.id?.toString()) == statusId }
                            if (revertIndex != -1) {
                                revertStatuses[revertIndex] = oldStatus
                                _loadedStatuses.value = revertStatuses
                                _uiState.value = TimelineUiState.Success(revertStatuses)
                            }
                        }
                    }
                }
            }
        }
    }
}
