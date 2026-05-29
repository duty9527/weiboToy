package com.example.weibochat.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weibochat.data.Message
import com.example.weibochat.data.BlockedKeywordRule
import com.example.weibochat.data.repository.MessageRepository
import com.example.weibochat.data.repository.AuthRepository
import com.example.weibochat.data.repository.SettingRepository
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val messages: List<Message>) : MainScreenUiState
}

class MainScreenViewModel(
    private val messageRepository: MessageRepository,
    private val authRepository: AuthRepository,
    private val settingRepository: SettingRepository
) : ViewModel() {

    val uiState: StateFlow<MainScreenUiState> =
        messageRepository.allMessages
            .map<List<Message>, MainScreenUiState> { MainScreenUiState.Success(it) }
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

    val messagesPagingData: Flow<PagingData<Message>> =
        messageRepository.getMessagesPagingData().cachedIn(viewModelScope)

    private val _contextMessages = MutableStateFlow<List<Message>?>(null)
    val contextMessages: StateFlow<List<Message>?> = _contextMessages.asStateFlow()

    private val _activeContextMessageId = MutableStateFlow<Long?>(null)
    val activeContextMessageId: StateFlow<Long?> = _activeContextMessageId.asStateFlow()

    private val _isLoadingHistory = MutableStateFlow(false)
    val isLoadingHistory: StateFlow<Boolean> = _isLoadingHistory.asStateFlow()

    private val _isSyncingHistory = MutableStateFlow(false)
    val isSyncingHistory: StateFlow<Boolean> = _isSyncingHistory.asStateFlow()

    private val _syncResultCount = MutableStateFlow<Int?>(null)
    val syncResultCount: StateFlow<Int?> = _syncResultCount.asStateFlow()

    fun sendMessage(content: String) {
        viewModelScope.launch {
            messageRepository.sendMessage(content, senderName = "我", contextId = 1)
        }
    }

    fun showContext(contextId: Long) {
        viewModelScope.launch {
            val msgs = messageRepository.getMessagesByContextId(contextId)
            _contextMessages.value = msgs
        }
    }

    fun showMessageContext(message: Message) {
        viewModelScope.launch {
            _activeContextMessageId.value = message.id
            val msgs = messageRepository.getMessageContext(message)
            _contextMessages.value = msgs
        }
    }

    fun hideContext() {
        _contextMessages.value = null
        _activeContextMessageId.value = null
    }

    fun saveCredentials(cookie: String, groupId: String) {
        authRepository.saveCredentials(cookie, groupId)
    }

    fun getCredentials(): Pair<String, String> {
        return authRepository.getCredentials()
    }

    fun getBlockedKeywordsString(): String {
        return settingRepository.getBlockedKeywordsString()
    }

    fun saveBlockedKeywords(keywords: String) {
        settingRepository.saveBlockedKeywords(keywords)
    }

    fun getBlockedKeywordRules(): List<BlockedKeywordRule> {
        return settingRepository.getBlockedKeywordRules()
    }

    fun saveBlockedKeywordRules(rules: List<BlockedKeywordRule>) {
        settingRepository.saveBlockedKeywordRules(rules)
    }

    fun getBlockedUsersString(): String {
        return settingRepository.getBlockedUsersString()
    }

    fun saveBlockedUsers(users: String) {
        settingRepository.saveBlockedUsers(users)
    }

    fun loadOlderMessages(oldestMid: Long) {
        if (_isLoadingHistory.value) return
        viewModelScope.launch {
            _isLoadingHistory.value = true
            messageRepository.fetchOlderMessages(oldestMid)
            delay(800)
            _isLoadingHistory.value = false
        }
    }

    fun startSyncHistory(targetTimeMillis: Long) {
        if (_isSyncingHistory.value) return
        viewModelScope.launch {
            _isSyncingHistory.value = true
            _syncResultCount.value = null
            try {
                val count = messageRepository.syncMessagesUntil(targetTimeMillis)
                _syncResultCount.value = count
            } catch (e: Exception) {
                android.util.Log.e("WeiboChat", "Sync history failed", e)
                _syncResultCount.value = 0
            } finally {
                _isSyncingHistory.value = false
            }
        }
    }

    fun clearSyncResult() {
        _syncResultCount.value = null
    }
}
