package com.example.meritrankerstudent.ui.progress

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.QuizList
import com.example.meritrankerstudent.SubjectProgress
import com.example.meritrankerstudent.data.model.ExamProfile
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
    val isRefreshing = (uiState as? ProgressUiState.Content)?.isRefreshing ?: false

    Surface(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        color = MaterialTheme.colorScheme.background
    ) {
        when (val state = uiState) {
            is ProgressUiState.Loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ProgressContentHeader(
                        selectedExamProfile = selectedExamProfile,
                        availableExamProfiles = availableExamProfiles,
                        onExamProfileSelected = { viewModel.selectExamProfile(it) },
                        isHindi = isHindi,
                        isRefreshing = true,
                        onRefresh = { viewModel.refresh() }
                    )
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            is ProgressUiState.Empty -> {
                EmptyProgressView(
                    selectedExamProfile = selectedExamProfile,
                    availableExamProfiles = availableExamProfiles,
                    onExamProfileSelected = { viewModel.selectExamProfile(it) },
                    selectedView = selectedView,
                    onSwitchView = { viewModel.switchView(it) },
                    onStartPractice = { onItemClick(QuizList) },
                    message = state.message,
                    isHindi = isHindi,
                    isRefreshing = isRefreshing,
                    onRefresh = { viewModel.refresh() }
                )
            }
            is ProgressUiState.Error -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    ProgressContentHeader(
                        selectedExamProfile = selectedExamProfile,
                        availableExamProfiles = availableExamProfiles,
                        onExamProfileSelected = { viewModel.selectExamProfile(it) },
                        isHindi = isHindi,
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refresh() }
                    )
                    ErrorProgressView(
                        message = state.message,
                        onRetry = { viewModel.loadPerformance() },
                        isHindi = isHindi
                    )
                }
            }
            is ProgressUiState.Content -> {
                ProgressContent(
                    response = state.response,
                    selectedView = state.selectedView,
                    selectedExamProfile = selectedExamProfile,
                    availableExamProfiles = availableExamProfiles,
                    onExamProfileSelected = { viewModel.selectExamProfile(it) },
                    examName = state.examName,
                    examProfileId = state.examProfileId,
                    isRefreshing = state.isRefreshing,
                    isHindi = isHindi,
                    onRefresh = { viewModel.refresh() },
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

@Composable
private fun ProgressContentHeader(
    selectedExamProfile: ExamProfile?,
    availableExamProfiles: List<ExamProfile>,
    onExamProfileSelected: (ExamProfile) -> Unit,
    isHindi: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Title Row with Refresh Action
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = if (isHindi) "अधिगम प्रगति" else "Progress",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.5).sp
                    )
                )
                Text(
                    text = if (isHindi) "विश्लेषण व अपनी तैयारी का आकलन करें" else "Your performance at a glance",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isHindi) "प्रगति ताज़ा करें" else "Refresh Progress",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Authoritative Dynamic ExamProfile Context & Selector
        if (availableExamProfiles.isNotEmpty() || selectedExamProfile != null) {
            val displayLabel = remember(selectedExamProfile) {
                if (selectedExamProfile != null) {
                    "${selectedExamProfile.examName} · ${selectedExamProfile.stage}"
                } else {
                    "Select Exam"
                }
            }
            var showMenu by remember { mutableStateOf(false) }

            Box {
                Surface(
                    onClick = { if (availableExamProfiles.isNotEmpty()) showMenu = true },
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                    enabled = availableExamProfiles.isNotEmpty()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = displayLabel,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (availableExamProfiles.size > 1) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Switch Exam Profile",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false },
                    shape = RoundedCornerShape(12.dp),
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    availableExamProfiles.forEach { profile ->
                        val isSelected = profile.examProfileId == selectedExamProfile?.examProfileId
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = profile.examName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                        )
                                    )
                                    Text(
                                        text = profile.stage,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            },
                            trailingIcon = if (isSelected) {
                                {
                                    Icon(
                                        Icons.Default.Check,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            } else null,
                            onClick = {
                                showMenu = false
                                onExamProfileSelected(profile)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressContent(
    response: StudentPerformanceResponse,
    selectedView: GetStudentPerformanceView,
    selectedExamProfile: ExamProfile?,
    availableExamProfiles: List<ExamProfile>,
    onExamProfileSelected: (ExamProfile) -> Unit,
    examName: String,
    examProfileId: String,
    isRefreshing: Boolean,
    isHindi: Boolean,
    onRefresh: () -> Unit,
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
        // Content-level Header
        item {
            ProgressContentHeader(
                selectedExamProfile = selectedExamProfile,
                availableExamProfiles = availableExamProfiles,
                onExamProfileSelected = onExamProfileSelected,
                isHindi = isHindi,
                isRefreshing = isRefreshing,
                onRefresh = onRefresh
            )
        }

        // View Switcher (PRACTICE vs REAL_EXAM)
        item {
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
    selectedExamProfile: ExamProfile?,
    availableExamProfiles: List<ExamProfile>,
    onExamProfileSelected: (ExamProfile) -> Unit,
    selectedView: GetStudentPerformanceView,
    onSwitchView: (GetStudentPerformanceView) -> Unit,
    onStartPractice: () -> Unit,
    message: String,
    isHindi: Boolean = false,
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Content Header
        ProgressContentHeader(
            selectedExamProfile = selectedExamProfile,
            availableExamProfiles = availableExamProfiles,
            onExamProfileSelected = onExamProfileSelected,
            isHindi = isHindi,
            isRefreshing = isRefreshing,
            onRefresh = onRefresh
        )

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
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
                text = if (isHindi) "अभ्यास पूरा करने के बाद आपकी प्रगति यहाँ दिखाई देगी।" else "Complete a practice quiz or mock test to start tracking your performance.",
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            )
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onStartPractice,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Assignment,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isHindi) "अभ्यास शुरू करें" else "Start Practice",
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1.5f))
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
