package com.mindfortress.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.theme.AmberFocus
import com.mindfortress.app.theme.FortressTeal
import com.mindfortress.app.ui.components.tactilePress

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CognitiveGymScreen(
    onPlayDualNBack: () -> Unit,
    onPlayStroop: () -> Unit,
    onPlayMentalMath: () -> Unit,
    onPlayBoxBreathing: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }
    val points by prefs.cognitivePointsFlow.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cognitive Gym", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Home", color = MaterialTheme.colorScheme.primary) }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Cognitive Capability Score",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "$points Points",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = AmberFocus
                            )
                        }
                        Text(text = "🧠", fontSize = 42.sp)
                    }
                }
            }

            item {
                Text(
                    text = "High-Leverage Mind Strengthening",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Replaces dopamine-sapping games with exercises proven to build executive function.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Dual N-Back
            item {
                TrainingModuleCard(
                    icon = "⚡",
                    title = "Dual N-Back",
                    tagline = "Fluid Intelligence & Working Memory",
                    description = "Clinical paradigm scientifically demonstrated to increase fluid cognitive capacity and prefrontal working memory.",
                    onClick = onPlayDualNBack
                )
            }

            // Stroop Test
            item {
                TrainingModuleCard(
                    icon = "🎯",
                    title = "Stroop Impulse Inhibitor",
                    tagline = "Willpower & Cognitive Inhibition",
                    description = "Trains the anterior cingulate cortex to override instant impulses, cravings, and compulsive scrolling.",
                    onClick = onPlayStroop
                )
            }

            // Mental Math
            item {
                TrainingModuleCard(
                    icon = "🔢",
                    title = "Mental Math Agility",
                    tagline = "Rapid Calculation & Focus Speed",
                    description = "Fast arithmetic sequence challenges that eliminate brain fog and stimulate cognitive switching speed.",
                    onClick = onPlayMentalMath
                )
            }

            // Box Breathing
            item {
                TrainingModuleCard(
                    icon = "🌊",
                    title = "Box Breathing Protocol",
                    tagline = "Navy SEAL Urge Reset (4-4-4-4)",
                    description = "Regulates the autonomic nervous system to halt dopamine cravings and bring total physiological calm.",
                    onClick = onPlayBoxBreathing
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun TrainingModuleCard(
    icon: String,
    title: String,
    tagline: String,
    description: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .tactilePress(pressedScale = 0.97f) { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = icon, fontSize = 28.sp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = FortressTeal
                    )
                    Text(
                        text = tagline,
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberFocus,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .tactilePress(pressedScale = 0.95f) { onClick() }
            ) {
                Text("Start Training", fontWeight = FontWeight.Bold)
            }
        }
    }
}
