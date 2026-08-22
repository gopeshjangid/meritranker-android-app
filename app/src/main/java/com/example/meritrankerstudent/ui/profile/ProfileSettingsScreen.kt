package com.example.meritrankerstudent.ui.profile

import android.app.DatePickerDialog
import android.widget.DatePicker
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meritrankerstudent.ui.auth.AuthViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import com.example.meritrankerstudent.ui.subscription.SubscriptionPlanSheet
import com.example.meritrankerstudent.ui.subscription.PurchaseReceiptsSheet
import com.example.meritrankerstudent.ui.subscription.SubscriptionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    profileViewModel: ProfileViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    subscriptionViewModel: SubscriptionViewModel = viewModel()
) {
    val uiState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val activeSubscription by subscriptionViewModel.activeSubscription.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showExamBottomSheet by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var showFeedbackBottomSheet by remember { mutableStateOf(false) }
    var showSubscriptionSheet by remember { mutableStateOf(false) }
    var showReceiptsSheet by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.statusBarsPadding()) {
                TopAppBar(
                    title = {
                        Text(
                            text = if ((uiState as? ProfileUiState.Success)?.isEditMode == true) "Edit Profile" else "Profile",
                            fontWeight = FontWeight.Bold
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        when (val state = uiState) {
                            is ProfileUiState.Success -> {
                                if (state.isEditMode) {
                                    TextButton(onClick = { profileViewModel.cancelEditMode() }) {
                                        Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Button(
                                        onClick = { profileViewModel.saveProfile() },
                                        enabled = !state.isSaving,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(end = 8.dp)
                                    ) {
                                        if (state.isSaving) {
                                            CircularProgressIndicator(
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp
                                            )
                                        } else {
                                            Text("Save", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                } else {
                                    IconButton(onClick = { profileViewModel.enterEditMode() }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Edit Profile",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            else -> {}
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            when (val state = uiState) {
                is ProfileUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
                is ProfileUiState.Error -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "We couldn’t load your profile.",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { profileViewModel.loadProfile() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Retry")
                            }
                            OutlinedButton(
                                onClick = { authViewModel.signOut() },
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Sign Out")
                            }
                        }
                    }
                }
                is ProfileUiState.Success -> {
                    val profile = state.profile

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Success / Error Notifications
                        if (state.saveSuccess) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "Profile updated successfully!",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            LaunchedEffect(state.saveSuccess) {
                                kotlinx.coroutines.delay(3000)
                                profileViewModel.clearSuccessMessage()
                            }
                        }

                        if (state.generalError != null) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = state.generalError,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(14.dp)
                                )
                            }
                        }

                        // Profile Avatar & Identity Summary Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    val initials = (profile.name ?: "Student")
                                        .split(" ")
                                        .mapNotNull { it.firstOrNull()?.toString() }
                                        .take(2)
                                        .joinToString("")
                                        .uppercase()

                                    Text(
                                        text = initials.ifEmpty { "S" },
                                        style = MaterialTheme.typography.headlineMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Spacer(modifier = Modifier.width(16.dp))

                                Column {
                                    Text(
                                        text = profile.name ?: "Student",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = profile.email ?: "student@meritranker.com",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        // ==================== MEMBERSHIP & SUBSCRIPTIONS CARD ====================
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(
                                width = if (activeSubscription != null) 1.5.dp else 1.dp,
                                color = if (activeSubscription != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Membership & Plan",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        color = if (activeSubscription != null)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(6.dp)
                                    ) {
                                        Text(
                                            text = if (activeSubscription != null) "PRO ACTIVE" else "FREE TIER",
                                            color = if (activeSubscription != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Text(
                                    text = if (activeSubscription != null)
                                        "You have full access to Unlimited AI Smart Tutor, AI Mock Tests, Detailed Solutions & Analytics."
                                    else
                                        "Upgrade to MeritRanker Pro for unlimited AI doubt solving, full mock tests & step-by-step solutions.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = { showSubscriptionSheet = true },
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = if (activeSubscription != null) "Manage Plan" else "Upgrade to Pro",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    OutlinedButton(
                                        onClick = { showReceiptsSheet = true },
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Receipts")
                                    }
                                }
                            }
                        }

                        if (!state.isEditMode) {
                            // ==================== VIEW MODE ====================
                            ProfileDetailCard(
                                label = "Email Address",
                                value = profile.email ?: "student@meritranker.com",
                                helperText = "Verified login email (Read-only)"
                            )

                            ProfileDetailCard(
                                label = "Date of Birth",
                                value = formatCanonicalDate(profile.dateOfBirth ?: "2000-08-15"),
                                helperText = null
                            )

                            // Primary & Additional Target Exams Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Target Exams",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "⭐ PRIMARY: ",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    text = profile.preparing ?: "Not Set",
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                            }
                                        }
                                    }

                                    if (profile.additionalExams.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Text(
                                            text = "Also targeting:",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            profile.additionalExams.forEach { addExam ->
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                                    shape = RoundedCornerShape(6.dp)
                                                ) {
                                                    Text(
                                                        text = addExam,
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            ProfileDetailCard(
                                label = "Language Preference",
                                value = profile.language?.uppercase() ?: "ENGLISH",
                                helperText = null
                            )
                        } else {
                            // ==================== EDIT MODE ====================
                            Text(
                                text = "Edit Personal Details",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // Full Name Field
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Full Name",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = state.draftName,
                                        onValueChange = { profileViewModel.onDraftNameChanged(it) },
                                        isError = state.nameError != null,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        singleLine = true,
                                        colors = OutlinedTextFieldDefaults.colors(
                                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                        )
                                    )
                                    if (state.nameError != null) {
                                        Text(
                                            text = state.nameError,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Email Field (READ-ONLY)
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Email Address (Read-only)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    OutlinedTextField(
                                        value = profile.email ?: "student@meritranker.com",
                                        onValueChange = {},
                                        enabled = false,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = OutlinedTextFieldDefaults.colors(
                                            disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                            disabledTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    )
                                }
                            }

                            // Date of Birth Field with Native Android DatePicker
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Date of Birth",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        OutlinedTextField(
                                            value = formatCanonicalDate(state.draftDateOfBirth),
                                            onValueChange = {},
                                            readOnly = true,
                                            modifier = Modifier.weight(1f),
                                            shape = RoundedCornerShape(8.dp),
                                            trailingIcon = {
                                                IconButton(onClick = {
                                                    val cal = Calendar.getInstance()
                                                    try {
                                                        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                        val parsedDate = sdf.parse(state.draftDateOfBirth)
                                                        if (parsedDate != null) cal.time = parsedDate
                                                    } catch (e: Exception) {
                                                        cal.set(2000, Calendar.AUGUST, 15)
                                                    }

                                                    val datePickerDialog = DatePickerDialog(
                                                        context,
                                                        { _: DatePicker, year: Int, monthOfYear: Int, dayOfMonth: Int ->
                                                            val selectedCal = Calendar.getInstance().apply {
                                                                set(year, monthOfYear, dayOfMonth)
                                                            }
                                                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                                            profileViewModel.onDateOfBirthChanged(sdf.format(selectedCal.time))
                                                        },
                                                        cal.get(Calendar.YEAR),
                                                        cal.get(Calendar.MONTH),
                                                        cal.get(Calendar.DAY_OF_MONTH)
                                                    )
                                                    // Prevent future dates
                                                    datePickerDialog.datePicker.maxDate = System.currentTimeMillis()
                                                    datePickerDialog.show()
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Default.DateRange,
                                                        contentDescription = "Pick Date of Birth",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            }

                            // Primary & Additional Exams Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                text = "Target Exam Selection",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Text(
                                                text = "Primary: ${state.draftPrimaryExam}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }

                                        Button(
                                            onClick = { showExamBottomSheet = true },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text("Choose Exams")
                                        }
                                    }

                                    if (state.draftAdditionalExams.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Also preparing for: ${state.draftAdditionalExams.joinToString(", ")}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (state.examError != null) {
                                        Text(
                                            text = state.examError,
                                            color = MaterialTheme.colorScheme.error,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }

                            // Language Preference Card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Language Preference (भाषा चुनें)",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        listOf(
                                            "english" to "English",
                                            "hindi" to "हिन्दी",
                                            "hinglish" to "Hinglish"
                                        ).forEach { (langKey, label) ->
                                            val isSelected = state.draftLanguage.equals(langKey, ignoreCase = true)
                                            FilterChip(
                                                selected = isSelected,
                                                onClick = { profileViewModel.onLanguageChanged(langKey) },
                                                label = {
                                                    Text(
                                                        text = label,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                },
                                                colors = FilterChipDefaults.filterChipColors(
                                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // ================= Legal & Privacy Section =================
                        Text(
                            text = "Legal & Privacy",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxWidth()) {
                                // 1. Privacy Policy
                                LegalMenuItem(
                                    title = "Privacy Policy",
                                    subtitle = "Read our data collection and protection practices",
                                    onClick = {
                                        com.example.meritrankerstudent.util.LegalConstants.openUrl(
                                            context,
                                            com.example.meritrankerstudent.util.LegalConstants.PRIVACY_POLICY_URL
                                        )
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // 2. Terms of Service
                                LegalMenuItem(
                                    title = "Terms of Service",
                                    subtitle = "Review student terms and service rules",
                                    onClick = {
                                        com.example.meritrankerstudent.util.LegalConstants.openUrl(
                                            context,
                                            com.example.meritrankerstudent.util.LegalConstants.TERMS_OF_SERVICE_URL
                                        )
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // 3. Privacy & Data Requests
                                LegalMenuItem(
                                    title = "Privacy & Data Requests",
                                    subtitle = "Exercise your data rights under India DPDP Act",
                                    onClick = {
                                        com.example.meritrankerstudent.util.LegalConstants.openEmail(
                                            context = context,
                                            email = com.example.meritrankerstudent.util.LegalConstants.PRIVACY_EMAIL,
                                            subject = "Privacy & Data Request - MeritRanker",
                                            body = "Hello Privacy Team,\n\nI would like to request the following regarding my MeritRanker account:\n- [ ] Access to my personal data\n- [ ] Correction of my personal data\n- [ ] Erasure of personal data\n- [ ] Withdrawal of consent\n\nDetails:\n"
                                        )
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // 4. Help & Support
                                LegalMenuItem(
                                    title = "Help / Contact Support",
                                    subtitle = "Contact student grievance and technical support",
                                    onClick = {
                                        com.example.meritrankerstudent.util.LegalConstants.openEmail(
                                            context = context,
                                            email = com.example.meritrankerstudent.util.LegalConstants.SUPPORT_EMAIL,
                                            subject = "Student Support - MeritRanker",
                                            body = "Hello Support Team,\n\n"
                                        )
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // 5. Send Product Feedback
                                LegalMenuItem(
                                    title = "Send Feedback",
                                    subtitle = "Help us improve MeritRanker learning features",
                                    onClick = { showFeedbackBottomSheet = true }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // 6. Rate MeritRanker on Google Play
                                LegalMenuItem(
                                    title = "Rate MeritRanker",
                                    subtitle = "Rate us on the Google Play Store",
                                    onClick = {
                                        com.example.meritrankerstudent.util.LegalConstants.openPlayStoreListing(context)
                                    }
                                )

                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                                // 7. Delete Account Action
                                LegalMenuItem(
                                    title = "Delete Account",
                                    subtitle = "Permanently remove your account and stored data",
                                    isDestructive = true,
                                    onClick = { showDeleteAccountDialog = true }
                                )
                            }
                        }

                        if (deleteError != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = deleteError!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Log Out Action Button
                        Button(
                            onClick = { authViewModel.signOut() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Sign out", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        val appVersion = "v${com.example.meritrankerstudent.BuildConfig.VERSION_NAME}"
                        Text(
                            text = "$appVersion • Managed by ${com.example.meritrankerstudent.util.LegalConstants.OPERATING_ENTITY}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = com.example.meritrankerstudent.util.LegalConstants.NON_GOVERNMENT_DISCLAIMER,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    // Product Feedback Modal Bottom Sheet
                    if (showFeedbackBottomSheet) {
                        ProductFeedbackBottomSheet(
                            onDismiss = { showFeedbackBottomSheet = false }
                        )
                    }

                    // Delete Account Confirmation Alert Dialog
                    if (showDeleteAccountDialog) {
                        AlertDialog(
                            onDismissRequest = { showDeleteAccountDialog = false },
                            title = {
                                Text("Delete Account", fontWeight = FontWeight.Bold)
                            },
                            text = {
                                Text(
                                    "Deleting your account will permanently remove your MeritRanker account and eligible associated data. Some information may be retained where required by law."
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        showDeleteAccountDialog = false
                                        authViewModel.deleteAccount { errorMsg ->
                                            deleteError = errorMsg
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error,
                                        contentColor = MaterialTheme.colorScheme.onError
                                    )
                                ) {
                                    Text("Delete Account", fontWeight = FontWeight.Bold)
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { showDeleteAccountDialog = false }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    // Exam Selection Multi-Select Modal Bottom Sheet
                    if (showExamBottomSheet && state.isEditMode) {
                        ExamSelectionBottomSheet(
                            currentPrimary = state.draftPrimaryExam,
                            currentAdditional = state.draftAdditionalExams,
                            availableExamProfiles = state.availableExamProfiles,
                            availableExams = state.availableExams,
                            onPrimarySelected = { profileViewModel.onPrimaryExamChanged(it) },
                            onPrimaryProfileSelected = { profileViewModel.onPrimaryExamProfileChanged(it) },
                            onToggleAdditional = { profileViewModel.onToggleAdditionalExam(it) },
                            onDismiss = { showExamBottomSheet = false }
                        )
                    }

                    // MeritRanker Pro Subscription Plan Modal Bottom Sheet
                    if (showSubscriptionSheet) {
                        SubscriptionPlanSheet(
                            viewModel = subscriptionViewModel,
                            onDismiss = { showSubscriptionSheet = false }
                        )
                    }

                    // Purchase History & Receipts Modal Bottom Sheet
                    if (showReceiptsSheet) {
                        PurchaseReceiptsSheet(
                            viewModel = subscriptionViewModel,
                            onDismiss = { showReceiptsSheet = false }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileDetailCard(
    label: String,
    value: String,
    helperText: String?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (helperText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = helperText,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamSelectionBottomSheet(
    currentPrimary: String,
    currentAdditional: List<String>,
    availableExamProfiles: List<com.example.meritrankerstudent.data.model.ExamProfile> = emptyList(),
    availableExams: List<String> = emptyList(),
    onPrimarySelected: (String) -> Unit,
    onPrimaryProfileSelected: ((com.example.meritrankerstudent.data.model.ExamProfile) -> Unit)? = null,
    onToggleAdditional: (String) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Target Exam Selection",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Select exactly one Primary Exam and optional additional target exams.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (availableExamProfiles.isNotEmpty()) {
                availableExamProfiles.forEach { profile ->
                    val isPrimary = profile.examName == currentPrimary || profile.examProfileId == currentPrimary
                    val isAdditional = currentAdditional.contains(profile.examName)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isPrimary) {
                                    if (onPrimaryProfileSelected != null) {
                                        onPrimaryProfileSelected(profile)
                                    } else {
                                        onPrimarySelected(profile.examName)
                                    }
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPrimary)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = profile.examName,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${profile.stage} • ${if (isPrimary) "Primary Target" else if (isAdditional) "Additional Target" else "Tap to set as Primary"}",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!isPrimary) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isAdditional,
                                        onCheckedChange = { onToggleAdditional(profile.examName) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text("Also target", fontSize = 11.sp)
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PRIMARY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                availableExams.forEach { exam ->
                    val isPrimary = exam == currentPrimary
                    val isAdditional = currentAdditional.contains(exam)

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (!isPrimary) {
                                    onPrimarySelected(exam)
                                }
                            },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isPrimary)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                            else
                                MaterialTheme.colorScheme.surface
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = exam,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPrimary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isPrimary) "Primary Exam" else if (isAdditional) "Additional Target" else "Tap to set as Primary",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (!isPrimary) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = isAdditional,
                                        onCheckedChange = { onToggleAdditional(exam) },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = MaterialTheme.colorScheme.primary
                                        )
                                    )
                                    Text("Also target", fontSize = 11.sp)
                                }
                            } else {
                                Surface(
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "PRIMARY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Done", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun formatCanonicalDate(canonical: String): String {
    return try {
        val sdfIn = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val date = sdfIn.parse(canonical) ?: return canonical
        val sdfOut = SimpleDateFormat("dd MMM yyyy", Locale.US)
        sdfOut.format(date)
    } catch (e: Exception) {
        canonical
    }
}

@Composable
private fun LegalMenuItem(
    title: String,
    subtitle: String,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                fontSize = 11.5.sp,
                color = if (isDestructive) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier
                .size(16.dp)
                .rotate(180f),
            tint = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
