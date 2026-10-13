package com.mindfortress.app.ui.screens.games

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.theme.AmberFocus
import com.mindfortress.app.theme.FortressTeal
import kotlinx.coroutines.delay
import kotlin.random.Random

data class ColorOption(val name: String, val color: Color)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StroopTestGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    val options = listOf(
        ColorOption("RED", Color(0xFFEF4444)),
        ColorOption("BLUE", Color(0xFF3B82F6)),
        ColorOption("GREEN", Color(0xFF10B981)),
        ColorOption("YELLOW", Color(0xFFEAB308))
    )

    var isPlaying by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var timeLeftSeconds by remember { mutableStateOf(30) }
    var score by remember { mutableStateOf(0) }
    var totalAttempts by remember { mutableStateOf(0) }

    var displayedWord by remember { mutableStateOf("") }
    var displayedInkColor by remember { mutableStateOf(Color.White) }
    var correctColorName by remember { mutableStateOf("") }

    fun nextStimulus() {
        val wordOption = options.random()
        // 70% chance of conflicting ink color to stimulate anterior cingulate cortex
        val inkOption = if (Random.nextFloat() < 0.7f) {
            options.filter { it.name != wordOption.name }.random()
        } else {
            wordOption
        }

        displayedWord = wordOption.name
        displayedInkColor = inkOption.color
        correctColorName = inkOption.name
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        timeLeftSeconds = 30
        score = 0
        totalAttempts = 0
        nextStimulus()

        while (timeLeftSeconds > 0) {
            delay(1000)
            timeLeftSeconds--
        }

        isPlaying = false
        isFinished = true
        val points = (score * 10).coerceAtLeast(10)
        prefs.addCognitivePoints(points)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stroop Impulse Inhibitor") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            if (!isPlaying && !isFinished) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Impulse Control & Willpower Conditioning",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Dopamine cravings bypass your logic center. The Stroop exercise forces your prefrontal cortex to inhibit automatic impulsive reactions.\n\nRules: A word will appear. Tap the INK COLOR of the word, NOT what the text spells out!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(30.dp))
                    Button(
                        onClick = { isPlaying = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Text("Start 30-Second Challenge", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            } else if (isFinished) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Test Finished!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = FortressTeal
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Impulse Score: $score / $totalAttempts",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        val accuracy = if (totalAttempts > 0) (score * 100 / totalAttempts) else 0
                        Text(
                            text = "Cognitive Accuracy: $accuracy%",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "+${(score * 10).coerceAtLeast(10)} Cognitive Strength Points",
                            style = MaterialTheme.typography.bodyLarge,
                            color = AmberFocus,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = {
                                isFinished = false
                                isPlaying = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Try Again")
                        }
                    }
                }
            } else {
                // Header status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Time: ${timeLeftSeconds}s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (timeLeftSeconds <= 5) Color(0xFFEF4444) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Score: $score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Central Stimulus
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = displayedWord,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = displayedInkColor
                    )
                }

                Text(
                    text = "Tap the INK COLOR!",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // 2x2 Answer Grid
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 0..1) {
                            val opt = options[i]
                            Button(
                                onClick = {
                                    totalAttempts++
                                    if (opt.name == correctColorName) score++
                                    nextStimulus()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = opt.color)
                            ) {
                                Text(opt.name, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        for (i in 2..3) {
                            val opt = options[i]
                            Button(
                                onClick = {
                                    totalAttempts++
                                    if (opt.name == correctColorName) score++
                                    nextStimulus()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(56.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = opt.color)
                            ) {
                                Text(opt.name, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            Text(
                text = "Training the anterior cingulate cortex for discipline",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
