package com.example.meritrankerstudent.util.notification

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.meritrankerstudent.MainActivity
import com.example.meritrankerstudent.R
import com.example.meritrankerstudent.observability.AppObservability
import com.example.meritrankerstudent.observability.TelemetryEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Production-grade Local Notification Manager for MeritRanker Smart Tutor & Practice.
 *
 * Enforces:
 * 1. Durable cross-process deduplication (SharedPreferences + In-Memory Fast Cache).
 * 2. Strict permission & channel verification on Android 13+ (POST_NOTIFICATIONS).
 * 3. Exact metadata synchronization between cards and notification headlines.
 * 4. Distinct channels and deep links for Practice Ready and Normal Doubt Answer Ready.
 * 5. Privacy compliance: Zero PII / private question text in notifications.
 */
class PracticeNotificationManager(private val context: Context) {

    companion object {
        private const val TAG = "PracticeNotifManager"
        const val CHANNEL_ID = "practice_generation_channel"
        const val EXTRA_NAV_TARGET = "EXTRA_NAV_TARGET"
        const val EXTRA_TEST_ID = "EXTRA_TEST_ID"
        const val EXTRA_CONVERSATION_ID = "EXTRA_CONVERSATION_ID"
        const val EXTRA_PRACTICE_MODE = "EXTRA_PRACTICE_MODE"
        const val NAV_TARGET_QUESTION_PLAYER = "NAV_TARGET_QUESTION_PLAYER"
        const val NAV_TARGET_SMART_TUTOR = "NAV_TARGET_SMART_TUTOR"

        private const val PREFS_NAME = "meritranker_delivered_notifications"
        private const val KEY_DELIVERED_SET = "delivered_keys"

        @Volatile
        private var instance: PracticeNotificationManager? = null

        fun getInstance(context: Context): PracticeNotificationManager {
            return instance ?: synchronized(this) {
                instance ?: PracticeNotificationManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // In-memory cache synced with durable SharedPreferences
    private val deliveredKeys = ConcurrentHashMap.newKeySet<String>()

    init {
        createNotificationChannel()
        loadDeliveredKeys()
    }

    private fun loadDeliveredKeys() {
        try {
            val saved = prefs.getStringSet(KEY_DELIVERED_SET, emptySet()) ?: emptySet()
            deliveredKeys.addAll(saved)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load delivered keys: ${e.message}")
        }
    }

    private fun persistDeliveredKey(key: String) {
        deliveredKeys.add(key)
        try {
            val current = prefs.getStringSet(KEY_DELIVERED_SET, emptySet())?.toMutableSet() ?: mutableSetOf()
            current.add(key)
            prefs.edit().putStringSet(KEY_DELIVERED_SET, current).apply()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to persist delivered key $key: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Smart Tutor & Practice Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifications when your AI-generated answers, practice questions, and mock tests are ready"
                enableLights(true)
                enableVibration(true)
                setShowBadge(true)
            }
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            manager?.createNotificationChannel(channel)
        }
    }

    fun canPostNotifications(): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            return false
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true
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
        val dedupeKey = "practice_$testId"
        if (testId.isBlank()) return

        AppObservability.analytics.logEvent(
            TelemetryEvent.PracticeReadyNotificationAttempt(activityType = activityType ?: "QUIZ")
        )

        if (deliveredKeys.contains(dedupeKey)) {
            Log.d(TAG, "PRACTICE_NOTIFICATION_SUPPRESSED reason=ALREADY_DELIVERED testId=$testId")
            AppObservability.analytics.logEvent(
                TelemetryEvent.PracticeReadyNotificationSuppressed("ALREADY_DELIVERED")
            )
            return
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "PRACTICE_NOTIFICATION_SUPPRESSED reason=NOTIFICATIONS_DISABLED testId=$testId")
            AppObservability.analytics.logEvent(
                TelemetryEvent.PracticeReadyNotificationSuppressed("NOTIFICATIONS_DISABLED")
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "PRACTICE_NOTIFICATION_SUPPRESSED reason=PERMISSION_DENIED testId=$testId")
                AppObservability.analytics.logEvent(
                    TelemetryEvent.PracticeReadyNotificationSuppressed("PERMISSION_DENIED")
                )
                return
            }
        }

