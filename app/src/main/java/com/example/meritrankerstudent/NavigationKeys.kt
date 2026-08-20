package com.example.meritrankerstudent

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object Main : NavKey

@Serializable
data object ProfileSettings : NavKey

@Serializable
data object GuidedPracticeDetail : NavKey

@Serializable
data object QuizList : NavKey

@Serializable
data object MockList : NavKey

@Serializable
data object PyqList : NavKey

@Serializable
data object WrongQuestionsList : NavKey

@Serializable
data object ConversationHistory : NavKey

@Serializable
data class QuestionPlayer(val mode: String, val id: String) : NavKey

@Serializable
data class ResultFeedback(val score: Int, val total: Int, val mode: String, val id: String) : NavKey

@Serializable
data class PracticeReview(val attemptId: String, val activityId: String) : NavKey

@Serializable
data class SubjectProgress(
    val subjectId: String,
    val subjectName: String,
    val examProfileId: String,
    val view: String
) : NavKey
