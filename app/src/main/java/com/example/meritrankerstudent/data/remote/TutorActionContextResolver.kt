package com.example.meritrankerstudent.data.remote

import com.example.meritrankerstudent.data.model.DoubtMessage

data class ResolvedActionContext(
    val actionType: String, // "SIMILAR" or "SIMPLIFY"
    val originalQuestion: String,
    val assistantAnswer: String? = null,
    val resolvedQuery: String,
    val displayText: String,
    val originatingTurnId: String? = null
)

object TutorActionContextResolver {

    const val ACTION_TYPE_NORMAL = "NORMAL"
    const val ACTION_TYPE_SIMILAR = "SIMILAR"
    const val ACTION_TYPE_SIMPLIFY = "SIMPLIFY"

    const val DISPLAY_TEXT_SIMILAR = "Create a similar question"
    const val DISPLAY_TEXT_SIMPLIFY = "Explain this more simply"

    /**
     * Resolves the exact student question that originated a specific assistant turn.
     * Uses turn identity (e.g. `user_<turnId>` matching `ai_<turnId>`) first,
     * with position-based fallback to the immediately preceding user message in the list.
     */
    fun resolveOriginatingQuestion(
        messages: List<DoubtMessage>,
        targetAssistantMessage: DoubtMessage
    ): String? {
        val targetIndex = messages.indexOfFirst { it.id == targetAssistantMessage.id }
        if (targetIndex < 0) return null

        // 1. Try exact turn-ID association if id conforms to "ai_<turnId>"
        if (targetAssistantMessage.id.startsWith("ai_")) {
            val turnId = targetAssistantMessage.id.removePrefix("ai_")
            val matchingUserMsg = messages.find { it.id == "user_$turnId" && it.sender == "USER" }
            if (matchingUserMsg != null && matchingUserMsg.text.isNotBlank()) {
                return matchingUserMsg.text.trim()
            }
        }

        // 2. Position-based lookup: find the last user message preceding the assistant message
        for (i in (targetIndex - 1) downTo 0) {
            val msg = messages[i]
            if (msg.sender == "USER" && msg.text.isNotBlank()) {
                return msg.text.trim()
            }
        }

        return null
    }

    /**
     * Constructs the fully resolved contextual instruction for the "Similar" action.
     * Returns null if originating question is blank or target assistant message is invalid.
     */
    fun resolveSimilarContext(
        messages: List<DoubtMessage>,
        targetAssistantMessage: DoubtMessage
    ): ResolvedActionContext? {
        if (targetAssistantMessage.sender != "AI") return null
        if (targetAssistantMessage.isThinking) return null
        if (targetAssistantMessage.errorMessage != null) return null

        val originalQuestion = resolveOriginatingQuestion(messages, targetAssistantMessage)
            ?: return null

        if (originalQuestion.isBlank()) return null

        val originatingTurnId = if (targetAssistantMessage.id.startsWith("ai_")) {
            targetAssistantMessage.id.removePrefix("ai_")
        } else null

        val resolvedQuery = buildString {
            append("Create a similar question based on the following question.\n\n")
            append("Keep the same core concept and approximately the same difficulty, but change the values/context so it is a fresh question.\n\n")
            append("Original question:\n")
            append(originalQuestion)
        }

        return ResolvedActionContext(
            actionType = ACTION_TYPE_SIMILAR,
            originalQuestion = originalQuestion,
            assistantAnswer = null,
            resolvedQuery = resolvedQuery,
            displayText = DISPLAY_TEXT_SIMILAR,
            originatingTurnId = originatingTurnId
        )
    }

    /**
     * Constructs the fully resolved contextual instruction for the "Simplify" action.
     * Returns null if assistant answer is blank, thinking, or has errors.
     */
    fun resolveSimplifyContext(
        messages: List<DoubtMessage>,
        targetAssistantMessage: DoubtMessage
    ): ResolvedActionContext? {
        if (targetAssistantMessage.sender != "AI") return null
        if (targetAssistantMessage.isThinking) return null
        if (targetAssistantMessage.errorMessage != null) return null

        val assistantAnswer = targetAssistantMessage.text.trim()
        if (assistantAnswer.isBlank()) return null

        val originalQuestion = resolveOriginatingQuestion(messages, targetAssistantMessage)

        val originatingTurnId = if (targetAssistantMessage.id.startsWith("ai_")) {
            targetAssistantMessage.id.removePrefix("ai_")
        } else null

        val resolvedQuery = buildString {
            append("Explain the following answer in a simpler way for a student.\n\n")
            append("Use simple language, shorter steps, and keep the same factual conclusion. Do not change the meaning or introduce a different answer.\n\n")
            if (!originalQuestion.isNullOrBlank()) {
                append("Original question:\n")
                append(originalQuestion)
                append("\n\n")
            }
            append("Current explanation:\n")
            append(assistantAnswer)
        }

        return ResolvedActionContext(
            actionType = ACTION_TYPE_SIMPLIFY,
            originalQuestion = originalQuestion ?: "",
            assistantAnswer = assistantAnswer,
            resolvedQuery = resolvedQuery,
            displayText = DISPLAY_TEXT_SIMPLY_SAFE(assistantAnswer),
            originatingTurnId = originatingTurnId
        )
    }

    private fun DISPLAY_TEXT_SIMPLY_SAFE(answer: String): String {
        return DISPLAY_TEXT_SIMPLIFY
    }
}
