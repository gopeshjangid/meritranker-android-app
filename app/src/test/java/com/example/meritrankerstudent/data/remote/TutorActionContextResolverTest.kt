package com.example.meritrankerstudent.data.remote

import com.example.meritrankerstudent.data.model.DoubtMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorActionContextResolverTest {

    @Test
    fun resolveSimilarContext_withValidAssistantMessage_returnsResolvedPromptAndDisplayText() {
        val messages = listOf(
            DoubtMessage(
                id = "user_turn_1",
                sender = "USER",
                text = "What is 25% of 480?",
                timestamp = 1000L
            ),
            DoubtMessage(
                id = "ai_turn_1",
                sender = "AI",
                text = "25% of 480 is (25/100) * 480 = 120.",
                timestamp = 1010L
            )
        )

        val targetAssistantMsg = messages[1]
        val resolved = TutorActionContextResolver.resolveSimilarContext(messages, targetAssistantMsg)

        assertNotNull(resolved)
        assertEquals(TutorActionContextResolver.ACTION_TYPE_SIMILAR, resolved!!.actionType)
        assertEquals(TutorActionContextResolver.DISPLAY_TEXT_SIMILAR, resolved.displayText)
        assertEquals("What is 25% of 480?", resolved.originalQuestion)
        assertEquals("turn_1", resolved.originatingTurnId)
        assertNull(resolved.assistantAnswer)

        assertTrue(resolved.resolvedQuery.contains("Create a similar question based on the following question."))
        assertTrue(resolved.resolvedQuery.contains("Keep the same core concept and approximately the same difficulty"))
        assertTrue(resolved.resolvedQuery.contains("Original question:\nWhat is 25% of 480?"))
    }

    @Test
    fun resolveSimplifyContext_withValidAssistantMessage_returnsResolvedPromptAndDisplayText() {
        val messages = listOf(
            DoubtMessage(
                id = "user_turn_1",
                sender = "USER",
                text = "Explain photosynthesis in detail.",
                timestamp = 1000L
            ),
            DoubtMessage(
                id = "ai_turn_1",
                sender = "AI",
                text = "Photosynthesis is the multi-step biochemical process converting light energy into chemical energy: 6CO2 + 6H2O -> C6H12O6 + 6O2 via light and dark reactions.",
                timestamp = 1010L
            )
        )

        val targetAssistantMsg = messages[1]
        val resolved = TutorActionContextResolver.resolveSimplifyContext(messages, targetAssistantMsg)

        assertNotNull(resolved)
        assertEquals(TutorActionContextResolver.ACTION_TYPE_SIMPLIFY, resolved!!.actionType)
        assertEquals(TutorActionContextResolver.DISPLAY_TEXT_SIMPLIFY, resolved.displayText)
        assertEquals("Explain photosynthesis in detail.", resolved.originalQuestion)
        assertEquals("turn_1", resolved.originatingTurnId)
        assertEquals(targetAssistantMsg.text, resolved.assistantAnswer)

        assertTrue(resolved.resolvedQuery.contains("Explain the following answer in a simpler way for a student."))
        assertTrue(resolved.resolvedQuery.contains("Original question:\nExplain photosynthesis in detail."))
        assertTrue(resolved.resolvedQuery.contains("Current explanation:\n${targetAssistantMsg.text}"))
    }

    @Test
    fun wrongTurnIsolation_resolveSimilarOnTurn1_usesQuestion1NotQuestion2() {
        val messages = listOf(
            DoubtMessage(id = "user_turn_1", sender = "USER", text = "Math: Solve 2x + 5 = 15", timestamp = 1000L),
            DoubtMessage(id = "ai_turn_1", sender = "AI", text = "Subtract 5: 2x = 10, divide 2: x = 5.", timestamp = 1010L),
            DoubtMessage(id = "user_turn_2", sender = "USER", text = "Science: State Newton's Second Law", timestamp = 2000L),
            DoubtMessage(id = "ai_turn_2", sender = "AI", text = "F = ma: Force equals mass times acceleration.", timestamp = 2010L)
        )

        // Student taps Similar on Turn 1 (Math)
        val similarTurn1 = TutorActionContextResolver.resolveSimilarContext(messages, messages[1])
        assertNotNull(similarTurn1)
        assertEquals("Math: Solve 2x + 5 = 15", similarTurn1!!.originalQuestion)
        assertEquals("turn_1", similarTurn1.originatingTurnId)
        assertTrue(similarTurn1.resolvedQuery.contains("Math: Solve 2x + 5 = 15"))
        assertTrue(!similarTurn1.resolvedQuery.contains("Newton"))

        // Student taps Simplify on Turn 2 (Science)
        val simplifyTurn2 = TutorActionContextResolver.resolveSimplifyContext(messages, messages[3])
        assertNotNull(simplifyTurn2)
        assertEquals("Science: State Newton's Second Law", simplifyTurn2!!.originalQuestion)
        assertEquals("turn_2", simplifyTurn2.originatingTurnId)
        assertTrue(simplifyTurn2.resolvedQuery.contains("Newton"))
        assertTrue(!simplifyTurn2.resolvedQuery.contains("Solve 2x + 5"))
    }

    @Test
    fun resolveSimilarContext_whenMessageIsThinking_returnsNull() {
        val messages = listOf(
            DoubtMessage(id = "user_turn_1", sender = "USER", text = "Find hypotenuse", timestamp = 1000L),
            DoubtMessage(id = "ai_turn_1", sender = "AI", text = "", isThinking = true, timestamp = 1010L)
        )
        val resolved = TutorActionContextResolver.resolveSimilarContext(messages, messages[1])
        assertNull(resolved)
    }

    @Test
    fun resolveSimilarContext_whenMessageHasError_returnsNull() {
        val messages = listOf(
            DoubtMessage(id = "user_turn_1", sender = "USER", text = "Find hypotenuse", timestamp = 1000L),
            DoubtMessage(id = "ai_turn_1", sender = "AI", text = "", errorMessage = "Network timeout", timestamp = 1010L)
        )
        val resolved = TutorActionContextResolver.resolveSimilarContext(messages, messages[1])
        assertNull(resolved)
    }

    @Test
    fun resolveSimplifyContext_whenAnswerIsBlank_returnsNull() {
        val messages = listOf(
            DoubtMessage(id = "user_turn_1", sender = "USER", text = "Find hypotenuse", timestamp = 1000L),
            DoubtMessage(id = "ai_turn_1", sender = "AI", text = "   ", timestamp = 1010L)
        )
        val resolved = TutorActionContextResolver.resolveSimplifyContext(messages, messages[1])
        assertNull(resolved)
    }

    @Test
    fun resolveSimplifyContext_preservesMathAndLatexSource() {
        val latexText = "Using formula \\( \\text{Area} = \\pi r^2 \\) with \\( r = 7 \\), \\( 49\\pi \\approx 153.86\\text{ cm}^2 \\)."
        val messages = listOf(
            DoubtMessage(id = "user_turn_1", sender = "USER", text = "Area of circle with r=7", timestamp = 1000L),
            DoubtMessage(id = "ai_turn_1", sender = "AI", text = latexText, timestamp = 1010L)
        )

        val resolved = TutorActionContextResolver.resolveSimplifyContext(messages, messages[1])
        assertNotNull(resolved)
        assertTrue(resolved!!.resolvedQuery.contains(latexText))
    }

    @Test
    fun resolveSimplifyContext_preservesHindiContent() {
        val hindiQuestion = "प्रकाश संश्लेषण क्या है और इसके मुख्य चरण क्या हैं?"
        val hindiAnswer = "प्रकाश संश्लेषण वह प्रक्रिया है जिसके द्वारा हरे पौधे सूर्य के प्रकाश की उपस्थिति में अपना भोजन बनाते हैं।"
        val messages = listOf(
            DoubtMessage(id = "user_turn_1", sender = "USER", text = hindiQuestion, timestamp = 1000L),
            DoubtMessage(id = "ai_turn_1", sender = "AI", text = hindiAnswer, timestamp = 1010L)
        )

        val resolved = TutorActionContextResolver.resolveSimplifyContext(messages, messages[1])
        assertNotNull(resolved)
        assertEquals(hindiQuestion, resolved!!.originalQuestion)
        assertEquals(hindiAnswer, resolved.assistantAnswer)
        assertTrue(resolved.resolvedQuery.contains(hindiQuestion))
        assertTrue(resolved.resolvedQuery.contains(hindiAnswer))
    }

    @Test
    fun resolveOriginatingQuestion_fallbackToPrecedingMessageWhenCustomId() {
        val messages = listOf(
            DoubtMessage(id = "msg_1", sender = "USER", text = "Custom user question", timestamp = 1000L),
            DoubtMessage(id = "msg_2", sender = "AI", text = "Custom AI answer", timestamp = 1010L)
        )

        val question = TutorActionContextResolver.resolveOriginatingQuestion(messages, messages[1])
        assertEquals("Custom user question", question)
    }
}
