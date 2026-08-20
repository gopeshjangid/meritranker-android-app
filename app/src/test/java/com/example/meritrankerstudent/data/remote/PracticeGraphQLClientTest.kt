package com.example.meritrankerstudent.data.remote

import com.example.meritrankerstudent.data.model.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PracticeGraphQLClientTest {

    @Test
    fun parseActivitySummary_extractsRequiredAndOptionalFields() {
        val json = JSONObject("""
            {
                "activityId": "act-101",
                "title": "Polity Daily Quiz",
                "activityType": "QUIZ",
                "language": "en",
                "subject": "Polity",
                "topic": "Fundamental Rights",
                "exams": ["SSC_CGL", "RRB_NTPC"],
                "questionCount": 10,
                "durationMinutes": 15,
                "hasResumableAttempt": true,
                "resumableAttemptId": "att-999",
                "playable": true,
                "latestAttemptScore": 8.0,
                "latestAttemptMaximumScore": 10.0
            }
        """.trimIndent())

        val summary = PracticeGraphQLClient.parseActivitySummary(json)
        assertEquals("act-101", summary.activityId)
        assertEquals("Polity Daily Quiz", summary.title)
        assertEquals(PracticeActivityType.QUIZ, summary.activityType)
        assertEquals("Polity", summary.subject)
        assertEquals(2, summary.exams.size)
        assertEquals(10, summary.questionCount)
        assertTrue(summary.hasResumableAttempt)
        assertEquals("att-999", summary.resumableAttemptId)
        assertTrue(summary.playable)
        assertEquals(8.0, summary.latestAttemptScore ?: 0.0, 0.001)
    }

    @Test
    fun parseQuestionPayload_parsesOptionsAndMarks() {
        val json = JSONObject("""
            {
                "questionId": "q-555",
                "position": 3,
                "question": "Which schedule contains list of languages?",
                "options": ["7th Schedule", "8th Schedule", "9th Schedule", "10th Schedule"],
                "marks": 2.0,
                "negativeMarks": 0.5,
                "language": "en"
            }
        """.trimIndent())

        val question = PracticeGraphQLClient.parseQuestionPayload(json)
        assertEquals("q-555", question.questionId)
        assertEquals(3, question.position)
        assertEquals(4, question.options.size)
        assertEquals("8th Schedule", question.options[1])
        assertEquals(2.0, question.marks, 0.001)
        assertEquals(0.5, question.negativeMarks, 0.001)
    }

    @Test
    fun parseAttemptPayload_parsesStatusAndPositions() {
        val json = JSONObject("""
            {
                "attemptId": "att-777",
                "activityId": "act-101",
                "activityType": "QUIZ",
                "status": "IN_PROGRESS",
                "startedAt": "2026-08-18T10:00:00Z",
                "currentQuestionPosition": 4
            }
        """.trimIndent())

        val attempt = PracticeGraphQLClient.parseAttemptPayload(json)
        assertEquals("att-777", attempt.attemptId)
        assertEquals(PracticeAttemptStatus.IN_PROGRESS, attempt.status)
        assertEquals(4, attempt.currentQuestionPosition)
    }

    @Test
    fun parseResponsePayload_extractsLockedAndReviewFlags() {
        val json = JSONObject("""
            {
                "questionId": "q-555",
                "selectedOption": "8th Schedule",
                "isAnswerLocked": true,
                "isMarkedForReview": false
            }
        """.trimIndent())

        val response = PracticeGraphQLClient.parseResponsePayload(json)
        assertEquals("q-555", response.questionId)
        assertEquals("8th Schedule", response.selectedOption)
        assertTrue(response.isAnswerLocked)
        assertFalse(response.isMarkedForReview)
    }

    @Test
    fun graphQLQueryConstants_containExpectedRootOperations() {
        assertTrue(PracticeGraphQLClient.START_PRACTICE_ATTEMPT_MUTATION.contains("startPracticeAttempt"))
        assertTrue(PracticeGraphQLClient.SAVE_PRACTICE_RESPONSE_MUTATION.contains("savePracticeResponse"))
        assertTrue(PracticeGraphQLClient.CHECK_QUIZ_ANSWER_MUTATION.contains("checkQuizAnswer"))
        assertTrue(PracticeGraphQLClient.SUBMIT_PRACTICE_ATTEMPT_MUTATION.contains("submitPracticeAttempt"))
        assertTrue(PracticeGraphQLClient.GET_PRACTICE_REVIEW_QUERY.contains("getPracticeReview"))
        assertTrue(PracticeGraphQLClient.ON_PRACTICE_GENERATION_PROGRESS_SUBSCRIPTION.contains("onPracticeGenerationProgress"))
    }

    @Test
    fun parseGenerationProgress_extractsProgressAndPlayability() {
        val json = JSONObject("""
            {
                "testId": "test-gen-888",
                "status": "READY",
                "totalQuestions": 5,
                "updatedAt": "2026-08-18T10:00:00Z",
                "meta": "{\"playable\":true,\"readyCount\":5,\"progressPercent\":100,\"errorCode\":null}"
            }
        """.trimIndent())

        val progress = PracticeGraphQLClient.parseGenerationProgress(json)
        assertEquals("test-gen-888", progress.testId)
        assertEquals(PracticeGenerationStatus.READY, progress.status)
        assertEquals(5, progress.totalQuestions)
        assertEquals(5, progress.readyCount)
        assertEquals(100, progress.progressPercent)
        assertTrue(progress.playable)
        assertNull(progress.errorCode)
    }

    @Test
    fun parseGenerationProgress_handlesStalledOrFailedMeta() {
        val json = JSONObject("""
            {
                "testId": "test-gen-999",
                "status": "FAILED",
                "totalQuestions": 5,
                "updatedAt": "2026-08-18T10:00:00Z",
                "meta": "{\"playable\":false,\"readyCount\":2,\"progressPercent\":40,\"errorCode\":\"PRACTICE_GENERATION_STALLED\"}"
            }
        """.trimIndent())

        val progress = PracticeGraphQLClient.parseGenerationProgress(json)
        assertEquals("test-gen-999", progress.testId)
        assertEquals(PracticeGenerationStatus.FAILED, progress.status)
        assertFalse(progress.playable)
        assertEquals("PRACTICE_GENERATION_STALLED", progress.errorCode)
    }
}
