package com.example.meritrankerstudent.ui.profile

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.meritrankerstudent.BuildConfig
import com.example.meritrankerstudent.data.repository.AppSyncProductFeedbackRepository
import com.example.meritrankerstudent.data.repository.AuthRepository
import com.example.meritrankerstudent.data.repository.ProductFeedbackRepository
import kotlinx.coroutines.launch

val FEEDBACK_CATEGORIES = listOf(
    "Something isn't working",
    "Suggestion",
    "Learning experience",
    "Other"
)

sealed interface FeedbackUiState {
    object Idle : FeedbackUiState
    object Submitting : FeedbackUiState
    data class Success(val ticketId: String) : FeedbackUiState
    data class Error(val message: String) : FeedbackUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFeedbackBottomSheet(
    onDismiss: () -> Unit,
    feedbackRepository: ProductFeedbackRepository? = null,
    authRepository: AuthRepository? = null,
    screenContext: String? = "ProfileSettingsScreen"
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val effectiveRepository = remember(context, feedbackRepository, authRepository) {
        feedbackRepository ?: AppSyncProductFeedbackRepository(
            context = context.applicationContext,
            authRepository = authRepository ?: com.example.meritrankerstudent.data.repository.DefaultAuthRepository()
        )
    }

    var selectedCategory by remember { mutableStateOf(FEEDBACK_CATEGORIES[0]) }
    var feedbackMessage by remember { mutableStateOf("") }
    var submissionState by remember { mutableStateOf<FeedbackUiState>(FeedbackUiState.Idle) }

    ModalBottomSheet(
        onDismissRequest = {
            if (submissionState !is FeedbackUiState.Submitting) {
                onDismiss()
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            when (val state = submissionState) {
                is FeedbackUiState.Idle, is FeedbackUiState.Error, is FeedbackUiState.Submitting -> {
                    Text(
                        text = "Help us improve MeritRanker",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "We value your input to make your exam preparation faster and more effective. Feedback is reviewed directly by our product and engineering team.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (state is FeedbackUiState.Error) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }

                    Text(
                        text = "Category",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        FEEDBACK_CATEGORIES.forEach { category ->
                            val isSelected = category == selectedCategory
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = state !is FeedbackUiState.Submitting) {
                                        selectedCategory = category
                                    },
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected)
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                                    else
                                        MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = category,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    OutlinedTextField(
                        value = feedbackMessage,
                        onValueChange = { feedbackMessage = it },
                        label = { Text("Your comments (optional)") },
                        placeholder = { Text("Describe what you're experiencing or suggest an improvement...") },
                        enabled = state !is FeedbackUiState.Submitting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp),
                        shape = RoundedCornerShape(8.dp),
                        maxLines = 5
                    )

                    Text(
                        text = "App version: v${BuildConfig.VERSION_NAME} • Build ${BuildConfig.VERSION_CODE}",
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            enabled = state !is FeedbackUiState.Submitting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    submissionState = FeedbackUiState.Submitting
                                    val result = effectiveRepository.submitFeedback(
                                        category = selectedCategory,
                                        message = feedbackMessage,
                                        screenContext = screenContext
                                    )
                                    submissionState = if (result.isSuccess) {
                                        FeedbackUiState.Success(result.getOrDefault(""))
                                    } else {
                                        val errorText = result.exceptionOrNull()?.localizedMessage
                                            ?: "Unable to send feedback. Please check your connection."
                                        FeedbackUiState.Error(errorText)
                                    }
                                }
                            },
                            enabled = state !is FeedbackUiState.Submitting,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (state is FeedbackUiState.Submitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Text("Submit", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }

                is FeedbackUiState.Success -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Success",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "Feedback Received",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Thank you for helping us improve MeritRanker. Your feedback has been recorded.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Done")
                        }
                    }
                }
            }
        }
    }
}
