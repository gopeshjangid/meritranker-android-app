package com.example.meritrankerstudent.data.repository

import android.util.Log
import com.amplifyframework.api.aws.GsonVariablesSerializer
import com.amplifyframework.api.graphql.SimpleGraphQLRequest
import com.amplifyframework.core.Amplify
import com.example.meritrankerstudent.MeritRankerApplication
import com.example.meritrankerstudent.data.local.AppDatabase
import com.example.meritrankerstudent.data.local.EntityMappers
import com.example.meritrankerstudent.data.local.FreshnessPolicy
import com.example.meritrankerstudent.data.local.FreshnessStatus
import com.example.meritrankerstudent.data.local.LocalProfileStore
import com.example.meritrankerstudent.data.local.SyncMetadataEntity
import com.example.meritrankerstudent.data.model.ExamCategoryCutoff
import com.example.meritrankerstudent.data.model.ExamPreviousCutoff
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.ExamSection
import com.example.meritrankerstudent.data.model.ExamSectionCutoff
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ExamProfileRepository {
    fun observeExamProfile(examProfileId: String): Flow<ExamProfile?>
    fun observeAllActiveExamProfiles(): Flow<List<ExamProfile>>
    suspend fun getExamProfile(examProfileId: String, forceRefresh: Boolean = false): ExamProfile?
    suspend fun fetchExamProfileForUser(preparing: String, stage: String? = null): ExamProfile?
    suspend fun listActiveProfilesForExam(examId: String): List<ExamProfile>
    suspend fun listAllActiveExamProfiles(forceRefresh: Boolean = false): List<ExamProfile>
    fun clearCache()
}

/**
 * Authoritative ExamProfileRepository connecting directly to Amplify Gen2 AppSync GraphQL
 * with a Local-First Stale-While-Revalidate Room cache.
 */
