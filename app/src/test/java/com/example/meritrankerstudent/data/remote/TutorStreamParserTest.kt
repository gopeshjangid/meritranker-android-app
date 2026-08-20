package com.example.meritrankerstudent.data.remote

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class TutorStreamParserTest {

    @Test
    fun parseChunkEvent_extractsContentAndRequestId() {
        val sseLine = """{"type":"chunk","request_id":"req-101","content":"**Answer:** Percentage calculation."}"""
        val json = JSONObject(sseLine)

        assertEquals("chunk", json.getString("type"))
        assertEquals("req-101", json.getString("request_id"))
        assertEquals("**Answer:** Percentage calculation.", json.getString("content"))
    }

    @Test
    fun parseCompletionEvent_extractsFinalAnswer() {
        val sseLine = """{"type":"complete","request_id":"req-101","stage":"complete","response":{"answer":"Final answer text."}}"""
        val json = JSONObject(sseLine)

        assertEquals("complete", json.getString("type"))
        val response = json.getJSONObject("response")
        assertEquals("Final answer text.", response.getString("answer"))
    }

    @Test
    fun parseErrorEvent_extractsValidationMessage() {
        val errorLine = """{"success": false, "answer": "Validation error: query is required."}"""
        val json = JSONObject(errorLine)

        assertFalse(json.getBoolean("success"))
        assertEquals("Validation error: query is required.", json.getString("answer"))
    }
}
