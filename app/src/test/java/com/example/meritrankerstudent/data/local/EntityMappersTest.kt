package com.example.meritrankerstudent.data.local

import com.example.meritrankerstudent.data.model.*
import org.junit.Assert.*
import org.junit.Test

class EntityMappersTest {

    @Test
    fun userProfileMapping_preservesAllFields() {
        val original = UserProfile(
            userId = "user_123",
            email = "student@example.com",
            name = "John Doe",
            preparing = "SSC CGL",
            examProfileId = "SSC_CGL#TIER_1",
            examStage = "TIER_1",
            additionalExams = listOf("CHSL", "CPO"),
            examTags = listOf("QUANT", "REASONING"),
            dateOfBirth = "2000-01-01",
            language = "ENGLISH",
            profileCompleted = true,
            onboardingStep = "COMPLETED",
            role = "student"
        )

        val entity = EntityMappers.userProfileToEntity(original)
        assertEquals("user_123", entity.userId)
        assertEquals("SSC_CGL#TIER_1", entity.examProfileId)

        val mappedBack = EntityMappers.entityToUserProfile(entity)
        assertEquals(original.userId, mappedBack.userId)
        assertEquals(original.email, mappedBack.email)
        assertEquals(original.name, mappedBack.name)
        assertEquals(original.preparing, mappedBack.preparing)
        assertEquals(original.examProfileId, mappedBack.examProfileId)
        assertEquals(original.additionalExams, mappedBack.additionalExams)
        assertEquals(original.examTags, mappedBack.examTags)
        assertEquals(original.profileCompleted, mappedBack.profileCompleted)
    }

    @Test
    fun examProfileMapping_preservesSectionsAndCutoffs() {
        val original = ExamProfile(
            examProfileId = "SSC_CGL#TIER_1",
            examId = "SSC_CGL",
            examName = "SSC Combined Graduate Level",
            stage = "Tier 1",
            description = "Preliminary examination",
            sections = listOf(
                ExamSection(
                    sectionId = "SEC_1",
                    name = "General Intelligence",
                    subject = "Reasoning",
                    level = "MODERATE",
                    questionCount = 25,
                    marks = 50.0,
                    timeMinutes = 15,
                    questionStyles = listOf("MCQ"),
                    negativeMarks = 0.5,
                    marksPerQuestion = 2.0,
                    excludeTopics = listOf("Calculus")
                )
            ),
            totalQuestions = 100,
            totalMarks = 200.0,
            totalTimeMinutes = 60,
            previousCutoff = ExamPreviousCutoff(
                year = 2023,
                overall = 150.0,
                categories = listOf(ExamCategoryCutoff("UR", 150.0)),
                sections = listOf(ExamSectionCutoff("SEC_1", 35.0))
            ),
            active = true,
            effectiveYear = 2024
        )

        val entity = EntityMappers.examProfileToEntity(original)
        val mappedBack = EntityMappers.entityToExamProfile(entity)

        assertEquals(original.examProfileId, mappedBack.examProfileId)
        assertEquals(original.sections.size, mappedBack.sections.size)
        assertEquals("SEC_1", mappedBack.sections[0].sectionId)
        assertEquals(2.0, mappedBack.sections[0].marksPerQuestion ?: 0.0, 0.01)
        assertEquals(150.0, mappedBack.previousCutoff?.overall ?: 0.0, 0.01)
    }

    @Test
    fun practiceActivityMapping_preservesStatusAndScores() {
        val original = PracticeActivitySummary(
            activityId = "act_123",
            title = "Quant Practice Set 1",
            activityType = PracticeActivityType.QUIZ,
            language = "en",
            subject = "Quantitative Aptitude",
            topic = "Percentages",
            exams = listOf("SSC_CGL"),
            questionCount = 10,
            durationMinutes = 15,
            hasResumableAttempt = true,
            resumableAttemptId = "att_456",
            playable = true,
            generationStatus = "READY",
            readyCount = 10,
            progressPercent = 100,
            errorCode = null,
            latestAttemptId = "att_456",
            latestAttemptStatus = "IN_PROGRESS",
            latestAttemptScore = 18.0,
            latestAttemptMaximumScore = 20.0
        )

        val entity = EntityMappers.practiceActivityToEntity(original, "user_123")
        assertEquals("user_123", entity.ownerUserId)
        assertEquals("READY", entity.generationStatus)

        val mappedBack = EntityMappers.entityToPracticeActivity(entity)
        assertEquals(original.activityId, mappedBack.activityId)
        assertEquals(original.hasResumableAttempt, mappedBack.hasResumableAttempt)
        assertEquals(original.latestAttemptScore, mappedBack.latestAttemptScore)
    }

    @Test
    fun studentPerformanceMapping_preservesInsightsAndOverall() {
        val original = StudentPerformanceResponse(
            overall = StudentPerformanceOverall(
                attempted = 50,
                correct = 40,
                wrong = 10,
                skipped = 0,
                accuracy = 80.0,
                label = "Strong",
                comment = "Excellent accuracy.",
                averageTimeMs = 35000L,
                targetPaceMs = 30000L,
                speedLabel = "On Track"
            ),
            items = listOf(
                StudentPerformanceItem(
                    subjectId = "QUANT",
                    subjectName = "Quantitative Aptitude",
                    attempted = 25,
                    correct = 20,
                    wrong = 5,
                    accuracy = 80.0,
                    label = "Strong",
                    comment = "Strong"
                )
            ),
            insights = listOf(
                StudentPerformanceInsight(
                    subjectId = "QUANT",
                    attemptId = "att_1",
                    questionId = "q_1",
                    descriptor = "Base Value Error",
                    given = "A & B",
                    find = "Percentage",
                    commonTrap = "Wrong denominator",
                    resultType = "WRONG",
                    assessmentMode = "PRACTICE",
                    createdAt = "2026-08-19T00:00:00Z"
                )
            ),
            isUpdatingLatestPerformance = true,
            lastProcessedAt = "2026-08-19T00:01:00Z"
        )

        val entity = EntityMappers.studentPerformanceToEntity(original, "user_123", "SSC_CGL#TIER_1", GetStudentPerformanceView.PRACTICE, null)
        assertEquals("user_123", entity.ownerUserId)
        assertEquals("ALL", entity.subjectId)
        assertTrue(entity.isUpdatingLatestPerformance)

        val mappedBack = EntityMappers.entityToStudentPerformance(entity)
        assertEquals(80.0, mappedBack.overall.accuracy, 0.01)
        assertEquals("Strong", mappedBack.overall.label)
        assertEquals(1, mappedBack.items.size)
        assertEquals(1, mappedBack.insights.size)
        assertEquals("Wrong denominator", mappedBack.insights[0].commonTrap)
        assertTrue(mappedBack.isUpdatingLatestPerformance)
    }
}
