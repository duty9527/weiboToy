package com.duty.weibotoy

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable data object Login : NavKey
@Serializable data object GroupList : NavKey
@Serializable data class Chat(val groupId: String, val groupName: String) : NavKey
