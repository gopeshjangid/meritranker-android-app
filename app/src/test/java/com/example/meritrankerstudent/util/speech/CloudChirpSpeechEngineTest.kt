package com.example.meritrankerstudent.util.speech

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudChirpSpeechEngineTest {

    @Test
    fun testVoiceLanguageMode_valuesAndCodes() {
        assertEquals("auto", VoiceLanguageMode.AUTO.code)
        assertEquals("hi", VoiceLanguageMode.HI.code)
        assertEquals("en", VoiceLanguageMode.EN.code)

        assertEquals(VoiceLanguageMode.AUTO, VoiceLanguageMode.fromCode("auto"))
        assertEquals(VoiceLanguageMode.HI, VoiceLanguageMode.fromCode("hi"))
        assertEquals(VoiceLanguageMode.HI, VoiceLanguageMode.fromCode("hindi"))
        assertEquals(VoiceLanguageMode.EN, VoiceLanguageMode.fromCode("en"))
        assertEquals(VoiceLanguageMode.EN, VoiceLanguageMode.fromCode("english"))
        assertEquals(VoiceLanguageMode.AUTO, VoiceLanguageMode.fromCode("unknown"))
    }

    @Test
    fun testVoiceConfig_defaults() {
        val config = VoiceConfig()
        assertEquals(VoiceLanguageMode.AUTO, config.languageMode)
        assertTrue(config.enableDenoising)
        assertTrue(config.enableSpeechAdaptation)
        assertEquals(60, config.maxDurationSeconds)
    }

    @Test
    fun testSpeechStreamEvent_contracts() {
        val partial = SpeechStreamEvent.Partial(transcript = "प्रतिशत", detectedLanguage = "hi-IN", isFinal = false)
        assertEquals("प्रतिशत", partial.transcript)
        assertEquals("hi-IN", partial.detectedLanguage)
        assertEquals(false, partial.isFinal)

        val finalEvent = SpeechStreamEvent.Final(transcript = "प्रतिशत का फार्मूला क्या है?", detectedLanguage = "hi-IN", confidence = 0.98f)
        assertEquals("प्रतिशत का फार्मूला क्या है?", finalEvent.transcript)
        assertEquals("hi-IN", finalEvent.detectedLanguage)
        assertEquals(0.98f, finalEvent.confidence)

        val fallback = SpeechStreamEvent.FallbackTriggered(reason = "Network interruption")
        assertEquals("Network interruption", fallback.reason)
    }

    @Test
    fun testSpeechJsonParsing_extractsFieldsAccurately() {
        val rawJson = """
            {
                "type": "partial",
                "transcript": "प्रतिशत के सवाल",
                "detected_language": "hi-IN",
                "is_final": false,
                "confidence": 0.95
            }
        """.trimIndent()

        val json = JSONObject(rawJson)
        assertEquals("partial", json.getString("type"))
        assertEquals("प्रतिशत के सवाल", json.getString("transcript"))
        assertEquals("hi-IN", json.getString("detected_language"))
        assertEquals(false, json.getBoolean("is_final"))
        assertEquals(0.95, json.getDouble("confidence"), 0.001)
    }
}
