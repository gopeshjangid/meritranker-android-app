package com.example.meritrankerstudent.ui.practice

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.*
import com.example.meritrankerstudent.data.repository.DefaultPracticeRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PracticeViewModel = viewModel { PracticeViewModel(DefaultPracticeRepository()) }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isHindi = uiState.selectedLanguage.startsWith("hi", ignoreCase = true)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        com.example.meritrankerstudent.ui.components.MeritRankerTopBar(
            title = if (isHindi) "अभ्यास" else "Practice",
            subtitle = if (isHindi) "लक्षित अभ्यास व मॉक टेस्ट" else "Targeted mock tests & quizzes",
            selectedExamProfile = uiState.selectedExamProfile,
            availableExamProfiles = uiState.availableExamProfiles,
            onExamProfileSelected = { viewModel.selectExamProfile(it) }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            // 1. Top Recommended Activity Card (AI Adaptive Practice)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
                        )
                    )
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
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
                            Text(
                                text = if (isHindi) "आपके लिए अनुशंसित" else "RECOMMENDED FOR YOU",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = if (isHindi) "🎯 अनुकूली क्विज़" else "🎯 Adaptive Quiz",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = if (isHindi) "प्रतिशत (Percentage) के 8 महत्वपूर्ण प्रश्न हल करें" else "Practice 8 Percentage Questions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isHindi) "आपके हालिया प्रदर्शन और परीक्षा पैटर्न के आधार पर तैयार" else "Tailored based on your recent performance & high-yield exam patterns",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = { onItemClick(GuidedPracticeDetail) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isHindi) "अभ्यास शुरू करें" else "Start Practice",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 2. Main Practice Paths Section
            Text(
                text = if (isHindi) "अभ्यास के मुख्य विकल्प (PRACTICE PATHS)" else "PRACTICE PATHS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.Bold
            )

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Quick Quiz Path
                StudyToolCard(
                    title = if (isHindi) "त्वरित क्विज़ (Quick Quiz)" else "Quick Quiz",
                    description = if (isHindi) "किसी भी विषय या टॉपिक का कुछ ही मिनटों में अभ्यास करें" else "Practice a subject or topic in a few minutes",
                    icon = Icons.Default.Edit,
                    color = MaterialTheme.colorScheme.primary,
                    onClick = { onItemClick(QuizList) }
                )

                // Mock Test Path
                StudyToolCard(
                    title = if (isHindi) "फुल मॉक टेस्ट (Mock Test)" else "Mock Test",
                    description = if (isHindi) "वास्तविक परीक्षा जैसी समय-सीमा और माहौल में अभ्यास करें" else "Practice under real exam conditions",
                    icon = Icons.Default.Check,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { onItemClick(MockList) }
                )

                // Previous Years Path
                StudyToolCard(
                    title = if (isHindi) "पिछले वर्षों के प्रश्न (PYQs)" else "Previous Years",
                    description = if (isHindi) "विगत परीक्षाओं में पूछे गए वास्तविक प्रश्नों को हल करें" else "Solve real questions asked in earlier exams",
                    icon = Icons.Default.Refresh,
                    color = MaterialTheme.colorScheme.secondary,
                    onClick = { onItemClick(PyqList) }
                )

                // Review Mistakes Path
                StudyToolCard(
                    title = if (isHindi) "गलतियों का सुधार (Review Mistakes)" else "Review Mistakes",
                    description = if (isHindi) "गलत हुए प्रश्नों को दोबारा हल करके कमजोरी दूर करें" else "Retry questions you answered incorrectly",
                    icon = Icons.Default.Warning,
                    color = MaterialTheme.colorScheme.error,
                    onClick = { onItemClick(WrongQuestionsList) }
                )
            }
        }
    }
}
}

@Composable
fun StudyToolCard(
    title: String,
    description: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Icon(
                imageVector = Icons.Default.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
