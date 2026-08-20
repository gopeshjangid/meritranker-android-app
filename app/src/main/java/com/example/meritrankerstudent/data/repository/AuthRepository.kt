package com.example.meritrankerstudent.data.repository

import android.app.Activity
import android.util.Log
import com.amplifyframework.auth.AuthProvider
import com.amplifyframework.auth.result.AuthSignInResult
import com.amplifyframework.core.Amplify
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AuthRepository {
    suspend fun isUserSignedIn(): Boolean
    suspend fun getCurrentUserId(): String?
    suspend fun fetchUserEmail(): String?
    suspend fun getAccessToken(): Result<String>
    suspend fun signInWithGoogle(activity: Activity): AuthSignInResult
    suspend fun signOut()
    suspend fun deleteAccount(): Result<Unit>
}

class DefaultAuthRepository(
    private val userProfileRepository: UserProfileRepository = DefaultUserProfileRepository()
) : AuthRepository {

    override suspend fun isUserSignedIn(): Boolean = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.getCurrentUser(
            { user ->
                if (continuation.isActive) {
                    continuation.resume(true)
                }
            },
            { _ ->
                Amplify.Auth.fetchAuthSession(
                    { session ->
                        if (continuation.isActive) {
                            continuation.resume(session.isSignedIn)
                        }
                    },
                    { _ ->
                        if (continuation.isActive) {
                            continuation.resume(false)
                        }
                    }
                )
            }
        )
    }

    override suspend fun getAccessToken(): Result<String> = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.fetchAuthSession(
            { session ->
                if (continuation.isActive) {
                    val awscognitoSession = session as? com.amplifyframework.auth.cognito.AWSCognitoAuthSession
                    val token = awscognitoSession?.userPoolTokensResult?.value?.accessToken
                    if (token != null) {
                        continuation.resume(Result.success(token))
                    } else {
                        continuation.resume(Result.failure(Exception("Access token not found in session.")))
                    }
                }
            },
            { error ->
                if (continuation.isActive) {
                    continuation.resume(Result.failure(error))
                }
            }
        )
    }

    override suspend fun getCurrentUserId(): String? = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.getCurrentUser(
            { user ->
                Log.d("AuthRepository", "getCurrentUser success: userId=${user.userId}")
                if (continuation.isActive) {
                    continuation.resume(user.userId)
                }
            },
            { error ->
                Log.w("AuthRepository", "getCurrentUser failed: ${error.message}", error)
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        )
    }

    override suspend fun fetchUserEmail(): String? = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.fetchUserAttributes(
            { attributes ->
                if (continuation.isActive) {
                    val emailAttr = attributes.find { it.key.keyString == "email" }
                    continuation.resume(emailAttr?.value)
                }
            },
            { error ->
                if (continuation.isActive) {
                    continuation.resume(null)
                }
            }
        )
    }

    override suspend fun signInWithGoogle(activity: Activity): AuthSignInResult = suspendCancellableCoroutine { continuation ->
        Amplify.Auth.signInWithSocialWebUI(
            AuthProvider.google(),
            activity,
            { result ->
                if (continuation.isActive) {
                    continuation.resume(result)
                }
            },
            { exception ->
                if (continuation.isActive) {
                    continuation.resumeWithException(exception)
                }
            }
        )
    }

    override suspend fun signOut(): Unit {
        val userId = getCurrentUserId()
        if (userId != null) {
            com.example.meritrankerstudent.MeritRankerApplication.instance?.let { ctx ->
                try {
                    com.example.meritrankerstudent.data.local.AppDatabase.getInstance(ctx).clearUserCache(userId)
                    com.example.meritrankerstudent.data.local.LocalProfileStore(ctx).clear()
                } catch (e: Exception) {
                    android.util.Log.w("AuthRepo", "Error clearing user cache on signOut: ${e.message}")
                }
            }
        }

        return suspendCancellableCoroutine { continuation ->
            Amplify.Auth.signOut(
                {
                    if (continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
            )
        }
    }

    override suspend fun deleteAccount(): Result<Unit> {
        val userId = getCurrentUserId()

        // 1. Delete server-side user data before auth session is invalidated
        if (userId != null) {
            try {
                userProfileRepository.deleteUserProfile(userId)
            } catch (e: Exception) {
                Log.w("AuthRepo", "deleteUserProfile non-fatal warning during account deletion: ${e.message}")
            }
        }

        // 2. Delete Cognito User Identity
        val deleteResult = suspendCancellableCoroutine<Result<Unit>> { continuation ->
            Amplify.Auth.deleteUser(
                {
                    if (continuation.isActive) {
                        continuation.resume(Result.success(Unit))
                    }
                },
                { error ->
                    Log.e("AuthRepo", "deleteUser failed on server: ${error.message}", error)
                    if (continuation.isActive) {
                        continuation.resume(Result.failure(error))
                    }
                }
            )
        }

        // 3. Clear local SQLite database and secure profile store
        if (deleteResult.isSuccess && userId != null) {
            com.example.meritrankerstudent.MeritRankerApplication.instance?.let { ctx ->
                try {
                    com.example.meritrankerstudent.data.local.AppDatabase.getInstance(ctx).clearUserCache(userId)
                    com.example.meritrankerstudent.data.local.LocalProfileStore(ctx).clear()
                    userProfileRepository.clearCache()
                } catch (e: Exception) {
                    Log.w("AuthRepo", "Error clearing user cache on deleteAccount: ${e.message}")
                }
            }
        }
        return deleteResult
    }
}
