package com.example.weibochat.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.Message
import com.example.weibochat.data.BlockedKeywordRule
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface MainScreenUiState {
    object Loading : MainScreenUiState
    data class Error(val throwable: Throwable) : MainScreenUiState
    data class Success(val messages: List<Message>) : MainScreenUiState
}

class MainScreenViewModel(private val dataRepository: DataRepository) : ViewModel() {

    val uiState: StateFlow<MainScreenUiState> =
        dataRepository.allMessages
            .map<List<Message>, MainScreenUiState> { MainScreenUiState.Success(it) }
            .catch { emit(MainScreenUiState.Error(it)) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MainScreenUiState.Loading)

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
            dataRepository.sendMessage(content, senderName = "我", contextId = 1)
        }
    }

    fun showContext(contextId: Long) {
        viewModelScope.launch {
            val msgs = dataRepository.getMessagesByContextId(contextId)
            _contextMessages.value = msgs
        }
    }

    fun showMessageContext(message: Message) {
        viewModelScope.launch {
            _activeContextMessageId.value = message.id
            val msgs = dataRepository.getMessageContext(message)
            _contextMessages.value = msgs
        }
    }

    fun hideContext() {
        _contextMessages.value = null
        _activeContextMessageId.value = null
    }

    fun saveCredentials(cookie: String, groupId: String) {
        dataRepository.saveCredentials(cookie, groupId)
    }

    fun getCredentials(): Pair<String, String> {
        return dataRepository.getCredentials()
    }

    fun getBlockedKeywordsString(): String {
        return dataRepository.getBlockedKeywordsString()
    }

    fun saveBlockedKeywords(keywords: String) {
        dataRepository.saveBlockedKeywords(keywords)
    }

    fun getBlockedKeywordRules(): List<BlockedKeywordRule> {
        return dataRepository.getBlockedKeywordRules()
    }

    fun saveBlockedKeywordRules(rules: List<BlockedKeywordRule>) {
        dataRepository.saveBlockedKeywordRules(rules)
    }

    fun getBlockedUsersString(): String {
        return dataRepository.getBlockedUsersString()
    }

    fun saveBlockedUsers(users: String) {
        dataRepository.saveBlockedUsers(users)
    }

    fun loadOlderMessages(oldestMid: Long) {
        if (_isLoadingHistory.value) return
        viewModelScope.launch {
            _isLoadingHistory.value = true
            dataRepository.fetchOlderMessages(oldestMid)
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
                val count = dataRepository.syncMessagesUntil(targetTimeMillis)
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
