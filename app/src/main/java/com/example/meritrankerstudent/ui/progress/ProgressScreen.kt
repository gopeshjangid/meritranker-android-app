package com.example.meritrankerstudent.ui.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.SubjectProgress
import com.example.meritrankerstudent.data.model.GetStudentPerformanceView
import com.example.meritrankerstudent.data.model.StudentPerformanceItem
import com.example.meritrankerstudent.data.model.StudentPerformanceOverall
import com.example.meritrankerstudent.data.model.StudentPerformanceResponse
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProgressViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedView by viewModel.selectedView.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val selectedExamProfile by viewModel.selectedExamProfile.collectAsStateWithLifecycle()
    val availableExamProfiles by viewModel.availableExamProfiles.collectAsStateWithLifecycle()
    val isHindi = selectedLanguage.startsWith("hi", ignoreCase = true)

    Scaffold(
        topBar = {
            com.example.meritrankerstudent.ui.components.MeritRankerTopBar(
                title = if (isHindi) "अधिगम प्रगति" else "Progress",
                subtitle = if (isHindi) "विश्लेषण व सुधार" else "Analytics & Mastery",
                selectedExamProfile = selectedExamProfile,
                availableExamProfiles = availableExamProfiles,
                onExamProfileSelected = { viewModel.selectExamProfile(it) },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = if (isHindi) "प्रगति ताज़ा करें" else "Refresh Progress"
                        )
                    }
                }
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
                is ProgressUiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is ProgressUiState.Empty -> {
                    EmptyProgressView(
                        examName = state.examName,
                        selectedView = selectedView,
                        onSwitchView = { viewModel.switchView(it) },
                        message = state.message,
                        isHindi = isHindi
                    )
                }
                is ProgressUiState.Error -> {
                    ErrorProgressView(
                        message = state.message,
                        onRetry = { viewModel.loadPerformance() },
                        isHindi = isHindi
                    )
                }
                is ProgressUiState.Content -> {
                    ProgressContent(
                        response = state.response,
                        selectedView = state.selectedView,
                        examName = state.examName,
                        examProfileId = state.examProfileId,
                        isRefreshing = state.isRefreshing,
                        isHindi = isHindi,
                        onSwitchView = { viewModel.switchView(it) },
                        onSubjectClick = { item ->
                            onItemClick(
                                SubjectProgress(
                                    subjectId = item.subjectId,
                                    subjectName = item.subjectName,
                                    examProfileId = state.examProfileId,
                                    view = state.selectedView.name
                                )
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressContent(
    response: StudentPerformanceResponse,
    selectedView: GetStudentPerformanceView,
    examName: String,
    examProfileId: String,
    isRefreshing: Boolean,
    isHindi: Boolean,
    onSwitchView: (GetStudentPerformanceView) -> Unit,
    onSubjectClick: (StudentPerformanceItem) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp)
    ) {
        // Exam Title & Mode Selector
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Exam pill
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = examName,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                // View Switcher (PRACTICE vs REAL_EXAM)
                SingleChoiceSegmentedButtonRow(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    SegmentedButton(
                        selected = selectedView == GetStudentPerformanceView.PRACTICE,
                        onClick = { onSwitchView(GetStudentPerformanceView.PRACTICE) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {
                            if (selectedView == GetStudentPerformanceView.PRACTICE) {
                                SegmentedButtonDefaults.Icon(active = true)
                            }
                        }
                    ) {
                        Text(if (isHindi) "अभ्यास (Practice)" else "Practice")
                    }
                    SegmentedButton(
                        selected = selectedView == GetStudentPerformanceView.REAL_EXAM,
                        onClick = { onSwitchView(GetStudentPerformanceView.REAL_EXAM) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {
                            if (selectedView == GetStudentPerformanceView.REAL_EXAM) {
                                SegmentedButtonDefaults.Icon(active = true)
                            }
                        }
                    ) {
                        Text(if (isHindi) "मॉक व टेस्ट (Mocks)" else "Mocks & Tests")
                    }
                }
            }
        }

        // Updating Indicator Banner (Non-blocking)
        if (response.isUpdatingLatestPerformance || isRefreshing) {
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
                            text = if (isHindi) "नवीनतम प्रगति अपडेट हो रही है..." else "Updating latest progress...",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.Medium
                            )
                        )
                    }
                }
            }
        }

        // Overall Performance Card
        item {
            OverallProgressCard(
                overall = response.overall,
                selectedView = selectedView,
                isHindi = isHindi
            )
        }

        // Subject Breakdown Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (isHindi) "विषयवार प्रदर्शन (Subject Performance)" else "Subject Performance",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                )
                Text(
                    text = if (isHindi) "${response.items.size} विषय" else "${response.items.size} subjects",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }

        // Subject Items List
        if (response.items.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (isHindi) "अभी तक कोई विषयवार गतिविधि दर्ज नहीं हुई है।" else "No subject activity recorded yet.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(response.items, key = { it.subjectId }) { item ->
                SubjectPerformanceRow(
                    item = item,
                    selectedView = selectedView,
                    isHindi = isHindi,
                    onClick = { onSubjectClick(item) }
                )
            }
        }
    }
}

