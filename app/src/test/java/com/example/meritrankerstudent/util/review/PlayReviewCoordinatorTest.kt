package com.example.meritrankerstudent.util.review

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*

class PlayReviewCoordinatorTest {

    private lateinit var mockContext: Context
    private lateinit var mockPrefs: SharedPreferences
    private lateinit var mockEditor: SharedPreferences.Editor
    private lateinit var inMemoryStorage: PlayReviewStorage
    private lateinit var fakeReviewManager: FakePlayReviewManager
    private lateinit var coordinator: PlayReviewCoordinator

    private val prefData = mutableMapOf<String, Any>()

    @Before
    fun setUp() {
        prefData.clear()

        mockContext = mock(Context::class.java)
        mockPrefs = mock(SharedPreferences::class.java)
        mockEditor = mock(SharedPreferences.Editor::class.java)

        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)

        `when`(mockPrefs.getLong(anyString(), anyLong())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val defaultVal = invocation.arguments[1] as Long
            prefData[key] as? Long ?: defaultVal
        }

        `when`(mockPrefs.getInt(anyString(), anyInt())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val defaultVal = invocation.arguments[1] as Int
            prefData[key] as? Int ?: defaultVal
        }

        `when`(mockPrefs.getBoolean(anyString(), anyBoolean())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val defaultVal = invocation.arguments[1] as Boolean
            prefData[key] as? Boolean ?: defaultVal
        }

        `when`(mockPrefs.edit()).thenReturn(mockEditor)

        `when`(mockEditor.putLong(anyString(), anyLong())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val value = invocation.arguments[1] as Long
            prefData[key] = value
            mockEditor
        }

        `when`(mockEditor.putInt(anyString(), anyInt())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val value = invocation.arguments[1] as Int
            prefData[key] = value
            mockEditor
        }

        `when`(mockEditor.putBoolean(anyString(), anyBoolean())).thenAnswer { invocation ->
            val key = invocation.arguments[0] as String
            val value = invocation.arguments[1] as Boolean
            prefData[key] = value
            mockEditor
        }

        `when`(mockEditor.clear()).thenAnswer {
            prefData.clear()
            mockEditor
        }

