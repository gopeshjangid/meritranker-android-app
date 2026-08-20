package com.example.meritrankerstudent.data.remote

import org.junit.Assert.*
import org.junit.Test

class TutorStreamClientPayloadTest {

    @Test
    fun buildJsonPayload_withImage_serializesCanonicalImageBase64AndMimeTypeOnly() {
        val payload = TutorRequestPayload(
            conversationId = "conv-123",
            turnId = "turn-456",
            userId = "student-789",
            query = "Explain this math problem",
            examProfileId = "ep-999",
            examId = "SSC_CGL",
            examStage = "Tier 1",
            language = "en",
            mode = "doubt_solver",
            stream = true,
            imageBase64 = "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==",
            imageMimeType = "image/jpeg"
        )

        val json = TutorStreamClient.buildJsonPayload(payload)

        // Verify required base fields
        assertEquals("conv-123", json.getString("conversation_id"))
        assertEquals("turn-456", json.getString("turn_id"))
        assertEquals("student-789", json.getString("user_id"))
        assertEquals("Explain this math problem", json.getString("query"))
        assertEquals("ep-999", json.getString("exam_profile_id"))
        assertEquals("SSC_CGL", json.getString("exam_id"))
        assertEquals("Tier 1", json.getString("exam_stage"))
        assertEquals("en", json.getString("language"))
        assertEquals("doubt_solver", json.getString("mode"))
        assertTrue(json.getBoolean("stream"))

        // Verify CANONICAL image fields
        assertTrue("Must contain canonical image_base64", json.has("image_base64"))
        assertEquals(payload.imageBase64, json.getString("image_base64"))
        assertTrue("Must contain canonical image_mime_type", json.has("image_mime_type"))
        assertEquals("image/jpeg", json.getString("image_mime_type"))

        // Regression assertion: duplicate/obsolete fields MUST NOT be present
        assertFalse("Must NOT contain duplicate image_data Data URI", json.has("image_data"))
        assertFalse("Must NOT contain duplicate raw_image", json.has("raw_image"))
    }

    @Test
    fun buildJsonPayload_withoutImage_doesNotContainImageFields() {
        val payload = TutorRequestPayload(
            conversationId = "conv-123",
            turnId = "turn-456",
            userId = "student-789",
            query = "What is the capital of India?",
            imageBase64 = null,
            imageMimeType = null
        )

        val json = TutorStreamClient.buildJsonPayload(payload)

        assertEquals("conv-123", json.getString("conversation_id"))
        assertEquals("What is the capital of India?", json.getString("query"))
        assertFalse("Should not contain image_base64", json.has("image_base64"))
        assertFalse("Should not contain image_mime_type", json.has("image_mime_type"))
        assertFalse("Should not contain image_data", json.has("image_data"))
    }

    @Test
    fun allowedActionRoutes_whitelistsKnownRoutesOnly() {
        assertTrue(TutorStreamClient.ALLOWED_ACTION_ROUTES.contains("QUIZ"))
        assertTrue(TutorStreamClient.ALLOWED_ACTION_ROUTES.contains("MOCK"))
        assertTrue(TutorStreamClient.ALLOWED_ACTION_ROUTES.contains("PYQ"))
        assertFalse(TutorStreamClient.ALLOWED_ACTION_ROUTES.contains("EXTERNAL_HTTP"))
        assertFalse(TutorStreamClient.ALLOWED_ACTION_ROUTES.contains("UNKNOWN_ROUTE"))
    }
}
