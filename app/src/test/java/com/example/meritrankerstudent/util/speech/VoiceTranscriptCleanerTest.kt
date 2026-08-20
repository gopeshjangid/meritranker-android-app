package com.example.meritrankerstudent.util.speech

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceTranscriptCleanerTest {

    @Test
    fun cleanTranscript_removesStutterAndConsecutiveDuplicates() {
        val input = "what what is the the formula of speed"
        val expected = "what is the formula of speed?"
        assertEquals(expected, VoiceTranscriptCleaner.cleanTranscript(input))
    }

    @Test
    fun cleanTranscript_removesLeadingAndTrailingFillerWords() {
        val input = "um uh what is the capital of India umm"
        val expected = "what is the capital of India?"
        assertEquals(expected, VoiceTranscriptCleaner.cleanTranscript(input))
    }

    @Test
    fun cleanTranscript_removesHindiFillersAndAddsQuestionMark() {
        val input = "मतलब kya kya profit and loss ka shortcut hai"
        val expected = "kya profit and loss ka shortcut hai?"
        assertEquals(expected, VoiceTranscriptCleaner.cleanTranscript(input))
    }

    @Test
    fun cleanTranscript_preservesNonInterrogativeStatements() {
        val input = "I want to revise trigonometry formulas."
        val expected = "I want to revise trigonometry formulas."
        assertEquals(expected, VoiceTranscriptCleaner.cleanTranscript(input))
    }

    @Test
    fun cleanTranscript_handlesEmptyAndBlankGracefully() {
        assertEquals("", VoiceTranscriptCleaner.cleanTranscript(""))
        assertEquals("", VoiceTranscriptCleaner.cleanTranscript("   "))
        assertEquals("", VoiceTranscriptCleaner.cleanTranscript(null))
        assertEquals("", VoiceTranscriptCleaner.cleanTranscript("um uh err"))
    }

    @Test
    fun cleanPreview_formatsPromptPreview() {
        assertEquals("Solving percentage doubt", VoiceTranscriptCleaner.cleanPreview("solving percentage doubt"))
        assertEquals("", VoiceTranscriptCleaner.cleanPreview("  "))
    }
}
