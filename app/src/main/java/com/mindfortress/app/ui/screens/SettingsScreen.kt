package com.mindfortress.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.ai.DeviceActionExecutor
import com.mindfortress.app.ai.VoiceBiometricManager
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    val actionExecutor = remember { DeviceActionExecutor(context) }
    val voiceBiometric = remember { VoiceBiometricManager(context) }
    val coroutineScope = rememberCoroutineScope()

    // Neural Uplink State
    var brainUrl by remember { mutableStateOf(prefs.brainServerUrl) }
    var apiKey by remember { mutableStateOf(prefs.frontierApiKey) }
    var selectedProvider by remember { mutableStateOf(prefs.frontierProvider) }
    var pingStatus by remember { mutableStateOf<String?>(null) }
    var isPinging by remember { mutableStateOf(false) }
    var providerDropdownExpanded by remember { mutableStateOf(false) }
    val providers = listOf("PC_BRAIN", "OPENAI", "ANTHROPIC", "GROQ", "GEMINI")
    var sovereignOfflineMode by remember { mutableStateOf(prefs.isSovereignOfflineMode) }

    // Circadian Bio-Clock State
    var sleepTime by remember { mutableStateOf(prefs.targetSleepTime) }
    var wakeTime by remember { mutableStateOf(prefs.targetWakeTime) }

    // Parse hour and minute for sliders
    fun parseTimeToHourFraction(timeStr: String, defaultHour: Float): Float {
        val parts = timeStr.split(":")
        if (parts.size == 2) {
            val h = parts[0].toIntOrNull() ?: return defaultHour
            val m = parts[1].toIntOrNull() ?: 0
            return h + (m / 60f)
        }
        return defaultHour
    }

    fun formatHourFraction(fraction: Float): String {
        val totalMinutes = (fraction * 60).roundToInt() % (24 * 60)
        val h = (totalMinutes / 60) % 24
        val m = totalMinutes % 60
        return String.format("%02d:%02d", h, m)
    }

    var sleepSliderVal by remember { mutableStateOf(parseTimeToHourFraction(sleepTime, 22.5f)) }
    var wakeSliderVal by remember { mutableStateOf(parseTimeToHourFraction(wakeTime, 6.5f)) }

    // Voice Persona State
    var speechRate by remember { mutableFloatStateOf(prefs.voiceSpeechRate) }
    var pitch by remember { mutableFloatStateOf(prefs.voicePitch) }

    // Custom Blocked Apps State
    var customPackages by remember { mutableStateOf(prefs.getCustomBlockedPackages().toList().sorted()) }
    var newPackageInput by remember { mutableStateOf("") }

    // Sensory Sentinel State
    var acousticRadarActive by remember { mutableStateOf(prefs.isAcousticRadarActive) }
    var lightSensingActive by remember { mutableStateOf(prefs.isLightSensingActive) }
    var motionTrackingActive by remember { mutableStateOf(prefs.isMotionTrackingActive) }

    // Biometrics State
    var isVoiceEnrolled by remember { mutableStateOf(voiceBiometric.isEnrolled) }

    Scaffold(
        containerColor = DeepSpaceObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SYSTEM SETTINGS",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                        color = ArcReactorCyan
                    )
                },
                navigationIcon = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) {
                            Text("Back", color = ArcReactorCyan)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CyberCardSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(8.dp)) }

            // On-device mode is the default. Turning it off explicitly permits LAN/cloud reasoning.
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(
                        1.dp,
                        if (sovereignOfflineMode) ArcReactorCyan else QuantumAmber
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                "Sovereign Offline Mode",
                                fontWeight = FontWeight.Bold,
                                color = if (sovereignOfflineMode) ArcReactorCyan else QuantumAmber
                            )
                            Text(
                                if (sovereignOfflineMode) {
                                    "ON · Assistant reasoning stays on-device; cloud and LAN routes are skipped."
                                } else {
                                    "OFF · The configured cloud provider or local-network brain may receive prompts."
                                },
                                color = NeutralTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                "Install a compatible local language model for stronger offline answers.",
                                color = NeutralTextMuted,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Switch(
                            checked = sovereignOfflineMode,
                            onCheckedChange = { enabled ->
                                sovereignOfflineMode = enabled
                                prefs.isSovereignOfflineMode = enabled
                                Toast.makeText(
                                    context,
                                    if (enabled) "Offline-only assistant mode enabled" else "Remote reasoning routes enabled",
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        )
                    }
                }
            }

            // 1. NEURAL UPLINK CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Neural Uplink",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Configure frontier intelligence server and API credentials.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Brain Server URL
                        OutlinedTextField(
                            value = brainUrl,
                            onValueChange = {
                                brainUrl = it
                                prefs.brainServerUrl = it
                            },
                            label = { Text("Brain Server URL", color = NeutralTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcReactorCyan,
                                unfocusedBorderColor = CyberBorderGlow,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Test Ping Button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    isPinging = true
                                    pingStatus = null
                                    coroutineScope.launch {
                                        val result = withContext(Dispatchers.IO) {
                                            try {
                                                val url = URL(brainUrl.trim().ifEmpty { "http://10.0.2.2:8080" })
                                                val connection = url.openConnection() as HttpURLConnection
                                                connection.connectTimeout = 3000
                                                connection.readTimeout = 3000
                                                connection.requestMethod = "GET"
                                                val responseCode = connection.responseCode
                                                connection.disconnect()
                                                "Online (HTTP $responseCode)"
                                            } catch (e: Exception) {
                                                "Offline: ${e.localizedMessage ?: "Connection Refused"}"
                                            }
                                        }
                                        pingStatus = result
                                        isPinging = false
                                        Toast.makeText(context, "Ping: $result", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                enabled = !isPinging,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = FortressTeal
                                )
                            ) {
                                Text(if (isPinging) "Pinging..." else "Test Ping")
                            }

                            if (pingStatus != null) {
                                Text(
                                    text = pingStatus ?: "",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (pingStatus?.startsWith("Online") == true) SuccessEmerald else AlertCrimson
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // API Key Input
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = {
                                apiKey = it
                                prefs.frontierApiKey = it
                            },
                            label = { Text("Frontier API Key", color = NeutralTextMuted) },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ArcReactorCyan,
                                unfocusedBorderColor = CyberBorderGlow,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        // Provider Selector
                        Text("Intelligence Provider:", style = MaterialTheme.typography.labelMedium, color = NeutralTextMuted)
                        Spacer(modifier = Modifier.height(6.dp))

                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { providerDropdownExpanded = true },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = ArcReactorCyan
                                ),
                                border = BorderStroke(1.dp, CyberBorderGlow)
                            ) {
                                Text(selectedProvider)
                            }

                            DropdownMenu(
                                expanded = providerDropdownExpanded,
                                onDismissRequest = { providerDropdownExpanded = false }
                            ) {
                                providers.forEach { prov ->
                                    DropdownMenuItem(
                                        text = { Text(prov) },
                                        onClick = {
                                            selectedProvider = prov
                                            prefs.frontierProvider = prov
                                            providerDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. CIRCADIAN BIO-CLOCK CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🌙", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Circadian Bio-Clock",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Calibrate bedtime and morning awakening anchors for neuroplasticity recovery.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Sleep Target Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Sleep Time:", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                text = sleepTime,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = AmberFocus
                            )
                        }
                        Slider(
                            value = sleepSliderVal,
                            onValueChange = {
                                sleepSliderVal = it
                                val formatted = formatHourFraction(it)
                                sleepTime = formatted
                                prefs.targetSleepTime = formatted
                            },
                            valueRange = 18f..26f, // 18:00 to 02:00 next day
                            steps = 15,
                            colors = SliderDefaults.colors(
                                thumbColor = AmberFocus,
                                activeTrackColor = AmberFocus,
                                inactiveTrackColor = CyberBorderGlow
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        // Wake Target Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Wake Time:", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                text = wakeTime,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = FortressTeal
                            )
                        }
                        Slider(
                            value = wakeSliderVal,
                            onValueChange = {
                                wakeSliderVal = it
                                val formatted = formatHourFraction(it)
                                wakeTime = formatted
                                prefs.targetWakeTime = formatted
                            },
                            valueRange = 4f..10f, // 04:00 to 10:00
                            steps = 11,
                            colors = SliderDefaults.colors(
                                thumbColor = FortressTeal,
                                activeTrackColor = FortressTeal,
                                inactiveTrackColor = CyberBorderGlow
                            )
                        )
                    }
                }
            }

            // 3. VOICE PERSONA CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🎙️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Persona",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Fine-tune JARVIS vocal cadence, speech rate, and audio pitch.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Speech Rate Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Speech Rate:", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                text = String.format("%.2fx", speechRate),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Slider(
                            value = speechRate,
                            onValueChange = {
                                speechRate = it
                                prefs.voiceSpeechRate = it
                                actionExecutor.setVoicePersona(pitch, it)
                            },
                            valueRange = 0.8f..1.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = ArcReactorCyan,
                                activeTrackColor = ArcReactorCyan,
                                inactiveTrackColor = CyberBorderGlow
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Pitch Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Pitch:", style = MaterialTheme.typography.bodyMedium, color = Color.White)
                            Text(
                                text = String.format("%.2fx", pitch),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Slider(
                            value = pitch,
                            onValueChange = {
                                pitch = it
                                prefs.voicePitch = it
                                actionExecutor.setVoicePersona(it, speechRate)
                            },
                            valueRange = 0.8f..1.3f,
                            colors = SliderDefaults.colors(
                                thumbColor = ArcReactorCyan,
                                activeTrackColor = ArcReactorCyan,
                                inactiveTrackColor = CyberBorderGlow
                            )
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Button(
                            onClick = {
                                actionExecutor.setVoicePersona(pitch, speechRate)
                                actionExecutor.speak("MindFortress vocal synthesis online. Speech rate and pitch synchronized, Commander Vivek.")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = FortressTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Test Voice Synthesis")
                        }
                    }
                }
            }

            // 4. CUSTOM BLOCKED APPS CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🛡️", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Custom Blocked Apps",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add custom packages to intercept or remove existing rules.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Add package field and button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newPackageInput,
                                onValueChange = { newPackageInput = it },
                                label = { Text("Package Name (e.g. com.app)", color = NeutralTextMuted) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = ArcReactorCyan,
                                    unfocusedBorderColor = CyberBorderGlow,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val trimmed = newPackageInput.trim()
                                    if (trimmed.isNotEmpty()) {
                                        prefs.addBlockedPackage(trimmed)
                                        customPackages = prefs.getCustomBlockedPackages().toList().sorted()
                                        newPackageInput = ""
                                        Toast.makeText(context, "Added $trimmed to blocklist", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FortressTeal)
                            ) {
                                Text("Add")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (customPackages.isEmpty()) {
                            Text(
                                text = "No custom blocked apps added yet.",
                                style = MaterialTheme.typography.bodySmall,
                                color = NeutralTextMuted
                            )
                        } else {
                            Text(
                                text = "Custom Packages (${customPackages.size}):",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArcReactorCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                        }
                    }
                }
            }

            // List of custom blocked packages with delete button
            items(customPackages) { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = DeepSpaceObsidian),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = {
                                prefs.removeBlockedPackage(pkg)
                                customPackages = prefs.getCustomBlockedPackages().toList().sorted()
                                Toast.makeText(context, "Removed $pkg from blocklist", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = AlertCrimson.copy(alpha = 0.8f)),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Remove", fontSize = 12.sp)
                        }
                    }
                }
            }

            // 5. SENSORY SENTINEL CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("📡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sensory Sentinel",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Real-time hardware environmental monitoring and discipline sensors.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Acoustic Radar Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Acoustic Radar", fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Monitors ambient dB to detect noisy environments", style = MaterialTheme.typography.bodySmall, color = NeutralTextMuted)
                            }
                            Switch(
                                checked = acousticRadarActive,
                                onCheckedChange = {
                                    acousticRadarActive = it
                                    prefs.isAcousticRadarActive = it
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CyberBorderGlow)

                        // Light Sensing Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Light Sensing", fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Ambient lux monitoring for circadian bedtime protection", style = MaterialTheme.typography.bodySmall, color = NeutralTextMuted)
                            }
                            Switch(
                                checked = lightSensingActive,
                                onCheckedChange = {
                                    lightSensingActive = it
                                    prefs.isLightSensingActive = it
                                }
                            )
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = CyberBorderGlow)

                        // Motion Tracking Toggle
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Motion Tracking", fontWeight = FontWeight.SemiBold, color = Color.White)
                                Text("Accelerometer stillness & desk rest analysis", style = MaterialTheme.typography.bodySmall, color = NeutralTextMuted)
                            }
                            Switch(
                                checked = motionTrackingActive,
                                onCheckedChange = {
                                    motionTrackingActive = it
                                    prefs.isMotionTrackingActive = it
                                }
                            )
                        }
                    }
                }
            }

            // 6. BIOMETRICS MANAGEMENT CARD
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("🧬", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Biometrics Management",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Speaker verification template and vocal biometric imprint.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Enrollment Status:", color = Color.White)
                            Text(
                                text = if (isVoiceEnrolled) "ACTIVE (64-dim Imprint)" else "NOT ENROLLED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isVoiceEnrolled) SuccessEmerald else AmberFocus
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = {
                                    voiceBiometric.clearEnrollment()
                                    isVoiceEnrolled = false
                                    Toast.makeText(context, "Voice profile reset. Speak to re-enroll.", Toast.LENGTH_LONG).show()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = FortressTeal),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Re-enroll Voice")
                            }

                            OutlinedButton(
                                onClick = {
                                    voiceBiometric.clearEnrollment()
                                    isVoiceEnrolled = false
                                    Toast.makeText(context, "Biometrics Cleared", Toast.LENGTH_SHORT).show()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = AlertCrimson),
                                border = BorderStroke(1.dp, AlertCrimson.copy(alpha = 0.5f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Clear Biometrics")
                            }
                        }
                    }
                }
            }

            // 7. SYSTEM SURVIVAL & 24/7 BACKGROUND HEALTH
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, CyberBorderGlow)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⚡", fontSize = 18.sp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "System Survival & 24/7 Health",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = ArcReactorCyan
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Ensure background guardians and voice listeners survive aggressive OS memory kills.",
                            style = MaterialTheme.typography.bodySmall,
                            color = NeutralTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        val isIgnoringBattery = com.mindfortress.app.MainActivity.isIgnoringBatteryOptimizations(context)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Battery Exemption:", color = Color.White)
                            Text(
                                text = if (isIgnoringBattery) "EXEMPTED (24/7 Survival)" else "RESTRICTED",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = if (isIgnoringBattery) SuccessEmerald else AmberFocus
                            )
                        }

                        if (!isIgnoringBattery) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = {
                                    com.mindfortress.app.MainActivity.requestBatteryOptimizationExemption(context)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = AmberFocus),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant 24/7 Background Exemption")
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}
