package com.example.meritrankerstudent.data.repository

import android.util.Log
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.local.AppDatabase
import com.example.meritrankerstudent.data.local.EntityMappers
import com.example.meritrankerstudent.data.local.FreshnessPolicy
import com.example.meritrankerstudent.data.local.FreshnessStatus
import com.example.meritrankerstudent.data.local.SyncMetadataEntity
import com.example.meritrankerstudent.data.model.GetStudentPerformanceView
import com.example.meritrankerstudent.data.model.StudentPerformanceResponse
import com.example.meritrankerstudent.data.remote.StudentPerformanceGraphQLClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

interface StudentPerformanceRepository {
    fun observePerformance(
        examProfileId: String,
        view: GetStudentPerformanceView = GetStudentPerformanceView.PRACTICE,
        subjectId: String? = null
    ): Flow<StudentPerformanceResponse?>

    suspend fun getPerformance(
        examProfileId: String,
        view: GetStudentPerformanceView = GetStudentPerformanceView.PRACTICE,
        subjectId: String? = null,
        forceRefresh: Boolean = false
    ): Result<StudentPerformanceResponse>
}

class DefaultStudentPerformanceRepository(
    private val client: StudentPerformanceGraphQLClient = StudentPerformanceGraphQLClient(),
    private val authRepository: AuthRepository = DefaultAuthRepository(),
    private val database: AppDatabase? = MeritRankerApplication.instance?.let { AppDatabase.getInstance(it) },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : StudentPerformanceRepository {

    private suspend fun getOwnerUserId(): String {
        return authRepository.getCurrentUserId() ?: "ANONYMOUS"
    }

    override fun observePerformance(
        examProfileId: String,
        view: GetStudentPerformanceView,
        subjectId: String?
    ): Flow<StudentPerformanceResponse?> {
        val dao = database?.performanceDao() ?: return flowOf(null)
        val safeSubject = subjectId ?: "ALL"

        return flow {
            val ownerUserId = authRepository.getCurrentUserId() ?: "ANONYMOUS"
            dao.getPerformance(ownerUserId, examProfileId, view.name, safeSubject).collect { entity ->
                emit(entity?.let { EntityMappers.entityToStudentPerformance(it) })
            }
        }
    }

    override suspend fun getPerformance(
        examProfileId: String,
        view: GetStudentPerformanceView,
        subjectId: String?,
        forceRefresh: Boolean
    ): Result<StudentPerformanceResponse> {
        val tokenResult = authRepository.getAccessToken()
        val token = tokenResult.getOrNull()
            ?: return Result.failure(tokenResult.exceptionOrNull() ?: Exception("Authentication token missing"))

        val ownerUserId = getOwnerUserId()
        val safeSubject = subjectId ?: "ALL"
        val dao = database?.performanceDao()
        val syncDao = database?.syncMetadataDao()

        // 1. Check Room cache
        val localEntity = dao?.getPerformanceSync(ownerUserId, examProfileId, view.name, safeSubject)
        val cached = localEntity?.let { EntityMappers.entityToStudentPerformance(it) }

        val syncMeta = syncDao?.getMetadata(ownerUserId, "STUDENT_PERFORMANCE", "${examProfileId}_${view.name}_$safeSubject")
        val isDirty = localEntity?.isDirty ?: false
        val freshness = if (forceRefresh || isDirty) FreshnessStatus.STALE_BUT_USABLE else FreshnessPolicy.evaluate(syncMeta?.lastSuccessfulFetchAt ?: localEntity?.lastSyncedAt, FreshnessPolicy.STUDENT_PERFORMANCE_TTL_MS)

        if (cached != null && freshness == FreshnessStatus.FRESH) {
            Log.d("StudentPerformanceRepo", "CACHE_HIT (FRESH) for performance ($ownerUserId, $examProfileId, $view, $safeSubject)")
            return Result.success(cached)
        }

        if (cached != null && freshness == FreshnessStatus.STALE_BUT_USABLE) {
            Log.d("StudentPerformanceRepo", "CACHE_HIT (STALE_BUT_USABLE) for performance, triggering background revalidation")
            coroutineScope.launch {
                try {
                    performRemoteFetchAndReconcile(examProfileId, view, subjectId, token, ownerUserId, safeSubject)
                } catch (e: Exception) {
                    Log.w("StudentPerformanceRepo", "Background performance revalidation failed: ${e.message}")
                }
            }
            return Result.success(cached)
        }

        Log.d("StudentPerformanceRepo", "CACHE_MISS for performance, performing remote fetch")
        return try {
            val response = performRemoteFetchAndReconcile(examProfileId, view, subjectId, token, ownerUserId, safeSubject)
            Result.success(response)
        } catch (e: Exception) {
            if (cached != null) {
                Result.success(cached)
            } else {
                Result.failure(e)
            }
        }
    }

    private suspend fun performRemoteFetchAndReconcile(
        examProfileId: String,
        view: GetStudentPerformanceView,
        subjectId: String?,
        token: String,
        ownerUserId: String,
        safeSubject: String
    ): StudentPerformanceResponse {
        val response = client.getStudentPerformance(
            examProfileId = examProfileId,
            view = view,
            subjectId = subjectId,
            authToken = token
        )

        // Save to Room cache with isDirty = false
        val entity = EntityMappers.studentPerformanceToEntity(response, ownerUserId, examProfileId, view, subjectId, isDirty = false)
        database?.performanceDao()?.upsertPerformance(entity)
        database?.syncMetadataDao()?.upsertMetadata(
            SyncMetadataEntity(
                ownerUserId = ownerUserId,
                resourceType = "STUDENT_PERFORMANCE",
                resourceScope = "${examProfileId}_${view.name}_$safeSubject",
                lastSuccessfulFetchAt = System.currentTimeMillis(),
                syncState = "IDLE"
            )
        )

        // If backend is still updating latest performance, run bounded reconciliation (max 4 attempts: 2s, 4s, 8s, 16s)
        if (response.isUpdatingLatestPerformance) {
            coroutineScope.launch {
                var attempt = 0
                val delays = listOf(2000L, 4000L, 8000L, 16000L)
                while (attempt < delays.size) {
                    delay(delays[attempt])
                    attempt++
                    try {
                        val latestToken = authRepository.getAccessToken().getOrNull() ?: token
                        val reconciled = client.getStudentPerformance(
                            examProfileId = examProfileId,
                            view = view,
                            subjectId = subjectId,
                            authToken = latestToken
                        )
                        val reconciledEntity = EntityMappers.studentPerformanceToEntity(reconciled, ownerUserId, examProfileId, view, subjectId, isDirty = false)
                        database?.performanceDao()?.upsertPerformance(reconciledEntity)
                        Log.d("StudentPerformanceRepo", "Reconciled updating performance on attempt $attempt: isUpdating=${reconciled.isUpdatingLatestPerformance}")
                        if (!reconciled.isUpdatingLatestPerformance) {
                            break
                        }
                    } catch (e: Exception) {
                        Log.w("StudentPerformanceRepo", "Reconciliation attempt $attempt failed: ${e.message}")
                    }
                }
            }
        }

        return response
    }
}
