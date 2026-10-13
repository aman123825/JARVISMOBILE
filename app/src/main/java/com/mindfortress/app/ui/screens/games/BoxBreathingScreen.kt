package com.mindfortress.app.ui.screens.games

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.theme.FortressTeal
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BoxBreathingScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var phaseIndex by remember { mutableStateOf(0) }
    var phaseSecondsRemaining by remember { mutableStateOf(4) }
    var totalCyclesCompleted by remember { mutableStateOf(0) }
    var isRunning by remember { mutableStateOf(true) }

    val phases = listOf("Inhale Slowly", "Hold Breath", "Exhale Fully", "Hold Empty")

    // Animate scale between 0.6 and 1.2
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "circle_scale"
    )

    LaunchedEffect(isRunning) {
        while (isRunning) {
            delay(1000)
            phaseSecondsRemaining--
            if (phaseSecondsRemaining <= 0) {
                phaseSecondsRemaining = 4
                phaseIndex = (phaseIndex + 1) % 4
                if (phaseIndex == 0) {
                    totalCyclesCompleted++
                    prefs.addCognitivePoints(5)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Box Breathing Protocol") },
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
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Dopamine Urge Reset (Navy SEAL 4-4-4-4)",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Activates the parasympathetic vagus nerve, lowering cortisol and extinguishing impulsive urges to check distracting apps or sites.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            // Animated Breathing Orb
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .scale(if (phaseIndex == 0 || phaseIndex == 1) scale else (1.8f - scale)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(FortressTeal.copy(alpha = 0.25f), CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(170.dp)
                        .background(FortressTeal, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = phases[phaseIndex],
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${phaseSecondsRemaining}s",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Cycles completed: $totalCyclesCompleted",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        isRunning = !isRunning
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (isRunning) "Pause Session" else "Resume Breathing")
                }
            }
        }
    }
}
