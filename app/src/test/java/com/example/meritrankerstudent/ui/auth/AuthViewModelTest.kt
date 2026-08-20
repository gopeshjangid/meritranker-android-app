package com.example.meritrankerstudent.ui.auth

import android.app.Activity
import com.amplifyframework.auth.result.AuthSignInResult
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.ExamSection
import com.example.meritrankerstudent.data.model.UserProfile
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.FakeExamProfileRepository
import com.example.meritrankerstudent.data.repository.UserProfileRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test

class AuthViewModelTest {

    private lateinit var fakeAuthRepo: AuthFakeAuthRepository
    private lateinit var fakeUserProfileRepo: AuthFakeUserProfileRepository
    private lateinit var fakeExamProfileRepo: FakeExamProfileRepository

    @OptIn(ExperimentalCoroutinesApi::class)
    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        MeritRankerApplication.setInitStatusForTesting(MeritRankerApplication.Companion.InitStatus.Success)
        fakeAuthRepo = AuthFakeAuthRepository()
        fakeUserProfileRepo = AuthFakeUserProfileRepository()
        fakeExamProfileRepo = FakeExamProfileRepository()

        // Populate valid test exam profiles
        fakeExamProfileRepo.setProfile(
            "SSC_CGL#TIER_1",
            ExamProfile(
                examProfileId = "SSC_CGL#TIER_1",
                examId = "SSC_CGL",
                examName = "SSC CGL",
                stage = "TIER_1",
                description = "SSC CGL Tier 1 Exam",
                sections = listOf(ExamSection("sec_quant", "Quant", "QUANT", "INTERMEDIATE", 25, 50.0, 15, listOf("mcq"), 0.5, 2.0, emptyList())),
                totalQuestions = 25,
                totalMarks = 50.0,
                totalTimeMinutes = 15,
                active = true
            )
        )
        fakeExamProfileRepo.setProfile(
            "RRB_NTPC#TIER_1",
            ExamProfile(
                examProfileId = "RRB_NTPC#TIER_1",
                examId = "RRB_NTPC",
                examName = "RRB NTPC",
                stage = "TIER_1",
                description = "RRB NTPC Exam",
                sections = listOf(ExamSection("sec_maths", "Maths", "QUANT", "INTERMEDIATE", 30, 30.0, 30, listOf("mcq"), 0.33, 1.0, emptyList())),
                totalQuestions = 30,
                totalMarks = 30.0,
                totalTimeMinutes = 30,
                active = true
            )
        )
    }

    @Test
    fun checkSession_whenNotSignedIn_emitsSignedOutState() = runTest {
        fakeAuthRepo.isSignedInResult = false

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.SignedOut)
    }

    @Test
    fun checkSession_whenSignedIn_andProfileExists_emitsReadyStateWithExamProfile() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-123"
        fakeUserProfileRepo.fetchProfileResult = UserProfile(
            userId = "user-123",
            email = "test@example.com",
            name = "John Doe",
            preparing = "SSC_CGL",
            examProfileId = "SSC_CGL#TIER_1",
            examStage = "TIER_1",
            profileCompleted = true
        )

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.Ready)
        val readyState = state as SessionState.Ready
        assertEquals("John Doe", readyState.profile.name)
        assertEquals("SSC CGL", readyState.examProfile.examName)
    }

    @Test
    fun checkSession_legacyUserWithNoStage_andSingleActiveProfile_autoResolvesCleanly() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-legacy-1"
        fakeUserProfileRepo.fetchProfileResult = UserProfile(
            userId = "user-legacy-1",
            email = "legacy@example.com",
            name = "Legacy User",
            preparing = "RRB_NTPC",
            examProfileId = null,
            examStage = null,
            profileCompleted = true
        )

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.Ready)
        val readyState = state as SessionState.Ready
        assertEquals("RRB NTPC", readyState.examProfile.examName)
    }

    @Test
    fun checkSession_whenSignedIn_andUnknownExamProfile_emitsExplicitErrorState() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-123"
        fakeUserProfileRepo.fetchProfileResult = UserProfile(
            userId = "user-123",
            email = "test@example.com",
            name = "John Doe",
            preparing = "NON_EXISTENT_EXAM_999",
            examStage = "TIER_1",
            profileCompleted = true
        )

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.Error)
        val errorState = state as SessionState.Error
        assertTrue(errorState.message.contains("isn't available yet"))
    }

    @Test
    fun checkSession_whenSignedIn_andProfileMissing_emitsIncompleteProfileState() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-123"
        fakeAuthRepo.fetchEmailResult = "newuser@example.com"
        fakeUserProfileRepo.fetchProfileResult = null
        fakeUserProfileRepo.createdProfileResult = UserProfile(
            userId = "user-123",
            email = "newuser@example.com",
            name = null,
            preparing = null,
            profileCompleted = false
        )

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.IncompleteProfile)
        val incompleteState = state as SessionState.IncompleteProfile
        assertEquals("newuser@example.com", incompleteState.profile.email)
    }

    @Test
    fun checkSession_whenProfileFetchFailsWithNetworkException_emitsExplicitErrorState() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-123"
        fakeUserProfileRepo.shouldThrowExceptionOnFetch = true

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.Error)
        val errorState = state as SessionState.Error
        assertTrue(errorState.message.contains("couldn't connect to MeritRanker right now"))
    }

    @Test
    fun completeProfile_createsProfileAndEmitsReadyState() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-123"
        fakeUserProfileRepo.updatedProfileResult = UserProfile(
            userId = "user-123",
            email = "test@example.com",
            name = "Jane Doe",
            preparing = "RRB_NTPC",
            examProfileId = "RRB_NTPC#TIER_1",
            examStage = "TIER_1",
            language = "ENGLISH",
            profileCompleted = true
        )

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)
        viewModel.completeProfile(
            name = "Jane Doe",
            preparing = "RRB_NTPC",
            examStage = "Tier 1",
            examProfileId = "RRB_NTPC#TIER_1",
            additionalExams = emptyList(),
            dateOfBirth = null,
            language = "ENGLISH"
        )

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.Ready)
        val readyState = state as SessionState.Ready
        assertEquals("Jane Doe", readyState.profile.name)
        assertEquals("RRB_NTPC", readyState.profile.preparing)
        assertEquals("RRB NTPC", readyState.examProfile.examName)
    }

    @Test
    fun signOut_clearsCacheAndEmitsSignedOutState() = runTest {
        fakeAuthRepo.isSignedInResult = true
        fakeAuthRepo.currentUserIdResult = "user-123"
        fakeUserProfileRepo.fetchProfileResult = UserProfile(
            userId = "user-123",
            email = "test@example.com",
            name = "John Doe",
            preparing = "SSC_CGL",
            examStage = "TIER_1",
            profileCompleted = true
        )

        val viewModel = AuthViewModel(fakeAuthRepo, fakeUserProfileRepo, fakeExamProfileRepo)
        assertTrue(viewModel.sessionState.value is SessionState.Ready)

        viewModel.signOut()

        val state = viewModel.sessionState.value
        assertTrue(state is SessionState.SignedOut)
        assertTrue(fakeUserProfileRepo.cacheCleared)
        assertTrue(fakeExamProfileRepo.cacheCleared)
    }
}

