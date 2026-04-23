package com.example.svoi.data.repository

import android.util.Log
import com.example.svoi.data.SupabaseReachabilityChecker
import com.example.svoi.data.model.ChatMember
import com.example.svoi.data.model.Profile
import com.example.svoi.data.model.UserPresence
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.decodeRecord
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class PresenceUpdate(
    @SerialName("user_id") val userId: String,
    val online: Boolean,
    @SerialName("last_seen") val lastSeen: String? = null
)

class UserRepository(
    private val supabase: SupabaseClient,
    private val checker: SupabaseReachabilityChecker
) {

    suspend fun getProfile(userId: String): Profile? {
        return try {
            supabase.from("profiles")
                .select { filter { eq("id", userId) } }
                .decodeSingleOrNull<Profile>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getCurrentProfile(): Profile? {
        val userId = supabase.auth.currentUserOrNull()?.id ?: return null
        return getProfile(userId)
    }

    /**
     * Searches users by display name. Excludes profiles where hidden_from_search = true.
     * For an additional server-side enforcement layer, the `search_users` SQL function
     * (SECURITY DEFINER) can be used via direct DB calls — see supabase/30_hidden_from_search.sql.
     */
    suspend fun searchUsers(query: String): List<Profile> {
        return try {
            supabase.from("profiles")
                .select {
                    filter {
                        ilike("display_name", "%$query%")
                        eq("hidden_from_search", false)
                    }
                    limit(20)
                }
                .decodeList<Profile>()
                .filter { it.id != supabase.auth.currentUserOrNull()?.id }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Returns all users the current user shares at least one active chat with.
     * Ignores hidden_from_search — if you've already chatted with someone, you can add them to a group.
     */
    suspend fun getMyContacts(): List<Profile> {
        return try {
            val myId = supabase.auth.currentUserOrNull()?.id ?: return emptyList()

            // Step 1 — chat IDs where I'm an active member
            val myChatIds = supabase.from("chat_members")
                .select { filter { eq("user_id", myId) } }
                .decodeList<ChatMember>()
                .filter { it.leftAt == null }
                .map { it.chatId }

            if (myChatIds.isEmpty()) return emptyList()

            // Step 2 — all user IDs sharing those chats (excluding me)
            val contactIds = supabase.from("chat_members")
                .select { filter { isIn("chat_id", myChatIds); neq("user_id", myId) } }
                .decodeList<ChatMember>()
                .map { it.userId }
                .distinct()

            if (contactIds.isEmpty()) return emptyList()

            // Step 3 — load profiles sorted by name (hidden_from_search intentionally NOT filtered)
            supabase.from("profiles")
                .select { filter { isIn("id", contactIds) } }
                .decodeList<Profile>()
                .sortedBy { it.displayName }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateHiddenFromSearch(hidden: Boolean): String? {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return "Не авторизован"
            supabase.from("profiles").update({
                set("hidden_from_search", hidden)
            }) {
                filter { eq("id", userId) }
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    suspend fun updateProfile(
        displayName: String,
        statusText: String,
        emoji: String,
        bgColor: String
    ): String? {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return "Не авторизован"
            supabase.from("profiles").update({
                set("display_name", displayName)
                set("status_text", statusText)
                set("emoji", emoji)
                set("bg_color", bgColor)
            }) {
                filter { eq("id", userId) }
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    suspend fun updateNameAndAbout(displayName: String, statusText: String): String? {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return "Не авторизован"
            supabase.from("profiles").update({
                set("display_name", displayName)
                set("status_text", statusText)
            }) {
                filter { eq("id", userId) }
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    suspend fun updateAvatar(emoji: String, bgColor: String): String? {
        return try {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return "Не авторизован"
            supabase.from("profiles").update({
                set("emoji", emoji)
                set("bg_color", bgColor)
            }) {
                filter { eq("id", userId) }
            }
            null
        } catch (e: Exception) {
            e.message
        }
    }

    suspend fun getProfiles(userIds: List<String>): List<Profile> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            supabase.from("profiles")
                .select { filter { isIn("id", userIds) } }
                .decodeList<Profile>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getPresences(userIds: List<String>): List<UserPresence> {
        if (userIds.isEmpty()) return emptyList()
        return try {
            supabase.from("user_presence_view")
                .select { filter { isIn("user_id", userIds) } }
                .decodeList<UserPresence>()
        } catch (e: Exception) { emptyList() }
    }

    suspend fun setOnline(online: Boolean) {
        if (!checker.isReachable.value) {
            Log.d("Presence", "setOnline($online): skipped — Supabase unreachable")
            return
        }
        val userId = supabase.auth.currentUserOrNull()?.id
        if (userId == null) {
            Log.w("Presence", "setOnline($online): no authenticated user")
            return
        }
        Log.d("Presence", "setOnline($online) userId=$userId")
        try {
            // Always update lastSeen — acts as heartbeat.
            // If app crashes, isTrulyOnline() will return false after 60s of no updates.
            val data = PresenceUpdate(
                userId = userId,
                online = online,
                lastSeen = java.time.Instant.now().toString()
            )
            supabase.from("user_presence").upsert(data)
            if (online) checker.markReachable()
            Log.d("Presence", "setOnline($online) SUCCESS")
        } catch (e: HttpRequestTimeoutException) {
            Log.e("Presence", "setOnline($online) TIMEOUT — notifying checker")
            checker.notifyTimeout()
        } catch (e: Exception) {
            Log.e("Presence", "setOnline($online) FAILED: ${e.message}", e)
        }
    }

    suspend fun getPresence(userId: String): UserPresence? {
        return try {
            val result = supabase.from("user_presence_view")
                .select { filter { eq("user_id", userId) } }
                .decodeSingleOrNull<UserPresence>()
            Log.d("Presence", "getPresence($userId) = $result")
            result
        } catch (e: Exception) {
            Log.e("Presence", "getPresence($userId) FAILED: ${e.message}")
            null
        }
    }

    /** Realtime flow — fires whenever any user_presence row is updated */
    fun presenceUpdateFlowAll(): Flow<UserPresence> = channelFlow {
        val channel = supabase.channel("user-presence-updates-all-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "user_presence"
        }.onEach { trySend(it.decodeRecord()) }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }

    /** Realtime flow — fires when a specific user's presence changes.
     *  After each event, fetches from user_presence_view to get server-computed is_truly_online. */
    fun presenceUpdateFlow(userId: String): Flow<UserPresence> = channelFlow {
        val channel = supabase.channel("user-presence-$userId-${java.util.UUID.randomUUID()}")
        channel.postgresChangeFlow<PostgresAction.Update>(schema = "public") {
            table = "user_presence"
            filter("user_id", FilterOperator.EQ, userId)
        }.onEach {
            // Fetch from view so is_truly_online is computed with server NOW()
            val fresh = runCatching {
                supabase.from("user_presence_view")
                    .select { filter { eq("user_id", userId) } }
                    .decodeSingleOrNull<UserPresence>()
            }.getOrNull()
            trySend(fresh ?: it.decodeRecord())
        }.launchIn(this)
        channel.subscribe()
        awaitClose {
            CoroutineScope(Dispatchers.IO).launch {
                runCatching { channel.unsubscribe() }
                runCatching { supabase.realtime.removeChannel(channel) }
            }
        }
    }
}