        inMemoryStorage = PlayReviewStorage(mockContext)
        fakeReviewManager = FakePlayReviewManager()
        coordinator = PlayReviewCoordinator(inMemoryStorage, fakeReviewManager)
    }

    private val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    @Test
    fun testDay1_NoReview() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)

        // Only 1 day elapsed
        val checkTime = now + (1 * ONE_DAY_MS)
        assertFalse("Day 1 must not be eligible for review", coordinator.isEligibleForReview(checkTime))
    }

    @Test
    fun testDay4_NoMeaningfulUsage_NoReview() {
        val now = 100_000_000L
        // Only recorded an outcome on day 0, but no additional sessions (< 3 sessions)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now) // 1 session

        val checkTime = now + (4 * ONE_DAY_MS)
        assertFalse("Day 4 with < 3 sessions must not be eligible", coordinator.isEligibleForReview(checkTime))
    }

    @Test
    fun testDay4_WithEngagementThreshold_IsEligible() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)

        // Day 2 session
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        // Day 3 session
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        // Day 4 check
        val checkTime = now + (4 * ONE_DAY_MS)
        assertTrue("Day 4 with 3 sessions and 1 outcome must be eligible", coordinator.isEligibleForReview(checkTime))
    }

    @Test
    fun testSuccessfulPracticeResult_ReviewRequestedOnce() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val checkTime = now + (4 * ONE_DAY_MS)
        val mockActivity = mock(Activity::class.java)

        var completed = false
        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, checkTime) {
            completed = it
        }

        assertTrue(completed)
        assertEquals(1, fakeReviewManager.launchCount)
        assertEquals(1, inMemoryStorage.reviewAttemptCount)
        assertEquals(checkTime, inMemoryStorage.lastReviewAttemptAt)
    }

    @Test
    fun testSameDaySubsequentPractice_NoAdditionalRequest() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val checkTime = now + (4 * ONE_DAY_MS)
        val mockActivity = mock(Activity::class.java)

        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, checkTime)
        assertEquals(1, fakeReviewManager.launchCount)

        // Subsequent practice on same day or next hour
        val laterSameDay = checkTime + (2 * 60 * 60 * 1000L)
        assertFalse("Subsequent practice on same day must not trigger review", coordinator.isEligibleForReview(laterSameDay))

        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, laterSameDay)
        // Count remains 1
        assertEquals(1, fakeReviewManager.launchCount)
    }

    @Test
    fun test14DaysLater_NoSecondPlayReviewAttempt() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val firstAttemptTime = now + (4 * ONE_DAY_MS)
        val mockActivity = mock(Activity::class.java)
        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, firstAttemptTime)

        // 14 days later
        val day14Later = firstAttemptTime + (14 * ONE_DAY_MS)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, day14Later)

        assertFalse("14 days later must NOT be eligible (requires 30-day cooldown)", coordinator.isEligibleForReview(day14Later))
    }

    @Test
    fun test30DaysLater_WithContinuedUsage_EligibleForSecondAttempt() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val firstAttemptTime = now + (4 * ONE_DAY_MS)
        val mockActivity = mock(Activity::class.java)
        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, firstAttemptTime)

        // 30 days later with continued usage (outcome #2)
        val day30Later = firstAttemptTime + (30 * ONE_DAY_MS)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, day30Later)

        assertTrue("30 days later with continued usage must be eligible for attempt #2", coordinator.isEligibleForReview(day30Later))

        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, day30Later)
        assertEquals(2, fakeReviewManager.launchCount)
        assertEquals(2, inMemoryStorage.reviewAttemptCount)
    }

    @Test
    fun testAfterAttempt2_NoMoreAutomaticPrompting() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val firstAttemptTime = now + (4 * ONE_DAY_MS)
        val mockActivity = mock(Activity::class.java)
        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, firstAttemptTime)

        val secondAttemptTime = firstAttemptTime + (30 * ONE_DAY_MS)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, secondAttemptTime)
        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, secondAttemptTime)

        assertEquals(2, inMemoryStorage.reviewAttemptCount)

        // 60 days later
        val day60Later = secondAttemptTime + (60 * ONE_DAY_MS)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, day60Later)

        assertFalse("After attempt #2, no more automatic review prompting allowed", coordinator.isEligibleForReview(day60Later))
    }

    @Test
    fun testMandatoryUpdatePending_SuppressesReview() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val checkTime = now + (4 * ONE_DAY_MS)
        assertTrue(coordinator.isEligibleForReview(checkTime))

        // Set mandatory update pending
        coordinator.setMandatoryUpdatePending(true)
        assertFalse("Mandatory update pending must strictly suppress review flow", coordinator.isEligibleForReview(checkTime))

        // After update clears
        coordinator.setMandatoryUpdatePending(false)
        assertTrue("After mandatory update clears, review eligibility is restored", coordinator.isEligibleForReview(checkTime))
    }

    @Test
    fun testReviewApiFailure_ContinuesCleanlyWithoutCrashing() {
        val now = 100_000_000L
        coordinator.recordAppSession(now)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED, now)
        coordinator.recordAppSession(now + (1 * ONE_DAY_MS))
        coordinator.recordAppSession(now + (2 * ONE_DAY_MS))

        val checkTime = now + (4 * ONE_DAY_MS)
        val mockActivity = mock(Activity::class.java)

        fakeReviewManager.shouldSucceed = false
        var completedSuccess = true
        coordinator.maybeRequestReview(mockActivity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED, checkTime) {
            completedSuccess = it
        }

        assertFalse("Failure callback received gracefully", completedSuccess)
        assertEquals("Attempt recorded even if API call fails", 1, inMemoryStorage.reviewAttemptCount)
    }
}
