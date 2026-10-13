package com.mindfortress.app.ui.screens.games

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.theme.*
import com.mindfortress.app.ui.components.AudioWaveformPulse
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.random.Random

enum class MatchFeedback {
    NONE,
    CORRECT,
    INCORRECT
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DualNBackGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    // Difficulty level: N = 1, N = 2, N = 3
    var nLevel by remember { mutableIntStateOf(1) }
    val totalTrials = 20

    var isGameRunning by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var isGameOver by remember { mutableStateOf(false) }
    var currentTrialIndex by remember { mutableIntStateOf(0) }

    // History of positions (0..8) and letters ('C', 'H', 'K', etc.)
    val positionHistory = remember { mutableStateListOf<Int>() }
    val letterHistory = remember { mutableStateListOf<Char>() }

    var currentPosition by remember { mutableIntStateOf(-1) }
    var currentLetter by remember { mutableStateOf(' ') }

    var positionPressed by remember { mutableStateOf(false) }
    var letterPressed by remember { mutableStateOf(false) }

    var positionFeedback by remember { mutableStateOf(MatchFeedback.NONE) }
    var letterFeedback by remember { mutableStateOf(MatchFeedback.NONE) }

    var correctMatches by remember { mutableIntStateOf(0) }
    var totalMatchesOpportunities by remember { mutableIntStateOf(0) }

    // Distinct phonemes for auditory stimulus
    val letters = listOf('C', 'H', 'K', 'L', 'P', 'Q', 'R', 'T')

