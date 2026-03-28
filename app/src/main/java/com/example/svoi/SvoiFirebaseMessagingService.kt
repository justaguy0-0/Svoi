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
import android.util.Log
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
        private const val TAG = "FCM"
        const val CHANNEL_ID = "svoi_messages"
        const val GROUP_KEY = "com.example.svoi.MESSAGES"
        const val SUMMARY_ID = 0
        private const val PRIMARY_COLOR = 0xFF1E88E5.toInt()
        private const val MAX_MESSAGES = 3

        fun notificationIdForChat(chatId: String): Int = chatId.hashCode().let {
            if (it == SUMMARY_ID) Int.MIN_VALUE else it
        }
    }

    override fun onNewToken(token: String) {
        Log.d(TAG, "onNewToken: new FCM token received")
        val app = applicationContext as SvoiApp
        if (app.authRepository.isLoggedIn()) {
            val userId = app.authRepository.currentUserId() ?: return
            CoroutineScope(Dispatchers.IO).launch {
                app.pushTokenRepository.saveToken(userId, token)
                Log.d(TAG, "onNewToken: token saved for userId=$userId")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        Log.d(TAG, "onMessageReceived: data=${message.data}")
        val data = message.data
        val title = data["title"] ?: run { Log.w(TAG, "no title, skip"); return }
        val body = data["body"] ?: run { Log.w(TAG, "no body, skip"); return }
        val chatId = data["chat_id"]

        if (chatId != null && chatId == ActiveChatTracker.activeChatId) {
            Log.d(TAG, "suppressed: user is in this chat")
            return
        }

        val isMention = data["is_mention"] == "true"
        val app = applicationContext as SvoiApp

        // Mentions bypass mute settings — always notify when someone calls you out
        if (!isMention) {
            if (app.themeManager.isNotificationsMuted()) {
                Log.d(TAG, "suppressed: global mute")
                return
            }
            if (chatId != null && app.themeManager.isChatMuted(chatId)) {
                Log.d(TAG, "suppressed: chat muted chatId=$chatId")
                return
            }
        }

        val isGroup = data["is_group"] == "true"
        val senderName = data["sender_name"] ?: title
        val avatarEmoji = data["avatar_emoji"] ?: "😊"
        val avatarColor = data["avatar_color"] ?: "#5C6BC0"

        try {
            val avatarBitmap = createAvatarBitmap(avatarColor, avatarEmoji)
            showNotification(
                conversationTitle = if (isGroup) title else null,
                senderName = senderName,
                body = body,
                chatId = chatId,
                avatarBitmap = avatarBitmap
            )
        } catch (e: Exception) {
            Log.e(TAG, "showNotification failed, falling back to simple notification", e)
            showSimpleNotification(title, body, chatId)
        }
    }

    private fun showNotification(
        conversationTitle: String?,
        senderName: String,
        body: String,
        chatId: String?,
        avatarBitmap: Bitmap
    ) {
        val notifManager = getSystemService(NotificationManager::class.java)
        ensureChannel(notifManager)

        val notifId = if (chatId != null) notificationIdForChat(chatId) else System.currentTimeMillis().toInt()

        val person = Person.Builder()
            .setName(senderName)
            .setIcon(IconCompat.createWithBitmap(avatarBitmap))
            .build()

        // Restore messages from existing notification for this chat (stacking)
        val existingMessages = try {
            notifManager.activeNotifications
                .firstOrNull { it.id == notifId }
                ?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it.notification) }
                ?.messages
                ?.takeLast(MAX_MESSAGES - 1)
                ?: emptyList()
        } catch (e: Exception) {
            Log.w(TAG, "could not read existing messages", e)
            emptyList()
        }

        val style = NotificationCompat.MessagingStyle(person)
        conversationTitle?.let { style.conversationTitle = it }
        existingMessages.forEach { style.addMessage(it) }
        style.addMessage(body, System.currentTimeMillis(), person)

        val pendingIntent = buildPendingIntent(chatId, notifId)

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
        postGroupSummary(notifManager)
        Log.d(TAG, "notification posted: id=$notifId chatId=$chatId")
    }

    /** Fallback plain notification used when MessagingStyle building throws. */
    private fun showSimpleNotification(title: String, body: String, chatId: String?) {
        val notifManager = getSystemService(NotificationManager::class.java)
        ensureChannel(notifManager)
        val notifId = if (chatId != null) notificationIdForChat(chatId) else System.currentTimeMillis().toInt()
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY_COLOR)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(buildPendingIntent(chatId, notifId))
            .setGroup(GROUP_KEY)
            .build()
        notifManager.notify(notifId, notification)
        postGroupSummary(notifManager)
        Log.d(TAG, "simple fallback notification posted: id=$notifId")
    }

    private fun ensureChannel(notifManager: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Сообщения", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
            }
            notifManager.createNotificationChannel(channel)
        }
    }

    private fun buildPendingIntent(chatId: String?, notifId: Int): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chat_id", it) }
        }
        return PendingIntent.getActivity(
            this, notifId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun postGroupSummary(notifManager: NotificationManager) {
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY_COLOR)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        notifManager.notify(SUMMARY_ID, summary)
    }

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
