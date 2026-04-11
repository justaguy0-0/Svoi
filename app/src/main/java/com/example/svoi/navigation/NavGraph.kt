package com.example.svoi.navigation

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.svoi.data.local.SvoiAccent
import com.example.svoi.data.local.ThemeMode
import com.example.svoi.ui.auth.AuthViewModel
import com.example.svoi.ui.auth.InviteKeyScreen
import com.example.svoi.ui.auth.LoginScreen
import com.example.svoi.ui.auth.SetupStep1Screen
import com.example.svoi.ui.auth.SetupStep2Screen
import com.example.svoi.ui.auth.SetupStep3Screen
import com.example.svoi.ui.chat.ChatScreen
import com.example.svoi.ui.chatlist.ChatListScreen
import com.example.svoi.ui.group.GroupInfoScreen
import com.example.svoi.ui.newchat.CreateGroupScreen
import com.example.svoi.ui.newchat.UserSearchScreen
import com.example.svoi.ui.profile.ProfileScreen
import com.example.svoi.ui.profile.UserProfileScreen
import com.example.svoi.ui.search.GlobalSearchScreen
import com.example.svoi.ui.settings.AppearanceScreen
import com.example.svoi.ui.settings.SettingsScreen
import com.example.svoi.ui.settings.WallpaperPickerScreen

object Routes {
    const val LOGIN = "login"
    const val INVITE_KEY = "invite_key"
    const val SETUP_STEP_1 = "setup_step1/{inviteKey}"
    const val SETUP_STEP_2 = "setup_step2"
    const val SETUP_STEP_3 = "setup_step3"
    const val CHAT_LIST = "chat_list"
    const val CHAT = "chat/{chatId}?messageId={messageId}"
    const val CHAT_NEW = "chat_new/{userId}"
    const val USER_SEARCH = "user_search"
    const val CREATE_GROUP = "create_group"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val USER_PROFILE = "user_profile/{userId}"
    const val GROUP_INFO = "group_info/{chatId}"
    const val GLOBAL_SEARCH = "global_search"
    const val WALLPAPER_PICKER = "wallpaper_picker"
    const val APPEARANCE = "appearance"

    fun setupStep1(inviteKey: String) = "setup_step1/$inviteKey"
    fun chat(chatId: String) = "chat/$chatId"
    fun chatWithMessage(chatId: String, messageId: String) = "chat/$chatId?messageId=$messageId"
    fun chatNew(userId: String) = "chat_new/$userId"
    fun userProfile(userId: String) = "user_profile/$userId"
    fun groupInfo(chatId: String) = "group_info/$chatId"
}

