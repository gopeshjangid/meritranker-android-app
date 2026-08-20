package com.example.meritrankerstudent.data.remote

import com.example.meritrankerstudent.data.model.GetStudentPerformanceView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StudentPerformanceGraphQLClientTest {

    @Test
    fun parseResponse_parsesFullPracticeResponseCorrectly() {
        val sampleJson = JSONObject().apply {
            put(
                "getStudentPerformance",
                JSONObject().apply {
                    put(
                        "overall",
                        JSONObject().apply {
                            put("attempted", 45)
                            put("correct", 36)
                            put("wrong", 9)
                            put("accuracy", 80.0)
                            put("label", "Strong")
                            put("comment", "Strong accuracy.")
                        }
                    )
                    put(
                        "items",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("subjectId", "QUANTITATIVE_APTITUDE")
                                    put("subjectName", "Quantitative Aptitude")
                                    put("attempted", 25)
                                    put("correct", 20)
                                    put("wrong", 5)
                                    put("accuracy", 80.0)
                                    put("label", "Strong")
                                    put("comment", "Strong accuracy.")
                                }
                            )
                        }
                    )
                    put(
                        "insights",
                        JSONArray().apply {
                            put(
                                JSONObject().apply {
                                    put("subjectId", "QUANTITATIVE_APTITUDE")
                                    put("attemptId", "att_123")
                                    put("questionId", "q_456")
                                    put("descriptor", "Percentage Increase vs Base Value")
                                    put("given", "Base value and final amount")
                                    put("find", "Percentage change")
                                    put("commonTrap", "Dividing by the final value instead of base value")
                                    put("resultType", "WRONG")
                                    put("assessmentMode", "PRACTICE")
                                    put("createdAt", "2026-08-18T12:00:00Z")
                                }
                            )
                        }
                    )
                    put("isUpdatingLatestPerformance", true)
                    put("lastProcessedAt", "2026-08-18T12:05:00Z")
                }
            )
        }

        val parsed = StudentPerformanceGraphQLClient.parseResponse(sampleJson)

        assertEquals(45, parsed.overall.attempted)
        assertEquals(36, parsed.overall.correct)
        assertEquals(9, parsed.overall.wrong)
        assertEquals(80.0, parsed.overall.accuracy, 0.01)
        assertEquals("Strong", parsed.overall.label)
        assertEquals("Strong accuracy.", parsed.overall.comment)
        assertNull(parsed.overall.averageTimeMs)

        assertEquals(1, parsed.items.size)
        val item = parsed.items[0]
        assertEquals("QUANTITATIVE_APTITUDE", item.subjectId)
        assertEquals("Quantitative Aptitude", item.subjectName)
        assertEquals(25, item.attempted)
        assertEquals(80.0, item.accuracy, 0.01)

        assertEquals(1, parsed.insights.size)
        val insight = parsed.insights[0]
        assertEquals("Percentage Increase vs Base Value", insight.descriptor)
        assertEquals("Dividing by the final value instead of base value", insight.commonTrap)
        assertEquals("WRONG", insight.resultType)

        assertTrue(parsed.isUpdatingLatestPerformance)
        assertEquals("2026-08-18T12:05:00Z", parsed.lastProcessedAt)
    }

    @Test
    fun parseResponse_parsesRealExamResponseWithPacingCorrectly() {
        val sampleJson = JSONObject().apply {
            put(
                "getStudentPerformance",
                JSONObject().apply {
                    put(
                        "overall",
                        JSONObject().apply {
                            put("attempted", 100)
                            put("correct", 75)
                            put("wrong", 20)
                            put("skipped", 5)
                            put("accuracy", 75.0)
                            put("label", "Good")
                            put("comment", "Good accuracy and speed.")
                            put("averageTimeMs", 42000L)
                            put("targetPaceMs", 36000L)
                            put("speedLabel", "On Track")
                        }
                    )
                    put("items", JSONArray())
                    put("insights", JSONArray())
                    put("isUpdatingLatestPerformance", false)
                }
            )
        }

        val parsed = StudentPerformanceGraphQLClient.parseResponse(sampleJson)

        assertEquals(100, parsed.overall.attempted)
        assertEquals(5, parsed.overall.skipped)
        assertEquals(42000L, parsed.overall.averageTimeMs)
        assertEquals(36000L, parsed.overall.targetPaceMs)
        assertEquals("On Track", parsed.overall.speedLabel)
        assertFalse(parsed.isUpdatingLatestPerformance)
    }

    @Test
    fun queryConstants_containExactGraphQLFields() {
        val query = StudentPerformanceGraphQLClient.GET_STUDENT_PERFORMANCE_QUERY
        assertTrue(query.contains("getStudentPerformance("))
        assertTrue(query.contains("examProfileId: \$examProfileId"))
        assertTrue(query.contains("view: \$view"))
        assertTrue(query.contains("subjectId: \$subjectId"))
        assertTrue(query.contains("isUpdatingLatestPerformance"))
        assertTrue(query.contains("lastProcessedAt"))
        assertTrue(query.contains("commonTrap"))
        assertTrue(query.contains("descriptor"))
    }
}
