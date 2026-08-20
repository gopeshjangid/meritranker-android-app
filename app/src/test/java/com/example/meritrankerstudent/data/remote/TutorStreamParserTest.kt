package com.example.meritrankerstudent.data.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TutorStreamParserTest {

    @Test
    fun parseStatusEvent_extractsStageAndLabel() {
        val sseLine = """{"type":"status","stage":"retrieving","label":"Searching knowledge base...","request_id":"req-101"}"""
        val json = JSONObject(sseLine)

        assertEquals("status", json.getString("type"))
        assertEquals("retrieving", json.getString("stage"))
        assertEquals("Searching knowledge base...", json.getString("label"))
        assertEquals("req-101", json.getString("request_id"))
    }

    @Test
    fun parseChunkEvent_extractsContentAndRequestId() {
        val sseLine = """{"type":"chunk","request_id":"req-101","content":"**Answer:** Percentage calculation."}"""
        val json = JSONObject(sseLine)

        assertEquals("chunk", json.getString("type"))
        assertEquals("req-101", json.getString("request_id"))
        assertEquals("**Answer:** Percentage calculation.", json.getString("content"))
    }

    @Test
    fun parsePracticeGenerationStartedEvent_extractsData() {
        val sseLine = """{"type":"practice_generation_started","request_id":"req-202","data":{"responseType":"practice_generation","practiceTestId":"test_abc123","status":"GENERATING","title":"Percentage Basics Quiz","questionCount":5,"message":"Preparing your 5 questions..."}}"""
        val json = JSONObject(sseLine)

        assertEquals("practice_generation_started", json.getString("type"))
        val data = json.getJSONObject("data")
        assertEquals("practice_generation", data.getString("responseType"))
        assertEquals("test_abc123", data.getString("practiceTestId"))
        assertEquals("GENERATING", data.getString("status"))
        assertEquals("Percentage Basics Quiz", data.getString("title"))
        assertEquals(5, data.getInt("questionCount"))
    }

    @Test
    fun parseCompletionEvent_withPracticeTestId_extractsAllMetadata() {
        val sseLine = """{"type":"complete","request_id":"req-202","response":{"schema_version":"1.0","status":"READY","content":{"format":"markdown","value":"I have prepared 5 practice questions for you."},"responseType":"practice_generation","practiceTestId":"test_abc123","title":"Percentage Basics Quiz","questionCount":5,"action_route":"QUIZ","action_text":"Start Practice"}}"""
        val json = JSONObject(sseLine)

        assertEquals("complete", json.getString("type"))
        val response = json.getJSONObject("response")
        assertEquals("READY", response.getString("status"))
        assertEquals("practice_generation", response.getString("responseType"))
        assertEquals("test_abc123", response.getString("practiceTestId"))
        assertEquals("QUIZ", response.getString("action_route"))
        assertEquals("Start Practice", response.getString("action_text"))
        assertEquals(5, response.getInt("questionCount"))
    }

    @Test
    fun parseErrorEvent_withStructuredError() {
        val sseLine = """{"type":"error","error":{"code":"PRACTICE_GENERATION_FAILED","message":"Unable to generate practice questions.","retryable":true}}"""
        val json = JSONObject(sseLine)

        assertEquals("error", json.getString("type"))
        val error = json.getJSONObject("error")
        assertEquals("PRACTICE_GENERATION_FAILED", error.getString("code"))
        assertEquals("Unable to generate practice questions.", error.getString("message"))
        assertTrue(error.getBoolean("retryable"))
    }

    @Test
    fun parseErrorEvent_extractsValidationMessage() {
        val errorLine = """{"success": false, "answer": "Validation error: query is required."}"""
        val json = JSONObject(errorLine)

        assertFalse(json.getBoolean("success"))
        assertEquals("Validation error: query is required.", json.getString("answer"))
    }
}
