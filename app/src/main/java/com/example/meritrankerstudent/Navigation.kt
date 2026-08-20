package com.example.meritrankerstudent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.example.meritrankerstudent.ui.auth.*
import com.example.meritrankerstudent.ui.main.MainScreen
import com.example.meritrankerstudent.ui.profile.ProfileSettingsScreen
import com.example.meritrankerstudent.ui.practice.GuidedPracticeDetailScreen
import com.example.meritrankerstudent.ui.practice.QuizListScreen
import com.example.meritrankerstudent.ui.practice.MockListScreen
import com.example.meritrankerstudent.ui.practice.PyqListScreen
import com.example.meritrankerstudent.ui.practice.WrongQuestionsListScreen
import com.example.meritrankerstudent.ui.player.QuestionPlayerScreen
import com.example.meritrankerstudent.ui.player.ResultFeedbackScreen
import com.example.meritrankerstudent.ui.doubt.AskDoubtViewModel
import com.example.meritrankerstudent.ui.doubt.ConversationHistoryScreen

@Composable
fun MainNavigation(
    authViewModel: AuthViewModel = viewModel(),
    askDoubtViewModel: AskDoubtViewModel = viewModel()
) {
    val sessionState by authViewModel.sessionState.collectAsStateWithLifecycle()

    when (val state = sessionState) {
        is SessionState.Initialising -> {
            SplashScreen(statusText = "Initializing MeritRanker...")
        }
        is SessionState.LoadingProfile -> {
            SplashScreen(statusText = "Loading student profile...")
        }
        is SessionState.SignedOut -> {
            LoginScreen(
                onSignInClick = { activity -> authViewModel.signInWithGoogle(activity) },
                error = state.error,
                isSigningIn = state.isSigningIn
            )
        }
        is SessionState.IncompleteProfile -> {
            val onboardingViewModel: OnboardingViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
            val uiState by onboardingViewModel.uiState.collectAsState()

            OnboardingScreen(
                uiState = uiState,
                initialName = state.profile.name ?: state.profile.email?.substringBefore("@") ?: "",
                onExamSelected = { goal -> onboardingViewModel.selectExam(goal) },
                onStageSelected = { option -> onboardingViewModel.selectStageOption(option) },
                onLanguageSelected = { lang -> onboardingViewModel.selectLanguage(lang) },
                onRetry = { onboardingViewModel.retry() },
                onComplete = { name, goal, stage, examProfileId, additionalExams, dateOfBirth, language ->
                    onboardingViewModel.setSaving(true)
                    authViewModel.completeProfile(
                        name = name,
                        preparing = goal,
                        examStage = stage,
                        examProfileId = examProfileId,
                        additionalExams = additionalExams,
                        dateOfBirth = dateOfBirth,
                        language = language,
                        onError = { errMsg ->
                            onboardingViewModel.setSaving(false, errMsg)
                        }
                    )
                },
                onSignOut = { authViewModel.signOut() }
            )
        }
        is SessionState.Error -> {
            SessionErrorScreen(
                message = state.message,
                onRetry = { authViewModel.checkSession() },
                onSignOut = { authViewModel.signOut() }
            )
        }
        is SessionState.Ready -> {
            val backStack = rememberNavBackStack(Main)

            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider =
                entryProvider {
                    entry<Main> {
                        MainScreen(
                            onItemClick = { navKey -> backStack.add(navKey) },
                            modifier = Modifier.safeDrawingPadding(),
                            askDoubtViewModel = askDoubtViewModel
                        )
                    }

                    entry<ProfileSettings> {
                        ProfileSettingsScreen(
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }

                    entry<ConversationHistory> {
                        ConversationHistoryScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onSelectSession = { session ->
                                askDoubtViewModel.selectConversationSession(session)
                            },
                            onNewChatClick = {
                                askDoubtViewModel.startNewChat()
                            },
                            viewModel = askDoubtViewModel
                        )
                    }

                    entry<GuidedPracticeDetail> {
                        GuidedPracticeDetailScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onStartPractice = { playerKey ->
                                backStack.removeLastOrNull()
                                backStack.add(playerKey)
                            }
                        )
                    }

                    entry<QuizList> {
                        QuizListScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onQuizClick = { playerKey -> backStack.add(playerKey) }
                        )
                    }

                    entry<MockList> {
                        MockListScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onMockClick = { playerKey -> backStack.add(playerKey) }
                        )
                    }

                    entry<PyqList> {
                        PyqListScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onPyqClick = { playerKey -> backStack.add(playerKey) }
                        )
                    }

                    entry<WrongQuestionsList> {
                        WrongQuestionsListScreen(
                            onBack = { backStack.removeLastOrNull() },
                            onQuestionClick = { playerKey -> backStack.add(playerKey) }
                        )
                    }

                    entry<QuestionPlayer> { key ->
                        QuestionPlayerScreen(
                            mode = key.mode,
                            id = key.id,
                            onBack = { backStack.removeLastOrNull() },
                            onFinish = { resultKey ->
                                backStack.removeLastOrNull()
                                backStack.add(resultKey)
                            }
                        )
                    }

                    entry<ResultFeedback> { key ->
                        ResultFeedbackScreen(
                            score = key.score,
                            total = key.total,
                            mode = key.mode,
                            id = key.id,
                            onHomeClick = {
                                backStack.removeLastOrNull()
                                backStack.removeLastOrNull()
                            },
                            onReviewClick = {
                                backStack.add(PracticeReview(attemptId = key.id, activityId = key.id))
                            }
                        )
                    }

                    entry<PracticeReview> { key ->
                        com.example.meritrankerstudent.ui.player.PracticeReviewScreen(
                            attemptId = key.attemptId,
                            activityId = key.activityId,
                            onBack = { backStack.removeLastOrNull() }
                        )
                    }

                    entry<SubjectProgress> { key ->
                        com.example.meritrankerstudent.ui.progress.SubjectProgressScreen(
                            subjectId = key.subjectId,
                            subjectName = key.subjectName,
                            examProfileId = key.examProfileId,
                            viewName = key.view,
                            onBack = { backStack.removeLastOrNull() },
                            onReviewClick = { attemptId, activityId ->
                                backStack.add(PracticeReview(attemptId = attemptId, activityId = activityId))
                            }
                        )
                    }
                },
            )
        }
    }
}

@Composable
fun SessionErrorScreen(
    message: String,
    onRetry: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Connection Error",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("Retry Connection", fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(16.dp))
            TextButton(onClick = onSignOut) {
                Text("Sign Out", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        }
    }
}
