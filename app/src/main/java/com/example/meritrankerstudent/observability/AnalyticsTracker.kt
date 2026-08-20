package com.example.meritrankerstudent.observability

import android.content.Context
import android.util.Log
import com.example.meritrankerstudent.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics

interface AnalyticsTracker {
    fun logEvent(event: TelemetryEvent)
    fun setScreen(screen: CanonicalScreen)
    fun setUserProperty(name: String, value: String?)
    fun setCollectionEnabled(enabled: Boolean)
    fun resetData()
}

class FirebaseAnalyticsTracker(
    context: Context,
    private val isDebug: Boolean = BuildConfig.DEBUG
) : AnalyticsTracker {

    private var runtimeCollectionOverride: Boolean? = null

    private val firebaseAnalytics: FirebaseAnalytics? by lazy {
        try {
            if (FirebaseApp.getApps(context).isNotEmpty()) {
                val analytics = FirebaseAnalytics.getInstance(context)
                // Default: In debug builds, normal collection is disabled unless explicitly enabled for validation
                val shouldEnable = runtimeCollectionOverride ?: (!isDebug || BuildConfig.ENABLE_DEBUG_TELEMETRY)
                analytics.setAnalyticsCollectionEnabled(shouldEnable)
                analytics
            } else {
                Log.w(TAG, "Firebase is not initialized; Analytics disabled.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseAnalytics", e)
            null
        }
    }

    override fun logEvent(event: TelemetryEvent) {
        try {
            val bundle = event.toBundle()
            firebaseAnalytics?.logEvent(event.eventName, bundle)
            if (isDebug) {
                Log.d(TAG, "Analytics Event: ${event.eventName}, params: ${event.params}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log event: ${event.eventName}", e)
        }
    }

    override fun setScreen(screen: CanonicalScreen) {
        logEvent(TelemetryEvent.ScreenView(screen))
    }

    override fun setUserProperty(name: String, value: String?) {
        try {
            if (!PiiSanitizer.isKeySafe(name)) {
                Log.w(TAG, "Attempted to set unsafe user property: $name")
                return
            }
            val sanitizedValue = value?.let { PiiSanitizer.sanitizeValue(name, it)?.toString() }
            firebaseAnalytics?.setUserProperty(name, sanitizedValue)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set user property: $name", e)
        }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        runtimeCollectionOverride = enabled
        try {
            firebaseAnalytics?.setAnalyticsCollectionEnabled(enabled)
            if (isDebug) {
                Log.d(TAG, "Analytics collection manually set to: $enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update analytics collection state", e)
        }
    }

    override fun resetData() {
        try {
            firebaseAnalytics?.resetAnalyticsData()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to reset analytics data", e)
        }
    }

    companion object {
        private const val TAG = "FirebaseAnalyticsTracker"
    }
}

class NoOpAnalyticsTracker : AnalyticsTracker {
    val loggedEvents = mutableListOf<TelemetryEvent>()
    val userProperties = mutableMapOf<String, String?>()
    var currentScreen: CanonicalScreen? = null
    var collectionEnabled: Boolean = true
        private set

    override fun logEvent(event: TelemetryEvent) {
        loggedEvents.add(event)
    }

    override fun setScreen(screen: CanonicalScreen) {
        currentScreen = screen
        logEvent(TelemetryEvent.ScreenView(screen))
    }

    override fun setUserProperty(name: String, value: String?) {
        userProperties[name] = value
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }

    override fun resetData() {
        loggedEvents.clear()
        userProperties.clear()
        currentScreen = null
    }
}
