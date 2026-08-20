package com.example.meritrankerstudent.observability

import android.util.Log
import com.example.meritrankerstudent.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.crashlytics.FirebaseCrashlytics
import java.util.concurrent.ConcurrentHashMap

interface CrashReporter {
    fun recordNonFatal(throwable: Throwable, context: Map<String, String> = emptyMap())
    fun recordBackendFailure(
        operation: String,
        category: ErrorCategory,
        status: String? = null,
        requestId: String? = null,
        throwable: Throwable? = null
    )
    fun setCustomKey(key: String, value: String)
    fun setScreen(screen: CanonicalScreen)
    fun logBreadcrumb(message: String)
    fun setCollectionEnabled(enabled: Boolean)
    fun testCrashForTestingOnly()
}

class FirebaseCrashReporter(
    private val isDebug: Boolean = BuildConfig.DEBUG
) : CrashReporter {

    private var runtimeCollectionOverride: Boolean? = null

    private val crashlytics: FirebaseCrashlytics? by lazy {
        try {
            if (FirebaseApp.getApps(com.example.meritrankerstudent.MeritRankerApplication.instance ?: return@lazy null).isNotEmpty()) {
                val instance = FirebaseCrashlytics.getInstance()
                val shouldEnable = runtimeCollectionOverride ?: (!isDebug || BuildConfig.ENABLE_DEBUG_TELEMETRY)
                instance.setCrashlyticsCollectionEnabled(shouldEnable)
                instance
            } else {
                Log.w(TAG, "Firebase is not initialized; Crashlytics disabled.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing FirebaseCrashlytics", e)
            null
        }
    }

    // Rate limiter: deduplicate identical backend non-fatals within a 5-minute window per session
    private val reportedNonFatals = ConcurrentHashMap<String, Long>()
    private val RATE_LIMIT_WINDOW_MS = 5 * 60 * 1000L

    override fun recordNonFatal(throwable: Throwable, context: Map<String, String>) {
        try {
            val safeContext = PiiSanitizer.sanitizeMap(context)
            safeContext.forEach { (k, v) ->
                crashlytics?.setCustomKey(k, v.toString())
            }
            crashlytics?.recordException(throwable)
            
            // Clear transient operation keys immediately after recording to prevent stale state in future unrelated crashes
            safeContext.keys.filter { it != "current_screen" }.forEach { k ->
                crashlytics?.setCustomKey(k, "")
            }

            if (isDebug) {
                Log.d(TAG, "Recorded non-fatal exception: ${throwable.message}, context: $safeContext")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record non-fatal exception", e)
        }
    }

    override fun recordBackendFailure(
        operation: String,
        category: ErrorCategory,
        status: String?,
        requestId: String?,
        throwable: Throwable?
    ) {
        try {
            val dedupKey = "$operation:${category.key}:${status ?: "none"}"
            val now = System.currentTimeMillis()
            val lastReported = reportedNonFatals[dedupKey]

            if (lastReported != null && (now - lastReported) < RATE_LIMIT_WINDOW_MS) {
                // Rate-limited duplicate in current session window
                return
            }
            reportedNonFatals[dedupKey] = now

            val contextMap = buildMap<String, String> {
                put("operation", operation)
                put("error_category", category.key)
                status?.let { put("status_bucket", it) }
                requestId?.let { put("request_id", it) }
            }

            val exceptionToRecord = throwable ?: BackendOperationException(
                "Backend failure: $operation [${category.key}] (status=${status ?: "unknown"})"
            )

            recordNonFatal(exceptionToRecord, contextMap)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to record backend failure", e)
        }
    }

    override fun setCustomKey(key: String, value: String) {
        try {
            if (!PiiSanitizer.isKeySafe(key)) {
                Log.w(TAG, "Attempted to set unsafe Crashlytics key: $key")
                return
            }
            val sanitized = PiiSanitizer.sanitizeValue(key, value)?.toString() ?: return
            crashlytics?.setCustomKey(key, sanitized)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set custom key: $key", e)
        }
    }

    override fun setScreen(screen: CanonicalScreen) {
        setCustomKey("current_screen", screen.screenName)
        logBreadcrumb("Navigated to ${screen.screenName}")
    }

    override fun logBreadcrumb(message: String) {
        try {
            val sanitized = message.take(150)
            crashlytics?.log(sanitized)
            if (isDebug) {
                Log.d(TAG, "Crashlytics Breadcrumb: $sanitized")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to log breadcrumb", e)
        }
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        runtimeCollectionOverride = enabled
        try {
            crashlytics?.setCrashlyticsCollectionEnabled(enabled)
            if (isDebug) {
                Log.d(TAG, "Crashlytics collection manually set to: $enabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to update crashlytics collection state", e)
        }
    }

    override fun testCrashForTestingOnly() {
        if (isDebug) {
            throw RuntimeException("Test Crash initiated via AppObservability (Debug/Test only)")
        } else {
            Log.w(TAG, "Test crash is strictly prohibited in production release builds.")
        }
    }

    companion object {
        private const val TAG = "FirebaseCrashReporter"
    }
}

class BackendOperationException(message: String) : Exception(message)

class NoOpCrashReporter : CrashReporter {
    val recordedExceptions = mutableListOf<Pair<Throwable, Map<String, String>>>()
    val customKeys = mutableMapOf<String, String>()
    val breadcrumbs = mutableListOf<String>()
    var collectionEnabled: Boolean = true
        private set

    override fun recordNonFatal(throwable: Throwable, context: Map<String, String>) {
        recordedExceptions.add(throwable to context)
    }

    override fun recordBackendFailure(
        operation: String,
        category: ErrorCategory,
        status: String?,
        requestId: String?,
        throwable: Throwable?
    ) {
        val map = mapOf(
            "operation" to operation,
            "error_category" to category.key,
            "status" to (status ?: "none"),
            "request_id" to (requestId ?: "none")
        )
        recordNonFatal(throwable ?: BackendOperationException("Backend failure: $operation"), map)
    }

    override fun setCustomKey(key: String, value: String) {
        customKeys[key] = value
    }

    override fun setScreen(screen: CanonicalScreen) {
        customKeys["current_screen"] = screen.screenName
        logBreadcrumb("Navigated to ${screen.screenName}")
    }

    override fun logBreadcrumb(message: String) {
        breadcrumbs.add(message)
    }

    override fun setCollectionEnabled(enabled: Boolean) {
        collectionEnabled = enabled
    }

    override fun testCrashForTestingOnly() {
        // No-op in tests
    }
}
