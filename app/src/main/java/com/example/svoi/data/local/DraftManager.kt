package com.example.svoi.data.local

import android.content.Context
import androidx.core.content.edit

/**
 * Persists unsent message drafts per chat.
 * Stored in plain SharedPreferences under key "draft_{chatId}".
 */
class DraftManager(context: Context) {
    private val prefs = context.getSharedPreferences("svoi_drafts", Context.MODE_PRIVATE)

    fun getDraft(chatId: String): String =
        prefs.getString("draft_$chatId", "") ?: ""

    fun saveDraft(chatId: String, text: String) {
        if (text.isBlank()) {
            prefs.edit { remove("draft_$chatId") }
        } else {
            prefs.edit { putString("draft_$chatId", text) }
        }
    }

    fun clearDraft(chatId: String) {
        prefs.edit { remove("draft_$chatId") }
    }

    fun hasDraft(chatId: String): Boolean =
        getDraft(chatId).isNotBlank()

    /** Returns map of chatId → draft text for all chats that have a saved draft. */
    fun getAllDrafts(): Map<String, String> =
        prefs.all
            .filter { (key, _) -> key.startsWith("draft_") }
            .mapKeys { (key, _) -> key.removePrefix("draft_") }
            .mapValues { (_, v) -> v as? String ?: "" }
            .filter { (_, text) -> text.isNotBlank() }
}
