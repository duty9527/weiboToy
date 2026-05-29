package com.example.weibochat.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Attachment
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.weibochat.data.Message
import com.example.weibochat.data.WeiboTimelineStatus
import com.example.weibochat.data.BlockedKeywordRule
import com.example.weibochat.data.MatchMode
import com.example.weibochat.data.cleanWeiboHtmlText
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.weibochat.theme.*
import com.example.weibochat.ui.weibo.ImagePreviewDialog
import com.example.weibochat.ui.weibo.WeiboDetailDialog
import com.example.weibochat.ui.weibo.WeiboTimelineViewModel
import com.example.weibochat.ui.weibo.VideoPreviewDialog
import com.example.weibochat.ui.weibo.WeiboMediaPreviewDialog
import com.example.weibochat.ui.weibo.MediaItem
import com.example.weibochat.ui.weibo.resolveImagePreview
import com.example.weibochat.ui.weibo.resolveVideoPreview
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import com.example.weibochat.data.parseMessageContent
import com.example.weibochat.data.findQuotedMessageIndex

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    groupId: String,
    groupName: String,
    onBackClick: () -> Unit,
    onItemClick: (NavKey) -> Unit,
    repository: com.example.weibochat.data.DataRepository,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel,
    timelineViewModel: WeiboTimelineViewModel
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contextMsgs by viewModel.contextMessages.collectAsStateWithLifecycle()
    val activeContextMsgId by viewModel.activeContextMessageId.collectAsStateWithLifecycle()
    var showSettingsDialog by remember { mutableStateOf(false) }

    val isSyncingHistory by viewModel.isSyncingHistory.collectAsStateWithLifecycle()
    val syncResultCount by viewModel.syncResultCount.collectAsStateWithLifecycle()

    LaunchedEffect(syncResultCount) {
        syncResultCount?.let { count ->
            android.widget.Toast.makeText(context, "同步完成！已下载并缓存了 ${count} 条历史消息", android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearSyncResult()
        }
    }

    val calendar = remember { java.util.Calendar.getInstance() }
    val datePickerDialog = remember {
        android.app.DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val selectedCal = java.util.Calendar.getInstance().apply {
                    set(java.util.Calendar.YEAR, year)
                    set(java.util.Calendar.MONTH, month)
                    set(java.util.Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(java.util.Calendar.HOUR_OF_DAY, 0)
                    set(java.util.Calendar.MINUTE, 0)
                    set(java.util.Calendar.SECOND, 0)
                    set(java.util.Calendar.MILLISECOND, 0)
                }
                viewModel.startSyncHistory(selectedCal.timeInMillis)
                showSettingsDialog = false
            },
            calendar.get(java.util.Calendar.YEAR),
            calendar.get(java.util.Calendar.MONTH),
            calendar.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }
    
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var activeMediaPreview by remember { mutableStateOf<Pair<List<MediaItem>, Int>?>(null) }
    var activeDetailStatus by remember { mutableStateOf<WeiboTimelineStatus?>(null) }
    var isDetailLoading by remember { mutableStateOf(false) }

    var isSearching by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var pendingScrollIndex by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(groupId) {
        repository.setActiveGroupId(groupId)
        onDispose {
            repository.setActiveGroupId(null)
        }
    }

    fun openWeiboStatus(statusId: String) {
        if (statusId.isBlank()) return
        coroutineScope.launch {
            isDetailLoading = true
            val status = repository.fetchWeiboStatus(statusId)
            isDetailLoading = false
            if (status != null) {
                activeDetailStatus = status
            } else {
                android.widget.Toast.makeText(context, "微博详情加载失败", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(pendingScrollIndex) {
        val idx = pendingScrollIndex
        if (idx != null) {
            kotlinx.coroutines.delay(100)
            listState.scrollToItem(idx)
            pendingScrollIndex = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
        topBar = {
            if (isSearching) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            placeholder = { Text("搜索关键字...", color = TextGrey, fontSize = 14.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            singleLine = true
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { 
                            isSearching = false
                            searchQuery = "" 
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "取消搜索",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "清除搜索",
                                    tint = Color.White
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg
                    )
                )
            } else {
                TopAppBar(
                    title = {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = groupName,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "微博群聊",
                                fontSize = 11.sp,
                                color = TextGrey
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { isSearching = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showSettingsDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "设置",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = DarkBg
                    )
                )
            }
        },
        containerColor = DarkBg
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(DarkBg)
        ) {
            when (state) {
                MainScreenUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = AccentGreen)
                    }
                }
                is MainScreenUiState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "加载错误: ${(state as MainScreenUiState.Error).throwable.message}",
                            color = Color.Red
                        )
                    }
                }
                is MainScreenUiState.Success -> {
                    val messages = (state as MainScreenUiState.Success).messages
                    val isLoadingHistory by viewModel.isLoadingHistory.collectAsStateWithLifecycle()
                    val cookie = remember(state) { viewModel.getCredentials().first }

                    if (isSearching) {
                        val searchResults = remember(searchQuery, messages) {
                            if (searchQuery.isBlank()) {
                                emptyList()
                            } else {
                                messages.filter { msg ->
                                    msg.content.contains(searchQuery, ignoreCase = true) ||
                                    msg.senderName.contains(searchQuery, ignoreCase = true)
                                }
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBg)
                        ) {
                            if (searchQuery.isBlank()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "输入关键字搜索群聊消息",
                                        color = TextGrey,
                                        fontSize = 14.sp
                                    )
                                }
                            } else {
                                if (searchResults.isEmpty()) {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "没有找到匹配的消息",
                                            color = TextGrey,
                                            fontSize = 14.sp
                                        )
                                    }
                                } else {
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        contentPadding = PaddingValues(vertical = 8.dp)
                                    ) {
                                        items(searchResults) { msg ->
                                            SearchResultItem(
                                                message = msg,
                                                searchQuery = searchQuery,
                                                onClick = {
                                                    val index = messages.indexOfFirst { it.id == msg.id }
                                                    if (index != -1) {
                                                        isSearching = false
                                                        searchQuery = ""
                                                        pendingScrollIndex = index
                                                    }
                                                }
                                            )
                                            HorizontalDivider(color = DividerGrey, thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        ChatContent(
                            messages = messages,
                            cookie = cookie,
                            isLoadingHistory = isLoadingHistory,
                            listState = listState,
                            coroutineScope = coroutineScope,
                            onSendMessage = { viewModel.sendMessage(it) },
                            onShowMessageContext = { message -> viewModel.showMessageContext(message) },
                            onLoadOlderMessages = { viewModel.loadOlderMessages(it) },
                            onImageClick = { clickedUrl ->
                                val imageMessages = messages.filter { it.imageUrl != null }
                                val imageUrls = imageMessages.map { getOriginalImageUrl(it.imageUrl!!) }
                                val clickedOriginalUrl = getOriginalImageUrl(clickedUrl)
                                val index = imageUrls.indexOf(clickedOriginalUrl).coerceAtLeast(0)
                                val mediaList = imageUrls.map { MediaItem(thumbnailUrl = it, largeUrl = it) }
                                activeMediaPreview = Pair(mediaList, index)
                            },
                            onVideoClick = { url ->
                                val parts = url.split("##")
                                val videoUrl = parts[0]
                                val coverUrl = parts.getOrNull(1)
                                val item = MediaItem(
                                    thumbnailUrl = coverUrl ?: "",
                                    largeUrl = coverUrl ?: "",
                                    isVideo = true,
                                    isLivePhoto = url.endsWith("##live"),
                                    videoSrc = videoUrl
                                )
                                activeMediaPreview = Pair(listOf(item), 0)
                            },
                            onOpenWeiboStatus = ::openWeiboStatus,
                            repository = repository,
                            groupId = groupId
                        )
                    }

                    // Unified Weibo Media Preview Dialog
                    activeMediaPreview?.let { (mediaList, index) ->
                        WeiboMediaPreviewDialog(
                            mediaItems = mediaList,
                            initialIndex = index,
                            repository = repository,
                            onDismiss = { activeMediaPreview = null }
                        )
                    }
                }
            }

            activeDetailStatus?.let { status ->
                WeiboDetailDialog(
                    status = status,
                    viewModel = timelineViewModel,
                    isLoadingLongText = isDetailLoading,
                    onDismiss = {
                        activeDetailStatus = null
                        isDetailLoading = false
                    },
                    onImageClick = { index, urls ->
                        activeMediaPreview = resolveImagePreview(status, index, urls)
                    },
                    onVideoClick = { url ->
                        activeMediaPreview = resolveVideoPreview(status, url)
                    },
                    onWebClick = { url ->
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
                    },
                    onOpenDetail = { activeDetailStatus = it }
                )
            }

            // Context Bottom Sheet
            contextMsgs?.let { msgs ->
                val mainMessages = (state as? MainScreenUiState.Success)?.messages ?: emptyList()
                ModalBottomSheet(
                    onDismissRequest = { viewModel.hideContext() },
                    containerColor = DarkBg,
                    contentColor = TextWhite,
                    dragHandle = {
                        BottomSheetDefaults.DragHandle(color = DividerGrey)
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "上下文对话线索",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGreen
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DividerGrey, thickness = 1.dp)
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                        ) {
                            items(msgs) { msg ->
                                val isAnchor = msg.id == activeContextMsgId
                                val parsedMsg = remember(msg.content) { parseMessageContent(msg.content) }
                                val displayText = remember(parsedMsg.cleanImmediateText) { replaceWeiboShortcodes(parsedMsg.cleanImmediateText) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            val index = mainMessages.indexOfFirst { it.id == msg.id }
                                            if (index != -1) {
                                                coroutineScope.launch {
                                                    listState.animateScrollToItem(index)
                                                }
                                            }
                                            viewModel.hideContext()
                                        }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "${msg.timestamp} | ",
                                            color = TextGrey,
                                            fontSize = 11.sp
                                        )
                                        Text(
                                            text = msg.senderName,
                                            color = TextWhite,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.clickable {
                                                val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                                                val clip = android.content.ClipData.newPlainText("username", msg.senderName)
                                                clipboard.setPrimaryClip(clip)
                                                android.widget.Toast.makeText(context, "已复制用户名: ${msg.senderName}", android.widget.Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "@${msg.groupSuffix}",
                                            color = AccentGreen,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                if (isAnchor) AccentGreen.copy(alpha = 0.15f) else BubbleBg,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .border(
                                                width = if (isAnchor) 1.dp else 0.dp,
                                                color = if (isAnchor) AccentGreen else Color.Transparent,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .padding(10.dp)
                                            .fillMaxWidth()
                                    ) {
                                        SelectionContainer {
                                            Text(
                                                text = displayText,
                                                color = TextWhite,
                                                fontSize = 13.sp,
                                                lineHeight = 18.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

        }
        
        if (showSettingsDialog) {
            var blockedRules by remember { mutableStateOf(viewModel.getBlockedKeywordRules().toMutableList()) }
            var blockedUsersText by remember { mutableStateOf(viewModel.getBlockedUsersString()) }

            AlertDialog(
                onDismissRequest = { showSettingsDialog = false },
                title = { Text("群聊设置与工具", color = Color.White) },
                text = {
                    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                        Text("屏蔽关键字:", color = TextGrey, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        blockedRules.forEachIndexed { index, rule ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                TextField(
                                    value = rule.text,
                                    onValueChange = { newText ->
                                        blockedRules = blockedRules.toMutableList().also {
                                            it[index] = rule.copy(text = newText)
                                        }
                                    },
                                    placeholder = { Text("关键字", color = TextGrey, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = InputBg,
                                        unfocusedContainerColor = InputBg,
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White
                                    )
                                )

                                var expanded by remember { mutableStateOf(false) }
                                Box {
                                    TextButton(
                                        onClick = { expanded = true },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                        modifier = Modifier.height(32.dp)
                                    ) {
                                        Text(rule.mode.label, color = AccentGreen, fontSize = 10.sp)
                                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = AccentGreen, modifier = Modifier.size(14.dp))
                                    }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        MatchMode.entries.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode.label, fontSize = 12.sp) },
                                                onClick = {
                                                    blockedRules = blockedRules.toMutableList().also {
                                                        it[index] = rule.copy(mode = mode)
                                                    }
                                                    expanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        blockedRules = blockedRules.toMutableList().also { it.removeAt(index) }
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color(0xFFE57373), modifier = Modifier.size(16.dp))
                                }
                            }
                            if (index < blockedRules.lastIndex) Spacer(modifier = Modifier.height(2.dp))
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = {
                                blockedRules = blockedRules.toMutableList().also {
                                    it.add(BlockedKeywordRule("", MatchMode.CONTAINS))
                                }
                            }
                        ) {
                            Icon(Icons.Default.Add, contentDescription = "添加", tint = AccentGreen, modifier = Modifier.size(16.dp))
                            Text("添加规则", color = AccentGreen, fontSize = 12.sp)
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text("屏蔽用户 (以逗号或中文逗号分隔):", color = TextGrey, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        TextField(
                            value = blockedUsersText,
                            onValueChange = { blockedUsersText = it },
                            placeholder = { Text("例如：用户A,用户B", color = TextGrey, fontSize = 12.sp) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = InputBg,
                                unfocusedContainerColor = InputBg,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))
                        Text("快速同步历史聊天记录:", color = TextGrey, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val targetTime = System.currentTimeMillis() - 3L * 24 * 3600 * 1000
                                    viewModel.startSyncHistory(targetTime)
                                    showSettingsDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InputBg,
                                    contentColor = TextWhite
                                ),
                                border = BorderStroke(0.8.dp, DividerGrey),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("最近3天", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val targetTime = System.currentTimeMillis() - 7L * 24 * 3600 * 1000
                                    viewModel.startSyncHistory(targetTime)
                                    showSettingsDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InputBg,
                                    contentColor = TextWhite
                                ),
                                border = BorderStroke(0.8.dp, DividerGrey),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("最近一周", fontSize = 11.sp)
                            }
                            Button(
                                onClick = {
                                    val targetTime = System.currentTimeMillis() - 30L * 24 * 3600 * 1000
                                    viewModel.startSyncHistory(targetTime)
                                    showSettingsDialog = false
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = InputBg,
                                    contentColor = TextWhite
                                ),
                                border = BorderStroke(0.8.dp, DividerGrey),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                            ) {
                                Text("最近一月", fontSize = 11.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                datePickerDialog.show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = InputBg,
                                contentColor = AccentGreen
                            ),
                            border = BorderStroke(0.8.dp, BorderGreen),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("📅 使用日历选择截止日期", color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("点击快速同步会在后台翻页拉取微博并缓存，拉取时主屏将被锁定直至拉取完成。", color = TextGrey, fontSize = 11.sp)
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val validRules = blockedRules.filter { it.text.isNotBlank() }
                            viewModel.saveBlockedKeywordRules(validRules)
                            viewModel.saveBlockedUsers(blockedUsersText)
                            showSettingsDialog = false
                        }
                    ) {
                        Text("保存并应用", color = AccentGreen)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showSettingsDialog = false
                        }
                    ) {
                        Text("取消", color = TextGrey)
                    }
                },
                containerColor = DarkBg
            )
        }

        if (isSyncingHistory) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable(enabled = false) {},
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = AccentGreen)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("正在同步并缓存历史聊天记录...", color = Color.White, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("该过程最多拉取数千条，视网络可能需要十几秒", color = TextGrey, fontSize = 11.sp)
                }
            }
        }
    }
}
}

@Composable
fun ChatContent(
    messages: List<Message>,
    cookie: String,
    isLoadingHistory: Boolean,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    onSendMessage: (String) -> Unit,
    onShowMessageContext: (Message) -> Unit,
    onLoadOlderMessages: (Long) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenWeiboStatus: (String) -> Unit,
    repository: com.example.weibochat.data.DataRepository,
    groupId: String
) {
    var inputText by remember { mutableStateOf("") }
    var initialScrollDone by remember(groupId) { mutableStateOf(false) }

    // Check if user is at the bottom of the message list
    val isAtBottom by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem != null && lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 2
        }
    }

    var lastMessageId by remember(groupId) { mutableStateOf<Long?>(messages.lastOrNull()?.id) }
    var jumpBackIndex by remember(groupId) { mutableStateOf<Int?>(null) }
    var showNewMessageBadge by remember { mutableStateOf(false) }

    // Detect scroll to top for loading history
    val oldestMessageId = remember(messages) { messages.firstOrNull()?.id }
    LaunchedEffect(listState.firstVisibleItemIndex, oldestMessageId) {
        if (listState.firstVisibleItemIndex == 0 && oldestMessageId != null && oldestMessageId > 0 && !isLoadingHistory) {
            onLoadOlderMessages(oldestMessageId)
        }
    }

    // Scroll memory initial load
    LaunchedEffect(messages.isNotEmpty(), groupId) {
        if (messages.isNotEmpty() && !initialScrollDone) {
            val savedPosition = repository.getReadPosition(groupId)
            if (savedPosition != null) {
                val (index, offset) = savedPosition
                listState.scrollToItem(index, offset)
            } else {
                val target = if (isLoadingHistory) messages.size else (messages.size - 1).coerceAtLeast(0)
                listState.scrollToItem(target)
            }
            initialScrollDone = true
        }
    }

    // Save scroll position continuously
    LaunchedEffect(initialScrollDone, groupId) {
        if (initialScrollDone) {
            snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
                .distinctUntilChanged()
                .collect { (index, offset) ->
                    repository.saveReadPosition(groupId, index, offset)
                }
        }
    }

    // Scroll to bottom when new messages are sent, or show new message badge if scrolled up
    LaunchedEffect(messages.size) {
        val newLastMsg = messages.lastOrNull()
        if (newLastMsg != null && lastMessageId != null && newLastMsg.id != lastMessageId) {
            if (newLastMsg.senderName == "我") {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                showNewMessageBadge = false
            } else {
                if (!isAtBottom) {
                    showNewMessageBadge = true
                }
            }
        }
        if (newLastMsg != null) {
            lastMessageId = newLastMsg.id
        }
    }

    // Clear badge when scrolled to bottom
    LaunchedEffect(isAtBottom) {
        if (isAtBottom) {
            showNewMessageBadge = false
        }
    }

    val onQuoteJump: (Int, Int) -> Unit = { fromIndex, toIndex ->
        jumpBackIndex = fromIndex
        coroutineScope.launch {
            listState.animateScrollToItem(toIndex)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Message List Container
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(top = 12.dp, bottom = 12.dp)
            ) {
                if (isLoadingHistory) {
                    item(key = "loading_history_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(
                                color = AccentGreen,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                }

                items(messages, key = { it.id }) { message ->
                    MessageItem(
                        message = message,
                        cookie = cookie,
                        messages = messages,
                        listState = listState,
                        coroutineScope = coroutineScope,
                        onShowMessageContext = onShowMessageContext,
                        onImageClick = onImageClick,
                        onVideoClick = onVideoClick,
                        onOpenWeiboStatus = onOpenWeiboStatus,
                        onQuoteJump = onQuoteJump
                    )
                }
            }

            // New Message Floating Badge
            if (showNewMessageBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(AccentGreen, RoundedCornerShape(20.dp))
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                                showNewMessageBadge = false
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⬇ 有新消息",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Jump Back Floating Badge
            if (jumpBackIndex != null && !showNewMessageBadge) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .background(AccentGreen, RoundedCornerShape(20.dp))
                        .clickable {
                            coroutineScope.launch {
                                jumpBackIndex?.let { index ->
                                    listState.animateScrollToItem(index)
                                    jumpBackIndex = null
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "↩ 返回跳转前位置",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Go to Bottom Floating Badge (when no new messages)
            if (!isAtBottom && !showNewMessageBadge && listState.layoutInfo.totalItemsCount > 5) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 16.dp, end = 16.dp)
                        .background(BubbleBg, RoundedCornerShape(20.dp))
                        .border(0.5.dp, DividerGrey, RoundedCornerShape(20.dp))
                        .clickable {
                            coroutineScope.launch {
                                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "⬇ 最下方",
                        color = TextWhite,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        HorizontalDivider(color = DividerGrey, thickness = 1.dp)

        val weiboEmojis = remember {
            listOf(
                "[哈哈]", "[揣手]", "[蜡烛]", "[拜拜]", "[色]", "[微笑]", 
                "[怒]", "[吃瓜]", "[允悲]", "[泪]", "[羞涩]", "[偷笑]", 
                "[凉凉]", "[思考]", "[吐]", "[给力]", "[抱抱]", "[作揖]", 
                "[赞]", "[弱]", "[心]", "[伤心]"
            )
        }
        var showEmojiPanel by remember { mutableStateOf(false) }

        if (showEmojiPanel) {
            androidx.compose.foundation.lazy.LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InputBg)
                    .padding(vertical = 8.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(weiboEmojis) { emoji ->
                    val displayEmoji = remember(emoji) { emojiMap[emoji] ?: emoji }
                    Box(
                        modifier = Modifier
                            .background(BubbleBg, RoundedCornerShape(12.dp))
                            .clickable { 
                                inputText += displayEmoji
                                showEmojiPanel = false // Auto close panel on select to stay clean
                            }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(text = displayEmoji, color = TextWhite, fontSize = 18.sp)
                    }
                }
            }
            HorizontalDivider(color = DividerGrey, thickness = 0.5.dp)
        }

        // Bottom Input Area
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkBg)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            IconButton(onClick = { /* Image attachment */ }) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "发送图片",
                    tint = TextGrey
                )
            }
            IconButton(onClick = { showEmojiPanel = !showEmojiPanel }) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = "发送表情",
                    tint = if (showEmojiPanel) AccentGreen else TextGrey
                )
            }
            
            TextField(
                value = inputText,
                onValueChange = { inputText = it },
                placeholder = {
                    Text(
                        text = "发送消息...",
                        color = TextGrey,
                        fontSize = 14.sp
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = InputBg,
                    unfocusedContainerColor = InputBg,
                    disabledContainerColor = InputBg,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(20.dp),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        val cleanText = replaceUnicodeWithShortcodes(inputText)
                        onSendMessage(cleanText)
                        inputText = ""
                        showEmojiPanel = false
                    }
                })
            )

            Spacer(modifier = Modifier.width(4.dp))

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        val cleanText = replaceUnicodeWithShortcodes(inputText)
                        onSendMessage(cleanText)
                        inputText = ""
                        showEmojiPanel = false
                    }
                },
                enabled = inputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "发送",
                    tint = if (inputText.isNotBlank()) AccentGreen else TextGrey
                )
            }
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    cookie: String,
    messages: List<Message>,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    onShowMessageContext: (Message) -> Unit,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onOpenWeiboStatus: (String) -> Unit,
    onQuoteJump: (Int, Int) -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        // Meta Row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f, fill = false)
            ) {
                Text(
                    text = "${message.timestamp} | ",
                    color = TextGrey,
                    fontSize = 11.sp
                )
                Text(
                    text = message.senderName,
                    color = TextWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.clickable {
                        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        val clip = android.content.ClipData.newPlainText("username", message.senderName)
                        clipboard.setPrimaryClip(clip)
                        android.widget.Toast.makeText(context, "已复制用户名: ${message.senderName}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "@${message.groupSuffix}",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .border(0.8.dp, BorderGreen, RoundedCornerShape(3.dp))
                    .clickable { onShowMessageContext(message) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "查看上下文",
                    color = AccentGreen,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Normal
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Parse quotes
        val parsedMessage = remember(message.content) { parseMessageContent(message.content) }
        if (parsedMessage.quoteLayers.isNotEmpty()) {
            QuoteBoxRecursive(
                layers = parsedMessage.quoteLayers,
                index = parsedMessage.quoteLayers.size - 1,
                messages = messages,
                currentMessageId = message.id,
                listState = listState,
                coroutineScope = coroutineScope,
                onQuoteJump = onQuoteJump
            )
        }

        // Content Bubble
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current
        val normalizedLinkUrl = remember(message.linkUrl, message.content) {
            val rawUrl = message.linkUrl ?: extractFirstUrl(message.content)
            rawUrl?.let(::normalizeChatUrl)
        }
        val weiboStatusId = remember(normalizedLinkUrl) {
            normalizedLinkUrl?.let(::extractChatWeiboStatusId)
        }
        val hasWeiboCard = weiboStatusId != null
        val hasExternalLink = normalizedLinkUrl != null && !hasWeiboCard

        val isVideo = message.imageUrl != null && message.fileUrl != null && message.fileName == "video.mp4"

        if (isVideo) {
            // Video message bubble
            var imageAspectRatio by remember(message.imageUrl) { mutableStateOf<Float?>(null) }
            Box(
                modifier = Modifier
                    .background(BubbleBg, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.clickable {
                        onVideoClick("${message.fileUrl}##${message.imageUrl}")
                    }
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(message.imageUrl)
                            .addHeader("Cookie", cookie)
                            .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            .addHeader("Referer", "https://weibo.com/")
                            .crossfade(true)
                            .build(),
                        contentDescription = "视频封面",
                        onState = { state ->
                            if (state is AsyncImagePainter.State.Success) {
                                val intrinsicWidth = state.painter.intrinsicSize.width
                                val intrinsicHeight = state.painter.intrinsicSize.height
                                if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                                    imageAspectRatio = intrinsicWidth / intrinsicHeight
                                }
                            }
                        },
                        modifier = Modifier
                            .then(
                                if (imageAspectRatio != null) {
                                    val ratio = imageAspectRatio!!
                                    val maxWidth = 240.dp
                                    val maxHeight = 240.dp
                                    if (ratio > 1f) {
                                        Modifier
                                            .width(maxWidth)
                                            .height(maxWidth / ratio)
                                    } else {
                                        Modifier
                                            .height(maxHeight)
                                            .width(maxHeight * ratio)
                                    }
                                } else {
                                    Modifier
                                        .widthIn(max = 240.dp)
                                        .heightIn(max = 240.dp)
                                }
                            )
                            .background(Color.DarkGray, RoundedCornerShape(6.dp)),
                        contentScale = androidx.compose.ui.layout.ContentScale.Fit
                    )
                    
                    // Dark semi-transparent overlay to make the play button pop and look professional
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                    )
                    
                    // Play icon in the center
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "播放视频",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        } else if (message.imageUrl != null) {
            // Image message bubble
            var imageAspectRatio by remember(message.imageUrl) { mutableStateOf<Float?>(null) }
            Box(
                modifier = Modifier
                    .background(BubbleBg, RoundedCornerShape(8.dp))
                    .padding(4.dp)
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(message.imageUrl)
                        .addHeader("Cookie", cookie)
                        .addHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                        .addHeader("Referer", "https://weibo.com/")
                        .crossfade(true)
                        .build(),
                    contentDescription = "图片内容",
                    onState = { state ->
                        if (state is AsyncImagePainter.State.Success) {
                            val intrinsicWidth = state.painter.intrinsicSize.width
                            val intrinsicHeight = state.painter.intrinsicSize.height
                            if (intrinsicWidth > 0 && intrinsicHeight > 0) {
                                imageAspectRatio = intrinsicWidth / intrinsicHeight
                            }
                        }
                    },
                    modifier = Modifier
                        .then(
                            if (imageAspectRatio != null) {
                                val ratio = imageAspectRatio!!
                                val maxWidth = 240.dp
                                val maxHeight = 240.dp
                                if (ratio > 1f) {
                                    Modifier
                                        .width(maxWidth)
                                        .height(maxWidth / ratio)
                                } else {
                                    Modifier
                                        .height(maxHeight)
                                        .width(maxHeight * ratio)
                                }
                            } else {
                                Modifier
                                    .widthIn(max = 240.dp)
                                    .heightIn(max = 240.dp)
                            }
                        )
                        .background(Color.DarkGray, RoundedCornerShape(6.dp))
                        .clickable { onImageClick(message.imageUrl) },
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit
                )
            }
        } else if (message.fileUrl != null) {
            // File message bubble card
            Card(
                colors = CardDefaults.cardColors(containerColor = InputBg),
                border = BorderStroke(0.5.dp, DividerGrey),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                        try {
                            val request = android.app.DownloadManager.Request(android.net.Uri.parse(message.fileUrl))
                            request.addRequestHeader("Cookie", cookie)
                            request.addRequestHeader("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                            request.addRequestHeader("Referer", "https://weibo.com/")
                            request.setTitle(message.fileName ?: "下载文件")
                            request.setDescription("正在从微博下载文件...")
                            request.setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                            request.setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, message.fileName ?: "download_file")
                            dm.enqueue(request)
                            android.widget.Toast.makeText(context, "已开始下载: ${message.fileName}", android.widget.Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            android.widget.Toast.makeText(context, "下载启动失败: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Attachment,
                        contentDescription = "文件附件",
                        tint = AccentGreen,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = message.fileName ?: "未命名文件",
                            color = TextWhite,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "文件附件 | 点击下载至本地",
                            color = TextGrey,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        } else if (!hasWeiboCard) {
            // Text message bubble
            val displayText = remember(message.content, parsedMessage) {
                if (hasExternalLink) {
                    normalizedLinkUrl
                } else {
                    replaceWeiboShortcodes(parsedMessage.immediateText)
                }
            }
            Box(
                modifier = Modifier
                    .background(BubbleBg, RoundedCornerShape(8.dp))
                    .then(
                        if (hasExternalLink) {
                            Modifier.clickable {
                                runCatching { uriHandler.openUri(normalizedLinkUrl) }
                            }
                        } else {
                            Modifier
                        }
                    )
                    .padding(12.dp)
                    .fillMaxWidth()
            ) {
                SelectionContainer {
                    Text(
                        text = displayText,
                        color = if (hasExternalLink) AccentGreen else TextWhite,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        if (hasWeiboCard) {
            Spacer(modifier = Modifier.height(6.dp))
            WeiboLinkMessageCard(
                title = message.linkTitle,
                desc = message.linkDesc,
                imageUrl = message.linkImg,
                onClick = { onOpenWeiboStatus(weiboStatusId) }
            )
        }
    }
}

@Composable
private fun WeiboLinkMessageCard(
    title: String?,
    desc: String?,
    imageUrl: String?,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Card(
        colors = CardDefaults.cardColors(containerColor = InputBg),
        border = BorderStroke(0.5.dp, DividerGrey),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(imageUrl.replace("http://", "https://"))
                        .crossfade(true)
                        .build(),
                    contentDescription = "微博缩略图",
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Gray, RoundedCornerShape(4.dp)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(10.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title?.takeIf { it.isNotBlank() } ?: "微博详情",
                    color = AccentGreen,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!desc.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = replaceWeiboShortcodes(desc),
                        color = TextWhite,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 16.sp
                    )
                }
            }
        }
    }
}


fun getOriginalImageUrl(thumbnailUrl: String): String {
    if (thumbnailUrl.contains("msget_thumbnail")) {
        var url = thumbnailUrl.replace("msget_thumbnail", "msget")
        url = url.replace(Regex("[&?]high=[^&]*"), "")
        url = url.replace(Regex("[&?]width=[^&]*"), "")
        url = url.replace(Regex("[&?]size=[^&]*"), "")
        url = url.replace("?&", "?").replace("&&", "&")
        return url
    }
    return thumbnailUrl
}

private fun extractFirstUrl(text: String): String? {
    return Regex("https?://[^\\s]+").find(text)?.value
}

private fun normalizeChatUrl(url: String): String {
    return when {
        url.startsWith("//") -> "https:$url"
        url.startsWith("/") -> "https://m.weibo.cn$url"
        url.startsWith("http://") || url.startsWith("https://") -> url
        else -> "https://m.weibo.cn/$url"
    }.trimEnd('，', '。', ',', '.', ')', '）')
}

private fun extractChatWeiboStatusId(url: String): String? {
    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull() ?: return null
    val host = uri.host ?: return null
    if (!host.endsWith("weibo.com") && !host.endsWith("weibo.cn")) return null

    uri.getQueryParameter("mid")?.takeIf { it.isNotBlank() }?.let { return it }
    uri.getQueryParameter("id")?.takeIf { it.isNotBlank() }?.let { return it }

    val pathSegments = uri.pathSegments ?: return null
    val statusIndex = pathSegments.indexOf("status")
    if (statusIndex != -1 && statusIndex + 1 < pathSegments.size) {
        return pathSegments[statusIndex + 1]
    }
    val detailIndex = pathSegments.indexOf("detail")
    if (detailIndex != -1 && detailIndex + 1 < pathSegments.size) {
        return pathSegments[detailIndex + 1]
    }
    if (pathSegments.size >= 2 && pathSegments[0].all { it.isDigit() } && pathSegments[1].length >= 8) {
        return pathSegments[1]
    }

    return pathSegments.firstOrNull { segment ->
        (segment.length >= 15 && segment.all { it.isDigit() }) ||
            (segment.length in 8..10 && segment.any { it.isLetter() } && segment.any { it.isDigit() })
    }
}

val emojiMap = mapOf(
    "[哈哈]" to "😄",
    "[揣手]" to "🤷",
    "[蜡烛]" to "🕯️",
    "[拜拜]" to "👋",
    "[色]" to "😍",
    "[微笑]" to "🙂",
    "[怒]" to "😡",
    "[吃瓜]" to "🍉",
    "[允悲]" to "😭",
    "[泪]" to "😢",
    "[羞涩]" to "😊",
    "[偷笑]" to "🤭",
    "[凉凉]" to "🥶",
    "[思考]" to "🤔",
    "[吐]" to "🤮",
    "[给力]" to "💪",
    "[抱抱]" to "🤗",
    "[作揖]" to "🙏",
    "[赞]" to "👍",
    "[弱]" to "👎",
    "[心]" to "❤️",
    "[伤心]" to "💔"
)

fun replaceWeiboShortcodes(content: String): String {
    var result = cleanWeiboHtmlText(content)
    emojiMap.forEach { (shortcode, emoji) ->
        result = result.replace(shortcode, emoji)
    }
    return result
}

fun replaceUnicodeWithShortcodes(content: String): String {
    var result = content
    emojiMap.forEach { (shortcode, emoji) ->
        result = result.replace(emoji, shortcode)
    }
    return result
}

@Composable
fun SearchResultItem(
    message: Message,
    searchQuery: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = message.senderName,
                color = AccentGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.clickable {
                    val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    val clip = android.content.ClipData.newPlainText("username", message.senderName)
                    clipboard.setPrimaryClip(clip)
                    android.widget.Toast.makeText(context, "已复制用户名: ${message.senderName}", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
            Text(
                text = message.timestamp,
                color = TextGrey,
                fontSize = 11.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        
        val rawContent = replaceWeiboShortcodes(parseMessageContent(message.content).immediateText)
        SelectionContainer {
            Text(
                text = rawContent,
                color = TextWhite,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun QuoteBoxRecursive(
    layers: List<com.example.weibochat.data.QuoteLayer>,
    index: Int,
    messages: List<Message>,
    currentMessageId: Long?,
    listState: LazyListState,
    coroutineScope: CoroutineScope,
    onQuoteJump: (Int, Int) -> Unit
) {
    if (index < 0 || index >= layers.size) return
    val layer = layers[index]
    val cleanText = remember(layer.cleanText) { replaceWeiboShortcodes(layer.cleanText) }
    
    val currentMsg = remember(messages, currentMessageId) {
        if (currentMessageId != null) messages.find { it.id == currentMessageId } else null
    }
    
    val currentLayerMsgId = remember(currentMsg) {
        currentMsg?.parentMsgId
    }
    
    val resolvedLayerMsgId = remember(currentLayerMsgId, currentMessageId, layer.senderName, layer.cleanText, messages) {
        if (currentLayerMsgId != null) {
            currentLayerMsgId
        } else if (currentMessageId != null) {
            val key = "$currentMessageId|${layer.senderName}|${layer.cleanText}"
            val cachedId = com.example.weibochat.data.quoteIdLookupCache[key]
            if (cachedId != null) {
                if (cachedId != -1L) cachedId else null
            } else {
                val idx = findQuotedMessageIndex(messages, currentMessageId, layer.senderName, layer.cleanText)
                val resolvedId = if (idx != -1) messages[idx].id else -1L
                com.example.weibochat.data.quoteIdLookupCache[key] = resolvedId
                if (resolvedId != -1L) resolvedId else null
            }
        } else {
            null
        }
    }

    val currentLayerMsgIdx = remember(resolvedLayerMsgId, messages) {
        if (resolvedLayerMsgId != null) {
            messages.indexOfFirst { it.id == resolvedLayerMsgId }
        } else {
            -1
        }
    }
    
    val quotedSender = remember(layer.senderName, currentLayerMsgIdx, messages) {
        layer.senderName ?: run {
            if (currentLayerMsgIdx != -1) messages[currentLayerMsgIdx].senderName else null
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
            .background(InputBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .border(
                BorderStroke(0.8.dp, DividerGrey),
                RoundedCornerShape(6.dp)
            )
            .clickable {
                if (currentLayerMsgIdx != -1) {
                    val originalIndex = listState.firstVisibleItemIndex
                    onQuoteJump(originalIndex, currentLayerMsgIdx)
                }
            }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(14.dp)
                        .background(AccentGreen, RoundedCornerShape(1.5.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                val context = LocalContext.current
                Text(
                    text = if (quotedSender != null) "@$quotedSender" else "引用内容",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable {
                        if (quotedSender != null) {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            val clip = android.content.ClipData.newPlainText("username", quotedSender)
                            clipboard.setPrimaryClip(clip)
                            android.widget.Toast.makeText(context, "已复制用户名: $quotedSender", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            
            if (index > 0) {
                QuoteBoxRecursive(
                    layers = layers,
                    index = index - 1,
                    messages = messages,
                    currentMessageId = resolvedLayerMsgId,
                    listState = listState,
                    coroutineScope = coroutineScope,
                    onQuoteJump = onQuoteJump
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            
            SelectionContainer {
                Text(
                    text = cleanText,
                    color = TextGrey,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
