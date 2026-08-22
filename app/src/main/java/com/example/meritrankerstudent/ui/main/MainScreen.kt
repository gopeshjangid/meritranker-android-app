package com.example.meritrankerstudent.ui.main

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import com.example.meritrankerstudent.ui.progress.ProgressScreen
import com.example.meritrankerstudent.ui.practice.PracticeScreen
import com.example.meritrankerstudent.ui.doubt.AskDoubtScreen
import com.example.meritrankerstudent.ui.doubt.AskDoubtViewModel
import com.example.meritrankerstudent.ui.profile.ProfileSettingsScreen
import com.example.meritrankerstudent.R

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel(),
    askDoubtViewModel: AskDoubtViewModel = viewModel()
) {
    val currentTab by viewModel.currentTab.collectAsStateWithLifecycle()

    val context = androidx.compose.ui.platform.LocalContext.current
    val globalCoordinator = androidx.compose.runtime.remember { com.example.meritrankerstudent.data.coordinator.SmartTutorGlobalCoordinator.getInstance(context) }
    val coordinator = androidx.compose.runtime.remember { com.example.meritrankerstudent.data.coordinator.PracticeGenerationCoordinator.getInstance(context) }
    val isGlobalBusy by globalCoordinator.isGlobalBusy.collectAsStateWithLifecycle()
    val totalActiveCount by globalCoordinator.totalActiveCount.collectAsStateWithLifecycle()
    val latestReady by coordinator.latestReadyTask.collectAsStateWithLifecycle()

    androidx.compose.runtime.LaunchedEffect(currentTab) {
        val canonical = when (currentTab) {
            com.example.meritrankerstudent.ui.main.MainTab.DOUBT -> com.example.meritrankerstudent.observability.CanonicalScreen.SMART_TUTOR
            com.example.meritrankerstudent.ui.main.MainTab.PRACTICE -> com.example.meritrankerstudent.observability.CanonicalScreen.PRACTICE_HOME
            com.example.meritrankerstudent.ui.main.MainTab.PROGRESS -> com.example.meritrankerstudent.observability.CanonicalScreen.PROGRESS
            com.example.meritrankerstudent.ui.main.MainTab.PROFILE -> com.example.meritrankerstudent.observability.CanonicalScreen.PROFILE
        }
        com.example.meritrankerstudent.observability.AppObservability.analytics.setScreen(canonical)
        com.example.meritrankerstudent.observability.AppObservability.crashReporter.setScreen(canonical)
        globalCoordinator.updateScreenVisibility(currentTab.name, askDoubtViewModel.uiState.value.activeConversationId)
    }
    val isImeVisible = WindowInsets.isImeVisible

    Scaffold(
        bottomBar = {
            AnimatedVisibility(
                visible = !isImeVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it })
            ) {
                Column {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        // Item 1: Smart Tutor (with subtle generation indicator)
                        NavigationBarItem(
                            selected = currentTab == MainTab.DOUBT,
                            onClick = { viewModel.selectTab(MainTab.DOUBT) },
                            icon = {
                                SmartTutorNavIcon(
                                    isBusy = isGlobalBusy,
                                    activeCount = totalActiveCount,
                                    isReady = latestReady != null && !isGlobalBusy,
                                    isSelected = currentTab == MainTab.DOUBT
                                )
                            },
                            label = { Text("Smart Tutor") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        // Item 2: Practice
                        NavigationBarItem(
                            selected = currentTab == MainTab.PRACTICE,
                            onClick = { viewModel.selectTab(MainTab.PRACTICE) },
                            icon = { Icon(Icons.AutoMirrored.Filled.Assignment, contentDescription = "Practice") },
                            label = { Text("Practice") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        // Item 3: Progress
                        NavigationBarItem(
                            selected = currentTab == MainTab.PROGRESS,
                            onClick = { viewModel.selectTab(MainTab.PROGRESS) },
                            icon = { Icon(Icons.AutoMirrored.Filled.TrendingUp, contentDescription = "Progress") },
                            label = { Text("Progress") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                        // Item 4: Profile (Rightmost tab)
                        NavigationBarItem(
                            selected = currentTab == MainTab.PROFILE,
                            onClick = { viewModel.selectTab(MainTab.PROFILE) },
                            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
                            label = { Text("Profile") },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        )
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            when (currentTab) {
                MainTab.DOUBT -> {
                    AskDoubtScreen(
                        onActionClick = onItemClick,
                        viewModel = askDoubtViewModel
                    )
                }
                MainTab.PRACTICE -> {
                    PracticeScreen(
                        onItemClick = onItemClick
                    )
                }
                MainTab.PROGRESS -> {
                    ProgressScreen(
                        onItemClick = onItemClick
                    )
                }
                MainTab.PROFILE -> {
                    ProfileSettingsScreen(
                        onBack = { viewModel.selectTab(MainTab.DOUBT) }
                    )
                }
            }
        }
    }
}
