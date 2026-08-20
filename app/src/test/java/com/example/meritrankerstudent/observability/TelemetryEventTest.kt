package com.example.meritrankerstudent.observability

import org.junit.Assert.*
import org.junit.Test

class TelemetryEventTest {

    @Test
    fun eventNames_areSnakeCaseAndControlled() {
        assertEquals("screen_view", TelemetryEvent.ScreenView(CanonicalScreen.LOGIN).eventName)
        assertEquals("login_started", TelemetryEvent.LoginStarted().eventName)
        assertEquals("login_succeeded", TelemetryEvent.LoginSucceeded().eventName)
        assertEquals("login_failed", TelemetryEvent.LoginFailed(errorCategory = ErrorCategory.AUTH).eventName)
        assertEquals("onboarding_completed", TelemetryEvent.OnboardingCompleted("RRB_NTPC_CBT_1_2024", "CBT 1", "ENGLISH").eventName)
        assertEquals("doubt_submitted", TelemetryEvent.DoubtSubmitted("CAT_MANAGEMENT_2024", false, false).eventName)
        assertEquals("doubt_first_response_received", TelemetryEvent.DoubtFirstResponseReceived("CAT_MANAGEMENT_2024", "lt_1s").eventName)
        assertEquals("doubt_completed", TelemetryEvent.DoubtCompleted("CAT_MANAGEMENT_2024", "3_5s", false).eventName)
        assertEquals("practice_started", TelemetryEvent.PracticeStarted("RRB_NTPC_CBT_1_2024", "quiz", "11_25").eventName)
        assertEquals("practice_completed", TelemetryEvent.PracticeCompleted("RRB_NTPC_CBT_1_2024", "quiz", "5_10s").eventName)
        assertEquals("backend_operation_failed", TelemetryEvent.BackendOperationFailed("doubt", "smart_tutor_stream", ErrorCategory.TIMEOUT, "unknown", true, "online").eventName)
    }

    @Test
    fun screenView_containsValidParameters() {
        val event = TelemetryEvent.ScreenView(CanonicalScreen.SMART_TUTOR)
        val params = event.params
        assertEquals("smart_tutor", params["screen_name"])
        assertEquals("SMART_TUTOR", params["screen_class"])
    }

    @Test
    fun telemetryEvents_containNoPii() {
        val event = TelemetryEvent.DoubtSubmitted(
            examProfileId = "SSC_CGL_TIER_1_2024",
            hasAttachment = true,
            isVoice = false
        )
        val params = event.params
        assertFalse(params.containsKey("prompt"))
        assertFalse(params.containsKey("query"))
        assertFalse(params.containsKey("question"))
        assertFalse(params.containsKey("answer"))
        assertFalse(params.containsKey("email"))
        assertEquals("SSC_CGL_TIER_1_2024", params["exam_profile_id"])
    }

    @Test
    fun noOpAnalyticsTracker_recordsEventsAndScreens() {
        val tracker = NoOpAnalyticsTracker()
        tracker.setScreen(CanonicalScreen.LOGIN)
        tracker.logEvent(TelemetryEvent.LoginStarted("google"))
        tracker.setUserProperty("study_language", "hindi")

        assertEquals(2, tracker.loggedEvents.size)
        assertEquals(CanonicalScreen.LOGIN, tracker.currentScreen)
        assertEquals("hindi", tracker.userProperties["study_language"])

        tracker.resetData()
        assertEquals(0, tracker.loggedEvents.size)
        assertNull(tracker.currentScreen)
        assertEquals(0, tracker.userProperties.size)
    }
}
