package com.example.svoi.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.svoi.ui.auth.InviteKeyScreen
import com.example.svoi.ui.auth.LoginScreen
import com.example.svoi.ui.auth.SetupProfileScreen
import com.example.svoi.ui.chat.ChatScreen
import com.example.svoi.ui.chatlist.ChatListScreen
import com.example.svoi.ui.newchat.CreateGroupScreen
import com.example.svoi.ui.newchat.UserSearchScreen
import com.example.svoi.ui.profile.ProfileScreen

object Routes {
    const val LOGIN = "login"
    const val INVITE_KEY = "invite_key"
    const val SETUP_PROFILE = "setup_profile/{inviteKey}"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}"
    const val USER_SEARCH = "user_search"
    const val CREATE_GROUP = "create_group"
    const val PROFILE = "profile"

    fun setupProfile(inviteKey: String) = "setup_profile/$inviteKey"
    fun chat(chatId: String) = "chat/$chatId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onInviteKeyClick = {
                    navController.navigate(Routes.INVITE_KEY)
                }
            )
        }

        composable(Routes.INVITE_KEY) {
            InviteKeyScreen(
                onKeyValidated = { key ->
                    navController.navigate(Routes.setupProfile(key))
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(
            route = Routes.SETUP_PROFILE,
            arguments = listOf(navArgument("inviteKey") { type = NavType.StringType })
        ) { backStack ->
            val inviteKey = backStack.arguments?.getString("inviteKey") ?: ""
            SetupProfileScreen(
                inviteKey = inviteKey,
                onSetupComplete = {
                    navController.navigate(Routes.CHAT_LIST) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CHAT_LIST) {
            ChatListScreen(
                onChatClick = { chatId ->
                    navController.navigate(Routes.chat(chatId))
                },
                onNewChatClick = {
                    navController.navigate(Routes.USER_SEARCH)
                },
                onProfileClick = {
                    navController.navigate(Routes.PROFILE)
                }
            )
        }

        composable(
            route = Routes.CHAT,
            arguments = listOf(navArgument("chatId") { type = NavType.StringType })
        ) { backStack ->
            val chatId = backStack.arguments?.getString("chatId") ?: ""
            ChatScreen(
                chatId = chatId,
                onBack = { navController.popBackStack() },
                onForwardTo = { targetChatId ->
                    navController.navigate(Routes.chat(targetChatId))
                }
            )
        }

        composable(Routes.USER_SEARCH) {
            UserSearchScreen(
                onChatOpened = { chatId ->
                    navController.navigate(Routes.chat(chatId)) {
                        popUpTo(Routes.USER_SEARCH) { inclusive = true }
                    }
                },
                onCreateGroup = {
                    navController.navigate(Routes.CREATE_GROUP)
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.CREATE_GROUP) {
            CreateGroupScreen(
                onGroupCreated = { chatId ->
                    navController.navigate(Routes.chat(chatId)) {
                        popUpTo(Routes.USER_SEARCH) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                }
            )
        }
    }
}
