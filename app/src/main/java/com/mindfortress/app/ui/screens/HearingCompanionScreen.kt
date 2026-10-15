package com.mindfortress.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.mindfortress.app.ai.ForegroundVoiceSession
import com.mindfortress.app.ai.HandsFreeVoiceService
import com.mindfortress.app.ai.NativeSovereignVoiceEngine
import com.mindfortress.app.data.PreferencesManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * User-initiated, on-device live speech captions for people who benefit from visual
 * access to conversation. Text is high-contrast and adjustable; audio is not saved.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HearingCompanionScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engine = remember { NativeSovereignVoiceEngine(context) }
    val preferences = remember { PreferencesManager.getInstance(context) }
    val foregroundVoiceSessionToken = remember { ForegroundVoiceSession.claimUiSession() }
    val restoreHandsFree = remember { preferences.isHandsFreeVoiceActive }

    val partial by engine.partialTranscript.collectAsState()
    val engineState by engine.state.collectAsState()
    val engineError by engine.error.collectAsState()
    val isModelLoading by engine.isModelLoading.collectAsState()
    val isReady by engine.isReady.collectAsState()
    val audioLevel by engine.audioDb.collectAsState()

    val captions = remember { mutableStateListOf<String>() }
    val listState = rememberLazyListState()
    var isListening by remember { mutableStateOf(false) }
    var statusMessage by remember {
        mutableStateOf("Captions are off. Tap Start live captions when you need help following speech.")
    }
    var captionFontSize by remember { mutableFloatStateOf(28f) }
    var selectedCaptionIndex by remember { mutableIntStateOf(-1) }
    var selectedCaptionSource by remember { mutableStateOf("") }
    var correctionText by remember { mutableStateOf("") }

    fun beginCaptions() {
        if (isListening) return
        scope.launch {
            try {
                // Hand over the exclusive microphone from the optional hands-free service.
                if (preferences.isHandsFreeVoiceActive) {
                    HandsFreeVoiceService.stop(context)
                    delay(450)
                }
                engine.startCaptions()
                isListening = true
                statusMessage = "Live captions are running locally. Ambient speech is never routed as a command."
            } catch (error: Throwable) {
                isListening = false
                statusMessage = error.message
                    ?: "Could not start captions. Close other microphone features and try again."
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            beginCaptions()
        } else {
            statusMessage = "Microphone permission is required. You can still review saved local captions."
        }
    }

    LaunchedEffect(engine) {
        engine.preloadModels()
    }

    LaunchedEffect(engine) {
        engine.captionEvents.collect { caption ->
            if (caption.isNotBlank()) {
                captions.add(caption)
                if (captions.size > 60) captions.removeAt(0)
            }
        }
    }

    LaunchedEffect(captions.size) {
        if (captions.isNotEmpty()) listState.animateScrollToItem(captions.lastIndex)
    }

    DisposableEffect(engine) {
        onDispose {
            engine.closeAsync {
                val ownsHandover = ForegroundVoiceSession.releaseUiSession(foregroundVoiceSessionToken)
                if (ownsHandover && restoreHandsFree) HandsFreeVoiceService.start(context)
            }
        }
    }

    Scaffold(
        containerColor = Color(0xFF05080D),
        topBar = {
            TopAppBar(
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            "HEARING COMPANION",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = Color(0xFF76E9FF)
                        )
                        Text(
                            "ON-DEVICE LIVE CAPTIONS",
                            fontSize = 10.sp,
                            letterSpacing = 1.2.sp,
                            color = Color(0xFFB7C7D4)
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("Back", color = Color(0xFF76E9FF))
                    }
                },
                actions = {
                    TextButton(onClick = { captions.clear() }) {
                        Text("Clear", color = Color(0xFF76E9FF))
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF05080D))
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111923)),
                border = BorderStroke(1.dp, Color(0xFF244052)),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            if (isListening) "CAPTIONS ACTIVE" else "CAPTIONS STANDBY",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isListening) Color(0xFF73F2B2) else Color(0xFFB7C7D4)
                        )
                        Text(
                            when (engineState) {
                                NativeSovereignVoiceEngine.State.STREAMING -> "Transcribing"
                                NativeSovereignVoiceEngine.State.SPEECH_DETECTED -> "Speech detected"
                                NativeSovereignVoiceEngine.State.UTTERANCE_COMPLETE -> "Phrase complete"
                                else -> if (isModelLoading) "Loading model" else "Ready"
                            },
                            fontSize = 11.sp,
                            color = Color(0xFF76E9FF)
                        )
                    }

                    Text(
                        "Live text",
                        fontSize = 12.sp,
                        color = Color(0xFFB7C7D4)
                    )
                    Surface(
                        color = Color(0xFF000000),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFF405361)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .semantics { contentDescription = "Live speech caption" }
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = partial.ifBlank {
                                    if (isListening) "Listening for the next phrase…" else "Your captions will appear here."
                                },
                                fontSize = captionFontSize.sp,
                                lineHeight = (captionFontSize * 1.25f).sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (isListening) {
                                    scope.launch {
                                        runCatching { engine.pause() }
                                        isListening = false
                                        statusMessage = "Captions paused. The microphone has been released."
                                    }
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) beginCaptions()
                                    else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        ) {
                            Text(if (isListening) "Pause captions" else "Start live captions")
                        }
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    runCatching { engine.pause() }
                                    isListening = false
                                    statusMessage = "Microphone stopped for privacy."
                                }
                            }
                        ) {
                            Text("Stop")
                        }
                    }

                    Text(
                        statusMessage,
                        fontSize = 12.sp,
                        color = Color(0xFFD3DEE8)
                    )
                    if (!engineError.isNullOrBlank()) {
                        Text(
                            engineError.orEmpty(),
                            fontSize = 12.sp,
                            color = Color(0xFFFFB4AB)
                        )
                    }
                    if (!isReady && !isModelLoading) {
                        Text(
                            "Models are not ready yet. Check that the ASR, keyword and Silero VAD assets are present in the app.",
                            fontSize = 11.sp,
                            color = Color(0xFFFFD180)
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111923)),
                border = BorderStroke(1.dp, Color(0xFF244052)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Caption text size", color = Color.White, fontSize = 13.sp)
                        Text("${captionFontSize.toInt()} sp", color = Color(0xFF76E9FF), fontSize = 12.sp)
                    }
                    Slider(
                        value = captionFontSize,
                        onValueChange = { captionFontSize = it },
                        valueRange = 20f..38f,
                        steps = 8,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "Use the larger setting for reading at a distance. This changes only the display.",
                        fontSize = 11.sp,
                        color = Color(0xFFB7C7D4)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("RECENT CAPTIONS", color = Color(0xFF76E9FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Text("${captions.size} phrases", color = Color(0xFFB7C7D4), fontSize = 11.sp)
            }

            if (captions.isEmpty()) {
                Text(
                    "Completed phrases will appear here. Select Correct on a phrase to teach JARVIS your preferred spelling or wording.",
                    fontSize = 13.sp,
                    color = Color(0xFFB7C7D4),
                    lineHeight = 19.sp
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(captions) { index, caption ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D141C)),
                            border = BorderStroke(1.dp, Color(0xFF263846)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    caption,
                                    fontSize = (captionFontSize - 2f).sp,
                                    lineHeight = ((captionFontSize - 2f) * 1.2f).sp,
                                    color = Color.White
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    TextButton(
                                        onClick = {
                                            selectedCaptionIndex = index
                                            selectedCaptionSource = caption
                                            correctionText = caption
                                        }
                                    ) {
                                        Text("Correct phrase", color = Color(0xFF76E9FF), fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (selectedCaptionIndex >= 0) {
        AlertDialog(
            onDismissRequest = { selectedCaptionIndex = -1 },
            title = { Text("Teach a correction") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("The original phrase stays on-device. Your correction is stored locally for future transcripts.")
                    TextField(
                        value = correctionText,
                        onValueChange = { correctionText = it },
                        label = { Text("Corrected phrase") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val index = selectedCaptionIndex
                        val corrected = correctionText.trim()
                        val saved = engine.savePhraseCorrection(selectedCaptionSource, corrected)
                        if (saved && index in captions.indices) captions[index] = corrected
                        statusMessage = if (saved) {
                            "Correction saved on this device and will be applied to future transcripts."
                        } else {
                            "Enter a different, non-empty correction."
                        }
                        selectedCaptionIndex = -1
                    }
                ) {
                    Text("Save correction")
                }
            },
            dismissButton = {
                TextButton(onClick = { selectedCaptionIndex = -1 }) {
                    Text("Cancel")
                }
            }
        )
    }
}
