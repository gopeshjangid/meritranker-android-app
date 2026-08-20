package com.example.meritrankerstudent.ui.profile

import android.app.Activity
import com.amplifyframework.auth.result.AuthSignInResult
import com.example.meritrankerstudent.data.model.UserProfile
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelPhaseOneTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var fakeUserProfileRepository: PhaseOneFakeUserProfileRepository
    private lateinit var fakeAuthRepository: PhaseOneFakeAuthRepository
    private lateinit var viewModel: ProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        fakeUserProfileRepository = PhaseOneFakeUserProfileRepository()
        fakeAuthRepository = PhaseOneFakeAuthRepository()
        viewModel = ProfileViewModel(fakeAuthRepository, fakeUserProfileRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun loadProfile_loadsSuccessState_andDefaultsToViewMode() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state is ProfileUiState.Success)
        val successState = state as ProfileUiState.Success

        assertEquals("Gopesh Jangid", successState.profile.name)
        assertEquals("student@meritranker.com", successState.profile.email)
        assertEquals("SSC CGL", successState.profile.preparing)
        assertFalse(successState.isEditMode)
    }

    @Test
    fun enterEditMode_populatesDraftFields() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.enterEditMode()

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertTrue(state.isEditMode)
        assertEquals("Gopesh Jangid", state.draftName)
        assertEquals("SSC CGL", state.draftPrimaryExam)
        assertEquals(listOf("RRB NTPC", "SBI PO"), state.draftAdditionalExams)
        assertEquals("2000-08-15", state.draftDateOfBirth)
    }

    @Test
    fun cancelEditMode_restoresOriginalProfileState() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.enterEditMode()
        viewModel.onDraftNameChanged("Modified Name")
        viewModel.onPrimaryExamChanged("SBI PO")

        viewModel.cancelEditMode()

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertFalse(state.isEditMode)
        assertEquals("Gopesh Jangid", state.draftName)
        assertEquals("SSC CGL", state.draftPrimaryExam)
    }

    @Test
    fun primaryExamSelection_preventsDuplicateInAdditionalExams() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.enterEditMode()

        viewModel.onPrimaryExamChanged("RRB NTPC")
        val state = viewModel.uiState.value as ProfileUiState.Success

        assertEquals("RRB NTPC", state.draftPrimaryExam)
        assertFalse(state.draftAdditionalExams.contains("RRB NTPC"))
    }

    @Test
    fun saveProfile_withValidFields_persistsChangesAndReturnsToViewMode() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.enterEditMode()

        viewModel.onDraftNameChanged("Gopesh New Name")
        viewModel.onPrimaryExamChanged("RRB NTPC")
        viewModel.onToggleAdditionalExam("SBI PO")
        viewModel.onDateOfBirthChanged("1998-05-20")

        viewModel.saveProfile()
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertFalse(state.isEditMode) // Returns to View Mode
        assertTrue(state.saveSuccess)
        assertEquals("Gopesh New Name", state.profile.name)
        assertEquals("RRB NTPC", state.profile.preparing)
        assertEquals("1998-05-20", state.profile.dateOfBirth)
    }

    @Test
    fun saveProfile_withEmptyName_showsErrorAndStaysInEditMode() = runTest(testDispatcher) {
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.enterEditMode()

        viewModel.onDraftNameChanged("")
        viewModel.saveProfile()

        val state = viewModel.uiState.value as ProfileUiState.Success
        assertTrue(state.isEditMode)
        assertNotNull(state.nameError)
        assertEquals("Name cannot be empty", state.nameError)
    }
}

class PhaseOneFakeUserProfileRepository : UserProfileRepository {
    private var profile = UserProfile(
        userId = "test_user_123",
        email = "student@meritranker.com",
        name = "Gopesh Jangid",
        preparing = "SSC CGL",
        additionalExams = listOf("RRB NTPC", "SBI PO"),
        dateOfBirth = "2000-08-15",
        language = "english",
        profileCompleted = true,
        role = "STUDENT"
    )

    override fun clearCache() {}

    override fun observeUserProfile(userId: String): kotlinx.coroutines.flow.Flow<UserProfile?> = kotlinx.coroutines.flow.flowOf(profile)

    override suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean): UserProfile? = profile

    override suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile = profile

    override suspend fun updateUserProfile(
        userId: String,
        name: String,
        preparing: String,
        examProfileId: String?,
        examStage: String?,
        additionalExams: List<String>,
        dateOfBirth: String?,
        language: String?
    ): UserProfile {
        profile = profile.copy(
            name = name,
            preparing = preparing,
            examProfileId = examProfileId,
            examStage = examStage,
            additionalExams = additionalExams,
            dateOfBirth = dateOfBirth,
            language = language
        )
        return profile
    }

    override suspend fun deleteUserProfile(userId: String): Result<Unit> = Result.success(Unit)
}

class PhaseOneFakeAuthRepository : AuthRepository {
    override suspend fun isUserSignedIn(): Boolean = true
    override suspend fun getCurrentUserId(): String? = "test_user_123"
    override suspend fun fetchUserEmail(): String? = "student@meritranker.com"
    override suspend fun getAccessToken(): Result<String> = Result.success("mock_token")
    override suspend fun signInWithGoogle(activity: Activity): AuthSignInResult {
        throw NotImplementedError()
    }
    override suspend fun signOut() {}
    override suspend fun deleteAccount(): Result<Unit> = Result.success(Unit)
}
