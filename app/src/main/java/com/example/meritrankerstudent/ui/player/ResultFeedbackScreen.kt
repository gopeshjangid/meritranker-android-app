package com.example.meritrankerstudent.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meritrankerstudent.util.review.OutcomeType
import com.example.meritrankerstudent.util.review.PlayReviewCoordinator
import com.example.meritrankerstudent.util.review.ReviewTriggerMoment
import kotlinx.coroutines.delay

data class FeedbackBand(
    val badge: String,
    val headline: String,
    val diagnostic: String,
    val nextActionHint: String
)

object DiagnosticFeedbackEngine {
    fun getBand(percentage: Int): FeedbackBand {
        return when {
            percentage >= 85 -> FeedbackBand(
                badge = "🎯 Mastery Cleared",
                headline = "Outstanding work — strong command demonstrated!",
                diagnostic = "You have demonstrated high accuracy and sharp conceptual precision. Keep up this momentum with regular revision.",
                nextActionHint = "Lock in your mastery or explore advanced practice sets."
            )
            percentage >= 70 -> FeedbackBand(
                badge = "🚀 High Exam Readiness",
                headline = "Strong performance — almost at the top bracket!",
                diagnostic = "Great conceptual grasp across most questions. Focusing on tricky edge cases and timing will push you into top percentiles.",
                nextActionHint = "Review explanations for missed items to solidify high-yield patterns."
            )
            percentage >= 50 -> FeedbackBand(
                badge = "📈 Solid Progress",
                headline = "Good progress — moving steadily forward!",
                diagnostic = "You have solid foundational clarity. Reviewing the step-by-step solutions for missed questions will convert these into reliable marks.",
                nextActionHint = "Analyze the solutions below and re-test this topic soon."
            )
            percentage >= 30 -> FeedbackBand(
                badge = "🌱 Building Foundation",
                headline = "Good start — now let's strengthen key areas!",
                diagnostic = "Every attempt gives valuable diagnostic data. The step-by-step solutions will show exactly how each equation and concept connects.",
                nextActionHint = "Read through the solutions carefully, then try a quick focused set."
            )
            else -> FeedbackBand(
                badge = "💡 Learning Opportunity",
                headline = "Valuable diagnostic — let's rebuild step by step!",
                diagnostic = "Starting from fundamentals is the most reliable path. Walking through the detailed solutions will help you master the core steps.",
                nextActionHint = "Start with the solution walkthroughs below to build clarity."
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultFeedbackScreen(
    score: Int,
    total: Int,
    mode: String,
    id: String,
    onHomeClick: () -> Unit,
    onReviewClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val percentage = if (total > 0) (score * 100) / total else 0
    val band = DiagnosticFeedbackEngine.getBand(percentage)

    LaunchedEffect(Unit) {
        val coordinator = PlayReviewCoordinator.getInstance(context)
        coordinator.recordMeaningfulOutcome(OutcomeType.PRACTICE_COMPLETED)

        // Non-blocking user review prompt check
        delay(1200L)
        val activity = context.findActivity()
        if (activity != null) {
            coordinator.maybeRequestReview(activity, ReviewTriggerMoment.PRACTICE_RESULT_VIEWED)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Summary", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onHomeClick) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back to Practice")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Motivational Hero Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    1.5.dp,
                    Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Badge Chip
                    Surface(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = band.badge,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = band.headline,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = band.diagnostic,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }
            }

            // 2. Performance Diagnostic Metrics (Calm & Objective)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SESSION SUMMARY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Correct items
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(com.example.meritrankerstudent.theme.MeritRankerColors.Success.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = com.example.meritrankerstudent.theme.MeritRankerColors.Success,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "$score / $total",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Correct Questions",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Accuracy rate
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "$percentage%",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Accuracy Rate",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // 3. Recommended Next Steps Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "💡", fontSize = 18.sp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = band.nextActionHint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. Action Buttons
            if (onReviewClick != null) {
                Button(
                    onClick = onReviewClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    Icon(imageVector = Icons.Default.List, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Review Solutions & Explanations", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedButton(
                onClick = onHomeClick,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                contentPadding = PaddingValues(14.dp)
            ) {
                Icon(imageVector = Icons.Default.Home, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Back to Practice Hub", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
