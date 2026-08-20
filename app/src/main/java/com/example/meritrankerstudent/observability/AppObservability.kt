package com.example.meritrankerstudent.observability

import android.content.Context
import android.util.Log

object AppObservability {

    private const val TAG = "AppObservability"

    @Volatile
    private var _analytics: AnalyticsTracker = NoOpAnalyticsTracker()

    @Volatile
    private var _crashReporter: CrashReporter = NoOpCrashReporter()

    val analytics: AnalyticsTracker
        get() = _analytics

    val crashReporter: CrashReporter
        get() = _crashReporter

    fun init(context: Context) {
        try {
            _analytics = FirebaseAnalyticsTracker(context.applicationContext)
            _crashReporter = FirebaseCrashReporter()
            Log.i(TAG, "AppObservability initialized successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "Fail-open: Error initializing AppObservability", e)
            _analytics = NoOpAnalyticsTracker()
            _crashReporter = NoOpCrashReporter()
        }
    }

    fun setObservabilityForTesting(tracker: AnalyticsTracker, reporter: CrashReporter) {
        _analytics = tracker
        _crashReporter = reporter
    }

    fun resetObservability() {
        _analytics.resetData()
        _analytics = NoOpAnalyticsTracker()
        _crashReporter = NoOpCrashReporter()
    }
}
