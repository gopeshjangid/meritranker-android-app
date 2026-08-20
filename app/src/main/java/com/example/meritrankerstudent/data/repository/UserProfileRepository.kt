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
import com.example.meritrankerstudent.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface UserProfileRepository {
    fun observeUserProfile(userId: String): Flow<UserProfile?>
    suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean = false): UserProfile?
    suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile
    suspend fun updateUserProfile(
        userId: String,
        name: String,
        preparing: String,
        examProfileId: String? = null,
        examStage: String? = null,
        additionalExams: List<String> = emptyList(),
        dateOfBirth: String? = null,
        language: String? = "ENGLISH"
    ): UserProfile
    suspend fun deleteUserProfile(userId: String): Result<Unit>
    fun clearCache()
}

class DefaultUserProfileRepository(
    private val localProfileStore: LocalProfileStore? = MeritRankerApplication.instance?.let { LocalProfileStore(it) },
    private val database: AppDatabase? = MeritRankerApplication.instance?.let { AppDatabase.getInstance(it) },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) : UserProfileRepository {

    init {
        coroutineScope.launch {
            database?.let { db ->
                localProfileStore?.migrateToRoom(db)
            }
        }
    }

    private val cachedProfiles = mutableMapOf<String, UserProfile>()

    override suspend fun deleteUserProfile(userId: String): Result<Unit> = suspendCancellableCoroutine { continuation ->
        val document = """
            mutation DeleteUser(${'$'}input: DeleteUserInput!) {
                deleteUser(input: ${'$'}input) {
                    userId
                }
            }
        """.trimIndent()

        val input = mapOf<String, Any>("userId" to userId)

        val request = SimpleGraphQLRequest<String>(
            document,
            mapOf("input" to input),
            String::class.java,
            GsonVariablesSerializer()
        )

        Amplify.API.mutate(
            request,
            { response ->
                if (continuation.isActive) {
                    if (response.hasErrors()) {
                        Log.w("UserProfileRepo", "deleteUser GraphQL response had errors: ${response.errors.firstOrNull()?.message}")
                    }
                    continuation.resume(Result.success(Unit))
                }
            },
            { failure ->
                Log.w("UserProfileRepo", "deleteUser network failure: ${failure.message}")
                if (continuation.isActive) {
                    continuation.resume(Result.success(Unit))
                }
            }
        )
    }

    override fun clearCache() {
        cachedProfiles.clear()
        localProfileStore?.clear()
        coroutineScope.launch {
            database?.profileDao()?.clearAllProfiles()
        }
    }

    override fun observeUserProfile(userId: String): Flow<UserProfile?> {
        val dao = database?.profileDao()
        if (dao != null) {
            return dao.getProfile(userId).map { entity ->
                entity?.let { EntityMappers.entityToUserProfile(it) }
            }
        }
        return kotlinx.coroutines.flow.flowOf(localProfileStore?.getUserProfile(userId) ?: cachedProfiles[userId])
    }

    override suspend fun fetchUserProfile(userId: String, forceRefresh: Boolean): UserProfile? {
        val profileDao = database?.profileDao()
        val syncDao = database?.syncMetadataDao()

        // 1. Try reading from Room cache
        val localEntity = profileDao?.getProfileSync(userId)
        val cached = if (localEntity != null) {
            val p = EntityMappers.entityToUserProfile(localEntity)
            cachedProfiles[userId] = p
            p
        } else {
            localProfileStore?.getUserProfile(userId)?.also { cachedProfiles[userId] = it }
        }

        val syncMeta = syncDao?.getMetadata(userId, "USER_PROFILE", userId)
        val freshness = if (forceRefresh) FreshnessStatus.STALE_BUT_USABLE else FreshnessPolicy.evaluate(syncMeta?.lastSuccessfulFetchAt ?: localEntity?.lastSyncedAt, FreshnessPolicy.USER_PROFILE_TTL_MS)

        if (cached != null && freshness == FreshnessStatus.FRESH) {
            Log.d("UserProfileRepo", "CACHE_HIT (FRESH) for userId=$userId")
            return cached
        }

        if (cached != null && freshness == FreshnessStatus.STALE_BUT_USABLE) {
            Log.d("UserProfileRepo", "CACHE_HIT (STALE_BUT_USABLE) for userId=$userId, triggering background revalidation")
            coroutineScope.launch {
                try {
                    performRemoteFetch(userId)
                } catch (e: Exception) {
                    Log.w("UserProfileRepo", "Background revalidation failed: ${e.message}")
                }
            }
            return cached
        }

        Log.d("UserProfileRepo", "CACHE_MISS for userId=$userId, performing remote fetch")
        return performRemoteFetch(userId)
    }

    private suspend fun performRemoteFetch(userId: String): UserProfile? {
        val networkResult = withTimeoutOrNull(35000L) {
            suspendCancellableCoroutine<UserProfileResult> { continuation ->
                val document = """
                    query GetUser(${'$'}userId: ID!) {
                        getUser(userId: ${'$'}userId) {
                            userId
                            email
                            name
                            preparing
                            onboardingStep
                            profileCompleted
                            profile
                            role
                            lang
                            preferences
                            examTags
                        }
                    }
                """.trimIndent()

                val request = SimpleGraphQLRequest<String>(
                    document,
                    mapOf("userId" to userId),
                    String::class.java,
                    GsonVariablesSerializer()
                )

                try {
                    Log.d("UserProfileRepo", "fetchUserProfile calling Amplify.API.query for userId=$userId")
                    Amplify.API.query(
                        request,
                        { response ->
                            Log.i("UserProfileRepo", "getUser response: hasErrors=${response.hasErrors()}, errors=${response.errors}, data=${response.data}")
                            if (continuation.isActive) {
                                if (response.hasErrors()) {
                                    val errMsg = response.errors.firstOrNull()?.message ?: "GraphQL Error"
                                    continuation.resume(UserProfileResult.Error(Exception(errMsg)))
                                } else if (response.data == null || response.data == "null") {
                                    continuation.resume(UserProfileResult.NotFound)
                                } else {
                                    try {
                                        val json = JSONObject(response.data)
                                        if (json.isNull("getUser")) {
                                            continuation.resume(UserProfileResult.NotFound)
                                        } else {
                                            val userJson = json.getJSONObject("getUser")
                                            val parsed = parseUserProfile(userJson)
                                            Log.d("UserProfileRepo", "Parsed user profile: userId=${parsed.userId}, preparing=${parsed.preparing}, stage=${parsed.examStage}")
                                            continuation.resume(UserProfileResult.Success(parsed))
                                        }
                                    } catch (e: Exception) {
                                        Log.e("UserProfileRepo", "Error parsing getUser JSON", e)
                                        continuation.resume(UserProfileResult.Error(e))
                                    }
                                }
                            }
                        },
                        { error ->
                            Log.e("UserProfileRepo", "Amplify.API.query failed", error)
                            if (continuation.isActive) {
                                continuation.resume(UserProfileResult.Error(error))
                            }
                        }
                    )
                } catch (e: Throwable) {
                    Log.e("UserProfileRepo", "Amplify.API.query threw exception", e)
                    if (continuation.isActive) {
                        continuation.resume(UserProfileResult.Error(Exception(e)))
                    }
                }
            }
        }

        return when (networkResult) {
            is UserProfileResult.Success -> {
                val profile = networkResult.profile
                cachedProfiles[userId] = profile
                localProfileStore?.saveUserProfile(profile)
                database?.profileDao()?.upsertProfile(EntityMappers.userProfileToEntity(profile))
                database?.syncMetadataDao()?.upsertMetadata(
                    SyncMetadataEntity(
                        ownerUserId = userId,
                        resourceType = "USER_PROFILE",
                        resourceScope = userId,
                        lastSuccessfulFetchAt = System.currentTimeMillis(),
                        syncState = "IDLE"
                    )
                )
                profile
            }
            is UserProfileResult.NotFound -> null
            is UserProfileResult.Error -> {
                cachedProfiles[userId] ?: throw networkResult.exception
            }
            null -> { // Timeout
                cachedProfiles[userId] ?: throw Exception("Profile request timed out. Please check connection.")
            }
        }
    }

    override suspend fun createUserProfile(userId: String, email: String?, name: String?): UserProfile = suspendCancellableCoroutine { continuation ->
        val document = """
            mutation CreateUser(${'$'}input: CreateUserInput!) {
                createUser(input: ${'$'}input) {
                    userId
                    email
                    name
                    preparing
                    onboardingStep
                    profileCompleted
                    profile
                    role
                    lang
                    preferences
                    examTags
                }
            }
        """.trimIndent()

        val input = mutableMapOf<String, Any>(
            "userId" to userId,
            "profileCompleted" to false,
            "onboardingStep" to "INITIAL",
            "role" to "student"
        )
        if (email != null) input["email"] = email
        if (name != null) input["name"] = name

        val request = SimpleGraphQLRequest<String>(
            document,
            mapOf("input" to input),
            String::class.java,
            GsonVariablesSerializer()
        )

        Amplify.API.mutate(
            request,
            { response ->
                if (continuation.isActive) {
                    if (response.hasErrors()) {
                        val errMsg = response.errors.firstOrNull()?.message ?: "Create User GraphQL error"
                        if (errMsg.contains("conditional", ignoreCase = true) || errMsg.contains("ConditionalCheckFailed", ignoreCase = true)) {
                            // User already exists in DB -> create fallback local representation for onboarding
                            val fallback = UserProfile(
                                userId = userId,
                                email = email,
                                name = name,
                                profileCompleted = false,
                                onboardingStep = "INITIAL",
                                role = "student"
                            )
                            cachedProfiles[userId] = fallback
                            continuation.resume(fallback)
                        } else {
                            continuation.resumeWithException(Exception(errMsg))
                        }
                    } else if (response.data == null || response.data == "null") {
                        continuation.resumeWithException(Exception("Empty GraphQL response on CreateUser"))
                    } else {
                        try {
                            val json = JSONObject(response.data)
                            val createdJson = json.optJSONObject("createUser")
                            val createdProfile = if (createdJson != null) {
                                parseUserProfile(createdJson)
                            } else {
                                UserProfile(
                                    userId = userId,
                                    email = email,
                                    name = name,
                                    profileCompleted = false,
                                    onboardingStep = "INITIAL",
                                    role = "student"
                                )
                            }
                            cachedProfiles[userId] = createdProfile
                            continuation.resume(createdProfile)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            },
            { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        )
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
    ): UserProfile = suspendCancellableCoroutine { continuation ->
        val document = """
            mutation UpdateUser(${'$'}input: UpdateUserInput!) {
                updateUser(input: ${'$'}input) {
                    userId
                    email
                    name
                    preparing
                    onboardingStep
                    profileCompleted
                    profile
                    role
                    lang
                    preferences
                    examTags
                }
            }
        """.trimIndent()

        val backendLang = when ((language ?: "en").trim().lowercase()) {
            "hi", "hindi" -> "hi"
            else -> "en"
        }

        val prefsJson = JSONObject().apply {
            if (!examProfileId.isNullOrBlank()) put("examProfileId", examProfileId)
            if (!examStage.isNullOrBlank()) put("examStage", examStage)
            put("additionalExams", JSONArray(additionalExams))
            put("dateOfBirth", dateOfBirth ?: "")
            put("studyLanguage", language ?: "ENGLISH")
            put("preparing", preparing)
        }.toString()

        val allExamTags = (listOf(preparing) + additionalExams).filter { it.isNotBlank() }.distinct()

        val input = mutableMapOf<String, Any>(
            "userId" to userId,
            "name" to name,
            "preparing" to preparing,
            "onboardingStep" to "COMPLETED",
            "profileCompleted" to true,
            "lang" to backendLang,
            "preferences" to prefsJson,
            "examTags" to allExamTags
        )

        val request = SimpleGraphQLRequest<String>(
            document,
            mapOf("input" to input),
            String::class.java,
            GsonVariablesSerializer()
        )

        Amplify.API.mutate(
            request,
            { response ->
                Log.i("UserProfileRepo", "updateUserProfile response: hasErrors=${response.hasErrors()}, errors=${response.errors}, data=${response.data}")
                if (continuation.isActive) {
                    if (response.hasErrors()) {
                        val errMsg = response.errors.firstOrNull()?.message ?: "Update User GraphQL error"
                        continuation.resumeWithException(Exception(errMsg))
                    } else if (response.data == null || response.data == "null") {
                        continuation.resumeWithException(Exception("Empty GraphQL response on UpdateUser"))
                    } else {
                        try {
                            val json = JSONObject(response.data)
                            val userJson = json.optJSONObject("updateUser")
                            val updatedProfile = if (userJson != null) {
                                parseUserProfile(userJson)
                            } else {
                                UserProfile(
                                    userId = userId,
                                    name = name,
                                    preparing = preparing,
                                    examProfileId = examProfileId,
                                    examStage = examStage,
                                    additionalExams = additionalExams,
                                    examTags = allExamTags,
                                    dateOfBirth = dateOfBirth,
                                    language = language ?: "ENGLISH",
                                    profileCompleted = true,
                                    onboardingStep = "COMPLETED",
                                    role = "student"
                                )
                            }
                            cachedProfiles[userId] = updatedProfile
                            localProfileStore?.saveUserProfile(updatedProfile)
                            coroutineScope.launch {
                                database?.profileDao()?.upsertProfile(EntityMappers.userProfileToEntity(updatedProfile))
                                database?.syncMetadataDao()?.upsertMetadata(
                                    SyncMetadataEntity(
                                        ownerUserId = userId,
                                        resourceType = "USER_PROFILE",
                                        resourceScope = userId,
                                        lastSuccessfulFetchAt = System.currentTimeMillis(),
                                        syncState = "IDLE"
                                    )
                                )
                            }
                            continuation.resume(updatedProfile)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        }
                    }
                }
            },
            { error ->
                Log.e("UserProfileRepo", "updateUserProfile network error", error)
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        )
    }

    private fun parseUserProfile(json: JSONObject): UserProfile {
        var addExams = emptyList<String>()
        var examTagsList = emptyList<String>()
        var dob: String? = null
        var profileId: String? = null
        var stage: String? = null
        var preparingVal = json.optNullableString("preparing")

        // Parse examTags array if present
        if (json.has("examTags") && !json.isNull("examTags")) {
            val tagsArr = json.optJSONArray("examTags")
            if (tagsArr != null) {
                val list = mutableListOf<String>()
                for (i in 0 until tagsArr.length()) {
                    val tag = tagsArr.optString(i)
                    if (tag.isNotBlank()) list.add(tag)
                }
                examTagsList = list
            }
        }

        // Parse preferences JSON
        if (json.has("preferences") && !json.isNull("preferences")) {
            try {
                val prefStr = json.getString("preferences")
                val prefObj = JSONObject(prefStr)
                if (prefObj.has("additionalExams")) {
                    val arr = prefObj.optJSONArray("additionalExams")
                    if (arr != null) {
                        val list = mutableListOf<String>()
                        for (i in 0 until arr.length()) {
                            val item = arr.optString(i)
                            if (item.isNotBlank()) list.add(item)
                        }
                        addExams = list
                    } else {
                        val rawStr = prefObj.optString("additionalExams", "")
                        if (rawStr.isNotBlank()) {
                            val items = rawStr.removeSurrounding("[", "]").split(",").map { it.trim().trim('"', '\'') }.filter { it.isNotEmpty() }
                            addExams = items
                        }
                    }
                }
                if (prefObj.has("dateOfBirth")) {
                    dob = prefObj.optNullableString("dateOfBirth")
                }
                if (prefObj.has("examProfileId")) {
                    profileId = prefObj.optNullableString("examProfileId")
                }
                if (prefObj.has("examStage")) {
                    stage = prefObj.optNullableString("examStage")
                }
                if (prefObj.has("preparing")) {
                    val p = prefObj.optNullableString("preparing")
                    if (!p.isNullOrBlank() && preparingVal.isNullOrBlank()) {
                        preparingVal = p
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors for preferences JSON
            }
        }

        // Parse profile JSON (AWSJSON) if present
        if (json.has("profile") && !json.isNull("profile")) {
            try {
                val profStr = json.getString("profile")
                val profObj = JSONObject(profStr)
                if (profileId.isNullOrBlank() && profObj.has("examProfileId")) {
                    profileId = profObj.optNullableString("examProfileId")
                }
                if (stage.isNullOrBlank() && profObj.has("examStage")) {
                    stage = profObj.optNullableString("examStage")
                }
                if (preparingVal.isNullOrBlank() && profObj.has("preparingFor")) {
                    val pArr = profObj.optJSONArray("preparingFor")
                    if (pArr != null && pArr.length() > 0) {
                        preparingVal = pArr.optString(0)
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }

        // Fallback preparing from examTags or additionalExams
        if (preparingVal.isNullOrBlank()) {
            if (examTagsList.isNotEmpty()) {
                preparingVal = examTagsList.first()
            } else if (addExams.isNotEmpty()) {
                preparingVal = addExams.first()
            }
        }

        val onboardingStepVal = json.optNullableString("onboardingStep")

        val isProfileCompleted = when {
            json.has("profileCompleted") && !json.isNull("profileCompleted") -> {
                val raw = json.opt("profileCompleted")
                raw == true || raw?.toString().equals("true", ignoreCase = true)
            }
            onboardingStepVal?.equals("COMPLETED", ignoreCase = true) == true ||
            onboardingStepVal?.equals("DONE", ignoreCase = true) == true ||
            onboardingStepVal?.equals("COMPLETE", ignoreCase = true) == true -> true
            !json.optNullableString("name").isNullOrBlank() && !preparingVal.isNullOrBlank() -> true
            else -> false
        }

        return UserProfile(
            userId = json.getString("userId"),
            email = json.optNullableString("email"),
            name = json.optNullableString("name"),
            preparing = preparingVal,
            examProfileId = profileId,
            examStage = stage,
            additionalExams = addExams,
            examTags = examTagsList,
            dateOfBirth = dob,
            language = json.optNullableString("lang") ?: "ENGLISH",
            profileCompleted = isProfileCompleted,
            onboardingStep = onboardingStepVal,
            role = json.optNullableString("role") ?: "student"
        )
    }

    private fun JSONObject.optNullableString(name: String): String? {
        if (!this.has(name) || this.isNull(name)) return null
        val value = this.optString(name)
        return if (value == "null" || value.isEmpty()) null else value
    }

    private sealed interface UserProfileResult {
        data class Success(val profile: UserProfile) : UserProfileResult
        data object NotFound : UserProfileResult
        data class Error(val exception: Exception) : UserProfileResult
    }
}
