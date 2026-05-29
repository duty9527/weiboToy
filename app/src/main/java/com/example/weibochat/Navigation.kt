package com.example.weibochat

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject
import com.example.weibochat.data.DataRepository
import com.example.weibochat.ui.main.MainScreen
import com.example.weibochat.ui.main.MainScreenViewModel
import com.example.weibochat.ui.login.LoginScreen
import com.example.weibochat.ui.group.GroupListScreen
import com.example.weibochat.ui.weibo.WeiboTimelineViewModel

@Composable
fun MainNavigation() {
  val repository: DataRepository = koinInject()
  val mainViewModel: MainScreenViewModel = koinViewModel()
  val timelineViewModel: WeiboTimelineViewModel = koinViewModel()

  
  val credentials = remember { repository.getCredentials() }
  val startDestination = remember {
    if (credentials.first.isNotBlank()) GroupList else Login
  }
  
  val backStack = rememberNavBackStack(startDestination)

  NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider =
      entryProvider {
        entry<Login> {
          LoginScreen(
            onLoginSuccess = {
              backStack.removeLastOrNull()
              backStack.add(GroupList)
            },
            repository = repository,
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<GroupList> {
          GroupListScreen(
            onGroupClick = { groupId, groupName ->
              backStack.add(Chat(groupId, groupName))
            },
            onLogout = {
              repository.saveCredentials("", "")
              backStack.removeLastOrNull()
              backStack.add(Login)
            },
            repository = repository,
            timelineViewModel = timelineViewModel,
            modifier = Modifier.fillMaxSize()
          )
        }
        entry<Chat> { chatKey ->
          MainScreen(
            groupId = chatKey.groupId,
            groupName = chatKey.groupName,
            onBackClick = { backStack.removeLastOrNull() },
            onItemClick = { navKey -> backStack.add(navKey) },
            viewModel = mainViewModel,
            timelineViewModel = timelineViewModel,
            repository = repository,
            modifier = Modifier.fillMaxSize()
          )
        }
      },
  )
}
