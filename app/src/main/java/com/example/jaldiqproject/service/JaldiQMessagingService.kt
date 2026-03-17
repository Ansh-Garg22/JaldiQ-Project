package com.example.jaldiqproject.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.jaldiqproject.MainActivity
import com.example.jaldiqproject.R
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

/**
 * FCM Messaging Service for JaldiQ.
 *
 * Handles:
 * - onNewToken: stores the FCM device token in Firebase for push targeting
 * - onMessageReceived: displays rich notifications for queue status updates
 */
class JaldiQMessagingService : FirebaseMessagingService() {

    companion object {
        const val CHANNEL_ID = "jaldiq_queue_alerts"
        const val CHANNEL_NAME = "Queue Alerts"
        const val NOTIFICATION_ID_TURN = 1001
        const val NOTIFICATION_ID_ALMOST = 1002
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        android.util.Log.d("JaldiQ-FCM", "New FCM token: $token")

        // Store locally for the ViewModel to pick up
        val prefs = getSharedPreferences("jaldiq_prefs", MODE_PRIVATE)
        prefs.edit().putString("fcm_token", token).apply()

        // Also push directly to Firebase if the user is authenticated
        val userId = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().reference
                .child("users").child(userId).child("fcmToken")
                .setValue(token)
                .addOnSuccessListener {
                    android.util.Log.d("JaldiQ-FCM", "FCM token updated in Firebase for user $userId")
                }
                .addOnFailureListener { e ->
                    android.util.Log.e("JaldiQ-FCM", "Failed to update FCM token in Firebase", e)
                }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)

        val title = message.data["title"] ?: message.notification?.title ?: "JaldiQ"
        val body = message.data["body"] ?: message.notification?.body ?: ""
        val type = message.data["type"] ?: "general"

        showNotification(title, body, type)
    }

    private fun showNotification(title: String, body: String, type: String) {
        val notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel (required for Android 8+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for your queue position updates"
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(channel)
        }

        // Deep link intent to open the app
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("notification_type", type)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationId = when (type) {
            "your_turn" -> NOTIFICATION_ID_TURN
            "almost_turn" -> NOTIFICATION_ID_ALMOST
            else -> System.currentTimeMillis().toInt()
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 300, 100, 300))
            .build()

        notificationManager.notify(notificationId, notification)
    }
}
