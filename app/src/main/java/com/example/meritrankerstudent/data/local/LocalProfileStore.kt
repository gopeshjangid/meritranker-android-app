package com.example.meritrankerstudent.data.local

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.UserProfile
import org.json.JSONArray
import org.json.JSONObject

class LocalProfileStore(private val context: Context) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("meritranker_profile_cache", Context.MODE_PRIVATE)
    }

    suspend fun migrateToRoom(database: AppDatabase) {
        if (prefs.getBoolean("migrated_to_room_v2", false)) {
            return
        }

        try {
            val allKeys = prefs.all.keys
            for (key in allKeys) {
                if (key.startsWith("user_profile_")) {
                    val userId = key.removePrefix("user_profile_")
                    getUserProfile(userId)?.let { profile ->
                        database.profileDao().upsertProfile(EntityMappers.userProfileToEntity(profile))
                        database.syncMetadataDao().upsertMetadata(
                            SyncMetadataEntity(
                                ownerUserId = userId,
                                resourceType = "USER_PROFILE",
                                resourceScope = userId,
                                lastSuccessfulFetchAt = System.currentTimeMillis(),
                                syncState = "IDLE"
                            )
                        )
                        Log.i("LocalProfileStore", "Migrated user profile $userId to Room")
                    }
                } else if (key.startsWith("exam_profile_")) {
                    val examProfileId = key.removePrefix("exam_profile_")
                    getExamProfile(examProfileId)?.let { examProfile ->
                        database.examProfileDao().upsertExamProfile(EntityMappers.examProfileToEntity(examProfile))
                        Log.i("LocalProfileStore", "Migrated exam profile $examProfileId to Room")
                    }
                }
            }
            prefs.edit().putBoolean("migrated_to_room_v2", true).apply()
            Log.i("LocalProfileStore", "One-time migration to Room v2 completed successfully")
        } catch (e: Exception) {
            Log.w("LocalProfileStore", "Profile migration to Room failed: ${e.message}")
        }
    }

    fun saveUserProfile(profile: UserProfile) {
        try {
            val json = JSONObject().apply {
                put("userId", profile.userId)
                put("email", profile.email ?: "")
                put("name", profile.name ?: "")
                put("preparing", profile.preparing ?: "")
                put("examProfileId", profile.examProfileId ?: "")
                put("examStage", profile.examStage ?: "")
                put("additionalExams", JSONArray(profile.additionalExams))
                put("examTags", JSONArray(profile.examTags))
                put("dateOfBirth", profile.dateOfBirth ?: "")
                put("language", profile.language ?: "")
                put("profileCompleted", profile.profileCompleted == true)
                put("onboardingStep", profile.onboardingStep ?: "")
                put("role", profile.role ?: "student")
            }
            prefs.edit()
                .putString("user_profile_${profile.userId}", json.toString())
                .putString("last_active_user_id", profile.userId)
                .apply()
        } catch (_: Exception) {}
    }

    fun getUserProfile(userId: String): UserProfile? {
        val raw = prefs.getString("user_profile_$userId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            val addExams = mutableListOf<String>()
            val tagsArr = json.optJSONArray("additionalExams")
            if (tagsArr != null) {
                for (i in 0 until tagsArr.length()) {
                    addExams.add(tagsArr.getString(i))
                }
            }
            val examTags = mutableListOf<String>()
            val tagsJsonArr = json.optJSONArray("examTags")
            if (tagsJsonArr != null) {
                for (i in 0 until tagsJsonArr.length()) {
                    examTags.add(tagsJsonArr.getString(i))
                }
            }
            UserProfile(
                userId = json.getString("userId"),
                email = json.optString("email").ifBlank { null },
                name = json.optString("name").ifBlank { null },
                preparing = json.optString("preparing").ifBlank { null },
                examProfileId = json.optString("examProfileId").ifBlank { null },
                examStage = json.optString("examStage").ifBlank { null },
                additionalExams = addExams,
                examTags = examTags,
                dateOfBirth = json.optString("dateOfBirth").ifBlank { null },
                language = json.optString("language").ifBlank { null },
                profileCompleted = json.optBoolean("profileCompleted", false),
                onboardingStep = json.optString("onboardingStep").ifBlank { null },
                role = json.optString("role", "student")
            )
        } catch (e: Exception) {
            null
        }
    }

    fun saveExamProfile(profile: ExamProfile) {
        try {
            val json = JSONObject().apply {
                put("examProfileId", profile.examProfileId)
                put("examId", profile.examId)
                put("examName", profile.examName)
                put("stage", profile.stage)
                put("description", profile.description)
                put("totalQuestions", profile.totalQuestions)
                put("totalMarks", profile.totalMarks)
                put("totalTimeMinutes", profile.totalTimeMinutes)
                put("active", profile.active)
            }
            prefs.edit().putString("exam_profile_${profile.examProfileId}", json.toString()).apply()
        } catch (_: Exception) {}
    }

    fun getExamProfile(examProfileId: String): ExamProfile? {
        val raw = prefs.getString("exam_profile_$examProfileId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            ExamProfile(
                examProfileId = json.getString("examProfileId"),
                examId = json.getString("examId"),
                examName = json.getString("examName"),
                stage = json.getString("stage"),
                description = json.optString("description", ""),
                sections = emptyList(),
                totalQuestions = json.optInt("totalQuestions", 0),
                totalMarks = json.optDouble("totalMarks", 0.0),
                totalTimeMinutes = json.optInt("totalTimeMinutes", 0),
                previousCutoff = null,
                active = json.optBoolean("active", true),
                effectiveYear = null,
                createdAt = null,
                updatedAt = null
            )
        } catch (e: Exception) {
            null
        }
    }

    fun clear(userId: String? = null) {
        if (userId != null) {
            prefs.edit().remove("user_profile_$userId").apply()
        } else {
            prefs.edit().clear().apply()
        }
    }
}
