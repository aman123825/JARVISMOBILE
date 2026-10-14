package com.mindfortress.app.ui.screens

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.ai.AmbientAcousticSentinel
import com.mindfortress.app.ai.AcousticEnvironment
import com.mindfortress.app.ai.LocalCompanionEngine
import com.mindfortress.app.ai.NativeSovereignVoiceEngine
import com.mindfortress.app.ai.DeviceActionProposal
import com.mindfortress.app.ai.DeviceActionType
import com.mindfortress.app.ai.PermissionFirstActionBroker
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.service.AppBlockerAccessibilityService
import com.mindfortress.app.service.JarvisFloatingOverlayService
import com.mindfortress.app.service.MindShieldVpnService
import com.mindfortress.app.theme.*
import com.mindfortress.app.ui.components.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JarvisMainHudScreen(
    onNavigateToGym: () -> Unit = {},
    onNavigateToShieldRules: () -> Unit = {},
    onNavigateToPurification: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    val companionEngine = remember { LocalCompanionEngine(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var coreState by remember { mutableStateOf(JarvisCoreState.STANDBY) }
    var inputText by remember { mutableStateOf("") }
    var executionError by remember { mutableStateOf<String?>(null) }
    var latestResponse by remember {
        mutableStateOf(
            "Greetings, Commander Vivek. All neural systems are online.\n\n" +
            "I am running locally on your Samsung Galaxy S25 in Sovereign Mode (zero laptop dependency). " +
            "Tap the Arc Reactor or speak your command to initiate our deep-work protocols."
        )
    }
    var latestUserQuery by remember { mutableStateOf<String?>(null) }

    // Dialog state
    var showUplinkDialog by remember { mutableStateOf(false) }
    var showLifeMatrixDialog by remember { mutableStateOf(false) }
    var pendingDeviceAction by remember { mutableStateOf<DeviceActionProposal?>(null) }
    var isLocalGemmaInstalled by remember { mutableStateOf(companionEngine.hasLocalGemmaModel()) }
    var isLocalGemmaImporting by remember { mutableStateOf(false) }
    var brainUrlInput by remember { mutableStateOf(prefs.brainServerUrl) }
    var cloudKeyInput by remember { mutableStateOf(prefs.frontierApiKey) }
    var selectedProvider by remember { mutableStateOf(prefs.frontierProvider) }

    // Overlay state
    var isOverlayActive by remember { mutableStateOf(prefs.isFloatingOverlayActive) }

    val blockedCount by prefs.blockedCountFlow.collectAsState()
    val cognitivePoints by prefs.cognitivePointsFlow.collectAsState()

    // Ambient Acoustic & Scene Sentinel (Hardware Light, Motion & Environment Classification)
    val ambientSentinel = remember { AmbientAcousticSentinel(context) }
    val ambientDb by ambientSentinel.ambientDbFlow.collectAsState()
    val acousticEnv by ambientSentinel.environmentFlow.collectAsState()
    val situationalCtx by ambientSentinel.situationalContextFlow.collectAsState()

    // 100% On-Device Sherpa-ONNX Voice Engine (Streaming Zipformer ASR, Keyword Spotter, Silero VAD)
    val voiceEngine = remember { NativeSovereignVoiceEngine(context) }
    val engineState by voiceEngine.state.collectAsState()
    val partialTranscript by voiceEngine.partialTranscript.collectAsState()
    val engineError by voiceEngine.error.collectAsState()
    val vadSpeechActive by voiceEngine.vadSpeechActive.collectAsState()
    val voiceAudioDb by voiceEngine.audioDb.collectAsState()
    val isModelLoading by voiceEngine.isModelLoading.collectAsState()
    val isEngineReady by voiceEngine.isReady.collectAsState()

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val micGranted = perms[android.Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            scope.launch {
                runCatching { voiceEngine.start() }
            }
        }
    }

    val localGemmaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isLocalGemmaImporting = true
                executionError = null
                try {
                    companionEngine.installLocalGemmaModel(uri)
                    isLocalGemmaInstalled = companionEngine.hasLocalGemmaModel()
                    latestResponse = "Local Gemma model imported. It will load on your first general chat request; that first response can take longer."
                } catch (error: Exception) {
                    executionError = error.message ?: "Could not import the local model."
                    latestResponse = "Local model import failed."
                } finally {
                    isLocalGemmaImporting = false
                }
            }
        }
    }

    fun handleSend(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return

        latestUserQuery = clean
        inputText = ""
        coreState = JarvisCoreState.THINKING
        executionError = null

        scope.launch {
            try {
                // Pause mic listening while Jarvis responds to prevent acoustic feedback
                voiceEngine.pause()
                val res = companionEngine.processInputWithLocalModel(clean, speakOutput = true)
                if (res.pendingAction != null) {
                    pendingDeviceAction = res.pendingAction
                    latestResponse = "Action awaiting approval: ${res.pendingAction.summary}"
                    coreState = JarvisCoreState.STANDBY
                    return@launch
                }
                coreState = JarvisCoreState.SPEAKING
                latestResponse = res.replyText
                delay(3000)
                coreState = JarvisCoreState.STANDBY
            } catch (e: Throwable) {
                executionError = e.message ?: "Neural synthesis encountered an unexpected issue."
                coreState = JarvisCoreState.STANDBY
            } finally {
                runCatching { voiceEngine.resume() }
            }
        }
    }

    /**
     * Executes only after the user presses APPROVE in the action dialog.
     */
    fun approvePendingAction(proposal: DeviceActionProposal) {
        pendingDeviceAction = null
        executionError = null

        when (proposal.type) {
            DeviceActionType.OPEN_APP -> {
                val appName = proposal.target.orEmpty()
                val (success, message) = companionEngine.actionExecutor.launchApplication(appName)
                latestResponse = message
                if (!success) executionError = message
                coreState = JarvisCoreState.STANDBY
            }

            DeviceActionType.FOCUS_LOCK -> {
                coreState = JarvisCoreState.THINKING
                scope.launch {
                    try {
                        voiceEngine.pause()
                        val result = companionEngine.processApprovedInput(
                            proposal.originalCommand,
                            speakOutput = true
                        )
                        latestResponse = result.replyText
                        coreState = JarvisCoreState.SPEAKING
                        delay(1800)
                        coreState = JarvisCoreState.STANDBY
                    } catch (e: Throwable) {
                        executionError = e.message ?: "JARVIS could not complete the approved action."
                        coreState = JarvisCoreState.STANDBY
                    } finally {
                        runCatching { voiceEngine.resume() }
                    }
                }
            }

            DeviceActionType.DRAFT_SMS -> {
                try {
                    context.startActivity(
                        PermissionFirstActionBroker.createSmsDraftIntent(proposal)
                    )
                    latestResponse = "Messaging app opened with the draft. Nothing was sent; review the recipient and message, then press Send in that app."
                    coreState = JarvisCoreState.STANDBY
                } catch (e: Exception) {
                    executionError = e.message ?: "No compatible messaging app was found."
                    latestResponse = "Could not open the messaging draft."
                    coreState = JarvisCoreState.STANDBY
                }
            }

            DeviceActionType.CALENDAR_REMINDER -> {
                try {
                    context.startActivity(
                        PermissionFirstActionBroker.createCalendarDraftIntent(proposal)
                    )
                    latestResponse = "Calendar event draft opened. Choose the date and time, then save it in Calendar."
                    coreState = JarvisCoreState.STANDBY
                } catch (e: Exception) {
                    executionError = e.message ?: "No compatible calendar app was found."
                    latestResponse = "Could not open the calendar draft."
                    coreState = JarvisCoreState.STANDBY
                }
            }
        }
    }

    // 1. Preload Sherpa-ONNX Zipformer weights in background coroutine on launch
    LaunchedEffect(Unit) {
        voiceEngine.preloadModels()
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic) {
            runCatching { voiceEngine.start() }
        }
    }

    // 2. Feed live mic decibels directly into the Ambient Sentinel & HUD telemetry
    LaunchedEffect(voiceAudioDb) {
        ambientSentinel.updateAudioLevel(voiceAudioDb)
    }

    // 3. Mirror live streaming ASR partial tokens to the input console
    LaunchedEffect(partialTranscript, engineState) {
        if (partialTranscript.isNotBlank() && (engineState == NativeSovereignVoiceEngine.State.STREAMING || engineState == NativeSovereignVoiceEngine.State.SPEECH_DETECTED)) {
            inputText = partialTranscript
            coreState = JarvisCoreState.LISTENING
        }
    }

    // 4. Automatically route finalized commands into Companion Engine with vocal execution
    LaunchedEffect(voiceEngine) {
        voiceEngine.commands.collect { commandText ->
            if (commandText.isNotBlank()) {
                handleSend(commandText)
            }
        }
    }

    DisposableEffect(voiceEngine) {
        ambientSentinel.onSituationalIntervention = { message ->
            latestResponse = message
            scope.launch {
                voiceEngine.pause()
                companionEngine.actionExecutor.speak(message)
                delay(4000)
                runCatching { voiceEngine.resume() }
            }
        }
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (hasMic && prefs.isAcousticRadarActive) {
            ambientSentinel.startSentinel()
        }
        onDispose {
            ambientSentinel.stopSentinel()
            scope.launch {
                runCatching { voiceEngine.close() }
            }
        }
    }

    val requestOverlayPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (Settings.canDrawOverlays(context)) {
            JarvisFloatingOverlayService.start(context)
            isOverlayActive = true
            prefs.isFloatingOverlayActive = true
        }
    }

    fun startListening() {
        val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (!hasMic) {
            Toast.makeText(context, "Microphone permission required for voice input", Toast.LENGTH_SHORT).show()
            permissionsLauncher.launch(
                arrayOf(
                    android.Manifest.permission.RECORD_AUDIO,
                    android.Manifest.permission.POST_NOTIFICATIONS
                )
            )
            return
        }

        if (coreState == JarvisCoreState.LISTENING) {
            scope.launch { runCatching { voiceEngine.pause() } }
            coreState = JarvisCoreState.STANDBY
            return
        }

        // Push-to-talk: bypass wake-word and stream immediately with Zipformer
        coreState = JarvisCoreState.LISTENING
        voiceEngine.forceWake()
    }

    Scaffold(
        containerColor = DeepSpaceObsidian,
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepSpaceObsidian)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(ArcReactorCyan)
                            )
                            Text(
                                "JARVIS // NEURAL OS",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                letterSpacing = 1.sp,
                                color = ArcReactorCyan
                            )
                        }
                        Text(
                            "Samsung Galaxy S25 Sovereign Edition",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = NeutralTextMuted
                        )
                    }

                    // Commander Vivek Profile Chip
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CyberCardSurface,
                        border = BorderStroke(1.dp, ArcReactorCyan.copy(alpha = 0.4f)),
                        modifier = Modifier.tactilePress(pressedScale = 0.95f) { showLifeMatrixDialog = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text("🛡️", fontSize = 12.sp)
                            Text(
                                "Vivek",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Sovereign On-Device Status Banner (100% Autonomous, Offline-Ready)
                SovereignOnDeviceBanner(
                    modifier = Modifier.tactilePress(pressedScale = 0.98f) { showUplinkDialog = true }
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "LOCAL GEMMA 4 MODEL",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            color = ArcReactorCyan
                        )
                        Text(
                            if (isLocalGemmaInstalled) "Export installed — inference is on-device"
                            else "Not installed — scripted offline fallback remains active",
                            fontSize = 10.sp,
                            color = if (isLocalGemmaInstalled) NeonEmerald else NeutralTextMuted
                        )
                    }
                    OutlinedButton(
                        onClick = { localGemmaPicker.launch(arrayOf("*/*")) },
                        enabled = !isLocalGemmaImporting
                    ) {
                        Text(
                            if (isLocalGemmaImporting) "IMPORTING…" else "IMPORT MODEL",
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = 32.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 0. Sovereign Voice Neural Telemetry Strip
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = CyberCardSurface,
                    border = BorderStroke(1.dp, if (vadSpeechActive) NeonEmerald.copy(alpha = 0.6f) else CyberBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isModelLoading -> QuantumAmber
                                            vadSpeechActive -> NeonEmerald
                                            isEngineReady -> ArcReactorCyan
                                            else -> NeutralTextMuted
                                        }
                                    )
                            )
                            Text(
                                when {
                                    isModelLoading -> "LOADING SHERPA-ONNX ZIPFORMER..."
                                    vadSpeechActive -> "VAD: VOICE DETECTED (STREAMING)"
                                    isEngineReady -> "OFFLINE VOICE: READY (SAY 'HEY JARVIS')"
                                    else -> "NEURAL VOICE: STANDBY"
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (vadSpeechActive) NeonEmerald else ArcReactorCyan
                            )
                        }

                        Text(
                            "${voiceAudioDb.toInt()} dB",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeutralTextMuted
                        )
                    }
                }
            }

            // 1. Centerpiece: Arc Reactor Core
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    ArcReactorCore(
                        state = coreState,
                        onClick = {
                            if (coreState == JarvisCoreState.STANDBY) {
                                startListening()
                            } else {
                                coreState = JarvisCoreState.STANDBY
                            }
                        }
                    )
                }
            }

            // 2. Holographic Interaction Console (Handling the 5 Mandatory UI Data States)
            item {
                when {
                    executionError != null -> {
                        ActionableErrorState(
                            title = "NEURAL SYNTHESIS INTERRUPTED",
                            errorMessage = executionError!!,
                            onRetry = {
                                val query = latestUserQuery ?: "Are you there Jarvis?"
                                handleSend(query)
                            }
                        )
                    }
                    coreState == JarvisCoreState.THINKING -> {
                        // Loading State: Shimmer Skeleton matching exact layout geometry
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                            border = BorderStroke(1.2.dp, ArcReactorCyanGlow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(ArcReactorCyan)
                                    )
                                    Text(
                                        "ON-DEVICE NEURAL REASONING...",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ArcReactorCyan
                                    )
                                }
                                Spacer(modifier = Modifier.height(12.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(16.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(0.55f)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .shimmerEffect()
                                )
                            }
                        }
                    }
                    latestResponse.isEmpty() -> {
                        // Actionable Empty State
                        ActionableEmptyState(
                            icon = "🛡️",
                            title = "SOVEREIGN SYSTEM READY",
                            description = "Operating 100% locally on your Samsung Galaxy S25 with zero network latency. All proofs and focus routines run on-device.",
                            actionButtonText = "Solve Stokes' Theorem Proof",
                            onActionClick = { handleSend("Derive Stokes Theorem") }
                        )
                    }
                    else -> {
                        // Ideal State
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = CyberCardSurface,
                            border = BorderStroke(1.2.dp, ArcReactorCyanGlow),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                if (latestUserQuery != null) {
                                    Text(
                                        "COMMANDER VIVEK:",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = QuantumAmber
                                    )
                                    Text(
                                        latestUserQuery!!,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        modifier = Modifier.padding(bottom = 12.dp)
                                    )
                                    Divider(color = CyberBorderGlow, thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))
                                }

                                Text(
                                    "JARVIS NEURAL SYNTHESIS:",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = ArcReactorCyan
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    latestResponse,
                                    fontSize = 13.sp,
                                    lineHeight = 20.sp,
                                    color = Color(0xFFE2E8F0)
                                )
                            }
                        }
                    }
                }
            }

            // 3. Command Input Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Command Jarvis...", color = NeutralTextMuted, fontSize = 13.sp) },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = ArcReactorCyan,
                            unfocusedBorderColor = CyberBorderGlow,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedContainerColor = CyberCardSurface,
                            unfocusedContainerColor = CyberCardSurface
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    // Mic Button with Emil Kowalski tactile physics
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(
                                if (coreState == JarvisCoreState.LISTENING) ArcReactorCyan else ArcReactorCyan.copy(alpha = 0.2f)
                            )
                            .tactilePress(pressedScale = 0.92f) { startListening() }
                    ) {
                        Text("🎙️", fontSize = 18.sp)
                    }

                    // Send Button with Emil Kowalski tactile physics
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(ArcReactorCyan)
                            .tactilePress(pressedScale = 0.92f) { handleSend(inputText) }
                    ) {
                        Text("⚡", fontSize = 18.sp, color = DeepSpaceObsidian)
                    }
                }
            }

            // 4. Tactical Life Matrix & Telemetry Hub
            item {
                Text(
                    "REAL-TIME SURROUNDING RADAR // ACOUSTICS",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = ArcReactorCyan
                )
            }

            // Card: Ambient Acoustic & Situational Radar (Continuous Listening & Environment Awareness)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyberCardSurface,
                    border = BorderStroke(1.2.dp, ArcReactorCyanGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🎧", fontSize = 18.sp)
                                Column {
                                    Text("Ambient Acoustic & Scene Radar", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text(
                                        when (acousticEnv) {
                                            AcousticEnvironment.QUIET_STUDY_SANCTUARY -> "Study Sanctuary (<45 dB)"
                                            AcousticEnvironment.MODERATE_AMBIENT -> "Moderate Ambient Sound"
                                            AcousticEnvironment.NOISY_CHAOS -> "High Noise Distraction Zone"
                                        },
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = when (acousticEnv) {
                                            AcousticEnvironment.QUIET_STUDY_SANCTUARY -> NeonEmerald
                                            AcousticEnvironment.MODERATE_AMBIENT -> QuantumAmber
                                            AcousticEnvironment.NOISY_CHAOS -> AlertCrimson
                                        }
                                    )
                                }
                            }
                            Text(
                                "${ambientDb.toInt()} dB",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = ArcReactorCyan
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Linear progress indicator for ambient noise level
                        LinearProgressIndicator(
                            progress = { (ambientDb / 100.0f).coerceIn(0.1f, 1.0f) },
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                            color = when (acousticEnv) {
                                AcousticEnvironment.QUIET_STUDY_SANCTUARY -> NeonEmerald
                                AcousticEnvironment.MODERATE_AMBIENT -> QuantumAmber
                                AcousticEnvironment.NOISY_CHAOS -> AlertCrimson
                            },
                            trackColor = CyberBorderGlow
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Fused Sensor Metrics
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "Desk: ${if (situationalCtx.isDeviceStationary) "Stationary (Rest)" else "In-Hand / Motion"}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NeutralTextMuted
                            )
                            Text(
                                "Light: ${situationalCtx.lightLux.toInt()} Lux",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = NeutralTextMuted
                            )
                            Text(
                                "Audio: ${if (situationalCtx.isHeadsetConnected) "Headphones 🎧" else "Speaker 🔊"}",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                color = ArcReactorCyan
                            )
                        }
                    }
                }
            }

            // Card: DRSI & Dopamine Armor
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyberCardSurface,
                    border = BorderStroke(1.dp, CyberBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(ArcReactorCyan.copy(alpha = 0.15f))
                        ) {
                            Text("🧬", fontSize = 22.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Dopamine Receptor Index", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text("86.5%", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold, color = NeonEmerald, fontSize = 13.sp)
                            }
                            Text(
                                "Sovereign Baseline: D2 receptors primed for hardest mathematical proofs and algorithms.",
                                fontSize = 11.sp,
                                color = NeutralTextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Card: Circadian & Sleep Window
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyberCardSurface,
                    border = BorderStroke(1.dp, CyberBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(54.dp)
                                .clip(CircleShape)
                                .background(QuantumAmber.copy(alpha = 0.15f))
                        ) {
                            Text("🌙", fontSize = 22.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Circadian Lockdown", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                Text("Target: ${prefs.targetSleepTime}", fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, color = QuantumAmber, fontSize = 12.sp)
                            }
                            Text(
                                "Autonomic wind-down protocol activates at ${prefs.targetSleepTime}. Target wake-up: ${prefs.targetWakeTime}.",
                                fontSize = 11.sp,
                                color = NeutralTextMuted,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // Card: Device Oversight & Floating HUD (King of the Phone)
            item {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = CyberCardSurface,
                    border = BorderStroke(1.dp, CyberBorderGlow),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🛸", fontSize = 18.sp)
                                Column {
                                    Text("Floating HUD Overlay", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                    Text("Persistent Arc Reactor orb on all apps", fontSize = 10.sp, color = NeutralTextMuted)
                                }
                            }
                            Switch(
                                checked = isOverlayActive,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        if (Settings.canDrawOverlays(context)) {
                                            JarvisFloatingOverlayService.start(context)
                                            isOverlayActive = true
                                            prefs.isFloatingOverlayActive = true
                                        } else {
                                            val intent = Intent(
                                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                Uri.parse("package:${context.packageName}")
                                            )
                                            requestOverlayPermission.launch(intent)
                                        }
                                    } else {
                                        JarvisFloatingOverlayService.stop(context)
                                        isOverlayActive = false
                                        prefs.isFloatingOverlayActive = false
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 5. Tactical Quick Action Grid
            item {
                Text(
                    "TACTICAL PROTOCOLS // ONE-TOUCH",
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp,
                    color = ArcReactorCyan
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TacticalActionButton(
                        icon = "🔒",
                        title = "90m Ultradian",
                        subtitle = "Peak Flow Lock",
                        color = QuantumAmber,
                        modifier = Modifier.weight(1f)
                    ) {
                        handleSend("Start 90 minute ultradian deep work block on STEM")
                    }

                    TacticalActionButton(
                        icon = "🫁",
                        title = "Physiological Sigh",
                        subtitle = "Crush Craving",
                        color = HologramTeal,
                        modifier = Modifier.weight(1f)
                    ) {
                        handleSend("Physiological sigh")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    TacticalActionButton(
                        icon = "🎓",
                        title = "Feynman Loop",
                        subtitle = "Jargon-Free Test",
                        color = ArcReactorCyan,
                        modifier = Modifier.weight(1f)
                    ) {
                        handleSend("Feynman challenge on Stokes Theorem")
                    }

                    TacticalActionButton(
                        icon = "📐",
                        title = "Mistake Drill",
                        subtitle = "Spaced Repetition",
                        color = NeonEmerald,
                        modifier = Modifier.weight(1f)
                    ) {
                        handleSend("drill my mistakes")
                    }
                }
            }
        }
    }

    // Action approval: every routed device action must be explicitly approved.
    pendingDeviceAction?.let { action ->
        AlertDialog(
            onDismissRequest = {
                pendingDeviceAction = null
                latestResponse = "Cancelled. No device action was performed."
            },
            title = {
                Text(
                    "JARVIS ACTION APPROVAL",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = QuantumAmber
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        action.summary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        action.explanation,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    action.target?.let { target ->
                        Text(
                            "Target / title: ${target}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = ArcReactorCyan
                        )
                    }
                    action.messageBody?.let { body ->
                        Text(
                            "Message draft:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ArcReactorCyan
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DeepSpaceObsidian,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                body,
                                color = Color.White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    Text(
                        "No action runs until you approve.",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = QuantumAmber
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { approvePendingAction(action) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonEmerald)
                ) {
                    Text("APPROVE", color = DeepSpaceObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingDeviceAction = null
                        latestResponse = "Cancelled. No device action was performed."
                    }
                ) {
                    Text("CANCEL", color = NeutralTextMuted)
                }
            },
            containerColor = CyberCardSurface
        )
    }

    // Modal: Neural Uplink Config (PC Brain / Cloud Frontier Model)
    if (showUplinkDialog) {
        AlertDialog(
            onDismissRequest = { showUplinkDialog = false },
            title = {
                Text(
                    "⚡ NEURAL UPLINK CONFIGURATION",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = ArcReactorCyan
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Connect Jarvis to your PC Brain or direct Cloud Frontier Model (Claude, GPT-4o, DeepSeek, Groq):",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )

                    OutlinedTextField(
                        value = brainUrlInput,
                        onValueChange = { brainUrlInput = it },
                        label = { Text("PC Brain Host IP") },
                        placeholder = { Text("e.g. http://192.168.1.5:8080") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = cloudKeyInput,
                        onValueChange = { cloudKeyInput = it },
                        label = { Text("Direct Cloud API Key (Optional)") },
                        placeholder = { Text("sk-... or gsk_...") },
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("PC_BRAIN", "OPENAI", "DEEPSEEK", "GROQ").forEach { prov ->
                            FilterChip(
                                selected = selectedProvider == prov,
                                onClick = { selectedProvider = prov },
                                label = { Text(prov, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.brainServerUrl = brainUrlInput.trim()
                        prefs.frontierApiKey = cloudKeyInput.trim()
                        prefs.frontierProvider = selectedProvider
                        showUplinkDialog = false
                        Toast.makeText(context, "Neural Uplink Updated", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ArcReactorCyan)
                ) {
                    Text("SAVE UPLINK", color = DeepSpaceObsidian, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUplinkDialog = false }) {
                    Text("CANCEL", color = NeutralTextMuted)
                }
            },
            containerColor = CyberCardSurface
        )
    }

    // Modal: Commander Vivek Life Matrix
    if (showLifeMatrixDialog) {
        AlertDialog(
            onDismissRequest = { showLifeMatrixDialog = false },
            title = {
                Text(
                    "🛡️ COMMANDER VIVEK // LIFE MATRIX",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = QuantumAmber
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "• Primary Objective: ${prefs.primaryAmbition}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    Text("• Target Sleep Window: ${prefs.targetSleepTime} -> ${prefs.targetWakeTime}", fontSize = 12.sp, color = NeutralTextMuted)
                    Text("• Streak: Day ${prefs.getStreak()} of Unbroken Discipline", fontSize = 12.sp, color = QuantumAmber)
                    Text("• Distractions Intercepted: $blockedCount feeds & dopamine loops blocked", fontSize = 12.sp, color = NeonEmerald)
                    Text("• Active Weak Spots Under Mastery: Stokes Theorem, Carnot Cycle, 0/1 Knapsack DP", fontSize = 11.sp, color = ArcReactorCyan)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showLifeMatrixDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = QuantumAmber)
                ) {
                    Text("CLOSE", color = DeepSpaceObsidian, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = CyberCardSurface
        )
    }
}

@Composable
fun TacticalActionButton(
    icon: String,
    title: String,
    subtitle: String,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = CyberCardSurface,
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
        modifier = modifier.tactilePress(pressedScale = 0.96f) { onClick() }
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(icon, fontSize = 20.sp)
            Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
            Text(subtitle, fontSize = 10.sp, color = color, fontFamily = FontFamily.Monospace)
        }
    }
}
