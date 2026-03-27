package com.example.svoi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import androidx.core.graphics.drawable.IconCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SvoiFirebaseMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "svoi_messages"
        const val GROUP_KEY = "com.example.svoi.MESSAGES"
        const val SUMMARY_ID = 0
        private const val PRIMARY_COLOR = 0xFF1E88E5.toInt()
        private const val MAX_MESSAGES = 3  // messages shown per chat notification

        /** Stable notification ID for a given chatId — same chat always uses the same ID */
        fun notificationIdForChat(chatId: String): Int = chatId.hashCode().let {
            // Avoid SUMMARY_ID collision
            if (it == SUMMARY_ID) Int.MIN_VALUE else it
        }
    }

    override fun onNewToken(token: String) {
        val app = applicationContext as SvoiApp
        if (app.authRepository.isLoggedIn()) {
            val userId = app.authRepository.currentUserId() ?: return
            CoroutineScope(Dispatchers.IO).launch {
                app.pushTokenRepository.saveToken(userId, token)
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: return
        val body = data["body"] ?: return
        val chatId = data["chat_id"]

        // Не показываем уведомление если пользователь сейчас в этом чате
        if (chatId != null && chatId == ActiveChatTracker.activeChatId) return

        val app = applicationContext as SvoiApp
        // Глобальное отключение уведомлений
        if (app.themeManager.isNotificationsMuted()) return
        // Отключение уведомлений для конкретного чата
        if (chatId != null && app.themeManager.isChatMuted(chatId)) return

        val isGroup = data["is_group"] == "true"
        // sender_name is always the actual sender; title is group name (group) or sender name (personal)
        val senderName = data["sender_name"] ?: title
        val avatarEmoji = data["avatar_emoji"] ?: "😊"
        val avatarColor = data["avatar_color"] ?: "#5C6BC0"

        val avatarBitmap = createAvatarBitmap(avatarColor, avatarEmoji)

        showNotification(
            conversationTitle = if (isGroup) title else null,
            senderName = senderName,
            body = body,
            chatId = chatId,
            avatarBitmap = avatarBitmap
        )
    }

    private fun showNotification(
        conversationTitle: String?,
        senderName: String,
        body: String,
        chatId: String?,
        avatarBitmap: Bitmap
    ) {
        val notifManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Сообщения", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
            }
            notifManager.createNotificationChannel(channel)
        }

        val notifId = if (chatId != null) notificationIdForChat(chatId) else System.currentTimeMillis().toInt()

        // ── Build sender Person with avatar ──────────────────────────────────────────
        val person = Person.Builder()
            .setName(senderName)
            .setIcon(IconCompat.createWithBitmap(avatarBitmap))
            .build()

        // ── Restore existing messages from active notification (stacking) ────────────
        val existingMessages = notifManager.activeNotifications
            .firstOrNull { it.id == notifId }
            ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification) }
            ?.messages
            ?.takeLast(MAX_MESSAGES - 1)   // keep room for the new message
            ?: emptyList()

        // ── Assemble MessagingStyle with accumulated messages ─────────────────────────
        val style = NotificationCompat.MessagingStyle(person)
        conversationTitle?.let { style.conversationTitle = it }
        existingMessages.forEach { style.addMessage(it) }
        style.addMessage(body, System.currentTimeMillis(), person)

        // ── Open chat on tap ─────────────────────────────────────────────────────────
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chat_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Post the notification ────────────────────────────────────────────────────
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY_COLOR)
            .setStyle(style)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .build()

        notifManager.notify(notifId, notification)

        // Group summary — required for notification stacking on Android
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY_COLOR)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notifManager.notify(SUMMARY_ID, summary)
    }

    /** Draws a 128×128 circular avatar bitmap with the sender's emoji on a colored background. */
    private fun createAvatarBitmap(bgColorHex: String, emoji: String): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = try {
            Color.parseColor(bgColorHex)
        } catch (e: Exception) {
            Color.parseColor("#5C6BC0")
        }

        canvas.drawCircle(
            size / 2f, size / 2f, size / 2f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bgColor }
        )

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = size * 0.52f
            textAlign = Paint.Align.CENTER
        }
        val bounds = Rect()
        textPaint.getTextBounds(emoji, 0, emoji.length, bounds)
        canvas.drawText(emoji, size / 2f, size / 2f - bounds.exactCenterY(), textPaint)

        return bitmap
    }
}
