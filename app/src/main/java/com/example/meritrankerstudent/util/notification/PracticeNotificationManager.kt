package com.example.meritrankerstudent.util.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.meritrankerstudent.MainActivity
import com.example.meritrankerstudent.R
import java.util.concurrent.ConcurrentHashMap

class PracticeNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_ID = "practice_generation_channel"
        const val EXTRA_NAV_TARGET = "EXTRA_NAV_TARGET"
        const val EXTRA_TEST_ID = "EXTRA_TEST_ID"
        const val EXTRA_PRACTICE_MODE = "EXTRA_PRACTICE_MODE"
        const val NAV_TARGET_QUESTION_PLAYER = "NAV_TARGET_QUESTION_PLAYER"

        @Volatile
        private var instance: PracticeNotificationManager? = null

        fun getInstance(context: Context): PracticeNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: PracticeNotificationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val deliveredNotifications = ConcurrentHashMap.newKeySet<String>()

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Practice & Test Generation",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when your AI-generated practice questions and mock tests are ready"
                enableLights(true)
                enableVibration(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun canPostNotifications(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun notifyPracticeReady(
        testId: String,
        title: String?,
        activityType: String?,
        questionCount: Int,
        subject: String? = null,
        topic: String? = null
    ) {
        if (deliveredNotifications.contains(testId)) {
            return
        }

        if (!canPostNotifications()) {
            return
        }

        val isMock = activityType.equals("MOCK", ignoreCase = true) || activityType.equals("MINI_MOCK", ignoreCase = true)
        val notifTitle = if (isMock) "Mock Test Ready" else "Practice Ready"
        val countText = if (questionCount > 0) "$questionCount-question " else ""
        val contextText = topic ?: subject ?: title ?: "practice"
        val body = "Your ${countText}${if (isMock) "mock test" else "practice"} on $contextText is ready to start."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TARGET, NAV_TARGET_QUESTION_PLAYER)
            putExtra(EXTRA_TEST_ID, testId)
            putExtra(EXTRA_PRACTICE_MODE, if (isMock) "MOCK" else "QUIZ")
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            testId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(notifTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(testId.hashCode(), notification)
            deliveredNotifications.add(testId)
        } catch (_: SecurityException) {
            // Permission revoked race
        }
    }

    fun markNotificationDelivered(testId: String) {
        deliveredNotifications.add(testId)
    }

    fun clearNotifications() {
        deliveredNotifications.clear()
        NotificationManagerCompat.from(context).cancelAll()
    }
}