@Composable
private fun OverallProgressCard(
    overall: StudentPerformanceOverall,
    selectedView: GetStudentPerformanceView,
    isHindi: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Accuracy + Status pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isHindi) "कुल सटीकता (Overall Accuracy)" else "Overall Accuracy",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    Text(
                        text = "${overall.accuracy.toInt()}%",
                        style = MaterialTheme.typography.headlineLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    )
                }

                if (overall.label.isNotBlank()) {
                    PerformanceLabelPill(label = overall.label, isHindi = isHindi)
                }
            }

            // Authoritative comment
            if (overall.comment.isNotBlank()) {
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
                            text = overall.comment,
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                                lineHeight = 18.sp
                            )
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Supporting metrics grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetricSummaryItem(
                    label = if (isHindi) "हल किए गए" else "Attempted",
                    value = "${overall.attempted}",
                    icon = Icons.AutoMirrored.Filled.Assignment
                )
                MetricSummaryItem(
                    label = if (isHindi) "सही उत्तर" else "Correct",
                    value = "${overall.correct}",
                    icon = Icons.Default.CheckCircle,
                    color = com.example.meritrankerstudent.theme.MeritRankerColors.Success
                )
                MetricSummaryItem(
                    label = if (isHindi) "गलत उत्तर" else "Wrong",
                    value = "${overall.wrong}",
                    icon = Icons.Default.Cancel,
                    color = MaterialTheme.colorScheme.error
                )
                if (selectedView == GetStudentPerformanceView.REAL_EXAM && overall.skipped != null) {
                    MetricSummaryItem(
                        label = if (isHindi) "छोड़े गए" else "Skipped",
                        value = "${overall.skipped}",
                        icon = Icons.Default.RemoveCircleOutline,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Pacing info if REAL_EXAM
            if (selectedView == GetStudentPerformanceView.REAL_EXAM && overall.averageTimeMs != null) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = if (isHindi) "औसत गति: ${formatTimeSeconds(overall.averageTimeMs)}" else "Avg Pace: ${formatTimeSeconds(overall.averageTimeMs)}",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontWeight = FontWeight.Medium
                            )
                        )
                        if (overall.targetPaceMs != null) {
                            Text(
                                text = if (isHindi) "(लक्ष्य: ${formatTimeSeconds(overall.targetPaceMs)})" else "(Target: ${formatTimeSeconds(overall.targetPaceMs)})",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }

                    if (!overall.speedLabel.isNullOrBlank()) {
                        Text(
                            text = overall.speedLabel,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SubjectPerformanceRow(
    item: StudentPerformanceItem,
    selectedView: GetStudentPerformanceView,
    isHindi: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.5.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.subjectName,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (isHindi) "${item.attempted} प्रश्न" else "${item.attempted} questions",
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                    if (item.label.isNotBlank()) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall.copy(
                                color = MaterialTheme.colorScheme.outline
                            )
                        )
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Medium,
                                color = labelColor(item.label)
                            )
                        )
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "${item.accuracy.toInt()}%",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                )
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = if (isHindi) "विवरण देखें" else "View Subject Details",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MetricSummaryItem(
    label: String,
    value: String,
    icon: ImageVector,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = color.copy(alpha = 0.8f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = color
            )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        )
    }
}

@Composable
fun PerformanceLabelPill(label: String, isHindi: Boolean = false) {
    val isDark = isSystemInDarkTheme()
    val (bg, textColor) = when (label.lowercase(Locale.ROOT)) {
        "strong" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.SuccessLight else com.example.meritrankerstudent.theme.MeritRankerColors.SuccessDark)
        "good" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandBlueContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.BrandBlueContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandBlueLight else com.example.meritrankerstudent.theme.MeritRankerColors.BrandBlueDark)
        "needs practice" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeLight else com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrangeDark)
        "needs focus" -> (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerDark else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorContainerLight) to (if (isDark) com.example.meritrankerstudent.theme.MeritRankerColors.ErrorLight else com.example.meritrankerstudent.theme.MeritRankerColors.ErrorDark)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    val displayLabel = if (isHindi) {
        when (label.lowercase(Locale.ROOT)) {
            "strong" -> "मजबूत (Strong)"
            "good" -> "अच्छा (Good)"
            "needs practice" -> "अभ्यास आवश्यक"
            "needs focus" -> "अधिक ध्यान दें"
            else -> label
        }
    } else label

    Surface(
        shape = CircleShape,
        color = bg,
        modifier = Modifier.border(0.5.dp, textColor.copy(alpha = 0.3f), CircleShape)
    ) {
        Text(
            text = displayLabel,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        )
    }
}

private fun labelColor(label: String): Color {
    return when (label.lowercase(Locale.ROOT)) {
        "strong" -> com.example.meritrankerstudent.theme.MeritRankerColors.Success
        "good" -> com.example.meritrankerstudent.theme.MeritRankerColors.BrandBlue
        "needs practice" -> com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange
        "needs focus" -> com.example.meritrankerstudent.theme.MeritRankerColors.Error
        else -> com.example.meritrankerstudent.theme.MeritRankerColors.Slate400
    }
}

private fun formatTimeSeconds(timeMs: Long): String {
    val seconds = (timeMs / 1000).coerceAtLeast(0)
    return "${seconds}s"
}

@Composable
private fun EmptyProgressView(
    examName: String,
    selectedView: GetStudentPerformanceView,
    onSwitchView: (GetStudentPerformanceView) -> Unit,
    message: String,
    isHindi: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.AutoMirrored.Filled.TrendingUp,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isHindi) "अभी तक कोई प्रगति डेटा नहीं है" else "No Progress Data Yet",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (isHindi) "अभ्यास पूरा करने के बाद आपकी प्रगति यहाँ दिखाई देगी।" else message,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
    }
}

@Composable
private fun ErrorProgressView(
    message: String,
    onRetry: () -> Unit,
    isHindi: Boolean = false
) {
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
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isHindi) "प्रगति लोड करने में त्रुटि" else "Failed to load progress",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Text(if (isHindi) "पुनः प्रयास करें" else "Retry")
        }
    }
}
