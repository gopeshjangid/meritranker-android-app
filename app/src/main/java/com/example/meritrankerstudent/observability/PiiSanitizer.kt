package com.example.meritrankerstudent.observability

object PiiSanitizer {

    private val BLOCKED_KEYS = setOf(
        "email",
        "name",
        "fullname",
        "password",
        "token",
        "auth_token",
        "authorization",
        "jwt",
        "id_token",
        "access_token",
        "refresh_token",
        "prompt",
        "query",
        "question",
        "question_text",
        "answer",
        "ai_answer",
        "response_text",
        "transcript",
        "audio",
        "image",
        "image_path",
        "image_uri",
        "ocr",
        "ocr_text",
        "dob",
        "date_of_birth",
        "phone",
        "phone_number",
        "address",
        "cognito_id",
        "user_id"
    )

    private val EMAIL_REGEX = Regex("[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}")
    private val JWT_REGEX = Regex("^[A-Za-z0-9-_=]+\\.[A-Za-z0-9-_=]+\\.?[A-Za-z0-9-_.+/=]*$")

    fun isKeySafe(key: String): Boolean {
        val normalized = key.lowercase().trim()
        return !BLOCKED_KEYS.contains(normalized) &&
                !normalized.contains("password") &&
                !normalized.contains("token") &&
                !normalized.contains("secret")
    }

    fun sanitizeValue(key: String, value: Any?): Any? {
        if (value == null) return null
        if (!isKeySafe(key)) return "[REDACTED_PII_KEY]"

        return when (value) {
            is Number, is Boolean -> value
            is String -> {
                val str = value.trim()
                if (EMAIL_REGEX.containsMatchIn(str)) {
                    "[REDACTED_EMAIL]"
                } else if (str.length > 50 && JWT_REGEX.matches(str)) {
                    "[REDACTED_TOKEN]"
                } else {
                    str.take(100)
                }
            }
            else -> value.toString().take(100)
        }
    }

    fun sanitizeMap(params: Map<String, Any?>): Map<String, Any> {
        val cleanMap = mutableMapOf<String, Any>()
        for ((k, v) in params) {
            if (!isKeySafe(k)) continue
            val cleanValue = sanitizeValue(k, v)
            if (cleanValue != null) {
                cleanMap[k] = cleanValue
            }
        }
        return cleanMap
    }
}
