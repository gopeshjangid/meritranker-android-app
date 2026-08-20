package com.example.meritrankerstudent.ui

import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.UserProfile
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.ExamProfileRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import com.example.meritrankerstudent.ui.auth.AuthViewModel
import com.example.meritrankerstudent.ui.auth.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock

@OptIn(ExperimentalCoroutinesApi::class)
class AuthAccountDeletionTest {

    private val testDispatcher = StandardTestDispatcher()

    class TestAuthRepository(var deleteSuccess: Boolean = true) : AuthRepository {
        var deleteAccountCalled = false
        override suspend fun isUserSignedIn(): Boolean = true
        override suspend fun getCurrentUserId(): String? = "student_test_1"
        override suspend fun fetchUserEmail(): String? = "student@test.com"
        override suspend fun getAccessToken(): Result<String> = Result.success("test_token")
        override suspend fun signInWithGoogle(activity: android.app.Activity): com.amplifyframework.auth.result.AuthSignInResult = mock()
        override suspend fun signOut() {}
        override suspend fun deleteAccount(): Result<Unit> {
            deleteAccountCalled = true
            return if (deleteSuccess) Result.success(Unit) else Result.failure(Exception("Cognito deletion failed"))
        }
    }

    class TestUserProfileRepository : UserProfileRepository {
        var cacheCleared = false
        override fun observeUserProfile(userId: String): Flow<UserProfile?> = flowOf(null)
        override suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean): UserProfile =
            UserProfile(userId = userId, name = "Test Student", preparing = "SSC CGL")
        override suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile =
            UserProfile(userId = userId, email = email, name = name)
        override suspend fun updateUserProfile(
            userId: String,
            name: String,
            preparing: String,
            examProfileId: String?,
            examStage: String?,
            additionalExams: List<String>,
            dateOfBirth: String?,
            language: String?
        ): UserProfile = UserProfile(userId = userId, name = name, preparing = preparing)
        override suspend fun deleteUserProfile(userId: String): Result<Unit> = Result.success(Unit)
        override fun clearCache() { cacheCleared = true }
    }

    class TestExamProfileRepository : ExamProfileRepository {
        var cacheCleared = false
        override fun observeExamProfile(examProfileId: String): Flow<ExamProfile?> = flowOf(null)
        override fun observeAllActiveExamProfiles(): Flow<List<ExamProfile>> = flowOf(emptyList())
        override suspend fun getExamProfile(examProfileId: String, forceRefresh: Boolean): ExamProfile? = null
        override suspend fun fetchExamProfileForUser(preparing: String, stage: String?): ExamProfile? = null
        override suspend fun listActiveProfilesForExam(examId: String): List<ExamProfile> = emptyList()
        override suspend fun listAllActiveExamProfiles(forceRefresh: Boolean): List<ExamProfile> = emptyList()
        override fun clearCache() { cacheCleared = true }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testDeleteAccount_success_clearsSessionAndCache() = runTest(testDispatcher) {
        val authRepo = TestAuthRepository(deleteSuccess = true)
        val userRepo = TestUserProfileRepository()
        val examRepo = TestExamProfileRepository()

        val viewModel = AuthViewModel(
            authRepository = authRepo,
            userProfileRepository = userRepo,
            examProfileRepository = examRepo,
            localProfileStore = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        var errorCallbackMsg: String? = null
        viewModel.deleteAccount { errorCallbackMsg = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(authRepo.deleteAccountCalled)
        assertTrue(userRepo.cacheCleared)
        assertTrue(examRepo.cacheCleared)
        assertNull(errorCallbackMsg)
        assertTrue(viewModel.sessionState.value is SessionState.SignedOut)
    }

    @Test
    fun testDeleteAccount_failure_retainsStateAndReportsError() = runTest(testDispatcher) {
        val authRepo = TestAuthRepository(deleteSuccess = false)
        val userRepo = TestUserProfileRepository()
        val examRepo = TestExamProfileRepository()

        val viewModel = AuthViewModel(
            authRepository = authRepo,
            userProfileRepository = userRepo,
            examProfileRepository = examRepo,
            localProfileStore = null
        )
        testDispatcher.scheduler.advanceUntilIdle()

        var errorCallbackMsg: String? = null
        viewModel.deleteAccount { errorCallbackMsg = it }
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(authRepo.deleteAccountCalled)
        assertFalse(userRepo.cacheCleared)
        assertNotNull(errorCallbackMsg)
        assertTrue(errorCallbackMsg!!.contains("Cognito deletion failed") || errorCallbackMsg!!.contains("Failed to delete"))
    }
}
