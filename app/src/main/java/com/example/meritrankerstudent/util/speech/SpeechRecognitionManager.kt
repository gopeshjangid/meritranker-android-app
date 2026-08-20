package com.example.meritrankerstudent.util.speech

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.example.meritrankerstudent.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    ERROR
}

interface SpeechRecognitionCallback {
    fun onStateChanged(state: VoiceState)
    fun onVoiceModeChanged(isVoiceModeActive: Boolean)
    fun onPartialTranscript(transcript: String)
    fun onFinalTranscript(transcript: String)
    fun onError(userFriendlyError: String)
}

/**
 * Enterprise SpeechRecognitionManager orchestrating Google Cloud STT V2 Chirp 3
 * as the primary online recognizer and OnDeviceSpeechEngine as the graceful fallback.
 */
class SpeechRecognitionManager(
    private val context: Context,
    private val authRepository: AuthRepository? = null,
    private val callback: SpeechRecognitionCallback
) {
    companion object {
        private const val TAG = "SpeechRecognitionMgr"
        
        fun resolveLanguageCode(selectedLanguage: String): String {
            return when (selectedLanguage.lowercase().trim()) {
                "hi", "hindi", "hinglish", "hing", "hi-in" -> "hi-IN"
                "en", "english", "en-in" -> "en-IN"
                else -> "en-IN"
            }
        }

        fun getErrorMessage(errorCode: Int): String? {
            return when (errorCode) {
                android.speech.SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
                android.speech.SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
                android.speech.SpeechRecognizer.ERROR_NETWORK -> "Network issue. Please try again."
                android.speech.SpeechRecognizer.ERROR_NO_MATCH,
                android.speech.SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
                android.speech.SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                android.speech.SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> null
                else -> "Voice recognition error (code $errorCode)."
            }
        }
    }

    private val isVoiceModeActive = AtomicBoolean(false)
    private var currentScope: CoroutineScope? = null
    private var eventJob: Job? = null

    private val cloudEngine: SpeechToTextEngine by lazy {
        if (authRepository != null) {
            CloudChirpSpeechEngine(context, authRepository)
        } else {
            OnDeviceSpeechEngine(context)
        }
    }

    private val onDeviceEngine: SpeechToTextEngine by lazy {
        OnDeviceSpeechEngine(context)
    }

    private var activeEngine: SpeechToTextEngine? = null

    fun startListening(
        languageMode: VoiceLanguageMode = VoiceLanguageMode.AUTO,
        subject: String? = null,
        examCategory: String? = null
    ) {
        if (isVoiceModeActive.getAndSet(true)) {
            Log.w(TAG, "Voice mode already active")
            return
        }

        callback.onVoiceModeChanged(true)
        val scope = CoroutineScope(Dispatchers.Main + Job())
        currentScope = scope

        val isOnline = isNetworkAvailable()
        val engineToUse = if (isOnline && authRepository != null) {
            Log.d(TAG, "DIAG_VOICE: Starting Primary Engine [Cloud Chirp 3] mode=${languageMode.label}")
            cloudEngine
        } else {
            Log.d(TAG, "DIAG_VOICE: Starting Fallback Engine [On-Device] mode=${languageMode.label}")
            onDeviceEngine
        }
        activeEngine = engineToUse

        val config = VoiceConfig(
            languageMode = languageMode,
            subject = subject,
            examCategory = examCategory,
            enableDenoising = true,
            enableSpeechAdaptation = true,
            maxDurationSeconds = 60
        )

        eventJob = scope.launch {
            engineToUse.startListening(config).collectLatest { event ->
                handleSpeechEvent(event, config)
            }
        }
    }

    fun startListening(selectedLanguage: String) {
        val mode = VoiceLanguageMode.fromCode(selectedLanguage)
        startListening(languageMode = mode)
    }

    private fun handleSpeechEvent(event: SpeechStreamEvent, config: VoiceConfig) {
        when (event) {
            is SpeechStreamEvent.Partial -> {
                val cleaned = VoiceTranscriptCleaner.cleanPreview(event.transcript, config.languageMode.code)
                if (cleaned.isNotBlank()) {
                    callback.onPartialTranscript(cleaned)
                }
            }
            is SpeechStreamEvent.Final -> {
                val cleaned = VoiceTranscriptCleaner.cleanTranscript(event.transcript, config.languageMode.code)
                if (cleaned.isNotBlank()) {
                    callback.onFinalTranscript(cleaned)
                }
            }
            is SpeechStreamEvent.StateChanged -> {
                callback.onStateChanged(event.state)
            }
            is SpeechStreamEvent.FallbackTriggered -> {
                Log.w(TAG, "DIAG_VOICE: FALLBACK_ACTIVATED reason=${event.reason}")
                switchToOnDeviceFallback(config)
            }
            is SpeechStreamEvent.Error -> {
                Log.e(TAG, "DIAG_VOICE: SPEECH_ERROR msg=${event.message} recoverable=${event.isRecoverable}")
                if (!event.isRecoverable) {
                    isVoiceModeActive.set(false)
                    callback.onVoiceModeChanged(false)
                    callback.onStateChanged(VoiceState.ERROR)
                    callback.onError(event.message)
                }
            }
        }
    }

    private fun switchToOnDeviceFallback(config: VoiceConfig) {
        if (!isVoiceModeActive.get()) return
        Log.i(TAG, "DIAG_VOICE: Transitioning seamlessly to OnDeviceSpeechEngine")

        cloudEngine.stopListening()
        activeEngine = onDeviceEngine
        eventJob?.cancel()

        currentScope?.launch {
            onDeviceEngine.startListening(config).collectLatest { fallbackEvent ->
                handleSpeechEvent(fallbackEvent, config)
            }
        }
    }

    fun stopListening() {
        if (!isVoiceModeActive.getAndSet(false)) return
        Log.d(TAG, "DIAG_VOICE: Stopping speech recognition session")
        callback.onVoiceModeChanged(false)
        activeEngine?.stopListening()
        eventJob?.cancel()
        currentScope?.cancel()
        currentScope = null
        callback.onStateChanged(VoiceState.IDLE)
    }

    fun cancel() {
        if (!isVoiceModeActive.getAndSet(false)) return
        Log.d(TAG, "DIAG_VOICE: Cancelling speech recognition session")
        callback.onVoiceModeChanged(false)
        activeEngine?.cancel()
        eventJob?.cancel()
        currentScope?.cancel()
        currentScope = null
        callback.onStateChanged(VoiceState.IDLE)
    }

    fun destroy() {
        cancel()
        cloudEngine.destroy()
        onDeviceEngine.destroy()
        activeEngine = null
    }

    private fun isNetworkAvailable(): Boolean {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            val network = cm?.activeNetwork ?: return false
            val capabilities = cm.getNetworkCapabilities(network) ?: return false
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (e: Throwable) {
            false
        }
    }

    fun isListening(): Boolean = isVoiceModeActive.get()
}
