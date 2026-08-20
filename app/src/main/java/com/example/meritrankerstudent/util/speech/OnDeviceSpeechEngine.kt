package com.example.meritrankerstudent.util.speech

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * On-Device Fallback Speech Recognition Engine.
 * 
 * Provides graceful offline capability when network connectivity is lost
 * or when the cloud voice stream encounters an unrecoverable failure.
 */
class OnDeviceSpeechEngine(
    private val context: Context
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "OnDeviceEngine"
        const val ENGINE_ID = "ON_DEVICE_FALLBACK"
        private const val RESTART_DELAY_MS = 250L
        private const val MAX_CONSECUTIVE_FAILURES = 3
    }

    override val engineName: String = ENGINE_ID

    private val isListening = AtomicBoolean(false)
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val sessionGeneration = AtomicLong(0L)
    private var consecutiveFailures = 0
    private var hadFallback = false
    private var activeLanguageCode: String = "hi-IN"
    private var scope: CoroutineScope? = null

    private val _events = MutableSharedFlow<SpeechStreamEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun startListening(config: VoiceConfig): Flow<SpeechStreamEvent> {
        if (isListening.getAndSet(true)) {
            Log.w(TAG, "On-device speech recognition already active")
            return _events.asSharedFlow()
        }

        scope = CoroutineScope(Dispatchers.Main + Job())
        consecutiveFailures = 0
        hadFallback = false
        activeLanguageCode = config.languageMode.bcp47Codes.firstOrNull() ?: "hi-IN"

        mainHandler.post {
            val gen = sessionGeneration.incrementAndGet()
            initAndStartRecognizer(activeLanguageCode, gen)
        }

        return _events.asSharedFlow()
    }

    private fun initAndStartRecognizer(langCode: String, gen: Long) {
        if (!isListening.get() || gen != sessionGeneration.get()) return

        try {
            speechRecognizer?.destroy()
            speechRecognizer = createRecognizer(forcePlatform = hadFallback)

            val recognizer = speechRecognizer
            if (recognizer == null) {
                Log.e(TAG, "SpeechRecognizer not available on device")
                emitEvent(SpeechStreamEvent.Error("On-device speech recognition unavailable", isRecoverable = false))
                stopListening()
                return
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
                if (langCode.isNotBlank()) {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, langCode)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, langCode)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_DETECTION, true)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_DETECTION_ALLOWED_LANGUAGES, arrayListOf("en-IN", "hi-IN"))
                    putExtra(RecognizerIntent.EXTRA_ENABLE_LANGUAGE_SWITCH, RecognizerIntent.LANGUAGE_SWITCH_BALANCED)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_SWITCH_ALLOWED_LANGUAGES, arrayListOf("en-IN", "hi-IN"))
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putExtra(RecognizerIntent.EXTRA_ENABLE_FORMATTING, RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY)
                }
            }

            recognizer.startListening(intent)
            emitEvent(SpeechStreamEvent.StateChanged(VoiceState.LISTENING))
        } catch (e: Throwable) {
            Log.e(TAG, "Error starting on-device recognizer", e)
            scheduleRestart()
        }
    }

    private fun createRecognizer(forcePlatform: Boolean = false): SpeechRecognizer? {
        return try {
            if (!forcePlatform && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && SpeechRecognizer.isOnDeviceRecognitionAvailable(context)) {
                SpeechRecognizer.createOnDeviceSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } else {
                null
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error creating recognizer, falling back to platform", e)
            try {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(createListener())
                    }
                } else null
            } catch (e2: Throwable) {
                null
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            private var lastPartial: String = ""

            override fun onReadyForSpeech(params: Bundle?) {
                lastPartial = ""
                emitEvent(SpeechStreamEvent.StateChanged(VoiceState.LISTENING))
            }

            override fun onBeginningOfSpeech() {
                emitEvent(SpeechStreamEvent.StateChanged(VoiceState.LISTENING))
            }

            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                emitEvent(SpeechStreamEvent.StateChanged(VoiceState.PROCESSING))
            }

            override fun onError(error: Int) {
                if (!isListening.get()) return

                // Language model missing fallback
                if ((error == 12 || error == 13 || error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) && !hadFallback) {
                    hadFallback = true
                    mainHandler.post {
                        speechRecognizer?.destroy()
                        speechRecognizer = createRecognizer(forcePlatform = true)
                        val gen = sessionGeneration.incrementAndGet()
                        initAndStartRecognizer(activeLanguageCode, gen)
                    }
                    return
                }

                // Recoverable silence / timeout errors
                if (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    consecutiveFailures++
                    if (consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
                        scheduleRestart()
                        return
                    }
                }

                // Deliver saved partial before error
                if (lastPartial.isNotBlank()) {
                    emitEvent(SpeechStreamEvent.Final(lastPartial, activeLanguageCode))
                    lastPartial = ""
                }

                emitEvent(SpeechStreamEvent.Error(getErrorMessage(error), isRecoverable = false))
                stopListening()
            }

            override fun onResults(results: Bundle?) {
                if (!isListening.get()) return
                consecutiveFailures = 0
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val confidences = results?.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES)

                val bestText = VoiceTranscriptCleaner.selectBestCandidate(matches ?: emptyList(), confidences, activeLanguageCode)
                if (bestText.isNotBlank()) {
                    emitEvent(SpeechStreamEvent.Final(bestText, activeLanguageCode))
                }

                if (isListening.get()) {
                    scheduleRestart()
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (!isListening.get()) return
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val rawText = matches?.firstOrNull()?.trim() ?: ""
                if (rawText.isNotBlank()) {
                    val preview = VoiceTranscriptCleaner.cleanPreview(rawText, activeLanguageCode)
                    lastPartial = preview
                    emitEvent(SpeechStreamEvent.Partial(preview, activeLanguageCode, isFinal = false))
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onLanguageDetection(results: Bundle) {
                val detected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    results.getString(SpeechRecognizer.DETECTED_LANGUAGE)
                } else {
                    results.getString("detected_language")
                }
                if (!detected.isNullOrBlank()) {
                    activeLanguageCode = detected
                }
            }
        }
    }

    private fun scheduleRestart() {
        if (!isListening.get()) return
        val currentGen = sessionGeneration.incrementAndGet()
        mainHandler.postDelayed({
            if (isListening.get() && currentGen == sessionGeneration.get()) {
                initAndStartRecognizer(activeLanguageCode, currentGen)
            }
        }, RESTART_DELAY_MS)
    }

    private fun emitEvent(event: SpeechStreamEvent) {
        scope?.launch {
            _events.emit(event)
        }
    }

    override fun stopListening() {
        if (!isListening.getAndSet(false)) return
        Log.d(TAG, "Stopping OnDeviceSpeechEngine")
        sessionGeneration.incrementAndGet()
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Throwable) {
                Log.e(TAG, "Error stopping recognizer", e)
            }
        }
        emitEvent(SpeechStreamEvent.StateChanged(VoiceState.IDLE))
    }

    override fun cancel() {
        if (!isListening.getAndSet(false)) return
        Log.d(TAG, "Cancelling OnDeviceSpeechEngine")
        sessionGeneration.incrementAndGet()
        mainHandler.post {
            try {
                speechRecognizer?.cancel()
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Throwable) {
                Log.e(TAG, "Error cancelling recognizer", e)
            }
        }
        emitEvent(SpeechStreamEvent.StateChanged(VoiceState.IDLE))
    }

    override fun destroy() {
        cancel()
        scope = null
    }

    override fun isListening(): Boolean = isListening.get()

    private fun getErrorMessage(errorCode: Int): String {
        return when (errorCode) {
            SpeechRecognizer.ERROR_AUDIO -> "Audio recording error. Check microphone."
            SpeechRecognizer.ERROR_CLIENT -> "Speech recognition client error."
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Microphone permission is required."
            SpeechRecognizer.ERROR_NETWORK -> "Network issue. Please try again."
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout."
            SpeechRecognizer.ERROR_NO_MATCH -> "No speech detected."
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Speech service is busy."
            SpeechRecognizer.ERROR_SERVER -> "Server error. Please try again."
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech input."
            12, 13, SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "Language package is not available on this device."
            else -> "Voice recognition error (code $errorCode)."
        }
    }
}
