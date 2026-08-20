package com.example.meritrankerstudent.data.local

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class FreshnessPolicyTest {

    @Test
    fun evaluate_returnsMissing_whenNullOrZero() {
        assertEquals(FreshnessStatus.MISSING, FreshnessPolicy.evaluate(null, 1000L))
        assertEquals(FreshnessStatus.MISSING, FreshnessPolicy.evaluate(0L, 1000L))
        assertEquals(FreshnessStatus.MISSING, FreshnessPolicy.evaluate(-1L, 1000L))
    }

    @Test
    fun evaluate_returnsFresh_whenWithinTtl() {
        val now = System.currentTimeMillis()
        val recent = now - 5000L
        assertEquals(FreshnessStatus.FRESH, FreshnessPolicy.evaluate(recent, 60000L))
    }

    @Test
    fun evaluate_returnsStaleButUsable_whenPastTtl() {
        val now = System.currentTimeMillis()
        val old = now - TimeUnit.HOURS.toMillis(2)
        assertEquals(FreshnessStatus.STALE_BUT_USABLE, FreshnessPolicy.evaluate(old, TimeUnit.HOURS.toMillis(1)))
    }

    @Test
    fun domainTtlValues_areProperlyConfigured() {
        assertTrue(FreshnessPolicy.EXAM_PROFILE_TTL_MS >= TimeUnit.HOURS.toMillis(12))
        assertTrue(FreshnessPolicy.USER_PROFILE_TTL_MS >= TimeUnit.MINUTES.toMillis(30))
        assertTrue(FreshnessPolicy.PRACTICE_CATALOGUE_TTL_MS <= TimeUnit.MINUTES.toMillis(10))
        assertTrue(FreshnessPolicy.STUDENT_PERFORMANCE_TTL_MS <= TimeUnit.MINUTES.toMillis(5))
    }
}
