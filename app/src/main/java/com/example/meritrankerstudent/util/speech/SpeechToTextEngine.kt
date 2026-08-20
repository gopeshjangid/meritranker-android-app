package com.example.meritrankerstudent.util.speech

import kotlinx.coroutines.flow.Flow

/**
 * Supported Student Voice Language Modes:
 * - AUTO: Multilingual code-switching between en-IN and hi-IN
 * - HI: Forced Hindi (hi-IN) for pure Devanagari transcription
 * - EN: Forced English (en-IN)
 */
enum class VoiceLanguageMode(val code: String, val label: String, val bcp47Codes: List<String>) {
    AUTO("auto", "AUTO", listOf("hi-IN", "en-IN")),
    HI("hi", "हिंदी", listOf("hi-IN")),
    EN("en", "EN", listOf("en-IN"));

    companion object {
        fun fromCode(code: String?): VoiceLanguageMode {
            return when (code?.lowercase()) {
                "hi", "hindi" -> HI
                "en", "english" -> EN
                else -> AUTO
            }
        }
    }
}

data class VoiceConfig(
    val languageMode: VoiceLanguageMode = VoiceLanguageMode.AUTO,
    val examCategory: String? = null,
    val subject: String? = null,
    val enableDenoising: Boolean = true,
    val enableSpeechAdaptation: Boolean = true,
    val maxDurationSeconds: Int = 60
)

sealed interface SpeechStreamEvent {
    data class Partial(
        val transcript: String,
        val detectedLanguage: String? = null,
        val isFinal: Boolean = false
    ) : SpeechStreamEvent

    data class Final(
        val transcript: String,
        val detectedLanguage: String? = null,
        val confidence: Float = 1.0f
    ) : SpeechStreamEvent

    data class StateChanged(val state: VoiceState) : SpeechStreamEvent
    data class FallbackTriggered(val reason: String) : SpeechStreamEvent
    data class Error(val message: String, val isRecoverable: Boolean = false) : SpeechStreamEvent
}

interface SpeechToTextEngine {
    val engineName: String
    fun startListening(config: VoiceConfig): Flow<SpeechStreamEvent>
    fun stopListening()
    fun cancel()
    fun destroy()
    fun isListening(): Boolean
}
