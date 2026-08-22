package com.example.meritrankerstudent.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.ResultFeedback
import com.example.meritrankerstudent.data.repository.DefaultPracticeRepository
import com.example.meritrankerstudent.ui.components.richtext.EducationalContentRenderer
import com.example.meritrankerstudent.ui.components.richtext.EducationalInlineText
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionPlayerScreen(
    mode: String,
    id: String,
    onBack: () -> Unit,
    onFinish: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QuestionPlayerViewModel = viewModel(key = "player_${mode}_$id") { QuestionPlayerViewModel(DefaultPracticeRepository(), mode, id) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            uiState.summary?.title ?: "$mode Player",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Exit Player")
                        }
                    },
                    actions = {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(9999.dp),
                            modifier = Modifier.padding(end = 12.dp)
                        ) {
                            Text(
                                text = formatTime(uiState.elapsedSeconds),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (uiState.questions.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        uiState.errorMessage ?: "No practice questions available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadAttempt() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            val question = uiState.questions[uiState.currentIndex]
            val questionId = question.questionId
            val selectedOptionText = uiState.selectedOptions[questionId]
            val isLocked = uiState.lockedQuestions[questionId] == true
            val feedback = uiState.feedback[questionId]
            val scrollState = rememberScrollState()

            // Smooth solution reveal auto-scroll: bounds to explanation card when feedback appears
            LaunchedEffect(uiState.currentIndex, feedback != null) {
                if (feedback != null) {
                    kotlinx.coroutines.delay(120)
                    scrollState.animateScrollTo(scrollState.maxValue)
                }
            }

            Column(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Progress indicator header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Question ${uiState.currentIndex + 1} of ${uiState.questions.size}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        if (uiState.playerMode == PlayerMode.RESUME_ATTEMPT) {
                            Text(
                                text = "Resumed Attempt",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        } else if (uiState.playerMode == PlayerMode.REVIEW_COMPLETED_ATTEMPT) {
                            Text(
                                text = "Review Mode",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    question.subject?.let { sub ->
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = sub,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                LinearProgressIndicator(
                    progress = { (uiState.currentIndex + 1) / uiState.questions.size.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )

                // Error banner if any
                uiState.errorMessage?.let { error ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }

                // Question Box
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        EducationalContentRenderer(
                            content = question.question,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                // Options List
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    question.options.forEachIndexed { optIdx, optionText ->
                        val isSelected = selectedOptionText == optionText
                        val isCorrectOption = feedback?.correctAnswer == optionText
                        val hasCheckedFeedback = feedback != null

                        val cardColor = when {
                            hasCheckedFeedback && isCorrectOption -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerLight
                            hasCheckedFeedback && isSelected && !isCorrectOption -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerLight
                            isSelected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surface
                        }

                        val borderStroke = when {
                            hasCheckedFeedback && isCorrectOption -> BorderStroke(1.5.dp, if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.Success)
                            hasCheckedFeedback && isSelected && !isCorrectOption -> BorderStroke(1.5.dp, if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.Error)
                            isSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                            else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        }

                        val textColor = when {
                            hasCheckedFeedback && isCorrectOption -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessDark
                            hasCheckedFeedback && isSelected && !isCorrectOption -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorDark
                            isSelected -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = !isLocked && uiState.playerMode != PlayerMode.REVIEW_COMPLETED_ATTEMPT && !uiState.isChecking && !uiState.isSubmitting) {
                                    viewModel.selectOption(questionId, optionText)
                                },
                            colors = CardDefaults.cardColors(containerColor = cardColor),
                            border = borderStroke,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${(optIdx + 65).toChar()}.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                EducationalInlineText(
                                    text = optionText,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = textColor,
                                    modifier = Modifier.weight(1f)
                                )

                                if (hasCheckedFeedback) {
                                    if (isCorrectOption) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Correct",
                                            tint = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.Success
                                        )
                                    } else if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Incorrect",
                                            tint = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.Error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Check Answer Button (Quiz Mode)
                if (!isLocked && selectedOptionText != null && uiState.playerMode != PlayerMode.REVIEW_COMPLETED_ATTEMPT) {
                    Button(
                        onClick = { viewModel.checkAnswer(questionId) },
                        enabled = !uiState.isChecking && !uiState.isSubmitting,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (uiState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text("Check Answer", fontWeight = FontWeight.Bold)
                    }
                }

                // Inline Answer Verification Feedback
                if (feedback != null) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (feedback.isCorrect) {
                                if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerLight
                            } else {
                                if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerLight
                            }
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (feedback.isCorrect) Icons.Default.CheckCircle else Icons.Default.Clear,
                                    contentDescription = null,
                                    tint = if (feedback.isCorrect) {
                                        if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.Success
                                    } else {
                                        if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.Error
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (feedback.isCorrect) "Correct Answer!" else "Incorrect",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (feedback.isCorrect) {
                                        if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessDark
                                    } else {
                                        if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorDark
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            EducationalContentRenderer(
                                content = feedback.explanation ?: "",
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation buttons row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { viewModel.previousQuestion() },
                        enabled = uiState.currentIndex > 0 && !uiState.isSubmitting,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Previous")
                    }

                    val isLast = uiState.currentIndex == uiState.questions.size - 1
                    val isReview = uiState.playerMode == PlayerMode.REVIEW_COMPLETED_ATTEMPT
                    val nextButtonText = when {
                        isReview && isLast -> "Done"
                        isLast -> "Submit Attempt"
                        else -> "Next Question"
                    }

                    Button(
                        onClick = {
                            if (isReview && isLast) {
                                onBack()
                            } else if (isLast) {
                                viewModel.submitAttempt { result ->
                                    onFinish(
                                        ResultFeedback(
                                            score = result.score.toInt(),
                                            total = result.maximumScore.toInt(),
                                            mode = mode,
                                            id = id,
                                            attemptId = result.attemptId
                                        )
                                    )
                                }
                            } else {
                                viewModel.nextQuestion()
                            }
                        },
                        enabled = !uiState.isSubmitting && !uiState.isChecking,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLast) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                            contentColor = if (isLast) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (uiState.isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(nextButtonText, fontWeight = FontWeight.Bold)
                        if (!isLast && !uiState.isSubmitting) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(imageVector = Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", mins, secs)
}
