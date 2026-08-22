package com.example.meritrankerstudent.ui.doubt

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import com.example.meritrankerstudent.BuildConfig
import com.example.meritrankerstudent.ui.components.richtext.EducationalContentRenderer
import com.example.meritrankerstudent.ui.components.richtext.ResponseShareRenderer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.meritrankerstudent.QuizList
import com.example.meritrankerstudent.MockList
import com.example.meritrankerstudent.PyqList
import com.example.meritrankerstudent.R
import com.example.meritrankerstudent.data.model.ConversationSession
import com.example.meritrankerstudent.data.model.DoubtMessage
import com.example.meritrankerstudent.data.repository.DefaultDoubtRepository
import com.example.meritrankerstudent.util.ImageUtils
import com.example.meritrankerstudent.util.speech.SpeechRecognitionCallback
import com.example.meritrankerstudent.util.speech.SpeechRecognitionManager
import com.example.meritrankerstudent.util.speech.VoiceState
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AskDoubtScreen(
    onActionClick: (NavKey) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AskDoubtViewModel = run {
        val context = LocalContext.current
        viewModel {
            AskDoubtViewModel(
                repository = DefaultDoubtRepository(),
                authRepository = com.example.meritrankerstudent.data.repository.DefaultAuthRepository(),
                userProfileRepository = com.example.meritrankerstudent.data.repository.DefaultUserProfileRepository(),
                examProfileRepository = com.example.meritrankerstudent.data.repository.DefaultExamProfileRepository(),
                practiceGenerationCoordinator = com.example.meritrankerstudent.data.coordinator.PracticeGenerationCoordinator.getInstance(context)
            )
        }
    }
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var isInputFocused by remember { mutableStateOf(false) }
    var autoFollowLatest by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        viewModel.loadCuratedSuggestions(context)
    }

    var showLanguageMenu by remember { mutableStateOf(false) }
    var showExamMenu by remember { mutableStateOf(false) }

    // Speech Recognition Engine with Google Cloud Chirp 3 Streaming and On-Device Fallback
    val speechManager = remember {
        SpeechRecognitionManager(
            context = context,
            authRepository = com.example.meritrankerstudent.data.repository.DefaultAuthRepository(),
            callback = object : SpeechRecognitionCallback {
                override fun onStateChanged(state: VoiceState) {
                    viewModel.onVoiceStateChanged(state)
                }
                override fun onVoiceModeChanged(isVoiceModeActive: Boolean) {
                    viewModel.onVoiceModeChanged(isVoiceModeActive)
                }
                override fun onPartialTranscript(transcript: String) {
                    viewModel.onVoicePartialTranscript(transcript)
                }
                override fun onFinalTranscript(transcript: String) {
                    viewModel.onVoiceFinalTranscript(transcript)
                }
                override fun onError(userFriendlyError: String) {
                    viewModel.onVoiceError(userFriendlyError)
                }
            }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_PAUSE || event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                speechManager.cancel()
                viewModel.cancelVoice()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            speechManager.destroy()
        }
    }

    // Microphone Permission Launcher
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onMicPermissionResult(
            isGranted = isGranted,
            shouldShowRationale = false
        )
        if (isGranted) {
            viewModel.startVoiceMode()
            speechManager.startListening(
                languageMode = uiState.voiceLanguageMode,
                subject = uiState.selectedExam,
                examCategory = uiState.selectedExam
            )
        }
    }

    fun toggleVoiceListening() {
        keyboardController?.hide()
        focusManager.clearFocus()
        if (uiState.isVoiceModeActive) {
            speechManager.stopListening()
            viewModel.stopVoiceMode()
        } else {
            val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                viewModel.startVoiceMode()
                speechManager.startListening(
                    languageMode = uiState.voiceLanguageMode,
                    subject = uiState.selectedExam,
                    examCategory = uiState.selectedExam
                )
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    // Temporary camera capture Uri state
    var pendingCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Camera Capture Launcher
    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        val uri = pendingCameraUri
        if (success && uri != null) {
            viewModel.onImagePreparationStarted()
            coroutineScope.launch {
                val bitmap = ImageUtils.loadDownsampledBitmap(context, uri)
                val base64 = ImageUtils.loadDownsampledBase64(context, uri)
                if (bitmap != null && base64 != null) {
                    viewModel.onImagePreparationCompleted(uri.toString(), base64, AttachmentSource.CAMERA)
                } else if (bitmap != null && base64 == null) {
                    viewModel.onImagePreparationError("This image is too large. Please choose a smaller image.")
                } else {
                    viewModel.onImagePreparationError("Couldn't open this image. Choose another one.")
                }
            }
        } else {
            // Camera cancelled cleanly
            viewModel.setAttachmentSheetOpen(false)
        }
    }

    // Camera Permission Launcher
    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.onCameraPermissionResult(
            isGranted = isGranted,
            shouldShowRationale = false
        )
        if (isGranted) {
            try {
                val file = ImageUtils.createTempCameraFile(context)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                viewModel.onImagePreparationError("Couldn't open camera. Try again.")
            }
        }
    }

    // Photo Picker / Gallery Launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.onImagePreparationStarted()
            coroutineScope.launch {
                val localFile = ImageUtils.copyUriToCacheFile(context, uri)
                val targetUri = if (localFile != null) Uri.fromFile(localFile) else uri
                val bitmap = ImageUtils.loadDownsampledBitmap(context, targetUri)
                val base64 = ImageUtils.loadDownsampledBase64(context, targetUri)
                if (bitmap != null && base64 != null) {
                    viewModel.onImagePreparationCompleted(targetUri.toString(), base64, AttachmentSource.GALLERY)
                } else if (bitmap != null && base64 == null) {
                    viewModel.onImagePreparationError("This image is too large. Please choose a smaller image.")
                } else {
                    viewModel.onImagePreparationError("Couldn't open this image. Choose another one.")
                }
            }
        } else {
            // Gallery cancelled cleanly
            viewModel.setAttachmentSheetOpen(false)
        }
    }

    fun launchCameraFlow() {
        val permissionCheck = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
            try {
                val file = ImageUtils.createTempCameraFile(context)
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
                pendingCameraUri = uri
                takePictureLauncher.launch(uri)
            } catch (e: Exception) {
                viewModel.onImagePreparationError("Couldn't open camera. Try again.")
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun launchGalleryFlow() {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    LaunchedEffect(uiState.isHistoryDrawerOpen) {
        if (uiState.isHistoryDrawerOpen && !drawerState.isOpen) {
            drawerState.open()
        } else if (!uiState.isHistoryDrawerOpen && drawerState.isOpen) {
            drawerState.close()
        }
    }

    LaunchedEffect(drawerState.isOpen) {
        if (drawerState.isOpen != uiState.isHistoryDrawerOpen) {
            viewModel.setHistoryDrawerOpen(drawerState.isOpen)
        }
        if (drawerState.isOpen) {
            viewModel.loadConversationHistory()
        }
    }

    // Auto-scroll when a new message is added
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            autoFollowLatest = true
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // Coalesced / throttled auto-scroll when auto-follow is active during streaming
    val lastMessageLength = uiState.messages.lastOrNull()?.text?.length ?: 0
    val isLatestThinking = uiState.messages.lastOrNull()?.isThinking == true
    val isPracticeGenerating = uiState.messages.lastOrNull()?.isPracticeGenerating == true
    
    LaunchedEffect(lastMessageLength / 120, isLatestThinking, isPracticeGenerating) {
        if (autoFollowLatest && uiState.messages.isNotEmpty() && listState.canScrollForward) {
            listState.animateScrollToItem(uiState.messages.size - 1)
        }
    }

    // User drag detection: dragging upward away from bottom disables auto-follow
    val isUserDragging by listState.interactionSource.collectIsDraggedAsState()
    LaunchedEffect(isUserDragging) {
        if (isUserDragging && listState.canScrollForward) {
            autoFollowLatest = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.width(320.dp)
            ) {
                ConversationHistoryDrawerContent(
                    sessions = uiState.sessions,
                    activeSessionId = uiState.activeConversationId,
                    isLoading = uiState.isLoadingSessions,
                    onSelectSession = { sessionId ->
                        speechManager.cancel()
                        viewModel.selectConversation(sessionId)
                        coroutineScope.launch { drawerState.close() }
                    },
                    onNewChatClick = {
                        speechManager.cancel()
                        viewModel.startNewChat()
                        coroutineScope.launch { drawerState.close() }
                    },
                    onClearHistory = {
                        speechManager.cancel()
                        viewModel.clearChat()
                        coroutineScope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            com.example.meritrankerstudent.ui.components.MeritRankerTopBar(
                showLogo = true,
                selectedExamProfile = uiState.selectedExamProfile,
                availableExamProfiles = uiState.availableExamProfiles,
                onExamProfileSelected = { viewModel.onExamProfileSelected(it) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.toggleHistoryDrawer() }) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "History Drawer",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    // Compact Language Selector Pill
                    Box {
                        Surface(
                            onClick = { showLanguageMenu = true },
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = uiState.selectedLanguage.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Switch Language",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showLanguageMenu,
                            onDismissRequest = { showLanguageMenu = false },
                            shape = RoundedCornerShape(12.dp),
                            containerColor = MaterialTheme.colorScheme.surface,
                            shadowElevation = 6.dp,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                        ) {
                            mapOf("EN" to "English", "HI" to "Hindi", "HING" to "Hinglish").forEach { (code, name) ->
                                val isSelected = uiState.selectedLanguage.equals(code, ignoreCase = true)
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "$code ($name)",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.onLanguageSelected(code)
                                        showLanguageMenu = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // New Chat Action Button
                    IconButton(onClick = {
                        speechManager.cancel()
                        viewModel.startNewChat(context)
                    }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Chat",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        focusManager.clearFocus()
                        keyboardController?.hide()
                    }
            ) {
                if (uiState.messages.isEmpty() && !uiState.isAiThinking) {
                    EmptyChatWelcomeView(
                        selectedLanguage = uiState.selectedLanguage,
                        selectedExam = uiState.selectedExam,
                        curatedSuggestions = uiState.curatedSuggestions,
                        onPromptClick = { promptText ->
                            viewModel.submitPromptDirectly(promptText)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp)
                    ) {
                        items(uiState.messages) { message ->
                            val isLatestAssistant = message == uiState.messages.lastOrNull { !it.sender.equals("USER", ignoreCase = true) }
                            DoubtMessageBubble(
                                message = message,
                                isLatestAssistantMessage = isLatestAssistant,
                                isStreaming = uiState.isStreaming && isLatestAssistant,
                                onActionClick = onActionClick,
                                onCopyClick = { textToCopy ->
                                    coroutineScope.launch {
                                        ResponseShareRenderer.copyResponseAsImage(context, textToCopy)
                                    }
                                },
                                onShareClick = { textToShare ->
                                    coroutineScope.launch {
                                        ResponseShareRenderer.shareResponseAsImage(context, textToShare, subject = uiState.selectedExam)
                                    }
                                },
                                onSimilarClick = {
                                    viewModel.onSimilarClicked(message)
                                },
                                onSimplifyClick = {
                                    viewModel.onSimplifyClicked(message)
                                },
                                onReportClick = { messageId ->
                                    viewModel.openReportDialog(messageId)
                                },
                                onCancelGeneration = {
                                    viewModel.stopGenerating()
                                }
                            )
                        }

                        if (uiState.sendError != null) {
                            item {
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.retrySendMessage() },
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = uiState.sendError!!,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = "RETRY",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Floating "↓ Latest" Floating Affordance (Section 92)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = !autoFollowLatest && listState.canScrollForward,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically { it / 2 },
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically { it / 2 },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 12.dp)
                    ) {
                        Surface(
                            onClick = {
                                autoFollowLatest = true
                                coroutineScope.launch {
                                    listState.animateScrollToItem((uiState.messages.size - 1).coerceAtLeast(0))
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            shadowElevation = 4.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Scroll to latest response",
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Latest",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }

            // Camera Permission Denied Banner
                if (uiState.isCameraPermissionDenied) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Camera access is needed to photograph your question.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.isCameraPermissionPermanentlyDenied) {
                                TextButton(onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }) {
                                    Text("Open Settings", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                IconButton(onClick = { viewModel.dismissCameraPermissionNotice() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                }

                // Microphone Permission Denied Banner
                if (uiState.isMicPermissionDenied) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Microphone access is needed for voice questions.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            if (uiState.isMicPermissionPermanentlyDenied) {
                                TextButton(onClick = {
                                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                        data = Uri.fromParts("package", context.packageName, null)
                                    }
                                    context.startActivity(intent)
                                }) {
                                    Text("Open Settings", fontWeight = FontWeight.Bold)
                                }
                            } else {
                                IconButton(onClick = { viewModel.dismissMicPermissionNotice() }) {
                                    Icon(Icons.Default.Clear, contentDescription = "Dismiss")
                                }
                            }
                        }
                    }
                }

                // Voice Recognizer Error Banner
                if (uiState.voiceErrorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "🎙️ ${uiState.voiceErrorMessage}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { viewModel.dismissVoiceError() },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

            // Sticky Bottom Interaction Composer Bar (Immersive Long-Answer Reading UX)
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    // Unified Floating AI Composer Card
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)),
                        shadowElevation = 2.dp,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            // Selected Image Attachment Chip (Clearable)
                            if (uiState.selectedAttachmentUri != null) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .padding(bottom = 6.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = if (uiState.attachmentSource == AttachmentSource.CAMERA) "Camera Photo" else "Attached Image",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    IconButton(
                                        onClick = { viewModel.removeAttachment() },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Remove photo",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }

                            // Full-Width Editable Text Area
                            val dynamicPlaceholder = if (uiState.messages.isNotEmpty()) {
                                if (uiState.selectedLanguage.startsWith("hi", ignoreCase = true)) "फॉलो-अप प्रश्न पूछें..." else "Ask a follow-up doubt..."
                            } else {
                                if (uiState.selectedLanguage.startsWith("hi", ignoreCase = true)) "डाउट पूछें या सवाल लिखें..." else "Ask anything..."
                            }

                            MultilineExpandingTextField(
                                value = uiState.inputText,
                                onValueChange = { viewModel.onInputTextChange(it) },
                                placeholder = dynamicPlaceholder,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                enabled = !uiState.isAiThinking,
                                focusRequester = focusRequester,
                                onFocusChanged = { isInputFocused = it }
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            // Bottom Action Items Row
                            if (uiState.isVoiceModeActive) {
                                // ACTIVE VOICE STATE: [ ✕ Cancel ] --- [ 🔴 Listening… ] --- [ ✓ Done ]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Cancel Action (Left)
                                    TextButton(
                                        onClick = {
                                            speechManager.cancel()
                                            viewModel.cancelVoice()
                                        },
                                        colors = ButtonDefaults.textButtonColors(
                                            contentColor = MaterialTheme.colorScheme.error
                                        ),
                                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Cancel voice input",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Cancel",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }

                                    // 2. Restrained Listening Indicator + Language Selector (Center)
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                        val pulseAlpha by infiniteTransition.animateFloat(
                                            initialValue = 0.3f,
                                            targetValue = 1f,
                                            animationSpec = infiniteRepeatable(
                                                animation = tween(600, easing = LinearEasing),
                                                repeatMode = RepeatMode.Reverse
                                            ),
                                            label = "pulseAlpha"
                                        )
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha))
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))

                                        // Compact Language Mode Pill: AUTO | हिंदी | EN
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(2.dp),
                                                horizontalArrangement = Arrangement.spacedBy(1.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                com.example.meritrankerstudent.util.speech.VoiceLanguageMode.values().forEach { mode ->
                                                    val isSelected = uiState.voiceLanguageMode == mode
                                                    Surface(
                                                        modifier = Modifier
                                                            .clip(RoundedCornerShape(6.dp))
                                                            .clickable {
                                                                viewModel.onVoiceLanguageModeSelected(mode)
                                                                speechManager.stopListening()
                                                                speechManager.startListening(
                                                                    languageMode = mode,
                                                                    subject = uiState.selectedExam,
                                                                    examCategory = uiState.selectedExam
                                                                )
                                                            },
                                                        color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                                                        shape = RoundedCornerShape(6.dp)
                                                    ) {
                                                        Text(
                                                            text = mode.label,
                                                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    // 3. Done Action (Right)
                                    FilledTonalButton(
                                        onClick = {
                                            speechManager.stopListening()
                                            viewModel.stopVoiceMode()
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Finish voice input",
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(3.dp))
                                        Text(
                                            text = "Done",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            } else {
                                // NORMAL IDLE STATE: [ + ] [ 🎤 ] ------------- [ Send ]
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 1. Plus (+) Attachment Action Button
                                    Surface(
                                        onClick = { viewModel.setAttachmentSheetOpen(true) },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Add attachment",
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(6.dp))

                                    // 2. Idle Mic Button
                                    Surface(
                                        onClick = { toggleVoiceListening() },
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                        modifier = Modifier.size(34.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.ic_mic),
                                                contentDescription = "Start voice input",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.weight(1f))

                                    // 3. Send Button (Right)
                                    val hasSendableContent = uiState.inputText.isNotBlank() || uiState.selectedAttachmentUri != null
                                    val isSendEnabled = hasSendableContent &&
                                            !uiState.isSending &&
                                            !uiState.isAttachmentPreparing &&
                                            uiState.attachmentError == null

                                    Surface(
                                        onClick = { viewModel.sendMessage() },
                                        enabled = isSendEnabled,
                                        shape = CircleShape,
                                        color = if (isSendEnabled)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                        contentColor = if (isSendEnabled)
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                                        modifier = Modifier.size(36.dp)
                                    ) {
                                        Box(
                                            contentAlignment = Alignment.Center,
                                            modifier = Modifier.fillMaxSize()
                                        ) {
                                            Icon(
                                                imageVector = Icons.AutoMirrored.Filled.Send,
                                                contentDescription = "Send message",
                                                modifier = Modifier.size(16.dp)
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

    // AI Content Report Modal Bottom Sheet
    if (uiState.reportingMessageId != null) {
        ReportResponseModalBottomSheet(
            messageId = uiState.reportingMessageId!!,
            isSubmitting = uiState.isReporting,
            onSubmit = { category, comment ->
                viewModel.submitContentReport(uiState.reportingMessageId!!, category, comment)
            },
            onDismiss = { viewModel.dismissReportDialog() }
        )
    }

    // AI Content Report Feedback Toast
    LaunchedEffect(uiState.reportSuccessMessage) {
        uiState.reportSuccessMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearReportFeedback()
        }
    }
    LaunchedEffect(uiState.reportErrorMessage) {
        uiState.reportErrorMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.clearReportFeedback()
        }
    }

    // Attachment Modal Bottom Sheet with EXACTLY 2 options
    if (uiState.isAttachmentSheetOpen) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.setAttachmentSheetOpen(false) },
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Attach Question Image",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Option 1: Take a photo
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setAttachmentSheetOpen(false)
                            launchCameraFlow()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Take a photo",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Take a photo",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Capture a question using your camera",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Option 2: Choose from gallery
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            viewModel.setAttachmentSheetOpen(false)
                            launchGalleryFlow()
                        },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Choose from gallery",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Choose from gallery",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Select a question image from your device",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun SelectedImagePreviewCard(
    uriString: String?,
    isPreparing: Boolean,
    errorMessage: String?,
    onRemove: () -> Unit,
    onReplace: () -> Unit
) {
    val context = LocalContext.current
    var previewBitmap by remember(uriString) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(uriString) {
        if (uriString != null) {
            try {
                val uri = Uri.parse(uriString)
                val bitmap = ImageUtils.loadDownsampledBitmap(context, uri, maxDimensionPx = 400)
                previewBitmap = bitmap
            } catch (e: Exception) {
                previewBitmap = null
            }
        } else {
            previewBitmap = null
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            if (errorMessage != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (isPreparing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Preparing image preview...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (errorMessage != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Remove image",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else if (previewBitmap != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Selected question image preview",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onReplace() },
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Question Image Attached",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Tap image to replace",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp
                        )
                    }
                }
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Remove image",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun MultilineExpandingTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null
) {
    var fieldModifier = modifier.fillMaxWidth()
    if (focusRequester != null) {
        fieldModifier = fieldModifier.focusRequester(focusRequester)
    }
    if (onFocusChanged != null) {
        fieldModifier = fieldModifier.onFocusChanged { state: androidx.compose.ui.focus.FocusState ->
            onFocusChanged(state.isFocused)
        }
    }

    androidx.compose.foundation.text.BasicTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        textStyle = MaterialTheme.typography.bodyMedium.copy(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
        modifier = fieldModifier,
        minLines = 1,
        maxLines = 5,
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    )
                }
                innerTextField()
            }
        }
    )
}

@Composable
fun SuggestionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(9999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

data class SubjectQueryCard(
    val subject: String,
    val title: String,
    val prompt: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun EmptyChatWelcomeView(
    selectedLanguage: String = "en",
    selectedExam: String = "",
    curatedSuggestions: List<TutorSuggestion> = emptyList(),
    onPromptClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isHindi = selectedLanguage.startsWith("hi", ignoreCase = true)
    val examLabel = selectedExam.ifBlank { if (isHindi) "प्रतियोगी परीक्षा" else "Exam" }

    // Fallback if curatedSuggestions is empty yet
    val displaySuggestions = remember(curatedSuggestions, context) {
        if (curatedSuggestions.isNotEmpty()) {
            curatedSuggestions
        } else {
            TutorSuggestionSelector.selectSuggestions(context, 3)
        }
    }

    // Show welcome banner for 2 minutes only, with close button and smooth fade/collapse
    var showWelcomeBanner by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(120_000L) // 2 mins
        showWelcomeBanner = false
    }

    // Dynamic Quick Action Chips (Concise, High-Impact)
    val actionChips = remember(isHindi, examLabel) {
        if (isHindi) {
            listOf(
                "🎯 10 प्रश्नों का टेस्ट" to "$examLabel के लिए 10 प्रश्नों का अभ्यास टेस्ट बनाएं",
                "📝 फुल मॉक टेस्ट" to "$examLabel के लिए संपूर्ण मॉक टेस्ट तैयार करें",
                "💡 स्टेप-बाय-स्टेप हल" to "इस प्रश्न को आसान स्टेप्स के साथ हल करें: "
            )
        } else {
            listOf(
                "🎯 10-Question Test" to "Create a 10-question practice test for $examLabel",
                "📝 Full Mock Test" to "Create a full mock test for $examLabel",
                "💡 Step-by-Step Solver" to "Solve this question step-by-step with clear explanation: "
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Welcome Banner Card (Visible for 2 minutes or until dismissed)
        androidx.compose.animation.AnimatedVisibility(
            visible = showWelcomeBanner,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
            exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.logo_short),
                            contentDescription = "MeritRanker Logo",
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isHindi) "नमस्ते! Smart Tutor में आपका स्वागत है 👋" else "Hello! Welcome to Smart Tutor 👋",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isHindi) "अपने किसी भी विषय का डाउट या सवाल यहाँ पूछें" else "Ask any question or clear your doubts in any subject",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 12.sp
                        )
                    }
                    IconButton(
                        onClick = { showWelcomeBanner = false },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Action Prompts / Exam Test Generation Chips
        Text(
            text = if (isHindi) "त्वरित क्रियाएँ ($examLabel)" else "QUICK AI ACTIONS ($examLabel)",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            actionChips.forEach { (chipLabel, prompt) ->
                Surface(
                    onClick = { onPromptClick(prompt) },
                    shape = RoundedCornerShape(9999.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                ) {
                    Text(
                        text = chipLabel,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                    )
                }
            }
        }

        // Section Title: Curated Suggestions
        Text(
            text = if (isHindi) "सुझाए गए प्रश्न" else "SUGGESTED QUESTIONS",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )

        // Clean Vertical Suggestion Cards (3 diverse items)
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            displaySuggestions.forEach { suggestion ->
                val subjectLabel = if (isHindi) suggestion.subjectHi else suggestion.subject
                val titleLabel = if (isHindi) suggestion.titleHi else suggestion.title
                val promptText = if (isHindi) suggestion.textHi else suggestion.text

                val (accentColor, iconVector) = when {
                    suggestion.subject.contains("Quant", ignoreCase = true) ->
                        Pair(com.example.meritrankerstudent.theme.MeritRankerColors.AiCyan, Icons.Default.Edit)
                    suggestion.subject.contains("Reasoning", ignoreCase = true) ->
                        Pair(com.example.meritrankerstudent.theme.MeritRankerColors.BrandPurple, Icons.Default.Info)
                    suggestion.subject.contains("English", ignoreCase = true) ->
                        Pair(com.example.meritrankerstudent.theme.MeritRankerColors.Success, Icons.Default.MenuBook)
                    else ->
                        Pair(com.example.meritrankerstudent.theme.MeritRankerColors.BrandBlue, Icons.Default.Science)
                }

                Surface(
                    onClick = { onPromptClick(promptText) },
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Subject Icon Pill
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(accentColor.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconVector,
                                contentDescription = subjectLabel,
                                tint = accentColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Query Details
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = subjectLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = titleLabel,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = promptText,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 12.sp,
                                lineHeight = 16.sp
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        // Tap Indicator
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Ask this question",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationHistoryDrawerContent(
    sessions: List<ConversationSession>,
    activeSessionId: String,
    isLoading: Boolean,
    onSelectSession: (String) -> Unit,
    onNewChatClick: () -> Unit,
    onClearHistory: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Chats",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = onNewChatClick,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "New Chat", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(12.dp))

        if (isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else if (sessions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No previous conversations yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sessions) { session ->
                    val isSelected = session.id == activeSessionId
                    Surface(
                        onClick = { onSelectSession(session.id) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else
                            Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected)
                            BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                        else
                            null
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = session.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = dateFormat.format(Date(session.lastActivityAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        if (BuildConfig.DEBUG) {
            var showDiag by remember { mutableStateOf(false) }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showDiag = !showDiag },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Diagnostics",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (showDiag) "Hide Diagnostics" else "Developer Diagnostics",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (showDiag) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "🛠️ MeritRanker History Diagnostics",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Sessions Cached: ${sessions.size}",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Auth Mode: COGNITO_USER_POOLS",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "AppSync GSI: listConversationSessionsByUser",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onClearHistory,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = "Clear History",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Clear All Conversations",
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun DoubtMessageBubble(
    message: DoubtMessage,
    isLatestAssistantMessage: Boolean = false,
    isStreaming: Boolean = false,
    onActionClick: (NavKey) -> Unit,
    onCopyClick: (String) -> Unit,
    onShareClick: (String) -> Unit,
    onSimilarClick: () -> Unit,
    onSimplifyClick: () -> Unit,
    onReportClick: (String) -> Unit = {},
    onCancelGeneration: () -> Unit = {}
) {
    val isUser = message.sender == "USER"
    val context = LocalContext.current

    val bubbleBgColor = if (isUser) {
        MaterialTheme.colorScheme.surfaceVariant
    } else {
        MaterialTheme.colorScheme.surface
    }

    val borderStroke = if (isUser) {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
    } else {
        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    }

    val alignment = if (isUser) Alignment.End else Alignment.Start

    var userImageBitmap by remember(message.imageUri) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(message.imageUri) {
        if (isUser && message.imageUri != null) {
            try {
                val uri = Uri.parse(message.imageUri)
                val bitmap = ImageUtils.loadDownsampledBitmap(context, uri, maxDimensionPx = 600)
                userImageBitmap = bitmap
            } catch (e: Exception) {
                userImageBitmap = null
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = bubbleBgColor,
            border = borderStroke,
            shadowElevation = if (isUser) 0.dp else 1.dp,
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            modifier = if (isUser) Modifier.widthIn(max = 310.dp) else Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                // If User message has an image attachment, render it inside the bubble
                if (isUser && message.imageUri != null) {
                    if (userImageBitmap != null) {
                        Image(
                            bitmap = userImageBitmap!!.asImageBitmap(),
                            contentDescription = "Attached Question Image",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 220.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (message.text.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    } else {
                        // Fallback pill for image
                        Surface(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Question Image Attached",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }

                if (!isUser) {
                    if (message.errorMessage != null) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f)),
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
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = message.errorMessage,
                                    style = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onErrorContainer),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else if (message.isThinking || (message.text.isBlank() && !message.isPracticeGenerating && message.practiceTestId == null)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = message.thinkingStatus ?: "Thinking…",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    } else if (message.text.isNotBlank()) {
                        EducationalContentRenderer(
                            content = message.text,
                            isStreaming = isStreaming
                        )
                    }
                } else if (message.text.isNotBlank()) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }

                // Inline Practice Assessment card: rendered exactly ONCE when this turn has an active or valid practice generation
                if (!isUser && message.practiceTestId != null && (message.isPracticeGenerating || message.practiceStatus == "READY" || message.practiceStatus == "GENERATING" || message.practiceStatus == "FAILED")) {
                    val testId = message.practiceTestId
                    val mode = if (message.actionRoute.equals("MOCK", ignoreCase = true)) "MOCK" else "QUIZ"
                    if (message.text.isNotBlank()) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    GeneratingPracticeCard(
                        testId = testId,
                        title = message.practiceTitle ?: "Practice Quiz",
                        totalQuestions = message.practiceTotalQuestions,
                        readyQuestions = message.practiceReadyQuestions,
                        status = message.practiceStatus ?: if (message.isPracticeGenerating) "GENERATING" else "READY",
                        onStartPractice = {
                            onActionClick(com.example.meritrankerstudent.QuestionPlayer(mode = mode, id = testId))
                        },
                        onCancel = onCancelGeneration
                    )
                }

                // Clean Unified AI Response Utilities (Copy, Share, Similar, Simplify, Report) - Exactly 1 Row
                if (!isUser && message.text.isNotBlank()) {
                    val actionsEnabled = !isStreaming && !message.isThinking && message.errorMessage == null && message.text.isNotBlank()
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(6.dp))

                    // Compact Single-Row Action Toolbar (5 items, zero wrapping)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1. Copy
                        ResponseToolbarAction(
                            icon = Icons.Default.Edit,
                            label = "Copy",
                            talkBackDescription = "Copy answer as image",
                            enabled = actionsEnabled,
                            onClick = { onCopyClick(message.text) },
                            modifier = Modifier.weight(1f)
                        )

                        // 2. Share
                        ResponseToolbarAction(
                            icon = Icons.Default.Share,
                            label = "Share",
                            talkBackDescription = "Share answer as image",
                            enabled = actionsEnabled,
                            onClick = { onShareClick(message.text) },
                            modifier = Modifier.weight(1f)
                        )

                        // 3. Similar
                        ResponseToolbarAction(
                            icon = Icons.Default.Star,
                            label = "Similar",
                            talkBackDescription = "Create similar question",
                            isAccent = true,
                            enabled = actionsEnabled,
                            onClick = onSimilarClick,
                            modifier = Modifier.weight(1f)
                        )

                        // 4. Simplify
                        ResponseToolbarAction(
                            icon = Icons.Default.Refresh,
                            label = "Simplify",
                            talkBackDescription = "Simplify explanation",
                            enabled = actionsEnabled,
                            onClick = onSimplifyClick,
                            modifier = Modifier.weight(1f)
                        )

                        // 5. Report
                        ResponseToolbarAction(
                            icon = Icons.Default.Warning,
                            label = "Report",
                            talkBackDescription = "Report response",
                            enabled = actionsEnabled,
                            onClick = { onReportClick(message.id) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // Compact Disclaimer beneath toolbar for the response
                    if (isLatestAssistantMessage) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "AI can make mistakes",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResponseToolbarAction(
    icon: ImageVector,
    label: String,
    talkBackDescription: String,
    isAccent: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (!enabled) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
    } else if (isAccent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    }
    val contentColor = if (!enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    } else if (isAccent) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        modifier = modifier
            .heightIn(min = 36.dp, max = 42.dp)
            .semantics { contentDescription = talkBackDescription }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(2.5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.5.sp),
                fontWeight = if (isAccent && enabled) FontWeight.Bold else FontWeight.Medium,
                color = if (isAccent && enabled) MaterialTheme.colorScheme.primary else contentColor,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun GeneratingPracticeCard(
    testId: String,
    title: String,
    totalQuestions: Int = 5,
    readyQuestions: Int = 0,
    status: String = "GENERATING", // "GENERATING", "READY", "FAILED"
    onStartPractice: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isGenerating = status == "GENERATING"
    val isReady = status == "READY"
    val isFailed = status == "FAILED"
    var isStarting by remember { mutableStateOf(false) }

    // Dynamic continuous glowing gradient border animation
    val infiniteTransition = rememberInfiniteTransition(label = "borderGradientAnimation")
    val gradientOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "gradientOffset"
    )

    val animatedBorder = if (isGenerating) {
        BorderStroke(
            width = 1.75.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    com.example.meritrankerstudent.theme.MeritRankerColors.AiCyanLight,
                    com.example.meritrankerstudent.theme.MeritRankerColors.BrandPurpleLight,
                    com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange,
                    com.example.meritrankerstudent.theme.MeritRankerColors.AiCyanLight
                ),
                start = Offset(gradientOffset, gradientOffset),
                end = Offset(gradientOffset + 500f, gradientOffset + 500f),
                tileMode = TileMode.Repeated
            )
        )
    } else if (isReady) {
        BorderStroke(
            width = 1.5.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    com.example.meritrankerstudent.theme.MeritRankerColors.Success,
                    com.example.meritrankerstudent.theme.MeritRankerColors.AiCyanLight,
                    com.example.meritrankerstudent.theme.MeritRankerColors.Success
                )
            )
        )
    } else {
        BorderStroke(1.dp, com.example.meritrankerstudent.theme.MeritRankerColors.Error.copy(alpha = 0.4f))
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = animatedBorder,
        shadowElevation = if (isGenerating) 3.dp else 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Category Badge + Total Questions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                when {
                                    isReady -> com.example.meritrankerstudent.theme.MeritRankerColors.Success.copy(alpha = 0.15f)
                                    isFailed -> com.example.meritrankerstudent.theme.MeritRankerColors.Error.copy(alpha = 0.15f)
                                    else -> com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange.copy(alpha = 0.15f)
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isReady -> Icons.Default.Check
                                isFailed -> Icons.Default.Warning
                                else -> Icons.Default.Edit
                            },
                            contentDescription = null,
                            tint = when {
                                isReady -> com.example.meritrankerstudent.theme.MeritRankerColors.Success
                                isFailed -> com.example.meritrankerstudent.theme.MeritRankerColors.Error
                                else -> com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange
                            },
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Text(
                        text = "QUICK PRACTICE",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isReady) com.example.meritrankerstudent.theme.MeritRankerColors.Success else com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange,
                        letterSpacing = 0.8.sp
                    )
                }
                Text(
                    text = "$totalQuestions Questions · ${totalQuestions} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )

            // Progress Bar (When Generating)
            if (isGenerating) {
                val progressFraction = if (totalQuestions > 0 && readyQuestions > 0) {
                    (readyQuestions.toFloat() / totalQuestions.toFloat()).coerceIn(0f, 1f)
                } else {
                    0f
                }
                if (progressFraction > 0f) {
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp)),
                        color = com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            // Progress Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(13.dp),
                            strokeWidth = 1.5.dp,
                            color = com.example.meritrankerstudent.theme.MeritRankerColors.BrandOrange
                        )
                    } else {
                        Icon(
                            imageVector = if (isReady) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isReady) com.example.meritrankerstudent.theme.MeritRankerColors.Success else com.example.meritrankerstudent.theme.MeritRankerColors.Error,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when {
                            isReady -> "Practice ready"
                            isFailed -> "Couldn't create this practice."
                            else -> "Preparing your practice..."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isReady) FontWeight.Medium else FontWeight.Normal,
                        color = if (isReady) com.example.meritrankerstudent.theme.MeritRankerColors.Success else MaterialTheme.colorScheme.onSurface
                    )
                }

                if (isGenerating && readyQuestions > 0) {
                    Text(
                        text = "$readyQuestions of $totalQuestions ready",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // CTA Action Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (isGenerating) {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
                    }
                } else if (isReady) {
                    Button(
                        onClick = {
                            if (!isStarting) {
                                isStarting = true
                                onStartPractice()
                            }
                        },
                        enabled = !isStarting,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isStarting) "Starting..." else "Start Practice",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                } else {
                    OutlinedButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    ) {
                        Text("Try Again", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun ClarificationChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportResponseModalBottomSheet(
    messageId: String,
    isSubmitting: Boolean,
    onSubmit: (category: String, comment: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val categories = listOf(
        "Incorrect or misleading",
        "Inappropriate content",
        "Unsafe content",
        "Other"
    )
    var selectedCategory by remember { mutableStateOf(categories[0]) }
    var comment by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Report AI Response",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = "Help us maintain exam accuracy and safety. What was wrong with this answer?",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.forEach { category ->
                    val isSelected = category == selectedCategory
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedCategory = category },
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        border = BorderStroke(
                            1.dp,
                            if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedCategory = category },
                                colors = RadioButtonDefaults.colors(selectedColor = MaterialTheme.colorScheme.primary)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = category,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            OutlinedTextField(
                value = comment,
                onValueChange = { if (it.length <= 300) comment = it },
                label = { Text("Additional details (optional)") },
                placeholder = { Text("Describe the error or exam correction...") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 3,
                shape = RoundedCornerShape(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSubmit(selectedCategory, comment.ifBlank { null }) },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text("Submit Report", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
