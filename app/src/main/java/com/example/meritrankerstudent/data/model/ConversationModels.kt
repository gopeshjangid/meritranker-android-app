package com.example.meritrankerstudent.data.model

import java.util.UUID

data class ConversationSession(
    val id: String = UUID.randomUUID().toString(),
    val userId: String,
    val title: String,
    val lastActivityAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val examId: String? = null,
    val language: String? = "english"
)

data class ConversationTurn(
    val id: String = UUID.randomUUID().toString(),
    val conversationId: String,
    val userId: String,
    val originalQuery: String,
    val finalAnswer: String,
    val subject: String? = null,
    val topic: String? = null,
    val examId: String? = null,
    val examStage: String? = null,
    val language: String = "english",
    val createdAt: Long = System.currentTimeMillis(),
    val actionText: String? = null,
    val actionRoute: String? = null,
    val imageUri: String? = null,
    val practiceTestId: String? = null,
    val responseType: String? = null
)

data class DoubtRequest(
    val userId: String,
    val conversationId: String,
    val turnId: String = UUID.randomUUID().toString(),
    val query: String,
    val language: String = "english",
    val examProfileId: String? = null,
    val examId: String? = null,
    val examStage: String? = null,
    val imageUri: String? = null,
    val imageBase64: String? = null,
    val imageMimeType: String? = null
)
