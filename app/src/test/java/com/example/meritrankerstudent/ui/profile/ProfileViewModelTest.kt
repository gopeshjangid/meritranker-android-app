package com.example.meritrankerstudent.ui.profile

import com.example.meritrankerstudent.data.model.UserProfile
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test

class ProfileViewModelTest {

    private lateinit var fakeAuthRepo: PhaseOneFakeAuthRepository
    private lateinit var fakeUserProfileRepo: PhaseOneFakeUserProfileRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeAuthRepo = PhaseOneFakeAuthRepository()
        fakeUserProfileRepo = PhaseOneFakeUserProfileRepository()
    }

    @Test
    fun loadProfile_onSuccess_transitionsToSuccessState() = runTest {
        val viewModel = ProfileViewModel(fakeAuthRepo, fakeUserProfileRepo)
        
        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        assertEquals("Gopesh Jangid", (state as ProfileUiState.Success).profile.name)
    }

    @Test
    fun saveProfile_whenNameEmpty_emitsNameError() = runTest {
        val viewModel = ProfileViewModel(fakeAuthRepo, fakeUserProfileRepo)
        viewModel.enterEditMode()
        viewModel.onDraftNameChanged("")
        viewModel.saveProfile()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        assertEquals("Name cannot be empty", (state as ProfileUiState.Success).nameError)
    }

    @Test
    fun saveProfile_onSuccess_persistsChangesAndReturnsToViewMode() = runTest {
        val viewModel = ProfileViewModel(fakeAuthRepo, fakeUserProfileRepo)
        viewModel.enterEditMode()
        viewModel.onDraftNameChanged("John Updated")
        viewModel.onPrimaryExamChanged("RRB NTPC")
        viewModel.saveProfile()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        val successState = state as ProfileUiState.Success
        assertEquals("John Updated", successState.profile.name)
        assertEquals("RRB NTPC", successState.profile.preparing)
        assertTrue(successState.saveSuccess)
    }

    @Test
    fun saveProfile_withExamProfile_persistsAuthoritativeExamProfileId() = runTest {
        val viewModel = ProfileViewModel(fakeAuthRepo, fakeUserProfileRepo)
        viewModel.enterEditMode()
        val examProfile = com.example.meritrankerstudent.data.model.ExamProfile(
            examProfileId = "SSC_CHSL_TIER_1_2024",
            examId = "SSC_CHSL",
            examName = "SSC CHSL",
            stage = "Tier 1",
            description = "Combined Higher Secondary Level",
            sections = emptyList(),
            totalQuestions = 100,
            totalMarks = 200.0,
            totalTimeMinutes = 60,
            active = true
        )
        viewModel.onPrimaryExamProfileChanged(examProfile)
        viewModel.saveProfile()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        val successState = state as ProfileUiState.Success
        assertEquals("SSC CHSL", successState.profile.preparing)
        assertEquals("SSC_CHSL_TIER_1_2024", successState.profile.examProfileId)
        assertEquals("Tier 1", successState.profile.examStage)
    }
}