    // Android Text-to-Speech Engine
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }

    DisposableEffect(context) {
        val ttsEngine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
            }
        }
        try {
            ttsEngine.language = Locale.US
        } catch (_: Exception) {}
        ttsEngine.setSpeechRate(0.95f)
        ttsEngine.setPitch(1.05f)
        tts = ttsEngine

        onDispose {
            ttsEngine.stop()
            ttsEngine.shutdown()
        }
    }

    fun startSession() {
        positionHistory.clear()
        letterHistory.clear()
        currentTrialIndex = 0
        correctMatches = 0
        totalMatchesOpportunities = 0
        positionPressed = false
        letterPressed = false
        positionFeedback = MatchFeedback.NONE
        letterFeedback = MatchFeedback.NONE
        currentPosition = -1
        currentLetter = ' '
        isGameOver = false
        isPaused = false
        isGameRunning = true
    }

    // Main Trial Sequencer
    LaunchedEffect(isGameRunning) {
        if (!isGameRunning) return@LaunchedEffect

        while (currentTrialIndex < totalTrials && isGameRunning) {
            // If paused, wait until resumed
            while (isPaused && isGameRunning) {
                delay(150)
            }
            if (!isGameRunning) break

            // Generate trial stimulus (35% match probability)
            val shouldMatchPos = positionHistory.size >= nLevel && Random.nextFloat() < 0.35f
            val shouldMatchLetter = letterHistory.size >= nLevel && Random.nextFloat() < 0.35f

            val pos = if (shouldMatchPos) {
                positionHistory[positionHistory.size - nLevel]
            } else {
                var candidate = Random.nextInt(9)
                if (positionHistory.size >= nLevel && candidate == positionHistory[positionHistory.size - nLevel]) {
                    candidate = (candidate + 1) % 9
                }
                candidate
            }

            val letter = if (shouldMatchLetter) {
                letterHistory[letterHistory.size - nLevel]
            } else {
                var candidate = letters.random()
                if (letterHistory.size >= nLevel && candidate == letterHistory[letterHistory.size - nLevel]) {
                    candidate = letters[(letters.indexOf(candidate) + 1) % letters.size]
                }
                candidate
            }

            // Check if there was an actual match opportunity for position or sound
            val hasPosMatch = positionHistory.size >= nLevel && pos == positionHistory[positionHistory.size - nLevel]
            val hasLetterMatch = letterHistory.size >= nLevel && letter == letterHistory[letterHistory.size - nLevel]
            if (hasPosMatch) totalMatchesOpportunities++
            if (hasLetterMatch) totalMatchesOpportunities++

            currentPosition = pos
            currentLetter = letter
            positionPressed = false
            letterPressed = false
            positionFeedback = MatchFeedback.NONE
            letterFeedback = MatchFeedback.NONE

            positionHistory.add(pos)
            letterHistory.add(letter)

            // Auditory Stimulus: speak letter aloud simultaneously via TTS
            tts?.speak(letter.toString(), TextToSpeech.QUEUE_FLUSH, null, "nback_$currentTrialIndex")

            // Present stimulus for 2000ms
            var elapsed = 0
            while (elapsed < 2000 && isGameRunning) {
                if (!isPaused) {
                    delay(100)
                    elapsed += 100
                } else {
                    delay(100)
                }
            }

            // Clear visual stimulus for 600ms inter-stimulus interval
            currentPosition = -1
            currentLetter = ' '

            elapsed = 0
            while (elapsed < 600 && isGameRunning) {
                if (!isPaused) {
                    delay(100)
                    elapsed += 100
                } else {
                    delay(100)
                }
            }

            currentTrialIndex++
        }

        if (isGameRunning && currentTrialIndex >= totalTrials) {
            isGameRunning = false
            isGameOver = true
            // Reward cognitive points scaled by N
            val basePoints = correctMatches * 15 + 20
            val pointsEarned = basePoints * nLevel
            prefs.addCognitivePoints(pointsEarned)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "DUAL N-BACK // AUDITORY & SPATIAL",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    TextButton(onClick = {
                        tts?.stop()
                        onBack()
                    }) {
                        Text("Back", color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    if (isGameRunning && !isGameOver) {
                        TextButton(onClick = {
                            isPaused = !isPaused
                            if (isPaused) tts?.stop()
                        }) {
                            Text(
                                if (isPaused) "RESUME" else "PAUSE",
                                color = if (isPaused) QuantumAmber else ArcReactorCyan,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!isGameRunning && !isGameOver) {
                // Pre-Game Setup & Instructions
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Working Memory & Executive Control",
                        style = MaterialTheme.typography.titleMedium,
                        color = ArcReactorCyan,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Dual N-Back simultaneously exercises visual-spatial working memory (grid positions) and auditory-phonological working memory (spoken letters) to expand fluid intelligence.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Difficulty Selector
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                        border = BorderStroke(1.dp, CyberBorderGlow)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SELECT N-BACK DIFFICULTY",
                                style = MaterialTheme.typography.labelMedium,
                                color = ArcReactorCyan,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                listOf(1, 2, 3).forEach { level ->
                                    val isSelected = nLevel == level
                                    OutlinedButton(
                                        onClick = { nLevel = level },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isSelected) ArcReactorCyan.copy(alpha = 0.18f) else Color.Transparent,
                                            contentColor = if (isSelected) ArcReactorCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) ArcReactorCyan else CyberBorderGlow
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                text = "N = $level",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = "${level}x Pts",
                                                fontSize = 11.sp,
                                                color = if (isSelected) QuantumAmber else NeutralTextMuted
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = when (nLevel) {
                                    1 -> "Match stimulus against 1 step ago (Base difficulty)"
                                    2 -> "Match stimulus against 2 steps ago (Advanced dual-stream)"
                                    else -> "Match stimulus against 3 steps ago (Frontier cognitive demand)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = NeutralTextMuted,
                                textAlign = TextAlign.Center
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Auditory Engine Readiness Status
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (isTtsReady) "● TTS Vocal Channel Online" else "○ Initializing Speech Engine...",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            color = if (isTtsReady) NeonEmerald else QuantumAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = { startSession() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = FortressTeal),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            "Start N=$nLevel Training Session",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            } else if (isGameOver) {
                // Session summary
                val basePoints = correctMatches * 15 + 20
                val pointsEarned = basePoints * nLevel
                val totalOps = totalMatchesOpportunities.coerceAtLeast(1)
                val accuracyPct = (correctMatches * 100) / totalOps

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                    border = BorderStroke(1.dp, ArcReactorCyan.copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "NEURAL SESSION COMPLETE",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = ArcReactorCyan,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Difficulty: N = $nLevel (${nLevel}x Multiplier)",
                            style = MaterialTheme.typography.labelLarge,
                            color = NeutralTextMuted,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "$correctMatches / $totalOps", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = FortressTealLight)
                                Text(text = "Matches Hit", fontSize = 12.sp, color = NeutralTextMuted)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(text = "$accuracyPct%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = NeonEmerald)
                                Text(text = "Accuracy", fontSize = 12.sp, color = NeutralTextMuted)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = SlateSurfaceDark)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Base Points: $basePoints  ×  N=$nLevel Multiplier",
                                    fontSize = 12.sp,
                                    color = NeutralTextMuted,
                                    fontFamily = FontFamily.Monospace
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "+$pointsEarned Cognitive Points",
                                    fontSize = 18.sp,
                                    color = QuantumAmber,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // Quick level change before re-training
                        Text(
                            text = "TRAIN AGAIN // SELECT LEVEL:",
                            fontSize = 11.sp,
                            color = NeutralTextMuted,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(1, 2, 3).forEach { lvl ->
                                OutlinedButton(
                                    onClick = { nLevel = lvl },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (nLevel == lvl) ArcReactorCyan.copy(alpha = 0.2f) else Color.Transparent
                                    ),
                                    border = BorderStroke(1.dp, if (nLevel == lvl) ArcReactorCyan else CyberBorderGlow)
                                ) {
                                    Text("N=$lvl", color = if (nLevel == lvl) ArcReactorCyan else Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { startSession() },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = FortressTeal)
                        ) {
                            Text("Start Next Session", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            } else {
                // Active game play
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // HUD Telemetry Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MODE: N = $nLevel",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = ArcReactorCyan
                        )
                        Text(
                            text = "TRIAL ${currentTrialIndex + 1} / $totalTrials",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = QuantumAmber
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Auditory Stimulus Display
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                        border = BorderStroke(1.dp, if (currentLetter != ' ') ArcReactorCyan else CyberBorderGlow)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "AUDITORY CHANNEL",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = NeutralTextMuted
                                )
                                Text(
                                    text = if (currentLetter != ' ') "SPOKEN: \"$currentLetter\"" else "LISTENING...",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (currentLetter != ' ') ArcReactorCyan else NeutralTextMuted
                                )
                            }
                            AudioWaveformPulse(
                                isActive = currentLetter != ' ',
                                glowColor = if (currentLetter != ' ') ArcReactorCyan else NeutralTextMuted
                            )
                        }
                    }
                }

                if (isPaused) {
                    // Paused Banner
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = CyberCardSurface),
                        border = BorderStroke(1.dp, QuantumAmber)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "SESSION PAUSED",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = QuantumAmber,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = { isPaused = false },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = FortressTeal)
                                ) {
                                    Text("Resume")
                                }
                                OutlinedButton(
                                    onClick = {
                                        isGameRunning = false
                                        isPaused = false
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Quit")
                                }
                            }
                        }
                    }
                } else {
                    // 3x3 Visual Spatial Grid
                    Box(
                        modifier = Modifier
                            .size(260.dp)
                            .border(2.dp, CyberBorderGlow, RoundedCornerShape(14.dp))
                            .background(DeepSpaceObsidian.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            for (row in 0..2) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                    for (col in 0..2) {
                                        val index = row * 3 + col
                                        val isHighlighted = index == currentPosition
                                        Box(
                                            modifier = Modifier
                                                .size(70.dp)
                                                .background(
                                                    if (isHighlighted) ArcReactorCyan else SlateSurfaceDark,
                                                    RoundedCornerShape(8.dp)
                                                )
                                                .border(
                                                    width = if (isHighlighted) 2.dp else 1.dp,
                                                    color = if (isHighlighted) Color.White else SlateBorderDark,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Dual N-Back Decision Match Action Buttons
                val canPressPosition = isGameRunning && !isPaused && !positionPressed && positionHistory.size > nLevel
                val canPressLetter = isGameRunning && !isPaused && !letterPressed && letterHistory.size > nLevel

                val posButtonColor = when (positionFeedback) {
                    MatchFeedback.CORRECT -> NeonEmerald
                    MatchFeedback.INCORRECT -> AlertCrimson
                    MatchFeedback.NONE -> if (positionPressed) SlateSurfaceDark else FortressTeal
                }

                val letterButtonColor = when (letterFeedback) {
                    MatchFeedback.CORRECT -> NeonEmerald
                    MatchFeedback.INCORRECT -> AlertCrimson
                    MatchFeedback.NONE -> if (letterPressed) SlateSurfaceDark else QuantumAmber
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Button(
                        onClick = {
                            if (canPressPosition) {
                                positionPressed = true
                                val targetPos = positionHistory[positionHistory.size - 1 - nLevel]
                                val lastPos = positionHistory.last()
                                if (lastPos == targetPos) {
                                    correctMatches++
                                    positionFeedback = MatchFeedback.CORRECT
                                } else {
                                    positionFeedback = MatchFeedback.INCORRECT
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        enabled = canPressPosition,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = posButtonColor,
                            disabledContainerColor = if (positionPressed) posButtonColor else SlateSurfaceDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = when {
                                positionFeedback == MatchFeedback.CORRECT -> "✓ Match!"
                                positionFeedback == MatchFeedback.INCORRECT -> "✗ Miss"
                                positionHistory.size <= nLevel -> "Buffering..."
                                else -> "Position Match"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }

                    Button(
                        onClick = {
                            if (canPressLetter) {
                                letterPressed = true
                                val targetLetter = letterHistory[letterHistory.size - 1 - nLevel]
                                val lastLetter = letterHistory.last()
                                if (lastLetter == targetLetter) {
                                    correctMatches++
                                    letterFeedback = MatchFeedback.CORRECT
                                } else {
                                    letterFeedback = MatchFeedback.INCORRECT
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(58.dp),
                        enabled = canPressLetter,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = letterButtonColor,
                            disabledContainerColor = if (letterPressed) letterButtonColor else SlateSurfaceDark
                        ),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(
                            text = when {
                                letterFeedback == MatchFeedback.CORRECT -> "✓ Match!"
                                letterFeedback == MatchFeedback.INCORRECT -> "✗ Miss"
                                letterHistory.size <= nLevel -> "Buffering..."
                                else -> "Sound Match"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // Bottom System Status Readout
            Text(
                text = "NEURAL TARGET: PREFRONTAL CORTEX & PARIETAL WORKING MEMORY",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = NeutralTextMuted
            )
        }
    }
}
