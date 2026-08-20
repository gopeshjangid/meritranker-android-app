package com.example.meritrankerstudent.util.speech

import org.junit.Assert.assertTrue
import org.junit.Test

class ExamSpeechAdaptationTest {

    @Test
    fun testCoreExamPhrases_includedInAllContexts() {
        val generalPhrases = ExamSpeechAdaptation.getPhrasesForContext(null, null)
        assertTrue(generalPhrases.contains("SSC CGL"))
        assertTrue(generalPhrases.contains("Tier 1"))
        assertTrue(generalPhrases.contains("Mock Test"))
    }

    @Test
    fun testQuantSubject_includesQuantPhrases() {
        val quantPhrases = ExamSpeechAdaptation.getPhrasesForContext("Quantitative Aptitude", "SSC CGL")
        assertTrue(quantPhrases.contains("Compound Interest"))
        assertTrue(quantPhrases.contains("Profit and Loss"))
        assertTrue(quantPhrases.contains("चक्रवृद्धि ब्याज"))
        assertTrue(quantPhrases.contains("प्रतिशत"))
    }

    @Test
    fun testReasoningSubject_includesReasoningPhrases() {
        val reasoningPhrases = ExamSpeechAdaptation.getPhrasesForContext("Logical Reasoning", "SSC CGL")
        assertTrue(reasoningPhrases.contains("Syllogism"))
        assertTrue(reasoningPhrases.contains("Venn Diagram"))
        assertTrue(reasoningPhrases.contains("न्याय वाक्य"))
    }

    @Test
    fun testPolitySubject_includesPolityPhrases() {
        val polityPhrases = ExamSpeechAdaptation.getPhrasesForContext("Polity & General Studies", "SSC CGL")
        assertTrue(polityPhrases.contains("Article 14"))
        assertTrue(polityPhrases.contains("Article 21"))
        assertTrue(polityPhrases.contains("Fundamental Rights"))
        assertTrue(polityPhrases.contains("अनुच्छेद 14"))
    }

    @Test
    fun testBoundedSize_neverExceedsCap() {
        val phrases = ExamSpeechAdaptation.getPhrasesForContext("General", "UPSC")
        assertTrue(phrases.size <= 60)
    }
}
