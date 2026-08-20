package com.example.meritrankerstudent.ui.player

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meritrankerstudent.data.repository.DefaultPracticeRepository
import com.example.meritrankerstudent.ui.components.richtext.EducationalContentRenderer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeReviewScreen(
    attemptId: String,
    activityId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeReviewViewModel = viewModel {
        PracticeReviewViewModel(DefaultPracticeRepository(), attemptId, activityId)
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isDark = isSystemInDarkTheme()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Detailed Solutions & Review", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
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
                        uiState.errorMessage ?: "No review data available.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = { viewModel.loadReview() }) {
                        Text("Retry")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                itemsIndexed(uiState.questions) { index, item ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Question ${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )

                                val isCorrect = item.isCorrect ?: (item.selectedOption == item.correctAnswer)
                                val statusText = when {
                                    item.selectedOption == null -> "Unanswered"
                                    isCorrect -> "Correct (+1)"
                                    else -> "Incorrect"
                                }
                                val statusColor = when {
                                    item.selectedOption == null -> MaterialTheme.colorScheme.onSurfaceVariant
                                    isCorrect -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessDark
                                    else -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorDark
                                }

                                Surface(
                                    color = statusColor.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(9999.dp)
                                ) {
                                    Text(
                                        text = statusText,
                                        color = statusColor,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            EducationalContentRenderer(
                                content = item.question,
                                modifier = Modifier.fillMaxWidth()
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Options breakdown
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                item.options.forEachIndexed { optIdx, optText ->
                                    val isUserChoice = item.selectedOption == optText
                                    val isCorrect = item.correctAnswer == optText

                                    val bg = when {
                                        isCorrect -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerLight
                                        isUserChoice && !isCorrect -> if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerLight
                                        else -> MaterialTheme.colorScheme.surface
                                    }
                                    val border = when {
                                        isCorrect -> BorderStroke(1.5.dp, if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.Success)
                                        isUserChoice && !isCorrect -> BorderStroke(1.5.dp, if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.Error)
                                        else -> BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                                    }

                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        color = bg,
                                        border = border,
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${(optIdx + 65).toChar()}.  $optText",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = if (isCorrect) {
                                                    if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessDark
                                                } else if (isUserChoice) {
                                                    if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorDark
                                                } else MaterialTheme.colorScheme.onSurface,
                                                modifier = Modifier.weight(1f)
                                            )
                                            if (isCorrect) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = "Correct",
                                                    tint = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.Success,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            } else if (isUserChoice) {
                                                Icon(
                                                    imageVector = Icons.Default.Clear,
                                                    contentDescription = "Your choice",
                                                    tint = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.Error,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            // Explanation
                            item.explanation?.let { expl ->
                                Spacer(modifier = Modifier.height(14.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            text = "Explanation",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        EducationalContentRenderer(
                                            content = expl,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
