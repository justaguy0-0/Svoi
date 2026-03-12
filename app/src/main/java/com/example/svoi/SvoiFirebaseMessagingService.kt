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
        private const val CHANNEL_ID = "svoi_messages"
        private const val GROUP_KEY = "com.example.svoi.MESSAGES"
        private const val SUMMARY_ID = 0
        private const val PRIMARY_COLOR = 0xFF1E88E5.toInt()
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
        val isGroup = data["is_group"] == "true"
        val avatarEmoji = data["avatar_emoji"] ?: "😊"
        val avatarColor = data["avatar_color"] ?: "#5C6BC0"
        val avatarLetter = data["avatar_letter"] ?: ""

        val avatarText = if (isGroup) avatarLetter else avatarEmoji
        val avatarBitmap = createAvatarBitmap(avatarColor, avatarText, isEmoji = !isGroup)

        showNotification(title = title, body = body, chatId = chatId, senderName = title, avatarBitmap = avatarBitmap)
    }

    private fun showNotification(title: String, body: String, chatId: String?, senderName: String, avatarBitmap: Bitmap) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Сообщения", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о новых сообщениях"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            chatId?.let { putExtra("chat_id", it) }
        }
        val pendingIntent = PendingIntent.getActivity(
            this, System.currentTimeMillis().toInt(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val sender = Person.Builder()
            .setName(senderName)
            .setIcon(IconCompat.createWithBitmap(avatarBitmap))
            .build()

        val messagingStyle = NotificationCompat.MessagingStyle(sender)
            .addMessage(body, System.currentTimeMillis(), sender)

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY_COLOR)
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setGroup(GROUP_KEY)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)

        // Group summary — fixes black square when notifications are stacked
        val summary = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(PRIMARY_COLOR)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(SUMMARY_ID, summary)
    }

    private fun createAvatarBitmap(bgColorHex: String, text: String, isEmoji: Boolean): Bitmap {
        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgColor = try {
            Color.parseColor(bgColorHex)
        } catch (e: Exception) {
            Color.parseColor("#5C6BC0")
        }

        val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = bgColor
        }
        canvas.drawCircle(size / 2f, size / 2f, size / 2f, circlePaint)

        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = if (isEmoji) size * 0.52f else size * 0.46f
            textAlign = Paint.Align.CENTER
        }

        val bounds = Rect()
        textPaint.getTextBounds(text, 0, text.length, bounds)
        val y = size / 2f - bounds.exactCenterY()
        canvas.drawText(text, size / 2f, y, textPaint)

        return bitmap
    }
}
