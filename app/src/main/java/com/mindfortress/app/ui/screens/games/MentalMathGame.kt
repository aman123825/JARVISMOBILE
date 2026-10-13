package com.mindfortress.app.ui.screens.games

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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

data class MathQuestion(val expression: String, val answer: Int, val choices: List<Int>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MentalMathGame(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var isPlaying by remember { mutableStateOf(false) }
    var isFinished by remember { mutableStateOf(false) }
    var timeLeftSeconds by remember { mutableStateOf(30) }
    var score by remember { mutableStateOf(0) }
    var currentQuestion by remember { mutableStateOf<MathQuestion?>(null) }

    fun generateQuestion(): MathQuestion {
        val type = Random.nextInt(3)
        val a: Int
        val b: Int
        val answer: Int
        val expr: String

        when (type) {
            0 -> { // Addition
                a = Random.nextInt(12, 89)
                b = Random.nextInt(12, 89)
                answer = a + b
                expr = "$a + $b"
            }
            1 -> { // Subtraction
                a = Random.nextInt(25, 99)
                b = Random.nextInt(10, a)
                answer = a - b
                expr = "$a - $b"
            }
            else -> { // Multiplication
                a = Random.nextInt(4, 14)
                b = Random.nextInt(4, 14)
                answer = a * b
                expr = "$a × $b"
            }
        }

        val distractors = mutableSetOf<Int>()
        while (distractors.size < 3) {
            val offset = listOf(-10, -2, -1, 1, 2, 10).random()
            val candidate = answer + offset
            if (candidate != answer && candidate > 0) {
                distractors.add(candidate)
            }
        }
        val choices = (distractors + answer).shuffled()
        return MathQuestion(expr, answer, choices)
    }

    LaunchedEffect(isPlaying) {
        if (!isPlaying) return@LaunchedEffect

        timeLeftSeconds = 30
        score = 0
        currentQuestion = generateQuestion()

        while (timeLeftSeconds > 0) {
            delay(1000)
            timeLeftSeconds--
        }

        isPlaying = false
        isFinished = true
        val points = score * 12 + 10
        prefs.addCognitivePoints(points)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mental Math Agility") },
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
                        text = "Working Memory & Focus Sharpness",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Quick mental arithmetic increases active cognitive bandwidth, clears dopamine fog, and stimulates rapid problem-solving.\n\nSolve as many equations as you can in 30 seconds.",
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
                        Text("Start 30-Second Sprint", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
                            text = "Sprint Complete!",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = FortressTeal
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Solved: $score equations",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "+${score * 12 + 10} Cognitive Strength Points",
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
                            Text("Solve Again")
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Time: ${timeLeftSeconds}s",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Score: $score",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                currentQuestion?.let { q ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${q.expression} = ?",
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (choice in q.choices.take(2)) {
                                Button(
                                    onClick = {
                                        if (choice == q.answer) score++
                                        currentQuestion = generateQuestion()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                ) {
                                    Text("$choice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            for (choice in q.choices.drop(2)) {
                                Button(
                                    onClick = {
                                        if (choice == q.answer) score++
                                        currentQuestion = generateQuestion()
                                    },
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(56.dp)
                                ) {
                                    Text("$choice", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Text(
                text = "Building neuro-efficiency & quantitative agility",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
