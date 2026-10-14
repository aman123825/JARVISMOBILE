package com.mindfortress.app.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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
    val voiceBiometric = remember { VoiceBiometricManager(context) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var inputText by remember { mutableStateOf("") }
    var isHandsFreeActive by remember { mutableStateOf(false) }
    var isVoiceEnrolled by remember { mutableStateOf(voiceBiometric.isEnrolled) }
    var isRecordingEnrollment by remember { mutableStateOf(false) }
    var enrollmentCountdown by remember { mutableStateOf(3) }
    var showServerDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(prefs.brainServerUrl) }

    val messages = remember {
        mutableStateListOf(
            ChatMessage(
                text = "Hello my friend. I am running locally on your Samsung Galaxy S25 as your teacher, companion, and zero-touch focus guardian.\n\nOnce your voice is registered, you never have to touch your phone: just speak, and I will execute tasks exclusively for your voice.",
                isUser = false
            )
        )
    }

    var isTtsSpeaking by remember { mutableStateOf(false) }

    var isListeningInApp by remember { mutableStateOf(false) }
    var speechHint by remember { mutableStateOf("") }

    val speechRecognizer = remember {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else null
    }

    fun sendMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return

        messages.add(ChatMessage(trimmed, isUser = true))
        inputText = ""

        scope.launch {
            val res = companionEngine.processInputWithLocalModel(trimmed)
            val visibleReply = if (res.pendingAction != null) {
                "${res.replyText}\n\nOpen the main JARVIS HUD to review and approve this action. Nothing was executed here."
            } else res.replyText
            messages.add(ChatMessage(visibleReply, isUser = false))
            listState.animateScrollToItem(messages.size - 1)

            // Auto-speak response
            companionEngine.actionExecutor.speak(res.replyText)
            isTtsSpeaking = true
        }
    }

    fun startListeningInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        speechRecognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                isListeningInApp = true
                speechHint = "Listening... Speak your command"
            }
            override fun onBeginningOfSpeech() {
                speechHint = "Listening to your voice..."
            }
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {
                isListeningInApp = false
                speechHint = ""
            }
            override fun onError(error: Int) {
                isListeningInApp = false
                speechHint = ""
            }
            override fun onResults(results: Bundle?) {
                isListeningInApp = false
                speechHint = ""
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val text = matches?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    sendMessage(text)
                }
            }
            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()
                if (!partial.isNullOrBlank()) {
                    inputText = partial
                }
            }
            override fun onEvent(eventType: Int, params: Bundle?) {}
        })
        speechRecognizer?.startListening(intent)
        isListeningInApp = true
        speechHint = "Listening..."
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
        Toast.makeText(context, "Recording voice profile... Please speak clearly for 3 seconds.", Toast.LENGTH_SHORT).show()

        scope.launch {
            val sampleRate = 16000
            val totalSamples = sampleRate * 3 // 48,000 samples for 3 seconds
            val recordedData = ShortArray(totalSamples)

            val success = withContext(Dispatchers.IO) {
                val minBufferSize = AudioRecord.getMinBufferSize(
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(sampleRate * 2)

                var record: AudioRecord? = null
                try {
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
                        val remaining = maxOf(1, 3 - elapsed)
                        withContext(Dispatchers.Main) {
                            enrollmentCountdown = remaining
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e("LocalCompanionScreen", "Error during voice enrollment recording: ${e.message}")
                    false
                } finally {
                    try {
                        record?.stop()
                        record?.release()
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            if (success) {
                val enrolled = voiceBiometric.enrollSpeaker(recordedData)
                if (enrolled) {
                    isVoiceEnrolled = true
                    Toast.makeText(context, "Voice biometric enrolled successfully!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Enrollment rejected: Voice too quiet or silence detected. Please speak louder.", Toast.LENGTH_LONG).show()
                }
            } else {
                Toast.makeText(context, "Microphone recording failed during enrollment.", Toast.LENGTH_SHORT).show()
            }
            isRecordingEnrollment = false
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

    DisposableEffect(Unit) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (e: Exception) {
                // fallback
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
                            "⚡ Snapdragon 8 Elite NPU | Zero-Touch Registered Voice",
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
            // Hands-Free Voice Biometric Controller Banner
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
                                if (checked) {
                                    HandsFreeVoiceService.start(context)
                                    Toast.makeText(context, "Hands-Free listener active. Speak commands without touching phone!", Toast.LENGTH_SHORT).show()
                                } else {
                                    HandsFreeVoiceService.stop(context)
                                    Toast.makeText(context, "Hands-Free listener stopped.", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
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
                                speechRecognizer?.stopListening()
                                isListeningInApp = false
                                speechHint = ""
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