class DefaultExamProfileRepository(
    private val localProfileStore: LocalProfileStore? = MeritRankerApplication.instance?.let { LocalProfileStore(it) },
    private val database: AppDatabase? = MeritRankerApplication.instance?.let { AppDatabase.getInstance(it) },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : ExamProfileRepository {

    companion object {
        fun canonical(value: String): String {
            return value.trim().uppercase()
                .replace(" ", "_")
                .replace("-", "_")
                .replace("(", "")
                .replace(")", "")
        }

        fun canonicalExamProfileId(examId: String, stage: String): String {
            return "${canonical(examId)}#${canonical(stage)}"
        }
    }

    private val memoryCache = mutableMapOf<String, ExamProfile>()

    override fun clearCache() {
        memoryCache.clear()
        coroutineScope.launch {
            database?.examProfileDao()?.clearAllExamProfiles()
        }
    }

    override fun observeExamProfile(examProfileId: String): Flow<ExamProfile?> {
        val dao = database?.examProfileDao()
        if (dao != null) {
            return dao.getExamProfile(examProfileId).map { entity ->
                entity?.let { EntityMappers.entityToExamProfile(it) }
            }
        }
        return flowOf(localProfileStore?.getExamProfile(examProfileId) ?: memoryCache[examProfileId])
    }

    override fun observeAllActiveExamProfiles(): Flow<List<ExamProfile>> {
        val dao = database?.examProfileDao()
        if (dao != null) {
            return dao.getAllActiveExamProfiles().map { list ->
                list.map { EntityMappers.entityToExamProfile(it) }
            }
        }
        return flowOf(memoryCache.values.filter { it.active })
    }

    override suspend fun getExamProfile(examProfileId: String, forceRefresh: Boolean): ExamProfile? {
        if (examProfileId.isBlank()) return null

        val dao = database?.examProfileDao()
        val syncDao = database?.syncMetadataDao()

        // 1. Try reading from Room cache
        val localEntity = dao?.getExamProfileSync(examProfileId)
        val cached = if (localEntity != null) {
            val p = EntityMappers.entityToExamProfile(localEntity)
            memoryCache[examProfileId] = p
            p
        } else {
            localProfileStore?.getExamProfile(examProfileId)?.also { memoryCache[examProfileId] = it }
        }

        val syncMeta = syncDao?.getMetadata("GLOBAL", "EXAM_PROFILE", examProfileId)
        val freshness = if (forceRefresh) FreshnessStatus.STALE_BUT_USABLE else FreshnessPolicy.evaluate(syncMeta?.lastSuccessfulFetchAt ?: localEntity?.lastSyncedAt, FreshnessPolicy.EXAM_PROFILE_TTL_MS)

        if (cached != null && freshness == FreshnessStatus.FRESH) {
            Log.d("ExamProfileRepo", "CACHE_HIT (FRESH) for examProfileId=$examProfileId")
            return cached
        }

        if (cached != null && freshness == FreshnessStatus.STALE_BUT_USABLE) {
            Log.d("ExamProfileRepo", "CACHE_HIT (STALE_BUT_USABLE) for examProfileId=$examProfileId, triggering background revalidation")
            coroutineScope.launch {
                try {
                    performRemoteGetExamProfile(examProfileId)
                } catch (e: Exception) {
                    Log.w("ExamProfileRepo", "Background revalidation failed: ${e.message}")
                }
            }
            return cached
        }

        Log.d("ExamProfileRepo", "CACHE_MISS for examProfileId=$examProfileId, performing remote fetch")
        return performRemoteGetExamProfile(examProfileId)
    }

    private suspend fun performRemoteGetExamProfile(examProfileId: String): ExamProfile? {
        val result = withTimeoutOrNull(35000L) {
            suspendCancellableCoroutine<ExamProfileResult> { continuation ->
                val document = """
                    query GetExamProfile(${'$'}examProfileId: ID!) {
                        getExamProfile(examProfileId: ${'$'}examProfileId) {
                            examProfileId
                            examId
                            examName
                            stage
                            description
                            sections {
                                sectionId
                                name
                                subject
                                level
                                questionCount
                                marks
                                timeMinutes
                                questionStyles
                                negativeMarks
                                marksPerQuestion
                                excludeTopics
                            }
                            totalQuestions
                            totalMarks
                            totalTimeMinutes
                            previousCutoff {
                                year
                                overall
                                categories {
                                    category
                                    cutoff
                                }
                                sections {
                                    sectionId
                                    cutoff
                                }
                            }
                            active
                            effectiveYear
                            createdAt
                            updatedAt
                        }
                    }
                """.trimIndent()

                val request = SimpleGraphQLRequest<String>(
                    document,
                    mapOf("examProfileId" to examProfileId),
                    String::class.java,
                    GsonVariablesSerializer()
                )

                try {
                    Amplify.API.query(
                        request,
                        { response ->
                            if (continuation.isActive) {
                                if (response.hasErrors()) {
                                    val errMsg = response.errors.firstOrNull()?.message ?: "GraphQL Error"
                                    continuation.resume(ExamProfileResult.Error(Exception(errMsg)))
                                } else if (response.data == null || response.data == "null") {
                                    continuation.resume(ExamProfileResult.NotFound)
                                } else {
                                    try {
                                        val json = JSONObject(response.data)
                                        if (json.isNull("getExamProfile")) {
                                            continuation.resume(ExamProfileResult.NotFound)
                                        } else {
                                            val profileJson = json.getJSONObject("getExamProfile")
                                            val parsed = parseExamProfile(profileJson)
                                            continuation.resume(ExamProfileResult.Success(parsed))
                                        }
                                    } catch (e: Exception) {
                                        continuation.resume(ExamProfileResult.Error(e))
                                    }
                                }
                            }
                        },
                        { error ->
                            if (continuation.isActive) {
                                continuation.resume(ExamProfileResult.Error(error))
                            }
                        }
                    )
                } catch (e: Throwable) {
                    if (continuation.isActive) {
                        continuation.resume(ExamProfileResult.Error(Exception(e)))
                    }
                }
            }
        }

        return when (result) {
            is ExamProfileResult.Success -> {
                if (result.profile.active) {
                    memoryCache[examProfileId] = result.profile
                    localProfileStore?.saveExamProfile(result.profile)
                    database?.examProfileDao()?.upsertExamProfile(EntityMappers.examProfileToEntity(result.profile))
                    database?.syncMetadataDao()?.upsertMetadata(
                        SyncMetadataEntity(
                            ownerUserId = "GLOBAL",
                            resourceType = "EXAM_PROFILE",
                            resourceScope = examProfileId,
                            lastSuccessfulFetchAt = System.currentTimeMillis(),
                            syncState = "IDLE"
                        )
                    )
                    result.profile
                } else {
                    null // Inactive profile is treated as unavailable
                }
            }
            is ExamProfileResult.NotFound -> null
            is ExamProfileResult.Error -> memoryCache[examProfileId]
            null -> memoryCache[examProfileId]
        }
    }

    override suspend fun listActiveProfilesForExam(examId: String): List<ExamProfile> {
        val allActive = listAllActiveExamProfiles()
        val cExam = canonical(examId)
        val filtered = allActive.filter {
            canonical(it.examId) == cExam || it.examId.equals(examId, ignoreCase = true)
        }
        return if (filtered.isNotEmpty()) filtered else allActive.filter { it.examId.contains(examId, ignoreCase = true) }
    }

    override suspend fun listAllActiveExamProfiles(forceRefresh: Boolean): List<ExamProfile> {
        val dao = database?.examProfileDao()
        val syncDao = database?.syncMetadataDao()

        val localEntities = dao?.getAllActiveExamProfilesSync() ?: emptyList()
        val cached = if (localEntities.isNotEmpty()) {
            localEntities.map { EntityMappers.entityToExamProfile(it) }
        } else {
            memoryCache.values.filter { it.active }.toList()
        }

        val syncMeta = syncDao?.getMetadata("GLOBAL", "ALL_EXAM_PROFILES", "ALL")
        val freshness = if (forceRefresh) FreshnessStatus.STALE_BUT_USABLE else FreshnessPolicy.evaluate(syncMeta?.lastSuccessfulFetchAt, FreshnessPolicy.EXAM_PROFILE_TTL_MS)

        if (cached.isNotEmpty() && freshness == FreshnessStatus.FRESH) {
            Log.d("ExamProfileRepo", "CACHE_HIT (FRESH) for all active exam profiles (${cached.size} profiles)")
            return cached
        }

        if (cached.isNotEmpty() && freshness == FreshnessStatus.STALE_BUT_USABLE) {
            Log.d("ExamProfileRepo", "CACHE_HIT (STALE_BUT_USABLE) for all active exam profiles, triggering background revalidation")
            coroutineScope.launch {
                try {
                    performRemoteListAllActiveExamProfiles()
                } catch (e: Exception) {
                    Log.w("ExamProfileRepo", "Background revalidation failed: ${e.message}")
                }
            }
            return cached
        }

        return performRemoteListAllActiveExamProfiles()
    }

    private suspend fun performRemoteListAllActiveExamProfiles(): List<ExamProfile> {
        val result = withTimeoutOrNull(15000L) {
            suspendCancellableCoroutine<List<ExamProfile>> { continuation ->
                val document = """
                    query ListActiveExamProfiles(${'$'}filter: ModelExamProfileFilterInput) {
                        listExamProfiles(filter: ${'$'}filter, limit: 100) {
                            items {
                                examProfileId
                                examId
                                examName
                                stage
                                description
                                sections {
                                    sectionId
                                    name
                                    subject
                                    level
                                    questionCount
                                    marks
                                    timeMinutes
                                    questionStyles
                                    negativeMarks
                                    marksPerQuestion
                                    excludeTopics
                                }
                                totalQuestions
                                totalMarks
                                totalTimeMinutes
                                active
                                effectiveYear
                                createdAt
                                updatedAt
                            }
                        }
                    }
                """.trimIndent()

                val filter = mapOf(
                    "active" to mapOf("eq" to true)
                )

                val request = SimpleGraphQLRequest<String>(
                    document,
                    mapOf("filter" to filter),
                    String::class.java,
                    GsonVariablesSerializer()
                )

                Amplify.API.query(
                    request,
                    { response ->
                        if (continuation.isActive) {
                            if (response.hasErrors() || response.data == null) {
                                continuation.resume(emptyList())
                            } else {
                                try {
                                    val json = JSONObject(response.data)
                                    val listObj = json.optJSONObject("listExamProfiles")
                                    val items = listObj?.optJSONArray("items")
                                    val profiles = mutableListOf<ExamProfile>()
                                    if (items != null) {
                                        for (i in 0 until items.length()) {
                                            val p = items.optJSONObject(i)
                                            if (p != null) {
                                                val parsed = parseExamProfile(p)
                                                if (parsed.active) {
                                                    profiles.add(parsed)
                                                }
                                            }
                                        }
                                    }
                                    continuation.resume(profiles)
                                } catch (e: Exception) {
                                    continuation.resume(emptyList())
                                }
                            }
                        }
                    },
                    { _ ->
                        if (continuation.isActive) {
                            continuation.resume(emptyList())
                        }
                    }
                )
            }
        } ?: emptyList()

        if (result.isNotEmpty()) {
            result.forEach { profile ->
                memoryCache[profile.examProfileId] = profile
                localProfileStore?.saveExamProfile(profile)
            }
            database?.examProfileDao()?.upsertExamProfiles(result.map { EntityMappers.examProfileToEntity(it) })
            database?.syncMetadataDao()?.upsertMetadata(
                SyncMetadataEntity(
                    ownerUserId = "GLOBAL",
                    resourceType = "ALL_EXAM_PROFILES",
                    resourceScope = "ALL",
                    lastSuccessfulFetchAt = System.currentTimeMillis(),
                    syncState = "IDLE"
                )
            )
            return result
        }

        return memoryCache.values.filter { it.active }.toList()
    }

    override suspend fun fetchExamProfileForUser(preparing: String, stage: String?): ExamProfile? {
        val canonicalExam = canonical(preparing)
        val stageSuffix = if (!stage.isNullOrBlank()) "#${canonical(stage)}" else ""
        val candidateId = if (stageSuffix.isNotEmpty()) "$canonicalExam$stageSuffix" else canonicalExam

        val cachedCandidate = getExamProfile(candidateId)
        if (cachedCandidate != null) return cachedCandidate

        val activeList = listActiveProfilesForExam(preparing)
        if (activeList.isNotEmpty()) {
            val exactMatch = activeList.firstOrNull { 
                if (!stage.isNullOrBlank()) canonical(it.stage) == canonical(stage) else true 
            }
            if (exactMatch != null) return exactMatch
            return activeList.first()
        }

        return getExamProfile(candidateId)
    }

    private fun canonical(value: String): String {
        return value.trim().uppercase()
            .replace(" ", "_")
            .replace("-", "_")
            .replace("(", "")
            .replace(")", "")
    }

    private fun parseExamProfile(json: JSONObject): ExamProfile {
        val sectionsList = mutableListOf<ExamSection>()
        val sectionsArray = json.optJSONArray("sections")
        if (sectionsArray != null) {
            for (i in 0 until sectionsArray.length()) {
                val secObj = sectionsArray.getJSONObject(i)
                val styles = mutableListOf<String>()
                val stylesArr = secObj.optJSONArray("questionStyles")
                if (stylesArr != null) {
                    for (j in 0 until stylesArr.length()) {
                        styles.add(stylesArr.getString(j))
                    }
                }

                val excludes = mutableListOf<String>()
                val excludesArr = secObj.optJSONArray("excludeTopics")
                if (excludesArr != null) {
                    for (j in 0 until excludesArr.length()) {
                        excludes.add(excludesArr.getString(j))
                    }
                }

                sectionsList.add(
                    ExamSection(
                        sectionId = secObj.getString("sectionId"),
                        name = secObj.getString("name"),
                        subject = secObj.getString("subject"),
                        level = secObj.getString("level"),
                        questionCount = secObj.getInt("questionCount"),
                        marks = secObj.getDouble("marks"),
                        timeMinutes = secObj.getInt("timeMinutes"),
                        questionStyles = styles,
                        negativeMarks = if (secObj.has("negativeMarks")) secObj.getDouble("negativeMarks") else null,
                        marksPerQuestion = if (secObj.has("marksPerQuestion")) secObj.getDouble("marksPerQuestion") else null,
                        excludeTopics = excludes
                    )
                )
            }
        }

        var cutoff: ExamPreviousCutoff? = null
        val cutoffObj = json.optJSONObject("previousCutoff")
        if (cutoffObj != null) {
            val categoriesList = mutableListOf<ExamCategoryCutoff>()
            val catArr = cutoffObj.optJSONArray("categories")
            if (catArr != null) {
                for (k in 0 until catArr.length()) {
                    val c = catArr.getJSONObject(k)
                    categoriesList.add(
                        ExamCategoryCutoff(
                            category = c.getString("category"),
                            cutoff = c.getDouble("cutoff")
                        )
                    )
                }
            }

            val sectionsCutoffList = mutableListOf<ExamSectionCutoff>()
            val sCutArr = cutoffObj.optJSONArray("sections")
            if (sCutArr != null) {
                for (k in 0 until sCutArr.length()) {
                    val sc = sCutArr.getJSONObject(k)
                    sectionsCutoffList.add(
                        ExamSectionCutoff(
                            sectionId = sc.getString("sectionId"),
                            cutoff = sc.getDouble("cutoff")
                        )
                    )
                }
            }

            cutoff = ExamPreviousCutoff(
                year = cutoffObj.optInt("year", 2024),
                overall = cutoffObj.optDouble("overall", 0.0),
                categories = categoriesList,
                sections = sectionsCutoffList
            )
        }

        return ExamProfile(
            examProfileId = json.getString("examProfileId"),
            examId = json.getString("examId"),
            examName = json.getString("examName"),
            stage = json.getString("stage"),
            description = json.optString("description", ""),
            sections = sectionsList,
            totalQuestions = json.optInt("totalQuestions", 0),
            totalMarks = json.optDouble("totalMarks", 0.0),
            totalTimeMinutes = json.optInt("totalTimeMinutes", 0),
            previousCutoff = cutoff,
            active = json.optBoolean("active", true),
            effectiveYear = if (json.has("effectiveYear") && !json.isNull("effectiveYear")) json.getInt("effectiveYear") else null
        )
    }

    private sealed interface ExamProfileResult {
        data class Success(val profile: ExamProfile) : ExamProfileResult
        data object NotFound : ExamProfileResult
        data class Error(val exception: Throwable) : ExamProfileResult
    }
}