class AuthFakeAuthRepository : AuthRepository {
    var isSignedInResult: Boolean = false
    var currentUserIdResult: String? = null
    var fetchEmailResult: String? = null

    override suspend fun isUserSignedIn(): Boolean = isSignedInResult
    override suspend fun getCurrentUserId(): String? = currentUserIdResult
    override suspend fun fetchUserEmail(): String? = fetchEmailResult
    override suspend fun getAccessToken(): Result<String> = Result.success("mock_token")
    
    override suspend fun signInWithGoogle(activity: Activity): AuthSignInResult {
        throw NotImplementedError()
    }

    override suspend fun signOut() {
        isSignedInResult = false
        currentUserIdResult = null
    }

    override suspend fun deleteAccount(): Result<Unit> {
        isSignedInResult = false
        currentUserIdResult = null
        return Result.success(Unit)
    }
}

class AuthFakeUserProfileRepository : UserProfileRepository {
    var fetchProfileResult: UserProfile? = null
    var createdProfileResult: UserProfile? = null
    var updatedProfileResult: UserProfile? = null
    var shouldThrowExceptionOnFetch: Boolean = false
    var cacheCleared: Boolean = false

    override suspend fun deleteUserProfile(userId: String): Result<Unit> {
        return Result.success(Unit)
    }

    override fun clearCache() {
        cacheCleared = true
    }

    override fun observeUserProfile(userId: String): kotlinx.coroutines.flow.Flow<UserProfile?> {
        return kotlinx.coroutines.flow.flowOf(fetchProfileResult)
    }

    override suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean): UserProfile? {
        if (shouldThrowExceptionOnFetch) {
            throw Exception("GraphQL network failure")
        }
        return fetchProfileResult
    }

    override suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile {
        return createdProfileResult ?: UserProfile(userId = userId, email = email, name = name, profileCompleted = false)
    }

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
        return updatedProfileResult ?: UserProfile(
            userId = userId,
            name = name,
            preparing = preparing,
            examProfileId = examProfileId,
            examStage = examStage,
            additionalExams = additionalExams,
            dateOfBirth = dateOfBirth,
            language = language,
            profileCompleted = true
        )
    }
}