        val isMock = activityType.equals("MOCK", ignoreCase = true) || activityType.equals("MINI_MOCK", ignoreCase = true)
        val notifTitle = if (isMock) "Mock test ready" else "Practice ready"

        // Authoritative display title resolution
        val resolvedName = when {
            !title.isNullOrBlank() && !title.equals("null", ignoreCase = true) && !title.equals("Unknown", ignoreCase = true) -> title
            !topic.isNullOrBlank() && !topic.equals("null", ignoreCase = true) -> topic
            !subject.isNullOrBlank() && !subject.equals("null", ignoreCase = true) -> subject
            else -> if (isMock) "Mock test" else "Practice test"
        }

        val body = if (questionCount > 0) {
            "$resolvedName · $questionCount questions"
        } else {
            "$resolvedName is ready to start"
        }

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
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notifTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify("practice", testId.hashCode(), notification)
            persistDeliveredKey(dedupeKey)
            Log.d(TAG, "NOTIFICATION_POSTED tag=practice testId=$testId title='$notifTitle' body='$body'")

            AppObservability.analytics.logEvent(
                TelemetryEvent.PracticeReadyNotificationShown(
                    activityType = (if (isMock) "MOCK" else "QUIZ"),
                    questionCountBucket = if (questionCount <= 5) "1_5" else if (questionCount <= 10) "6_10" else "gt_10"
                )
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while posting notification: ${e.message}")
            AppObservability.analytics.logEvent(
                TelemetryEvent.PracticeReadyNotificationSuppressed("PERMISSION_DENIED")
            )
        }
    }

    @android.annotation.SuppressLint("MissingPermission")
    fun notifyDoubtAnswerReady(
        conversationId: String,
        turnId: String
    ) {
        val dedupeKey = "doubt_${conversationId}_$turnId"
        if (conversationId.isBlank() || turnId.isBlank()) return

        AppObservability.analytics.logEvent(TelemetryEvent.DoubtReadyNotificationAttempt)

        if (deliveredKeys.contains(dedupeKey)) {
            Log.d(TAG, "DOUBT_NOTIFICATION_SUPPRESSED reason=ALREADY_DELIVERED convId=$conversationId turnId=$turnId")
            AppObservability.analytics.logEvent(
                TelemetryEvent.DoubtReadyNotificationSuppressed("ALREADY_DELIVERED")
            )
            return
        }

        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            Log.w(TAG, "DOUBT_NOTIFICATION_SUPPRESSED reason=NOTIFICATIONS_DISABLED convId=$conversationId")
            AppObservability.analytics.logEvent(
                TelemetryEvent.DoubtReadyNotificationSuppressed("NOTIFICATIONS_DISABLED")
            )
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                Log.w(TAG, "DOUBT_NOTIFICATION_SUPPRESSED reason=PERMISSION_DENIED convId=$conversationId")
                AppObservability.analytics.logEvent(
                    TelemetryEvent.DoubtReadyNotificationSuppressed("PERMISSION_DENIED")
                )
                return
            }
        }

        val notifTitle = "Smart Tutor answer ready"
        val body = "Your answer is ready to view."

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_NAV_TARGET, NAV_TARGET_SMART_TUTOR)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
        }

        val notifId = (conversationId + turnId).hashCode()
        val pendingIntent = PendingIntent.getActivity(
            context,
            notifId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(notifTitle)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify("doubt", notifId, notification)
            persistDeliveredKey(dedupeKey)
            Log.d(TAG, "NOTIFICATION_POSTED tag=doubt convId=$conversationId turnId=$turnId title='$notifTitle'")

            AppObservability.analytics.logEvent(TelemetryEvent.DoubtReadyNotificationShown)
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException while posting doubt notification: ${e.message}")
            AppObservability.analytics.logEvent(
                TelemetryEvent.DoubtReadyNotificationSuppressed("PERMISSION_DENIED")
            )
        }
    }

    fun markNotificationDelivered(key: String) {
        persistDeliveredKey(key)
    }

    fun clearNotifications() {
        deliveredKeys.clear()
        try {
            prefs.edit().remove(KEY_DELIVERED_SET).apply()
        } catch (_: Exception) {}
        NotificationManagerCompat.from(context).cancelAll()
    }
}
