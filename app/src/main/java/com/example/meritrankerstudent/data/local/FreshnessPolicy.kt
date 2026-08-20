package com.example.meritrankerstudent.data.local

import java.util.concurrent.TimeUnit

enum class FreshnessStatus {
    FRESH,
    STALE_BUT_USABLE,
    MISSING
}

object FreshnessPolicy {
    // Domain-specific Stale-While-Revalidate TTL durations
    val EXAM_PROFILE_TTL_MS = TimeUnit.HOURS.toMillis(24)      // Master exam syllabus changes rarely
    val USER_PROFILE_TTL_MS = TimeUnit.HOURS.toMillis(1)       // User profile & exam target
    val PRACTICE_CATALOGUE_TTL_MS = TimeUnit.MINUTES.toMillis(5) // Activity list & statuses
    val PRACTICE_QUESTIONS_TTL_MS = TimeUnit.HOURS.toMillis(2) // Playable ready question payloads
    val STUDENT_PERFORMANCE_TTL_MS = TimeUnit.MINUTES.toMillis(2) // Evolving student performance metrics
    val CONVERSATION_SESSIONS_TTL_MS = TimeUnit.MINUTES.toMillis(10) // Chat history list

    fun evaluate(lastFetchedAt: Long?, ttlMs: Long): FreshnessStatus {
        if (lastFetchedAt == null || lastFetchedAt <= 0L) {
            return FreshnessStatus.MISSING
        }
        val age = System.currentTimeMillis() - lastFetchedAt
        return if (age < ttlMs) {
            FreshnessStatus.FRESH
        } else {
            FreshnessStatus.STALE_BUT_USABLE
        }
    }
}
