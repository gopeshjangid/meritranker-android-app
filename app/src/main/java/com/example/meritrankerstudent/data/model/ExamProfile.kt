package com.example.meritrankerstudent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class ExamSection(
    val sectionId: String,
    val name: String,
    val subject: String,
    val level: String? = null,
    val questionCount: Int,
    val marks: Double,
    val timeMinutes: Int? = null,
    val questionStyles: List<String> = emptyList(),
    val negativeMarks: Double? = null,
    val marksPerQuestion: Double? = null,
    val excludeTopics: List<String> = emptyList()
)

@Serializable
data class ExamCategoryCutoff(
    val category: String,
    val cutoff: Double
)

@Serializable
data class ExamSectionCutoff(
    val sectionId: String,
    val cutoff: Double
)

@Serializable
data class ExamPreviousCutoff(
    val year: Int,
    val overall: Double? = null,
    val categories: List<ExamCategoryCutoff> = emptyList(),
    val sections: List<ExamSectionCutoff> = emptyList()
)

@Serializable
data class ExamProfile(
    val examProfileId: String,
    val examId: String,
    val examName: String,
    val stage: String,
    val description: String,
    val sections: List<ExamSection>,
    val totalQuestions: Int,
    val totalMarks: Double,
    val totalTimeMinutes: Int,
    val previousCutoff: ExamPreviousCutoff? = null,
    val active: Boolean,
    val effectiveYear: Int? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null
)
