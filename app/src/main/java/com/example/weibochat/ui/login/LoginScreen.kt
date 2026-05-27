package com.example.weibochat.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalContext
import com.example.weibochat.data.DataRepository
import com.example.weibochat.data.WeiboApiClient

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    repository: DataRepository,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    var qrImageUrl by remember { mutableStateOf<String?>(null) }
    var qrid by remember { mutableStateOf<String?>(null) }
    var statusText by remember { mutableStateOf("正在获取二维码...") }
    var isError by remember { mutableStateOf(false) }
    var pollingJobActive by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val apiClient = remember { WeiboApiClient(context) }

    fun refreshQrCode() {
        coroutineScope.launch {
            statusText = "正在获取二维码..."
            isError = false
            qrImageUrl = null
            qrid = null
            apiClient.clearSession()
            val response = apiClient.fetchQrCode()
            if (response != null && response.retcode == 20000000 && response.data != null) {
                qrImageUrl = response.data.image
                qrid = response.data.qrid
                statusText = "请使用微博APP扫码登录"
                pollingJobActive = true
            } else {
                statusText = "获取二维码失败，请重试"
                isError = true
            }
        }
    }

    LaunchedEffect(Unit) {
        refreshQrCode()
    }

    LaunchedEffect(qrid, pollingJobActive) {
        val currentQrid = qrid
        if (currentQrid != null && pollingJobActive) {
            while (true) {
                delay(2000)
                val statusResponse = apiClient.checkQrStatus(currentQrid)
                if (statusResponse != null) {
                    when (statusResponse.retcode) {
                        20000000 -> {
                            statusText = "登录成功，同步Cookie中..."
                            val alt = statusResponse.data?.alt
                            if (alt != null) {
                                val mergedCookie = apiClient.performSsoLogin(alt)
                                if (mergedCookie != null) {
                                    repository.saveCredentials(mergedCookie, "")
                                    onLoginSuccess()
                                    break
                                } else {
                                    statusText = "跨域登录失败，请重试"
                                    pollingJobActive = false
                                    isError = true
                                    break
                                }
                            } else {
                                statusText = "参数缺失，请重试"
                                pollingJobActive = false
                                isError = true
                                break
                            }
                        }
                        50114002 -> {
                            statusText = "已扫码，请在手机端确认授权"
                        }
                        50114001 -> {
                            statusText = "请使用微博APP扫码登录"
                        }
                        50114004 -> {
                            statusText = "二维码已失效，点击刷新"
                            pollingJobActive = false
                            isError = true
                            break
                        }
                        else -> {
                            statusText = statusResponse.msg ?: "未知状态，请刷新重试"
                            pollingJobActive = false
                            isError = true
                            break
                        }
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F111A),
                        Color(0xFF1B1829),
                        Color(0xFF281F3D)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(340.dp)
                .padding(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0x11FFFFFF)
            ),
            shape = RoundedCornerShape(24.dp),
            border = CardDefaults.outlinedCardBorder(true).copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0x33FFFFFF),
                        Color(0x05FFFFFF)
                    )
                )
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "Weibo Chat",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    letterSpacing = 1.5.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "微博账号扫码登录",
                    fontSize = 14.sp,
                    color = Color(0xFFA0A5C0),
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(28.dp))

                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .background(Color(0x0AFFFFFF), RoundedCornerShape(16.dp))
                        .padding(8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (qrImageUrl != null) {
                        AsyncImage(
                            model = qrImageUrl,
                            contentDescription = "Scan to Login",
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.White, RoundedCornerShape(12.dp))
                                .padding(8.dp)
                        )
                    } else {
                        CircularProgressIndicator(
                            color = Color(0xFFC084FC)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = statusText,
                    fontSize = 15.sp,
                    color = if (isError) Color(0xFFFCA5A5) else Color.White,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                if (isError) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { refreshQrCode() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC084FC)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(text = "刷新二维码", color = Color.White)
                    }
                }
            }
        }
    }
}
