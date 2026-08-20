package com.example.meritrankerstudent.observability

import org.junit.Assert.*
import org.junit.Test

class PiiSanitizerTest {

    @Test
    fun isKeySafe_blocksPiiKeys() {
        assertFalse(PiiSanitizer.isKeySafe("email"))
        assertFalse(PiiSanitizer.isKeySafe("password"))
        assertFalse(PiiSanitizer.isKeySafe("token"))
        assertFalse(PiiSanitizer.isKeySafe("auth_token"))
        assertFalse(PiiSanitizer.isKeySafe("jwt"))
        assertFalse(PiiSanitizer.isKeySafe("prompt"))
        assertFalse(PiiSanitizer.isKeySafe("question"))
        assertFalse(PiiSanitizer.isKeySafe("question_text"))
        assertFalse(PiiSanitizer.isKeySafe("answer"))
        assertFalse(PiiSanitizer.isKeySafe("ai_answer"))
        assertFalse(PiiSanitizer.isKeySafe("transcript"))
        assertFalse(PiiSanitizer.isKeySafe("audio"))
        assertFalse(PiiSanitizer.isKeySafe("image"))
        assertFalse(PiiSanitizer.isKeySafe("ocr"))
        assertFalse(PiiSanitizer.isKeySafe("dob"))
        assertFalse(PiiSanitizer.isKeySafe("phone"))
        assertFalse(PiiSanitizer.isKeySafe("user_id"))
    }

    @Test
    fun isKeySafe_allowsNonPiiKeys() {
        assertTrue(PiiSanitizer.isKeySafe("exam_profile_id"))
        assertTrue(PiiSanitizer.isKeySafe("stage"))
        assertTrue(PiiSanitizer.isKeySafe("study_language"))
        assertTrue(PiiSanitizer.isKeySafe("practice_type"))
        assertTrue(PiiSanitizer.isKeySafe("latency_bucket"))
        assertTrue(PiiSanitizer.isKeySafe("duration_bucket"))
        assertTrue(PiiSanitizer.isKeySafe("error_category"))
        assertTrue(PiiSanitizer.isKeySafe("operation"))
        assertTrue(PiiSanitizer.isKeySafe("status_bucket"))
    }

    @Test
    fun sanitizeValue_redactsEmailPatterns() {
        val email = "student123@example.com"
        assertEquals("[REDACTED_EMAIL]", PiiSanitizer.sanitizeValue("safe_field", email))
    }

    @Test
    fun sanitizeValue_redactsJwtPatterns() {
        val fakeJwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"
        assertEquals("[REDACTED_TOKEN]", PiiSanitizer.sanitizeValue("safe_field", fakeJwt))
    }

    @Test
    fun sanitizeMap_filtersOutUnsafeKeysAndSanitizesValues() {
        val input = mapOf(
            "email" to "test@example.com",
            "prompt" to "What is the capital of France?",
            "exam_profile_id" to "RRB_NTPC_CBT_1_2024",
            "is_voice" to true,
            "latency_bucket" to "lt_1s"
        )
        val clean = PiiSanitizer.sanitizeMap(input)

        assertFalse(clean.containsKey("email"))
        assertFalse(clean.containsKey("prompt"))
        assertEquals("RRB_NTPC_CBT_1_2024", clean["exam_profile_id"])
        assertEquals(true, clean["is_voice"])
        assertEquals("lt_1s", clean["latency_bucket"])
    }
}
