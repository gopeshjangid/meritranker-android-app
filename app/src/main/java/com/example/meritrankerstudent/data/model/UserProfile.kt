package com.example.meritrankerstudent.data.model

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val userId: String,
    val email: String? = null,
    val name: String? = null,
    val preparing: String? = null,
    val examProfileId: String? = null,
    val examStage: String? = null,
    val additionalExams: List<String> = emptyList(),
    val examTags: List<String> = emptyList(),
    val dateOfBirth: String? = null,
    val language: String? = "ENGLISH",
    val profileCompleted: Boolean? = null,
    val onboardingStep: String? = null,
    val role: String? = null
)