@Composable
fun NavGraph(
    navController: NavHostController,
    startDestination: String,
    onThemeChanged: (ThemeMode) -> Unit = {},
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    autoPlayVideos: Boolean = true,
    onAutoPlayChanged: (Boolean) -> Unit = {},
    currentAccent: SvoiAccent = SvoiAccent.BLUE,
    onAccentChanged: (SvoiAccent) -> Unit = {}
) {
    // Shared AuthViewModel — scoped to NavGraph (Activity), so all auth screens share state
    val authViewModel: AuthViewModel = viewModel()

    // Debounce: prevent rapid-fire navigation events (e.g. accidental double-tap)
    var lastNavMs by remember { mutableLongStateOf(0L) }
    fun canNav(): Boolean {
        val now = System.currentTimeMillis()
        return if (now - lastNavMs > 400L) { lastNavMs = now; true } else false
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            enterTransition    = { fadeIn(tween(200)) },
            exitTransition     = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(200)) },
            popExitTransition  = { fadeOut(tween(180)) }
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
                        if (canNav()) navController.navigate(Routes.setupStep1(key))
                    },
                    onBack = { if (canNav()) navController.navigateUp() },
                    viewModel = authViewModel
                )
            }

            composable(
                route = Routes.SETUP_STEP_1,
                arguments = listOf(navArgument("inviteKey") { type = NavType.StringType })
            ) { backStack ->
                val inviteKey = backStack.arguments?.getString("inviteKey") ?: ""
                SetupStep1Screen(
                    inviteKey = inviteKey,
                    onNext = { if (canNav()) navController.navigate(Routes.SETUP_STEP_2) },
                    onBack = { if (canNav()) navController.navigateUp() },
                    viewModel = authViewModel
                )
            }

            composable(Routes.SETUP_STEP_2) {
                SetupStep2Screen(
                    onNext = { if (canNav()) navController.navigate(Routes.SETUP_STEP_3) },
                    onBack = { if (canNav()) navController.navigateUp() },
                    viewModel = authViewModel
                )
            }

            composable(Routes.SETUP_STEP_3) {
                SetupStep3Screen(
                    onSetupComplete = {
                        if (canNav()) navController.navigate(Routes.CHAT_LIST) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    },
                    onBack = { if (canNav()) navController.navigateUp() },
                    viewModel = authViewModel
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
                    },
                    onSearchClick = {
                        if (canNav()) navController.navigate(Routes.GLOBAL_SEARCH)
                    }
                )
            }

            composable(Routes.GLOBAL_SEARCH) {
                GlobalSearchScreen(
                    onBack = { if (canNav()) navController.navigateUp() },
                    onChatClick = { chatId, messageId ->
                        if (canNav()) navController.navigate(Routes.chatWithMessage(chatId, messageId))
                    }
                )
            }

            composable(
                route = Routes.CHAT,
                arguments = listOf(
                    navArgument("chatId") { type = NavType.StringType },
                    navArgument("messageId") { type = NavType.StringType; nullable = true; defaultValue = null }
                )
            ) { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: ""
                val messageId = backStack.arguments?.getString("messageId")?.takeIf { it.isNotEmpty() }
                ChatScreen(
                    chatId = chatId,
                    initialMessageId = messageId,
                    autoPlayVideos = autoPlayVideos,
                    onBack = { if (canNav()) navController.navigateUp() },
                    onForwardTo = { targetChatId ->
                        if (canNav()) navController.navigate(Routes.chat(targetChatId))
                    },
                    onUserClick = { userId ->
                        if (canNav()) navController.navigate(Routes.userProfile(userId))
                    },
                    onGroupInfoClick = { groupChatId ->
                        if (canNav()) navController.navigate(Routes.groupInfo(groupChatId))
                    }
                )
            }

            composable(
                route = Routes.GROUP_INFO,
                arguments = listOf(navArgument("chatId") { type = NavType.StringType })
            ) { backStack ->
                val chatId = backStack.arguments?.getString("chatId") ?: ""
                GroupInfoScreen(
                    chatId = chatId,
                    onBack = { if (canNav()) navController.navigateUp() },
                    onMemberClick = { userId ->
                        if (canNav()) navController.navigate(Routes.userProfile(userId))
                    },
                    onChatDeleted = {
                        navController.navigate(Routes.CHAT_LIST) {
                            popUpTo(Routes.CHAT_LIST) { inclusive = false }
                        }
                    }
                )
            }

            composable(
                route = Routes.USER_PROFILE,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStack ->
                val userId = backStack.arguments?.getString("userId") ?: ""
                UserProfileScreen(
                    userId = userId,
                    onBack = { if (canNav()) navController.navigateUp() },
                    onOpenChat = { chatId ->
                        if (canNav()) navController.navigate(Routes.chat(chatId)) {
                            popUpTo(Routes.CHAT_LIST) { inclusive = false }
                        }
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
                    onDraftChat = { userId ->
                        if (canNav()) navController.navigate(Routes.chatNew(userId)) {
                            popUpTo(Routes.USER_SEARCH) { inclusive = true }
                        }
                    },
                    onCreateGroup = {
                        if (canNav()) navController.navigate(Routes.CREATE_GROUP)
                    },
                    onBack = { if (canNav()) navController.navigateUp() }
                )
            }

            composable(
                route = Routes.CHAT_NEW,
                arguments = listOf(navArgument("userId") { type = NavType.StringType })
            ) { backStack ->
                val userId = backStack.arguments?.getString("userId") ?: ""
                ChatScreen(
                    chatId = "",
                    draftUserId = userId,
                    autoPlayVideos = autoPlayVideos,
                    onBack = { if (canNav()) navController.navigateUp() },
                    onForwardTo = { targetChatId ->
                        if (canNav()) navController.navigate(Routes.chat(targetChatId))
                    },
                    onUserClick = { uid ->
                        if (canNav()) navController.navigate(Routes.userProfile(uid))
                    },
                    onGroupInfoClick = { groupChatId ->
                        if (canNav()) navController.navigate(Routes.groupInfo(groupChatId))
                    }
                )
            }

            composable(Routes.CREATE_GROUP) {
                CreateGroupScreen(
                    onGroupCreated = { chatId ->
                        if (canNav()) navController.navigate(Routes.chat(chatId)) {
                            popUpTo(Routes.USER_SEARCH) { inclusive = true }
                        }
                    },
                    onBack = { if (canNav()) navController.navigateUp() }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onNavigateToChats = {
                        if (canNav()) navController.popBackStack(Routes.CHAT_LIST, inclusive = false)
                    },
                    onNavigateToSettings = {
                        if (canNav()) navController.navigate(Routes.SETTINGS) {
                            popUpTo(Routes.CHAT_LIST) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onLogout = {
                        if (canNav()) navController.navigate(Routes.LOGIN) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            composable(Routes.SETTINGS) {
                SettingsScreen(
                    autoPlayVideos = autoPlayVideos,
                    onAutoPlayChanged = onAutoPlayChanged,
                    onNavigateToChats = {
                        if (canNav()) navController.popBackStack(Routes.CHAT_LIST, inclusive = false)
                    },
                    onNavigateToProfile = {
                        if (canNav()) navController.navigate(Routes.PROFILE) {
                            popUpTo(Routes.CHAT_LIST) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onWallpaperClick = {
                        if (canNav()) navController.navigate(Routes.WALLPAPER_PICKER)
                    },
                    onAppearanceClick = {
                        if (canNav()) navController.navigate(Routes.APPEARANCE)
                    }
                )
            }

            composable(Routes.WALLPAPER_PICKER) {
                WallpaperPickerScreen(
                    onBack = { if (canNav()) navController.navigateUp() }
                )
            }

            composable(Routes.APPEARANCE) {
                AppearanceScreen(
                    currentThemeMode = currentThemeMode,
                    onThemeChanged = onThemeChanged,
                    currentAccent = currentAccent,
                    onAccentChanged = onAccentChanged,
                    onBack = { if (canNav()) navController.navigateUp() }
                )
            }
        }
    }
}
