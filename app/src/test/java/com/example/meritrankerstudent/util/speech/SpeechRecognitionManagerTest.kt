package com.example.meritrankerstudent.util.speech

import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechRecognitionManagerTest {

    @Test
    fun resolveLanguageCode_resolvesHindiForHiAndHindiPreferences() {
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("hi"))
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("hindi"))
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("HI-IN"))
        assertEquals("hi-IN", SpeechRecognitionManager.resolveLanguageCode("Hinglish"))
    }

    @Test
    fun resolveLanguageCode_resolvesEnglishDefault() {
        assertEquals("en-IN", SpeechRecognitionManager.resolveLanguageCode("en"))
        assertEquals("en-IN", SpeechRecognitionManager.resolveLanguageCode("english"))
        assertEquals("en-IN", SpeechRecognitionManager.resolveLanguageCode("unknown_lang"))
    }

    @Test
    fun getErrorMessage_mapsErrorCodesToUserFriendlyMessages() {
        assertEquals("Audio recording error. Check microphone.", SpeechRecognitionManager.getErrorMessage(SpeechRecognizer.ERROR_AUDIO))
        assertEquals("Microphone permission is required.", SpeechRecognitionManager.getErrorMessage(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertEquals(null, SpeechRecognitionManager.getErrorMessage(SpeechRecognizer.ERROR_NO_MATCH))
        assertEquals(null, SpeechRecognitionManager.getErrorMessage(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertEquals(null, SpeechRecognitionManager.getErrorMessage(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertEquals("Network issue. Please try again.", SpeechRecognitionManager.getErrorMessage(SpeechRecognizer.ERROR_NETWORK))
    }
}
