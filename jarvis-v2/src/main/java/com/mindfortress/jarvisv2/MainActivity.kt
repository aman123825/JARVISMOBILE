package com.mindfortress.jarvisv2

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.jarvisv2.model.LocalModelEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val Background = Color(0xFF080D16)
private val Panel = Color(0xFF111B2A)
private val Accent = Color(0xFF69E0FF)
private val Muted = Color(0xFFA7B6C9)

class MainActivity : ComponentActivity() {
    private lateinit var modelEngine: LocalModelEngine

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        modelEngine = LocalModelEngine(applicationContext)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Accent,
                    onPrimary = Background,
                    background = Background,
                    surface = Panel,
                    onSurface = Color.White,
                    secondary = Color(0xFF8EE6C8)
                )
            ) {
                JarvisChatScreen(modelEngine)
            }
        }
    }

    override fun onDestroy() {
        if (::modelEngine.isInitialized && isFinishing) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
                runCatching { modelEngine.close() }
            }
        }
        super.onDestroy()
    }
}

private data class ChatLine(val text: String, val fromUser: Boolean)

@Composable
private fun JarvisChatScreen(engine: LocalModelEngine) {
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val messages = remember {
        mutableStateListOf(
            ChatLine(
                "JARVIS V2 is ready. This app does not start the microphone or load a model in the background. Import a .litertlm model to enable local AI chat.",
                false
            )
        )
    }
    var input by remember { mutableStateOf("") }
    var modelInstalled by remember { mutableStateOf(engine.isModelInstalled()) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (engine.isModelInstalled()) "Model installed · loads on first message" else "No model installed")
    }
    var progressText by remember { mutableStateOf<String?>(null) }

    val modelPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            status = "Importing model…"
            progressText = "Copying into private app storage. Keep JARVIS open; nothing is uploaded."
            try {
                val bytes = engine.installModel(uri)
                modelInstalled = true
                status = "Model installed · ${String.format("%.2f", bytes / (1024.0 * 1024.0 * 1024.0))} GiB"
                messages.add(ChatLine("Model import succeeded. Send “hi” to test it. The first answer loads the model; later replies should be quicker.", false))
            } catch (error: Throwable) {
                status = if (engine.isModelInstalled()) "Previous model remains installed" else "Model import failed"
                messages.add(ChatLine("Import failed: ${error.message ?: error.javaClass.simpleName}", false))
            } finally {
                busy = false
                progressText = null
            }
        }
    }

    fun sendMessage() {
        val prompt = input.trim()
        if (prompt.isEmpty() || busy) return
        messages.add(ChatLine(prompt, true))
        input = ""
        if (!modelInstalled) {
            messages.add(ChatLine("No model is installed, so I cannot generate an AI answer yet. Tap IMPORT MODEL and select an exported .litertlm file. I will not fake a model response.", false))
            return
        }

        busy = true
        status = "Generating locally…"
        progressText = "Loading the local model if this is the first request."
        val responseIndex = messages.size
        messages.add(ChatLine("Loading model / starting generation…", false))
        progressText = "The first local load can take longer. Generated text will appear as it arrives."
        scope.launch {
            try {
                val reply = engine.respond(prompt) { partial ->
                    if (responseIndex < messages.size) {
                        messages[responseIndex] = ChatLine(partial.ifBlank { "Generating…" }, false)
                    }
                }
                if (responseIndex < messages.size) messages[responseIndex] = ChatLine(reply, false)
                status = "Ready · on-device inference"
            } catch (error: Throwable) {
                val reason = error.message ?: error.javaClass.simpleName
                val failure = if (reason.contains("Timed out", ignoreCase = true)) {
                    "The local response exceeded 60 seconds and was stopped. The UI is still responsive; check model compatibility and retry."
                } else {
                    "Local AI error: $reason"
                }
                if (responseIndex < messages.size) messages[responseIndex] = ChatLine(failure, false)
                status = "Not ready · error shown in chat"
            } finally {
                busy = false
                progressText = null
            }
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Surface(modifier = Modifier.fillMaxSize(), color = Background) {
        Column(
            modifier = Modifier.fillMaxSize().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("JARVIS", color = Accent, fontSize = 27.sp, fontWeight = FontWeight.Black)
                    Text("V2 · CLEAN LOCAL CORE", color = Muted, fontSize = 10.sp, letterSpacing = 1.5.sp)
                }
                OutlinedButton(enabled = !busy, onClick = { modelPicker.launch(arrayOf("*/*")) }) {
                    Text(if (modelInstalled) "REPLACE MODEL" else "IMPORT MODEL")
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = Panel), shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(8.dp).background(
                                if (modelInstalled) Color(0xFF69E0A7) else Color(0xFFFFC766),
                                RoundedCornerShape(50)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(status, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                    if (busy) {
                        Spacer(Modifier.size(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text(progressText.orEmpty(), color = Muted, fontSize = 11.sp)
                        }
                    } else {
                        Spacer(Modifier.size(4.dp))
                        Text(
                            if (modelInstalled) "The model stays on this phone. There is no network-chat fallback."
                            else "Text chat needs no microphone, account login, or background listener.",
                            color = Muted,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF203149))

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(messages) { _, line -> ChatBubble(line) }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(modifier = Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(14.dp)) {
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 14.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) Text("Message JARVIS…", color = Muted, fontSize = 14.sp)
                                inner()
                            }
                        },
                        maxLines = 5
                    )
                }
                Button(enabled = input.isNotBlank() && !busy, onClick = { sendMessage() }) {
                    Text("SEND")
                }
            }

            Text(
                "Private by design · Local model · No automatic microphone",
                modifier = Modifier.fillMaxWidth(),
                color = Muted,
                textAlign = TextAlign.Center,
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun ChatBubble(line: ChatLine) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (line.fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(if (line.fromUser) 0.86f else 0.96f),
            color = if (line.fromUser) Color(0xFF163149) else Panel,
            shape = RoundedCornerShape(14.dp)
        ) {
            Text(
                text = line.text,
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 11.dp),
                color = Color.White,
                fontSize = 14.sp,
                lineHeight = 20.sp
            )
        }
    }
}
