package com.example.meritrankerstudent.data.coordinator

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmartTutorGlobalCoordinatorTest {

    private lateinit var testDispatcher: TestDispatcher
    private lateinit var coordinator: SmartTutorGlobalCoordinator

    @Before
    fun setUp() {
        testDispatcher = kotlinx.coroutines.test.StandardTestDispatcher()
        coordinator = SmartTutorGlobalCoordinator(
            context = null,
            practiceCoordinator = null,
            notificationManager = null,
            scope = CoroutineScope(testDispatcher)
        )
    }

    @Test
    fun normalTurnLifecycle_updatesBusyStateAndActiveCount() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()
        assertFalse(coordinator.isGlobalBusy.value)
        assertEquals(0, coordinator.totalActiveCount.value)

        // Start normal doubt turn
        coordinator.markNormalTurnStarted(conversationId = "conv_1", turnId = "turn_1")
        testScheduler.advanceUntilIdle()

        assertTrue(coordinator.isGlobalBusy.value)
        assertEquals(1, coordinator.totalActiveCount.value)
        assertEquals(1, coordinator.activeNormalTurns.value.size)

        // Complete normal doubt turn
        coordinator.markNormalTurnCompleted(conversationId = "conv_1", turnId = "turn_1", isSuccess = true)
        testScheduler.advanceUntilIdle()

        assertFalse(coordinator.isGlobalBusy.value)
        assertEquals(0, coordinator.totalActiveCount.value)
        assertEquals(0, coordinator.activeNormalTurns.value.size)
    }

    @Test
    fun multipleNormalTurns_aggregatesCorrectly() = runTest(testDispatcher) {
        testScheduler.advanceUntilIdle()

        coordinator.markNormalTurnStarted("conv_1", "turn_1")
        coordinator.markNormalTurnStarted("conv_1", "turn_2")
        testScheduler.advanceUntilIdle()

        assertTrue(coordinator.isGlobalBusy.value)
        assertEquals(2, coordinator.totalActiveCount.value)

        coordinator.markNormalTurnCompleted("conv_1", "turn_1", isSuccess = true)
        testScheduler.advanceUntilIdle()

        assertTrue(coordinator.isGlobalBusy.value)
        assertEquals(1, coordinator.totalActiveCount.value)

        coordinator.markNormalTurnCompleted("conv_1", "turn_2", isSuccess = true)
        testScheduler.advanceUntilIdle()

        assertFalse(coordinator.isGlobalBusy.value)
        assertEquals(0, coordinator.totalActiveCount.value)
    }

    @Test
    fun screenVisibilityTracking_contextualSuppressionLogic() {
        coordinator.setAppForeground(true)
        coordinator.updateScreenVisibility(tabName = "DOUBT", conversationId = "conv_1")

        // Active conversation visible in foreground -> returns true
        assertTrue(coordinator.isConversationVisible("conv_1"))

        // Other conversation -> returns false
        assertFalse(coordinator.isConversationVisible("conv_2"))

        // Navigated to Progress tab -> returns false
        coordinator.updateScreenVisibility(tabName = "PROGRESS", conversationId = "conv_1")
        assertFalse(coordinator.isConversationVisible("conv_1"))

        // App backgrounded -> returns false
        coordinator.updateScreenVisibility(tabName = "DOUBT", conversationId = "conv_1")
        coordinator.setAppForeground(false)
        assertFalse(coordinator.isConversationVisible("conv_1"))
    }
}
