package com.mindfortress.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.ai.HandsFreeVoiceService
import com.mindfortress.app.ai.LocalCompanionEngine
import com.mindfortress.app.ai.MicrophoneCaptureArbiter
import com.mindfortress.app.ai.NativeSovereignVoiceEngine
import com.mindfortress.app.ai.VoiceBiometricManager
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.theme.AmberFocus
import com.mindfortress.app.theme.FortressTeal
import com.mindfortress.app.theme.SuccessEmerald
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

data class ChatMessage(val text: String, val isUser: Boolean, val timestamp: Long = System.currentTimeMillis())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocalCompanionScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    val companionEngine = remember { LocalCompanionEngine(context) }
    val localLlmRuntime = remember { companionEngine.localLlmRuntime }
    val voiceBiometric = remember { VoiceBiometricManager(context) }
    val voiceEngine = remember { NativeSovereignVoiceEngine(context) }
    val enrollmentMicOwner = remember { Any() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isHandsFreeActive by remember { mutableStateOf(prefs.isHandsFreeVoiceActive) }
    var isVoiceEnrolled by remember { mutableStateOf(voiceBiometric.isEnrolled) }
    var isRecordingEnrollment by remember { mutableStateOf(false) }
    var enrollmentCountdown by remember { mutableStateOf(3) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(prefs.brainServerUrl) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hello! JARVIS can use its bundled on-device speech model for voice input and local captions.\n\nWake-word detection is not proof of speaker identity, and voice enrollment is experimental. Keep confirmations enabled for sensitive actions.",
                isUser = false
            )
        )
    }

    var isTtsSpeaking by remember { mutableStateOf(false) }

    var isListeningInApp by remember { mutableStateOf(false) }
    var speechHint by remember { mutableStateOf("") }
    var importedModelPath by remember { mutableStateOf(localLlmRuntime.configuredModelPath) }
    var isModelImporting by remember { mutableStateOf(false) }
    var modelStatus by remember {
        mutableStateOf(
            if (localLlmRuntime.isModelConfigured) "Local model selected; it will load on the first open-ended question."
            else "No local language model installed. Built-in commands and topic rules still work."
        )
    }

    val modelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                isModelImporting = true
                modelStatus = "Importing model into app-private storage. Large models can take a while."
                try {
                    val imported = localLlmRuntime.importModel(uri)
                    importedModelPath = imported.absolutePath
                    modelStatus = "Imported ${imported.name}. First response will load the model on CPU."
                    Toast.makeText(context, "Local language model imported.", Toast.LENGTH_SHORT).show()
                } catch (error: Throwable) {
                    modelStatus = error.message ?: "Model import failed."
                    Toast.makeText(context, modelStatus, Toast.LENGTH_LONG).show()
                } finally {
                    isModelImporting = false
                }
            }
        }
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        messages.add(ChatMessage(trimmed, isUser = true))
        inputText = ""
        isListeningInApp = false
        speechHint = ""

        scope.launch {
            // Pause voice capture before reasoning/TTS to prevent feedback and mic contention.
            runCatching { voiceEngine.pause() }
            if (prefs.isHandsFreeVoiceActive) {
                HandsFreeVoiceService.stop(context)
                delay(450)
            }

            try {
                val res = companionEngine.processInput(trimmed, speakOutput = false)
                messages.add(ChatMessage(res.replyText, isUser = false))
                listState.animateScrollToItem(messages.size - 1)

                val speech = res.speechText.ifBlank { res.replyText }
                if (speech.isNotBlank()) {
                    companionEngine.actionExecutor.speak(speech)
                    isTtsSpeaking = true
                }
                delay(4000)
            } catch (error: Throwable) {
                messages.add(ChatMessage("I couldn't complete that request: ${error.message ?: "unknown error"}", isUser = false))
            } finally {
                if (prefs.isHandsFreeVoiceActive) HandsFreeVoiceService.start(context)
            }
        }
    }

    fun startListeningInternal() {
        if (isListeningInApp) return
        isListeningInApp = true
        speechHint = "Preparing on-device speech recognition…"
        inputText = ""

        scope.launch {
            if (prefs.isHandsFreeVoiceActive) {
                HandsFreeVoiceService.stop(context)
                delay(450)
            }
            voiceEngine.forceWake()
            speechHint = "Listening locally. Speak your question, then pause."
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startListeningInternal()
        } else {
            Toast.makeText(context, "Microphone permission is required for voice commands", Toast.LENGTH_SHORT).show()
        }
    }

    fun startEnrollmentRecording() {
        if (isRecordingEnrollment) return
        isRecordingEnrollment = true
        enrollmentCountdown = 3
        Toast.makeText(context, "Recording a local voice profile (experimental). Please speak clearly for 3 seconds.", Toast.LENGTH_SHORT).show()

        scope.launch {
            runCatching { voiceEngine.pause() }
            if (prefs.isHandsFreeVoiceActive) {
                HandsFreeVoiceService.stop(context)
                delay(500)
            }

            val sampleRate = 16000
            val totalSamples = sampleRate * 3
            val recordedData = ShortArray(totalSamples)

            val success = withContext(Dispatchers.IO) {
                if (!MicrophoneCaptureArbiter.tryAcquire(enrollmentMicOwner)) {
                    Log.w("LocalCompanionScreen", "Enrollment could not acquire exclusive microphone lease")
                    return@withContext false
                }

                var record: AudioRecord? = null
                try {
                    val minBufferSize = AudioRecord.getMinBufferSize(
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT
                    ).coerceAtLeast(sampleRate * 2)
                    record = AudioRecord(
                        MediaRecorder.AudioSource.MIC,
                        sampleRate,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        minBufferSize
                    )

                    if (record.state != AudioRecord.STATE_INITIALIZED) {
                        Log.e("LocalCompanionScreen", "AudioRecord not initialized")
                        return@withContext false
                    }

                    record.startRecording()
                    var samplesRead = 0
                    val readBuffer = ShortArray(1024)
                    val startTime = System.currentTimeMillis()
                    while (samplesRead < totalSamples && isRecordingEnrollment) {
                        val toRead = minOf(readBuffer.size, totalSamples - samplesRead)
                        val read = record.read(readBuffer, 0, toRead)
                        if (read > 0) {
                            System.arraycopy(readBuffer, 0, recordedData, samplesRead, read)
                            samplesRead += read
                        }
                        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                        withContext(Dispatchers.Main) { enrollmentCountdown = maxOf(1, 3 - elapsed) }
                    }
                    samplesRead == totalSamples
                } catch (e: Exception) {
                    Log.e("LocalCompanionScreen", "Error during local voice enrollment recording: ${e.message}")
                    false
                } finally {
                    runCatching { record?.stop() }
                    runCatching { record?.release() }
                    MicrophoneCaptureArbiter.release(enrollmentMicOwner)
                }
            }

            if (success) {
                val enrolled = voiceBiometric.enrollSpeaker(recordedData)
                if (enrolled) {
                    isVoiceEnrolled = true
                    Toast.makeText(context, "Voice profile saved locally. It is not a secure identity factor.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Enrollment rejected: signal too quiet or silence detected. Please try again in a quiet room.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Microphone recording failed or another feature still owns the microphone.", Toast.LENGTH_SHORT).show()
            }
            isRecordingEnrollment = false
            if (prefs.isHandsFreeVoiceActive) {
                delay(450)
                HandsFreeVoiceService.start(context)
            }
        }
    }

    val enrollPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            startEnrollmentRecording()
        } else {
            Toast.makeText(context, "Microphone permission is required to enroll your voice.", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(voiceEngine) {
        voiceEngine.partialTranscript.collect { partial ->
            if (isListeningInApp && partial.isNotBlank()) {
                inputText = partial
                speechHint = "Live on-device transcription…"
            }
        }
    }

    LaunchedEffect(voiceEngine) {
        voiceEngine.commands.collect { command ->
            if (isListeningInApp && command.isNotBlank()) sendMessage(command)
        }
    }

    LaunchedEffect(voiceEngine) {
        voiceEngine.error.collect { error ->
            if (isListeningInApp && !error.isNullOrBlank()) {
                isListeningInApp = false
                speechHint = "Local microphone error: $error"
                if (prefs.isHandsFreeVoiceActive) {
                    delay(350)
                    HandsFreeVoiceService.start(context)
                }
            }
        }
    }

    DisposableEffect(voiceEngine) {
        onDispose {
            voiceEngine.closeAsync {
                if (prefs.isHandsFreeVoiceActive) HandsFreeVoiceService.start(context)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Jarvis S25: Friend & Teacher", fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(SuccessEmerald, CircleShape)
                            )
                        }
                        Text(
                            "ON-DEVICE SHERPA-ONNX | LOCAL VOICE INPUT",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = MaterialTheme.colorScheme.primary) }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (isTtsSpeaking) {
                                companionEngine.actionExecutor.stopSpeaking()
                                isTtsSpeaking = false
                            } else {
                                val lastAssistantMsg = messages.lastOrNull { !it.isUser }
                                lastAssistantMsg?.let {
                                    companionEngine.actionExecutor.speak(it.text)
                                    isTtsSpeaking = true
                                }
                            }
                        }
                    ) {
                        Text(if (isTtsSpeaking) "🔇" else "🔊", fontSize = 20.sp)
                    }

                    IconButton(onClick = { showServerDialog = true }) {
                        Text("⚙️", fontSize = 18.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Hands-free voice control status
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Zero-Touch Voice Control",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = when {
                                    isRecordingEnrollment -> "🎙️ Enrolling (${enrollmentCountdown}s)"
                                    isVoiceEnrolled -> "✅ Enrolled"
                                    else -> "⚠️ Not Enrolled"
                                },
                                fontSize = 11.sp,
                                color = if (isVoiceEnrolled) SuccessEmerald else AmberFocus,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Text(
                            text = "Listens in background and responds only to your unique registered voice.",
                            style = MaterialTheme.typography.bodySmall,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isRecordingEnrollment) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp,
                                    color = FortressTeal
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    "${enrollmentCountdown}s",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = FortressTeal
                                )
                            }
                        } else if (!isVoiceEnrolled) {
                            TextButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        startEnrollmentRecording()
                                    } else {
                                        enrollPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            ) {
                                Text("Enroll Voice", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (hasPermission) {
                                        startEnrollmentRecording()
                                    } else {
                                        enrollPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            ) {
                                Text("Re-enroll", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Switch(
                            checked = isHandsFreeActive,
                            onCheckedChange = { checked ->
                                isHandsFreeActive = checked
                                prefs.isHandsFreeVoiceActive = checked
                                if (checked) {
                                    HandsFreeVoiceService.start(context)
                                    Toast.makeText(context, "Hands-free listening requested. A visible notification appears while microphone capture is active.", Toast.LENGTH_LONG).show()
                                } else {
                                    HandsFreeVoiceService.stop(context)
                                    Toast.makeText(context, "Hands-free listening stopped.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                    }
                }
            }

            // Real on-device LLM is optional: the model file is selected and installed by the user.
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = androidx.compose.foundation.BorderStroke(1.dp, FortressTeal.copy(alpha = 0.55f))
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🧠", fontSize = 19.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("On-device LLM", fontWeight = FontWeight.Bold, color = FortressTeal)
                            Text(
                                if (importedModelPath.isNotBlank()) importedModelPath.substringAfterLast('/') else "No .litertlm model selected",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                        if (isModelImporting) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    }
                    Text(
                        modelStatus,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = !isModelImporting,
                            onClick = { modelPickerLauncher.launch(arrayOf("*/*")) }
                        ) {
                            Text(if (importedModelPath.isBlank()) "Import model" else "Replace model", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !isModelImporting && importedModelPath.isNotBlank(),
                            onClick = {
                                scope.launch {
                                    isModelImporting = true
                                    try {
                                        localLlmRuntime.removeModel()
                                        importedModelPath = ""
                                        modelStatus = "Model removed. Built-in commands and local rules remain available."
                                    } catch (error: Throwable) {
                                        modelStatus = error.message ?: "Could not remove the model."
                                    } finally {
                                        isModelImporting = false
                                    }
                                }
                            }
                        ) {
                            Text("Remove", fontSize = 12.sp)
                        }
                        TextButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            Uri.parse("https://huggingface.co/litert-community")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                }.onFailure { modelStatus = "Couldn't open the model catalogue: ${it.message}" }
                            }
                        ) {
                            Text("Models", fontSize = 12.sp)
                        }
                    }
                }
            }

            // Message Stream
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }

                items(messages) { msg ->
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (msg.isUser) Alignment.CenterEnd else Alignment.CenterStart
                    ) {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (msg.isUser) FortressTeal else MaterialTheme.colorScheme.surface
                            ),
                            modifier = Modifier.widthIn(max = 320.dp)
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = msg.text,
                                    color = if (msg.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                                    fontSize = 15.sp,
                                    lineHeight = 22.sp
                                )
                            }
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // Quick Action Control Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SuggestionChip(
                    onClick = { sendMessage("Lock my phone for 60 minutes") },
                    label = { Text("🔒 Lock 60m", fontSize = 12.sp) }
                )
                SuggestionChip(
                    onClick = { sendMessage("Quiz me on my subjects") },
                    label = { Text("🧠 Quiz Me", fontSize = 12.sp) }
                )
                SuggestionChip(
                    onClick = { sendMessage("I feel lazy and unmotivated") },
                    label = { Text("🤝 Reset Mind", fontSize = 12.sp) }
                )
            }

            // Active Listening Banner
            if (speechHint.isNotEmpty()) {
                Surface(
                    color = FortressTeal.copy(alpha = 0.15f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🎙️", fontSize = 14.sp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            speechHint,
                            color = FortressTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Input Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (isListeningInApp) {
                                scope.launch {
                                    runCatching { voiceEngine.pause() }
                                    isListeningInApp = false
                                    speechHint = "Microphone paused."
                                    if (prefs.isHandsFreeVoiceActive) {
                                        delay(350)
                                        HandsFreeVoiceService.start(context)
                                    }
                                }
                            } else {
                                val hasPermission = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED

                                if (hasPermission) {
                                    startListeningInternal()
                                } else {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        }
                    ) {
                        if (isListeningInApp) {
                            Text("🔴", fontSize = 22.sp)
                        } else {
                            Text("🎙️", fontSize = 22.sp)
                        }
                    }

                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Speak or ask anything...") },
                        modifier = Modifier.weight(1f),
                        singleLine = false,
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp)
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { sendMessage(inputText) },
                        shape = CircleShape,
                        colors = ButtonDefaults.buttonColors(containerColor = FortressTeal),
                        modifier = Modifier.size(48.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text("➤", fontSize = 18.sp, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
        }
    }

    if (showServerDialog) {
        AlertDialog(
            onDismissRequest = { showServerDialog = false },
            title = { Text("PC Brain Server URL", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "Enter your PC's IP address and port (e.g. http://192.168.1.15:8080) to federate with the PC frontier models and deterministic math sandbox.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = serverUrlInput,
                        onValueChange = { serverUrlInput = it },
                        placeholder = { Text("http://192.168.x.x:8080") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        prefs.brainServerUrl = serverUrlInput.trim()
                        showServerDialog = false
                        Toast.makeText(context, "Brain Server URL saved!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
