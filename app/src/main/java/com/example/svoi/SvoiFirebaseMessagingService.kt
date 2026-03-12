package com.example.svoi

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SvoiFirebaseMessagingService : FirebaseMessagingService() {

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
        // Called when app is in foreground — FCM auto-shows notification in background/killed state
        val notification = message.notification ?: return
        showNotification(
            title = notification.title ?: return,
            body = notification.body ?: return,
            chatId = message.data["chat_id"]
        )
    }

    private fun showNotification(title: String, body: String, chatId: String?) {
        val channelId = "svoi_messages"
        val notificationManager = getSystemService(NotificationManager::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId, "Сообщения", NotificationManager.IMPORTANCE_HIGH
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
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }
}
