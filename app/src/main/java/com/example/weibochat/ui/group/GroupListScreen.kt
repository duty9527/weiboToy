package com.example.weibochat.ui.group

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.platform.LocalContext
import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.WeiboContact
import com.example.weibochat.ui.main.replaceWeiboShortcodes
import com.example.weibochat.ui.weibo.WeiboTimelineScreen
import com.example.weibochat.ui.weibo.WeiboTimelineViewModel
import com.example.weibochat.ui.weibo.WeiboWebScreen
import com.example.weibochat.ui.weibo.WEIBO_SEARCH_URL
import com.example.weibochat.ui.weibo.WeiboMobileCookieSync
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Close
import com.example.weibochat.ui.weibo.seedWeiboWebViewCookies
import com.example.weibochat.ui.weibo.collectWeiboWebViewCookies
import com.example.weibochat.ui.weibo.hasWeiboAuthCookie
import com.example.weibochat.ui.weibo.isWeiboLoginUrl
import com.example.weibochat.ui.weibo.mergeCookieStrings
import com.example.weibochat.ui.weibo.isAllowedWeiboHost
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.webkit.CookieManager
import android.webkit.WebResourceRequest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupListScreen(
    onGroupClick: (groupId: String, groupName: String) -> Unit,
    onLogout: () -> Unit,
    repository: DataRepository,
    timelineViewModel: WeiboTimelineViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val coroutineScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var groups by remember { mutableStateOf<List<WeiboContact>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var selectedTab by remember { mutableStateOf(0) }
    var showWeiboSearch by remember { mutableStateOf(false) }
    var showVerificationWebView by remember { mutableStateOf(false) }

    fun loadGroups() {
        android.util.Log.d("GroupListScreen", "loadGroups() called")
        coroutineScope.launch {
            isLoading = groups.isEmpty()
            errorMessage = null
            try {
                val list = repository.fetchContacts()
                android.util.Log.d("GroupListScreen", "loadGroups(): fetched ${list.size} contacts")
                val filtered = list.filter { it.user?.type == 2 }
                android.util.Log.d("GroupListScreen", "loadGroups(): filtered ${filtered.size} group contacts")
                if (filtered.isNotEmpty()) {
                    groups = filtered
                } else if (groups.isEmpty()) {
                    errorMessage = "未获取到群聊列表，请检查网络或Cookie后重试"
                } else {
                    android.widget.Toast.makeText(context, "同步群聊列表为空，展示缓存数据", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                android.util.Log.e("GroupListScreen", "loadGroups() failed", e)
                if (groups.isEmpty()) {
                    errorMessage = "加载失败: ${e.localizedMessage}"
                } else {
                    android.widget.Toast.makeText(context, "刷新失败: ${e.localizedMessage}，已展示本地缓存", android.widget.Toast.LENGTH_LONG).show()
                }
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadGroups()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (selectedTab == 0) "微博群聊列表" else if (showWeiboSearch) "微博搜索" else "最新微博",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    if (selectedTab == 1 && showWeiboSearch) {
                        IconButton(onClick = { showWeiboSearch = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回微博列表",
                                tint = Color.White
                            )
                        }
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = { loadGroups() }) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "刷新",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onLogout) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "注销登录",
                                tint = Color(0xFFFCA5A5)
                            )
                        }
                    } else {
                        IconButton(onClick = { showWeiboSearch = true }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索微博",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F111A)
                )
            )
        },
        bottomBar = {
            var lastWeiboTabClickTime by remember { mutableStateOf(0L) }
            NavigationBar(
                containerColor = Color(0xFF0F111A),
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "消息"
                        )
                    },
                    label = { Text("消息") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFF97316),
                        selectedTextColor = Color(0xFFF97316),
                        indicatorColor = Color(0x1AF97316),
                        unselectedIconColor = Color(0xFFA0A5C0),
                        unselectedTextColor = Color(0xFFA0A5C0)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = {
                        val currentTime = System.currentTimeMillis()
                        if (selectedTab == 1 && currentTime - lastWeiboTabClickTime < 300) {
                            timelineViewModel.triggerScrollToTopAndRefresh()
                        } else {
                            selectedTab = 1
                            showWeiboSearch = false
                        }
                        lastWeiboTabClickTime = currentTime
                    },
                    icon = {
                        Icon(
                            imageVector = Icons.Default.RssFeed,
                            contentDescription = "微博"
                        )
                    },
                    label = { Text("微博") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFF97316),
                        selectedTextColor = Color(0xFFF97316),
                        indicatorColor = Color(0x1AF97316),
                        unselectedIconColor = Color(0xFFA0A5C0),
                        unselectedTextColor = Color(0xFFA0A5C0)
                    )
                )
            }
        },
        containerColor = Color(0xFF0F111A),
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF0F111A),
                            Color(0xFF1B1829)
                        )
                    )
                )
        ) {
            if (selectedTab == 0) {
                if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFFC084FC)
                )
            } else if (errorMessage != null) {
                val isSessionExpired = errorMessage?.contains("会话已失效") == true || errorMessage?.contains("重新登录") == true
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = errorMessage ?: "",
                        color = Color(0xFFFCA5A5),
                        fontSize = 15.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Button(
                            onClick = {
                                if (isSessionExpired) {
                                    onLogout()
                                } else {
                                    loadGroups()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFC084FC)
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (isSessionExpired) "重新登录" else "重试", color = Color.White)
                        }

                        if (!isSessionExpired) {
                            Button(
                                onClick = {
                                    showVerificationWebView = true
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFFF97316)
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("手动验证", color = Color.White)
                            }
                        }
                    }
                }
            } else if (groups.isEmpty()) {
                Text(
                    text = "暂无群聊联系人",
                    color = Color(0xFFA0A5C0),
                    fontSize = 16.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(groups) { item ->
                        val user = item.user
                        val message = item.message
                        val groupId = user?.idstr ?: user?.id?.toString() ?: ""
                        val groupName = user?.name ?: "未命名群聊"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    repository.setActiveGroupId(groupId)
                                    onGroupClick(groupId, groupName)
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0x1AFFFFFF)
                            ),
                            shape = RoundedCornerShape(16.dp),
                            border = CardDefaults.outlinedCardBorder(true).copy(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0x1FFFFFFF),
                                        Color(0x05FFFFFF)
                                    )
                                )
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Group Avatar
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color(0x1AFFFFFF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (!user?.profile_image_url.isNullOrBlank()) {
                                        AsyncImage(
                                            model = user.profile_image_url,
                                            contentDescription = groupName,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                        )
                                    } else {
                                        Text(
                                            text = groupName.take(1),
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                // Group Details
                                Column(
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = groupName,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    
                                    val lastMsgText = if (message != null) {
                                         val rawText = message.text ?: ""
                                         val parts = rawText.split(Regex("[:：]"), 2)
                                         val sender = if (parts.size > 1) parts[0].trim() else (message.sender_screen_name ?: "")
                                         val content = if (parts.size > 1) parts[1].trim() else rawText
                                         
                                         if (repository.isMessageBlocked(sender, content)) {
                                             "已屏蔽消息"
                                         } else {
                                             rawText
                                         }
                                     } else {
                                         "暂无新消息"
                                     }
                                    
                                    Text(
                                        text = replaceWeiboShortcodes(lastMsgText),
                                        color = Color(0xFFA0A5C0),
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Unread Badge
                                if (item.unread_count > 0) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                brush = Brush.horizontalGradient(
                                                    colors = listOf(
                                                        Color(0xFFEF4444),
                                                        Color(0xFFF97316)
                                                    )
                                                ),
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.unread_count.toString(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            } else if (showWeiboSearch) {
                WeiboWebScreen(
                    repository = repository,
                    reloadSignal = 0,
                    initialUrl = WEIBO_SEARCH_URL,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                WeiboTimelineScreen(
                    viewModel = timelineViewModel,
                    repository = repository,
                    modifier = Modifier.fillMaxSize()
                )
            }

            WeiboMobileCookieSync(
                repository = repository,
                onCookiesReady = {
                    loadGroups()
                    timelineViewModel.refresh()
                }
            )

            if (showVerificationWebView) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xCC000000))
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFF10121B)
                        ),
                        border = CardDefaults.outlinedCardBorder(true).copy(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color(0x33FFFFFF),
                                    Color(0x05FFFFFF)
                                )
                            )
                        )
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1A1C24))
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "微博安全验证与同步",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                IconButton(onClick = { showVerificationWebView = false }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "关闭",
                                        tint = Color.White
                                    )
                                }
                            }

                            Box(modifier = Modifier.weight(1f)) {
                                var webViewInstance by remember { mutableStateOf<WebView?>(null) }
                                
                                DisposableEffect(Unit) {
                                    onDispose {
                                        webViewInstance?.apply {
                                            stopLoading()
                                            webViewClient = WebViewClient()
                                            removeAllViews()
                                            destroy()
                                        }
                                        webViewInstance = null
                                    }
                                }

                                val webViewUserAgent = "Mozilla/5.0 (Linux; Android 14; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        seedWeiboWebViewCookies(repository.getAllCookies())
                                        WebView(ctx).apply {
                                            webViewInstance = this
                                            settings.javaScriptEnabled = true
                                            settings.domStorageEnabled = true
                                            settings.cacheMode = WebSettings.LOAD_DEFAULT
                                            settings.userAgentString = webViewUserAgent
                                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                            webViewClient = object : WebViewClient() {
                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    CookieManager.getInstance().flush()
                                                    val webViewCookie = collectWeiboWebViewCookies()
                                                    val hasAuth = hasWeiboAuthCookie(webViewCookie)
                                                    val isLogin = isWeiboLoginUrl(url)

                                                    if (webViewCookie.isNotBlank() && hasAuth && !isLogin) {
                                                        val merged = mergeCookieStrings(repository.getAllCookies(), webViewCookie)
                                                        repository.saveMobileCookie(merged)
                                                        android.widget.Toast.makeText(context, "验证并同步Cookie成功！", android.widget.Toast.LENGTH_SHORT).show()
                                                        showVerificationWebView = false
                                                        loadGroups()
                                                        timelineViewModel.refresh()
                                                    }
                                                }

                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView?,
                                                    request: WebResourceRequest?
                                                ): Boolean {
                                                    val host = request?.url?.host ?: return false
                                                    return !isAllowedWeiboHost(host)
                                                }
                                            }
                                            loadUrl("https://m.weibo.cn/")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
