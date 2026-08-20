package com.example.meritrankerstudent.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class GetStudentPerformanceView {
    PRACTICE,
    REAL_EXAM
}

@Serializable
data class StudentPerformanceOverall(
    val attempted: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int? = null,
    val accuracy: Double,
    val label: String,
    val comment: String,
    val averageTimeMs: Long? = null,
    val targetPaceMs: Long? = null,
    val speedLabel: String? = null
)

@Serializable
data class StudentPerformanceItem(
    val subjectId: String,
    val subjectName: String,
    val attempted: Int,
    val correct: Int,
    val wrong: Int,
    val skipped: Int? = null,
    val accuracy: Double,
    val label: String,
    val comment: String,
    val averageTimeMs: Long? = null,
    val targetPaceMs: Long? = null,
    val speedLabel: String? = null
)

@Serializable
data class StudentPerformanceInsight(
    val subjectId: String,
    val attemptId: String,
    val questionId: String,
    val descriptor: String,
    val given: String,
    val find: String,
    val commonTrap: String? = null,
    val resultType: String,
    val assessmentMode: String,
    val createdAt: String
)

@Serializable
data class StudentPerformanceResponse(
    val overall: StudentPerformanceOverall,
    val items: List<StudentPerformanceItem> = emptyList(),
    val insights: List<StudentPerformanceInsight> = emptyList(),
    val isUpdatingLatestPerformance: Boolean = false,
    val lastProcessedAt: String? = null
)
