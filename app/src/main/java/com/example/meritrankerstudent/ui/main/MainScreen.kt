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
                        // Item 1: Smart Tutor
                        NavigationBarItem(
                            selected = currentTab == MainTab.DOUBT,
                            onClick = { viewModel.selectTab(MainTab.DOUBT) },
                            icon = { Icon(Icons.Default.Star, contentDescription = "Smart Tutor") },
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
                            icon = { Icon(Icons.Default.Home, contentDescription = "Practice") },
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
