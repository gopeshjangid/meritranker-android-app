package com.example.meritrankerstudent.util.speech

import android.content.Context
import android.util.Base64
import android.util.Log
import com.example.meritrankerstudent.BuildConfig
import com.example.meritrankerstudent.data.repository.AuthRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Primary Online Speech-to-Text Engine powered by Google Cloud STT V2 (chirp_3).
 * 
 * Security & Zero-Secret Guarantee:
 * Android communicates strictly through MeritRanker's Cognito-authenticated Voice Gateway.
 * Google Cloud Service Account credentials reside exclusively on the server.
 */
class CloudChirpSpeechEngine(
    private val context: Context,
    private val authRepository: AuthRepository,
    private val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite for WebSocket streaming
        .writeTimeout(10, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()
) : SpeechToTextEngine {

    companion object {
        private const val TAG = "CloudChirpEngine"
        const val ENGINE_ID = "GOOGLE_CHIRP_3_STREAMING"
        private const val MAX_SESSION_DURATION_MS = 60_000L // 60 seconds safety guard
    }

    override val engineName: String = ENGINE_ID

    private val isListening = AtomicBoolean(false)
    private val audioRecorder = AudioStreamRecorder()
    private var scope: CoroutineScope? = null
    private var streamJob: Job? = null
    private var timeoutJob: Job? = null
    private var webSocket: WebSocket? = null
    private var currentVoiceSessionId: String? = null

    private val _events = MutableSharedFlow<SpeechStreamEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun startListening(config: VoiceConfig): Flow<SpeechStreamEvent> {
        if (isListening.getAndSet(true)) {
            Log.w(TAG, "Speech session already in progress")
            return _events.asSharedFlow()
        }

        val sessionScope = CoroutineScope(Dispatchers.IO + Job())
        scope = sessionScope
        val voiceSessionId = "vsession-${UUID.randomUUID()}"
        currentVoiceSessionId = voiceSessionId

        sessionScope.launch {
            _events.emit(SpeechStreamEvent.StateChanged(VoiceState.LISTENING))

            // 1. Resolve Authentication Token
            val authTokenResult = authRepository.getAccessToken()
            val token = authTokenResult.getOrNull()

            // 2. Derive Bounded Domain Adaptation Phrases
            val phrases = if (config.enableSpeechAdaptation) {
                ExamSpeechAdaptation.getPhrasesForContext(config.subject, config.examCategory)
            } else {
                emptyList()
            }

            // 3. Connect to Voice Gateway WebSocket
            val wsUrl = resolveWebSocketUrl()
            val requestBuilder = Request.Builder().url(wsUrl)
            if (!token.isNullOrBlank()) {
                requestBuilder.addHeader("Authorization", "Bearer $token")
            }
            requestBuilder.addHeader("X-Voice-Session-Id", voiceSessionId)

            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.d(TAG, "DIAG_VOICE: CLOUD_WS_CONNECTED session=$voiceSessionId")
                    // Send Configuration Frame
                    val initConfig = JSONObject().apply {
                        put("action", "start_recognition")
                        put("model", "chirp_3")
                        put("voice_session_id", voiceSessionId)
                        put("language_mode", config.languageMode.name)
                        put("language_codes", org.json.JSONArray(config.languageMode.bcp47Codes))
                        put("sample_rate_hertz", AudioStreamRecorder.SAMPLE_RATE_HZ)
                        put("encoding", "LINEAR16")
                        put("enable_denoising", config.enableDenoising)
                        put("phrases", org.json.JSONArray(phrases))
                    }
                    webSocket.send(initConfig.toString())
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingTranscriptJson(text)
                }

                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    handleIncomingTranscriptJson(bytes.utf8())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "DIAG_VOICE: CLOUD_WS_FAILURE - ${t.message}")
                    sessionScope.launch {
                        _events.emit(SpeechStreamEvent.FallbackTriggered("Network interruption: ${t.localizedMessage ?: "Cloud STT stream dropped"}"))
                    }
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.d(TAG, "DIAG_VOICE: CLOUD_WS_CLOSED code=$code reason=$reason")
                }
            }

            try {
                webSocket = okHttpClient.newWebSocket(requestBuilder.build(), listener)
            } catch (e: Throwable) {
                Log.e(TAG, "WebSocket initialization failed, triggering fallback", e)
                _events.emit(SpeechStreamEvent.FallbackTriggered("Cannot connect to cloud voice engine"))
                return@launch
            }

            // 4. Start Audio Capture & Pipe to WebSocket
            val audioStarted = audioRecorder.start(sessionScope)
            if (!audioStarted) {
                _events.emit(SpeechStreamEvent.Error("Microphone initialization failed", isRecoverable = false))
                stopListening()
                return@launch
            }

            streamJob = sessionScope.launch {
                audioRecorder.audioChunks.collect { chunk ->
                    if (isActive && isListening.get()) {
                        webSocket?.send(chunk.toByteString(0, chunk.size))
                    }
                }
            }

            // 5. Max Session Safety Guard (60 seconds)
            timeoutJob = sessionScope.launch {
                delay(MAX_SESSION_DURATION_MS)
                if (isActive && isListening.get()) {
                    Log.i(TAG, "DIAG_VOICE: MAX_SESSION_LIMIT_REACHED")
                    _events.emit(SpeechStreamEvent.StateChanged(VoiceState.PROCESSING))
                    stopListening()
                }
            }
        }

        return _events.asSharedFlow()
    }

    private fun handleIncomingTranscriptJson(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val type = json.optString("type", "")
            val transcript = json.optString("transcript", "").trim()
            val detectedLanguage = if (json.has("detected_language") && !json.isNull("detected_language")) {
                json.getString("detected_language").takeIf { it.isNotBlank() }
            } else null
            val isFinal = json.optBoolean("is_final", false)
            val confidence = json.optDouble("confidence", 1.0).toFloat()

            if (transcript.isBlank()) return

            scope?.launch {
                if (isFinal || type == "final") {
                    _events.emit(
                        SpeechStreamEvent.Final(
                            transcript = transcript,
                            detectedLanguage = detectedLanguage,
                            confidence = confidence
                        )
                    )
                } else {
                    _events.emit(
                        SpeechStreamEvent.Partial(
                            transcript = transcript,
                            detectedLanguage = detectedLanguage,
                            isFinal = false
                        )
                    )
                }
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to parse speech response: $jsonStr", e)
        }
    }

    private fun resolveWebSocketUrl(): String {
        val base = BuildConfig.TUTOR_API_BASE_URL
        val wsBase = when {
            base.startsWith("https://") -> base.replace("https://", "wss://")
            base.startsWith("http://") -> base.replace("http://", "ws://")
            else -> "ws://$base"
        }
        return "$wsBase/api/voice/stream"
    }

    override fun stopListening() {
        if (!isListening.getAndSet(false)) return
        Log.d(TAG, "Stopping CloudChirpSpeechEngine")
        try {
            audioRecorder.stop()
            timeoutJob?.cancel()
            streamJob?.cancel()

            // Gracefully send end of audio signal before closing
            val finishJson = JSONObject().apply {
                put("action", "stop_recognition")
                put("voice_session_id", currentVoiceSessionId)
            }
            webSocket?.send(finishJson.toString())
            webSocket?.close(1000, "Normal Voice Session Completion")
            webSocket = null

            scope?.launch {
                _events.emit(SpeechStreamEvent.StateChanged(VoiceState.IDLE))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error stopping cloud recognizer", e)
        }
    }

    override fun cancel() {
        if (!isListening.getAndSet(false)) return
        Log.d(TAG, "Cancelling CloudChirpSpeechEngine session=$currentVoiceSessionId")
        try {
            audioRecorder.stop()
            timeoutJob?.cancel()
            streamJob?.cancel()

            val cancelJson = JSONObject().apply {
                put("action", "cancel_recognition")
                put("voice_session_id", currentVoiceSessionId)
            }
            webSocket?.send(cancelJson.toString())
            webSocket?.cancel()
            webSocket = null

            scope?.launch {
                _events.emit(SpeechStreamEvent.StateChanged(VoiceState.IDLE))
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Error cancelling cloud recognizer", e)
        }
    }

    override fun destroy() {
        cancel()
        scope?.launch {
            // Reclaim resources
        }
        scope = null
    }

    override fun isListening(): Boolean = isListening.get()
}
