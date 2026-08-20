package com.example.meritrankerstudent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Question(
    val id: String,
    val text: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val subject: String,
    val averageTimeSeconds: Int = 45
)

@Serializable
data class Quiz(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val totalQuestions: Int,
    val subject: String
)

@Serializable
data class MockTest(
    val id: String,
    val title: String,
    val durationMinutes: Int,
    val totalQuestions: Int,
    val examName: String
)

@Serializable
data class Pyq(
    val id: String,
    val title: String,
    val year: Int,
    val examName: String,
    val totalQuestions: Int
)

@Serializable
data class ProgressSummary(
    val examReadinessPercentage: Int,
    val weakTopic: String,
    val lastMockScore: String,
    val wrongQuestionsCount: Int
)

@Serializable
data class DoubtMessage(
    val id: String,
    val sender: String, // "USER" or "AI"
    val text: String,
    val timestamp: Long,
    val actionText: String? = null,
    val actionRoute: String? = null, // e.g. "QUIZ", "MOCK", "PYQ"
    val imageUri: String? = null,
    val practiceTestId: String? = null,
    val isPracticeGenerating: Boolean = false,
    val practiceTitle: String? = null,
    val practiceTotalQuestions: Int = 5,
    val practiceReadyQuestions: Int = 0,
    val practiceStatus: String = "GENERATING" // "GENERATING", "READY", "FAILED"
)
