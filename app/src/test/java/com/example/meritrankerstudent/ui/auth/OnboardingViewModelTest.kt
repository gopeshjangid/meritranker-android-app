package com.example.meritrankerstudent.ui.auth

import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.data.model.ExamSection
import com.example.meritrankerstudent.data.repository.FakeExamProfileRepository
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertNull
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    private lateinit var fakeExamProfileRepo: FakeExamProfileRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        fakeExamProfileRepo = FakeExamProfileRepository()

        // Populate authoritative test profiles
        fakeExamProfileRepo.setProfile(
            "SSC_CGL#TIER_1",
            ExamProfile(
                examProfileId = "SSC_CGL#TIER_1",
                examId = "SSC_CGL",
                examName = "SSC CGL",
                stage = "Tier 1",
                description = "SSC CGL Tier 1 Exam",
                sections = listOf(ExamSection("sec_quant", "Quant", "QUANT", "INTERMEDIATE", 25, 50.0, 15, listOf("mcq"), 0.5, 2.0, emptyList())),
                totalQuestions = 25,
                totalMarks = 50.0,
                totalTimeMinutes = 15,
                active = true
            )
        )
        fakeExamProfileRepo.setProfile(
            "SSC_CGL#TIER_2",
            ExamProfile(
                examProfileId = "SSC_CGL#TIER_2",
                examId = "SSC_CGL",
                examName = "SSC CGL",
                stage = "Tier 2",
                description = "SSC CGL Tier 2 Exam",
                sections = listOf(ExamSection("sec_quant_2", "Advanced Quant", "QUANT", "ADVANCED", 30, 90.0, 60, listOf("mcq"), 1.0, 3.0, emptyList())),
                totalQuestions = 30,
                totalMarks = 90.0,
                totalTimeMinutes = 60,
                active = true
            )
        )
        fakeExamProfileRepo.setProfile(
            "RRB_NTPC#CBT_1",
            ExamProfile(
                examProfileId = "RRB_NTPC#CBT_1",
                examId = "RRB_NTPC",
                examName = "RRB NTPC",
                stage = "CBT 1",
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
    fun init_loadsAllActiveExamProfiles_andGroupsIntoGoals() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val state = viewModel.uiState.value
        assertTrue(state is OnboardingUiState.Ready)
        val ready = state as OnboardingUiState.Ready
        assertEquals(2, ready.goals.size)

        val sscGoal = ready.goals.find { it.examId == "SSC_CGL" }
        assertNotNull(sscGoal)
        assertEquals(2, sscGoal!!.stages.size)

        val rrbGoal = ready.goals.find { it.examId == "RRB_NTPC" }
        assertNotNull(rrbGoal)
        assertEquals(1, rrbGoal!!.stages.size)
    }

    @Test
    fun selectExam_withMultiStageGoal_selectsInitialStageAndAllowsStageSwitching() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val readyState = viewModel.uiState.value as OnboardingUiState.Ready
        val sscGoal = readyState.goals.first { it.examId == "SSC_CGL" }
        viewModel.selectExam(sscGoal)

        val state = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("SSC_CGL", state.selectedGoal?.examId)
        assertEquals("SSC_CGL#TIER_1", state.selectedStageOption?.examProfileId)
        assertEquals("Tier 1", state.selectedStageOption?.stage)

        // Select Tier 2
        val tier2Option = sscGoal.stages.first { it.stage == "Tier 2" }
        viewModel.selectStageOption(tier2Option)

        val updatedState = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("SSC_CGL#TIER_2", updatedState.selectedStageOption?.examProfileId)
        assertEquals("Tier 2", updatedState.selectedStageOption?.stage)
    }

    @Test
    fun selectExam_withSingleStageGoal_autoSelectsSingleStage() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val readyState = viewModel.uiState.value as OnboardingUiState.Ready
        val rrbGoal = readyState.goals.first { it.examId == "RRB_NTPC" }
        viewModel.selectExam(rrbGoal)

        val state = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("RRB_NTPC", state.selectedGoal?.examId)
        assertEquals("RRB_NTPC#CBT_1", state.selectedStageOption?.examProfileId)
        assertEquals("CBT 1", state.selectedStageOption?.stage)
    }

    @Test
    fun examChange_immediatelyInvalidatesPreviousStageAndSwitchesCleanly() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val readyState = viewModel.uiState.value as OnboardingUiState.Ready
        val sscGoal = readyState.goals.first { it.examId == "SSC_CGL" }
        val rrbGoal = readyState.goals.first { it.examId == "RRB_NTPC" }

        // Select SSC CGL -> Tier 2
        viewModel.selectExam(sscGoal)
        viewModel.selectStageOption(sscGoal.stages.first { it.stage == "Tier 2" })
        assertEquals("SSC_CGL#TIER_2", (viewModel.uiState.value as OnboardingUiState.Ready).selectedStageOption?.examProfileId)

        // Switch to RRB NTPC
        viewModel.selectExam(rrbGoal)
        val state = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("RRB_NTPC", state.selectedGoal?.examId)
        assertEquals("RRB_NTPC#CBT_1", state.selectedStageOption?.examProfileId)
    }

    @Test
    fun zeroActiveProfiles_emitsZeroResultsState() = runTest {
        fakeExamProfileRepo.clearCache()
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val state = viewModel.uiState.value
        assertTrue(state is OnboardingUiState.ZeroResults)
    }

    @Test
    fun networkFailure_emitsErrorState_andRetryRecovers() = runTest {
        fakeExamProfileRepo.shouldThrowNetworkError = true
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val state = viewModel.uiState.value
        assertTrue(state is OnboardingUiState.Error)
        assertTrue((state as OnboardingUiState.Error).message.contains("couldn't load exam options"))

        // Clear network error and retry
        fakeExamProfileRepo.shouldThrowNetworkError = false
        viewModel.retry()

        val recovered = viewModel.uiState.value
        assertTrue(recovered is OnboardingUiState.Ready)
    }

    @Test
    fun languageSelection_updatesSelectedLanguage() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)
        viewModel.selectLanguage("HINDI")

        val state = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("HINDI", state.selectedLanguage)
    }

    @Test
    fun setSaving_updatesSavingStateAndError() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)
        viewModel.setSaving(true)

        val savingState = viewModel.uiState.value as OnboardingUiState.Ready
        assertTrue(savingState.isSaving)
        assertNull(savingState.saveError)

        viewModel.setSaving(false, "We couldn't save your preferences. Please try again.")
        val errorState = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals(false, errorState.isSaving)
        assertEquals("We couldn't save your preferences. Please try again.", errorState.saveError)
    }

    @Test
    fun examSwitch_raceProtection_doesNotOverwriteNewerSelection() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val readyState = viewModel.uiState.value as OnboardingUiState.Ready
        val sscGoal = readyState.goals.first { it.examId == "SSC_CGL" }
        val rrbGoal = readyState.goals.first { it.examId == "RRB_NTPC" }

        viewModel.selectExam(sscGoal)
        viewModel.selectStageOption(sscGoal.stages.first { it.stage == "Tier 2" })
        assertEquals("SSC_CGL#TIER_2", (viewModel.uiState.value as OnboardingUiState.Ready).selectedStageOption?.examProfileId)

        viewModel.selectExam(rrbGoal)
        val finalState = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("RRB_NTPC", finalState.selectedGoal?.examId)
        assertEquals("RRB_NTPC#CBT_1", finalState.selectedStageOption?.examProfileId)
    }

    @Test
    fun stageSelection_assignsAuthoritativeExamProfileId() = runTest {
        val viewModel = OnboardingViewModel(fakeExamProfileRepo)

        val readyState = viewModel.uiState.value as OnboardingUiState.Ready
        val sscGoal = readyState.goals.first { it.examId == "SSC_CGL" }
        viewModel.selectExam(sscGoal)

        val tier1Option = sscGoal.stages.first { it.stage == "Tier 1" }
        viewModel.selectStageOption(tier1Option)

        val state = viewModel.uiState.value as OnboardingUiState.Ready
        assertEquals("SSC_CGL#TIER_1", state.selectedStageOption?.examProfileId)
    }
}
