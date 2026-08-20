package com.example.meritrankerstudent.data.repository

import com.example.meritrankerstudent.data.model.ExamCategoryCutoff
import com.example.meritrankerstudent.data.model.ExamPreviousCutoff
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.ExamSection
import com.example.meritrankerstudent.data.model.ExamSectionCutoff
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExamProfileRepositoryTest {

    private lateinit var fakeRepository: FakeExamProfileRepository

    @Before
    fun setUp() {
        fakeRepository = FakeExamProfileRepository()
    }

    @Test
    fun canonicalExamProfileId_generatesUpperUnderscoreFormat() {
        assertEquals("SSC_CGL#TIER_1", DefaultExamProfileRepository.canonicalExamProfileId("ssc-cgl", "Tier 1"))
        assertEquals("SBI_PO#PRELIMS", DefaultExamProfileRepository.canonicalExamProfileId("sbi-po", "Prelims"))
        assertEquals("RRB_NTPC#CBT_1", DefaultExamProfileRepository.canonicalExamProfileId("rrb-ntpc", "CBT 1"))
    }

    @Test
    fun getExamProfile_forValidCanonicalId_returnsExamProfileWithSections() = runTest {
        fakeRepository.setProfile(
            "SSC_CGL#TIER_1",
            ExamProfile(
                examProfileId = "SSC_CGL#TIER_1",
                examId = "SSC_CGL",
                examName = "SSC CGL",
                stage = "TIER_1",
                description = "SSC CGL Tier 1 Examination",
                sections = listOf(
                    ExamSection("sec_quant", "Quant", "QUANT", "INTERMEDIATE", 25, 50.0, 15, listOf("mcq"), 0.5, 2.0, emptyList()),
                    ExamSection("sec_reasoning", "Reasoning", "REASONING", "INTERMEDIATE", 25, 50.0, 15, listOf("mcq"), 0.5, 2.0, emptyList())
                ),
                totalQuestions = 50,
                totalMarks = 100.0,
                totalTimeMinutes = 30,
                previousCutoff = ExamPreviousCutoff(2025, 135.5, listOf(ExamCategoryCutoff("UR", 135.5)), listOf(ExamSectionCutoff("sec_quant", 20.0))),
                active = true,
                effectiveYear = 2026
            )
        )

        val profile = fakeRepository.getExamProfile("SSC_CGL#TIER_1")
        assertNotNull(profile)
        assertEquals("SSC_CGL#TIER_1", profile?.examProfileId)
        assertEquals("SSC CGL", profile?.examName)
        assertEquals(2, profile?.sections?.size)
        assertEquals(50, profile?.totalQuestions)
        assertEquals(135.5, profile?.previousCutoff?.overall ?: 0.0, 0.01)
    }

    @Test
    fun getExamProfile_forInactiveProfile_returnsNull() = runTest {
        fakeRepository.setProfile(
            "OLD_EXAM#TIER_1",
            ExamProfile(
                examProfileId = "OLD_EXAM#TIER_1",
                examId = "OLD_EXAM",
                examName = "Old Exam",
                stage = "TIER_1",
                description = "Deprecated Exam",
                sections = emptyList(),
                totalQuestions = 0,
                totalMarks = 0.0,
                totalTimeMinutes = 0,
                active = false
            )
        )

        val profile = fakeRepository.getExamProfile("OLD_EXAM#TIER_1")
        assertNull(profile)
    }

    @Test
    fun getExamProfile_forUnknownExamId_returnsNullWithoutFallback() = runTest {
        val profile = fakeRepository.getExamProfile("UNKNOWN_EXAM#TIER_1")
        assertNull(profile)
    }

    @Test
    fun fetchExamProfileForUser_forUnknownExam_returnsNullWithoutFallback() = runTest {
        val profile = fakeRepository.fetchExamProfileForUser("unknown-exam-99", "Tier 1")
        assertNull(profile)
    }

    @Test(expected = Exception::class)
    fun getExamProfile_onNetworkError_throwsException() = runTest {
        fakeRepository.shouldThrowNetworkError = true
        fakeRepository.getExamProfile("SSC_CGL#TIER_1")
    }
}

class FakeExamProfileRepository : ExamProfileRepository {
    private val profiles = mutableMapOf<String, ExamProfile>()
    var shouldThrowNetworkError: Boolean = false
    var cacheCleared: Boolean = false

    fun setProfile(id: String, profile: ExamProfile) {
        profiles[id] = profile
    }

    override fun clearCache() {
        cacheCleared = true
        profiles.clear()
    }

    override fun observeExamProfile(examProfileId: String): Flow<ExamProfile?> {
        val p = profiles[examProfileId]
        return flowOf(if (p != null && p.active) p else null)
    }

    override fun observeAllActiveExamProfiles(): Flow<List<ExamProfile>> {
        return flowOf(profiles.values.filter { it.active }.toList())
    }

    override suspend fun getExamProfile(examProfileId: String, forceRefresh: Boolean): ExamProfile? {
        if (shouldThrowNetworkError) throw Exception("GraphQL Network Connection Timeout")
        val p = profiles[examProfileId] ?: return null
        return if (p.active) p else null
    }

    override suspend fun listActiveProfilesForExam(examId: String): List<ExamProfile> {
        if (shouldThrowNetworkError) throw Exception("GraphQL Network Connection Timeout")
        val canonicalExam = DefaultExamProfileRepository.canonical(examId)
        return profiles.values.filter {
            it.examId.equals(canonicalExam, ignoreCase = true) && it.active
        }
    }

    override suspend fun listAllActiveExamProfiles(forceRefresh: Boolean): List<ExamProfile> {
        if (shouldThrowNetworkError) throw Exception("GraphQL Network Connection Timeout")
        return profiles.values.filter { it.active }.toList()
    }

    override suspend fun fetchExamProfileForUser(preparing: String, stage: String?): ExamProfile? {
        if (shouldThrowNetworkError) throw Exception("GraphQL Network Connection Timeout")
        val canonicalId = DefaultExamProfileRepository.canonicalExamProfileId(preparing, stage ?: "Tier 1")
        val exact = profiles[canonicalId]
        if (exact != null && exact.active) return exact
        return null
    }
}
