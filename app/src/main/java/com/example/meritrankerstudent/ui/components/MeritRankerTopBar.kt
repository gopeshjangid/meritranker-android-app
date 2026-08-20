package com.example.meritrankerstudent.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meritrankerstudent.R
import com.example.meritrankerstudent.data.model.ExamProfile
import com.example.meritrankerstudent.theme.MeritRankerColors
import com.example.meritrankerstudent.theme.MeritRankerShapes
import com.example.meritrankerstudent.theme.MeritRankerSpacing

/**
 * Shared production TopBar for MeritRanker authenticated screens.
 * Displays authoritative ExamProfile context, dynamic exam selector, and consistent status-bar insets.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeritRankerTopBar(
    title: String? = null,
    subtitle: String? = null,
    showLogo: Boolean = false,
    selectedExamProfile: ExamProfile? = null,
    availableExamProfiles: List<ExamProfile> = emptyList(),
    onExamProfileSelected: ((ExamProfile) -> Unit)? = null,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showExamMenu by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (showLogo) {
                            Image(
                                painter = painterResource(id = R.drawable.logo_long),
                                contentDescription = "MeritRanker",
                                modifier = Modifier.height(24.dp)
                            )
                        } else if (title != null) {
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (subtitle != null) {
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    navigationIcon?.invoke()
                },
                actions = {
                    // Dynamic Exam Selector Chip (when profiles exist)
                    if (availableExamProfiles.isNotEmpty() || selectedExamProfile != null) {
                        val displayLabel = remember(selectedExamProfile) {
                            if (selectedExamProfile != null) {
                                "${selectedExamProfile.examName} · ${selectedExamProfile.stage}"
                            } else {
                                "Select Exam"
                            }
                        }

                        Box {
                            Surface(
                                onClick = {
                                    if (availableExamProfiles.isNotEmpty() && onExamProfileSelected != null) {
                                        showExamMenu = true
                                    }
                                },
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                                shape = RoundedCornerShape(20.dp),
                                enabled = availableExamProfiles.isNotEmpty() && onExamProfileSelected != null
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = displayLabel,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (availableExamProfiles.size > 1 && onExamProfileSelected != null) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = "Switch Exam",
                                            modifier = Modifier.size(14.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Clean, theme-aware DropdownMenu
                            if (availableExamProfiles.isNotEmpty() && onExamProfileSelected != null) {
                                DropdownMenu(
                                    expanded = showExamMenu,
                                    onDismissRequest = { showExamMenu = false },
                                    shape = RoundedCornerShape(12.dp),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    shadowElevation = 6.dp,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)),
                                    modifier = Modifier.widthIn(min = 220.dp, max = 320.dp)
                                ) {
                                    Text(
                                        text = "TARGET EXAM",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        letterSpacing = 1.sp,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    )

                                    HorizontalDivider(
                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )

                                    availableExamProfiles.forEach { profile ->
                                        val isSelected = selectedExamProfile?.examProfileId == profile.examProfileId
                                        val itemBg = if (isSelected) {
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                        } else {
                                            androidx.compose.ui.graphics.Color.Transparent
                                        }

                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(vertical = 2.dp),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = profile.examName,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        Text(
                                                            text = profile.stage,
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }

                                                    if (isSelected) {
                                                        Box(
                                                            modifier = Modifier
                                                                .size(18.dp)
                                                                .background(MaterialTheme.colorScheme.primary, CircleShape),
                                                            contentAlignment = Alignment.Center
                                                        ) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = "Selected",
                                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                                modifier = Modifier.size(12.dp)
                                                            )
                                                        }
                                                    }
                                                }
                                            },
                                            onClick = {
                                                onExamProfileSelected(profile)
                                                showExamMenu = false
                                            },
                                            modifier = Modifier.background(itemBg)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    actions?.invoke(this)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets(0, 0, 0, 0)
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
            )
        }
    }
}
