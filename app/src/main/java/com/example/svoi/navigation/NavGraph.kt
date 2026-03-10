package com.example.svoi.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.navArgument
import com.example.svoi.data.local.ThemeMode
import com.example.svoi.ui.auth.InviteKeyScreen
import com.example.svoi.ui.auth.LoginScreen
import com.example.svoi.ui.auth.SetupProfileScreen
import com.example.svoi.ui.chat.ChatScreen
import com.example.svoi.ui.chatlist.ChatListScreen
import com.example.svoi.ui.newchat.CreateGroupScreen
import com.example.svoi.ui.newchat.UserSearchScreen
import com.example.svoi.ui.profile.ProfileScreen
import com.example.svoi.ui.settings.SettingsScreen

object Routes {
    const val LOGIN = "login"
    const val INVITE_KEY = "invite_key"
    const val SETUP_PROFILE = "setup_profile/{inviteKey}"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}"
    const val USER_SEARCH = "user_search"
    const val CREATE_GROUP = "create_group"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"

    fun setupProfile(inviteKey: String) = "setup_profile/$inviteKey"
    fun chat(chatId: String) = "chat/$chatId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    onThemeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM
) {
    // Debounce: prevent rapid-fire navigation events (e.g. accidental double-tap)
    var lastNavMs by remember { mutableLongStateOf(0L) }
    fun canNav(): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastNavMs > 400L) { lastNavMs = now; true } else false
    }

    // Safety net: if back stack somehow becomes empty, go back to start
    val backEntry by navController.currentBackStackEntryAsState()
    if (backEntry == null && navController.graph.startDestinationRoute != null) {
        navController.navigate(startDestination) {
            popUpTo(0) { inclusive = true }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        if (canNav()) navController.navigate(Routes.CHAT_LIST) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onInviteKeyClick = {
                        if (canNav()) navController.navigate(Routes.INVITE_KEY)
                    }
                )
            }

            composable(Routes.INVITE_KEY) {
                InviteKeyScreen(
                    onKeyValidated = { key ->
                        if (canNav()) navController.navigate(Routes.setupProfile(key))
                    },
                    onBack = { if (canNav()) navController.popBackStack() }
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
                        if (canNav()) navController.navigate(Routes.CHAT_LIST) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onBack = { if (canNav()) navController.popBackStack() }
                )
            }

            composable(Routes.CHAT_LIST) {
                ChatListScreen(
                    onChatClick = { chatId ->
                        if (canNav()) navController.navigate(Routes.chat(chatId))
                    },
                    onNewChatClick = {
                        if (canNav()) navController.navigate(Routes.USER_SEARCH)
                    },
                    onProfileClick = {
                        if (canNav()) navController.navigate(Routes.PROFILE)
                    },
                    onSettingsClick = {
                        if (canNav()) navController.navigate(Routes.SETTINGS)
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
                    onBack = { if (canNav()) navController.popBackStack() },
                    onForwardTo = { targetChatId ->
                        if (canNav()) navController.navigate(Routes.chat(targetChatId))
                    }
                )
            }

            composable(Routes.USER_SEARCH) {
                UserSearchScreen(
                    onChatOpened = { chatId ->
                        if (canNav()) navController.navigate(Routes.chat(chatId)) {
                            popUpTo(Routes.USER_SEARCH) { inclusive = true }
                        }
                    },
                    onCreateGroup = {
                        if (canNav()) navController.navigate(Routes.CREATE_GROUP)
                    },
                    onBack = { if (canNav()) navController.popBackStack() }
                )
            }

            composable(Routes.CREATE_GROUP) {
                CreateGroupScreen(
                    onGroupCreated = { chatId ->
                        if (canNav()) navController.navigate(Routes.chat(chatId)) {
                            popUpTo(Routes.USER_SEARCH) { inclusive = true }
                        }
                    },
                    onBack = { if (canNav()) navController.popBackStack() }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onBack = { if (canNav()) navController.popBackStack() },
                    onLogout = {
                        if (canNav()) navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeChanged = onThemeChanged,
                    onBack = { if (canNav()) navController.popBackStack() }
                )
            }
        }
    }
}
