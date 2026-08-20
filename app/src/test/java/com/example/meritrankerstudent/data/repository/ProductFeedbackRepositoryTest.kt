package com.example.meritrankerstudent.data.repository

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class ProductFeedbackRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockAuthRepository: AuthRepository

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockAuthRepository = mock(AuthRepository::class.java)
        `when`(mockContext.applicationContext).thenReturn(mockContext)
    }

    @Test
    fun testSubmitFeedback_UnauthenticatedUser_FailsGracefully() = runBlocking {
        `when`(mockAuthRepository.getCurrentUserId()).thenReturn(null)

        val repo = AppSyncProductFeedbackRepository(mockContext, mockAuthRepository)
        val result = repo.submitFeedback("Suggestion", "Add dark mode toggle")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
