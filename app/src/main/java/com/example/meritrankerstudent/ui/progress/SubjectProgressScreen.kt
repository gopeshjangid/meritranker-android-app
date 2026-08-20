package com.example.meritrankerstudent.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.data.model.GetStudentPerformanceView
import com.example.meritrankerstudent.data.model.StudentPerformanceInsight
import com.example.meritrankerstudent.data.model.StudentPerformanceItem
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubjectProgressScreen(
    subjectId: String,
    subjectName: String,
    examProfileId: String,
    viewName: String,
    onBack: () -> Unit,
    onReviewClick: (attemptId: String, activityId: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    viewModel: SubjectProgressViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val performanceView = try {
        GetStudentPerformanceView.valueOf(viewName)
    } catch (e: Exception) {
        GetStudentPerformanceView.PRACTICE
    }

    LaunchedEffect(subjectId, examProfileId, performanceView) {
        viewModel.loadSubjectPerformance(
            examProfileId = examProfileId,
            subjectId = subjectId,
            view = performanceView
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = subjectName,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = if (performanceView == GetStudentPerformanceView.PRACTICE) "Practice Analytics" else "Exam Analytics",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        viewModel.loadSubjectPerformance(
                            examProfileId = examProfileId,
                            subjectId = subjectId,
                            view = performanceView,
                            isRefresh = true
                        )
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Subject Progress")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        modifier = modifier
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val state = uiState) {
                is SubjectProgressUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is SubjectProgressUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.loadSubjectPerformance(
                                examProfileId = examProfileId,
                                subjectId = subjectId,
                                view = performanceView
                            )
                        }) {
                            Text("Retry")
                        }
                    }
                }
                is SubjectProgressUiState.Content -> {
                    SubjectProgressContent(
                        subjectItem = state.subjectItem,
                        insights = state.insights,
                        selectedView = state.selectedView,
                        isUpdating = state.isUpdatingLatestPerformance,
                        isRefreshing = state.isRefreshing,
                        onReviewClick = onReviewClick
                    )
                }
            }
        }
    }
}

@Composable
private fun SubjectProgressContent(
    subjectItem: StudentPerformanceItem?,
    insights: List<StudentPerformanceInsight>,
    selectedView: GetStudentPerformanceView,
    isUpdating: Boolean,
    isRefreshing: Boolean,
    onReviewClick: (attemptId: String, activityId: String) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        if (isUpdating || isRefreshing) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "Updating latest progress...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Subject Summary Card
        if (subjectItem != null) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Subject Accuracy",
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                )
                                Text(
                                    text = "${subjectItem.accuracy.toInt()}%",
                                    style = MaterialTheme.typography.headlineLarge.copy(
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                )
                            }
                            if (subjectItem.label.isNotBlank()) {
                                PerformanceLabelPill(label = subjectItem.label)
                            }
                        }

                        if (subjectItem.comment.isNotBlank()) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Info,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = subjectItem.comment,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurface,
                                            lineHeight = 18.sp
                                        )
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Attempted", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${subjectItem.attempted}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Correct", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${subjectItem.correct}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = com.example.meritrankerstudent.theme.MeritRankerColors.Success)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Wrong", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${subjectItem.wrong}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                            }
                            if (selectedView == GetStudentPerformanceView.REAL_EXAM && subjectItem.skipped != null) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Skipped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("${subjectItem.skipped}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Section Title: Diagnostic Insights & Patterns
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Diagnostic Question Insights",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = "${insights.size} insights",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Insights Cards List
        if (insights.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No diagnostic insights recorded yet for this subject.",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        } else {
            items(insights) { insight ->
                InsightEvidenceCard(
                    insight = insight,
                    onReviewClick = onReviewClick
                )
            }
        }
    }
}

@Composable
private fun InsightEvidenceCard(
    insight: StudentPerformanceInsight,
    onReviewClick: (attemptId: String, activityId: String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Descriptor & Result Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = insight.descriptor,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                ResultTypeBadge(resultType = insight.resultType)
            }

            // Given & Find Box
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (insight.given.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "Given: ",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Text(
                                text = insight.given,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                    if (insight.find.isNotBlank()) {
                        Row(verticalAlignment = Alignment.Top) {
                            Text(
                                text = "To Find: ",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                            Text(
                                text = insight.find,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }

            // Common Trap Card (if present)
            if (!insight.commonTrap.isNullOrBlank()) {
                val isDark = isSystemInDarkTheme()
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeContainerLight,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Default.Warning,
                            contentDescription = "Common Trap",
                            tint = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeLight else com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Column {
                            Text(
                                text = "Common Trap",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeLight else com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeDark
                                )
                            )
                            Text(
                                text = insight.commonTrap,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurface,
                                    lineHeight = 16.sp
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultTypeBadge(resultType: String) {
    val isDark = isSystemInDarkTheme()
    val (bg, textColor) = when (resultType.uppercase(Locale.ROOT)) {
        "CORRECT" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessDark)
        "WRONG", "INCORRECT" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorDark)
        "SKIPPED", "UNANSWERED" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandPurpleContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.BrandPurpleContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandPurpleLight else com.example.meritrankerstudent.theme.MeritRankerColors.BrandPurple)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bg
    ) {
        Text(
            text = resultType.uppercase(Locale.ROOT),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}
