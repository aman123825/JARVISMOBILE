package com.mindfortress.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.mindfortress.app.data.PreferencesManager
import com.mindfortress.app.service.AppBlockerAccessibilityService
import com.mindfortress.app.service.MindShieldVpnService
import com.mindfortress.app.service.SituationalGuardianService
import com.mindfortress.app.theme.AlertCrimson
import com.mindfortress.app.theme.AmberFocus
import com.mindfortress.app.theme.FortressTeal
import com.mindfortress.app.theme.SuccessEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onNavigateToGym: () -> Unit,
    onNavigateToShieldRules: () -> Unit,
    onNavigateToPurification: () -> Unit,
    onNavigateToCompanion: () -> Unit = {}
) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var isWebShieldActive by remember { mutableStateOf(prefs.isWebShieldActive) }
    var isAppBlockerActive by remember { mutableStateOf(prefs.isAppBlockerActive) }
    var isMonkModeActive by remember { mutableStateOf(prefs.isMonkModeActive) }
    var isSituationalGuardianActive by remember { mutableStateOf(prefs.isSituationalGuardianActive) }
    var isInvisibleInterventionMode by remember { mutableStateOf(prefs.isInvisibleInterventionMode) }

    val dailyScreenOnMinutes by prefs.dailyScreenOnMinutesFlow.collectAsState()
    val dailyUnlocksCount by prefs.dailyUnlocksCountFlow.collectAsState()
    val dailyShortsBlocked by prefs.dailyShortsBlockedFlow.collectAsState()
    val blockedCount by prefs.blockedCountFlow.collectAsState()
    val cognitivePoints by prefs.cognitivePointsFlow.collectAsState()

    val isAccessibilityGranted = remember {
        AppBlockerAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    val vpnLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            MindShieldVpnService.start(context)
            isWebShieldActive = true
            prefs.isWebShieldActive = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("MindFortress", fontWeight = FontWeight.ExtraBold)
                        Text(
                            "Cognitive Focus & Mind Strength Shield",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Surface(
                        color = AmberFocus.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("🔥", fontSize = 14.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "Day ${prefs.getStreak()}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = AmberFocus
                            )
                        }
                    }
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

                // Master Shield Status Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .background(
                                            if (isWebShieldActive && isAppBlockerActive) SuccessEmerald else AmberFocus,
                                            CircleShape
                                        )
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = if (isWebShieldActive && isAppBlockerActive) "FORTRESS SHIELD ACTIVE" else "PARTIAL PROTECTION",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isWebShieldActive && isAppBlockerActive) SuccessEmerald else AmberFocus
                                )
                            }
                            Text("🛡️", fontSize = 24.sp)
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Adult & Porn Web Shield", fontWeight = FontWeight.Bold)
                                Text("Blocks explicit sites & ads system-wide", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isWebShieldActive,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        val vpnIntent = VpnService.prepare(context)
                                        if (vpnIntent != null) {
                                            vpnLauncher.launch(vpnIntent)
                                        } else {
                                            MindShieldVpnService.start(context)
                                            isWebShieldActive = true
                                            prefs.isWebShieldActive = true
                                        }
                                    } else {
                                        MindShieldVpnService.stop(context)
                                        isWebShieldActive = false
                                        prefs.isWebShieldActive = false
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("App & Game Interceptor", fontWeight = FontWeight.Bold)
                                Text("Blocks addictive games & social media", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isAppBlockerActive,
                                onCheckedChange = { checked ->
                                    isAppBlockerActive = checked
                                    prefs.isAppBlockerActive = checked
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Monk Mode (Strict Lock)", fontWeight = FontWeight.Bold)
                                Text("Prevents impulsive disablement", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isMonkModeActive,
                                onCheckedChange = { checked ->
                                    isMonkModeActive = checked
                                    prefs.isMonkModeActive = checked
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Autonomous Invisible Guardian", fontWeight = FontWeight.Bold)
                                Text("Background whispers: late-night sleep & compulsive unlock protection", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isSituationalGuardianActive,
                                onCheckedChange = { checked ->
                                    isSituationalGuardianActive = checked
                                    prefs.isSituationalGuardianActive = checked
                                    if (checked) {
                                        SituationalGuardianService.start(context)
                                    } else {
                                        SituationalGuardianService.stop(context)
                                    }
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Invisible Audio Intervention", fontWeight = FontWeight.Bold)
                                Text("Blocks distractions silently via whisper without screen popups", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = isInvisibleInterventionMode,
                                onCheckedChange = { checked ->
                                    isInvisibleInterventionMode = checked
                                    prefs.isInvisibleInterventionMode = checked
                                }
                            )
                        }
                    }
                }
            }

            // Accessibility alert if needed
            if (!isAccessibilityGranted) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmberFocus.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ Enable Game & App Interceptor",
                                fontWeight = FontWeight.Bold,
                                color = AmberFocus
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "MindFortress needs Accessibility Service permission to block addictive games and distraction apps immediately when launched.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { AppBlockerAccessibilityService.openAccessibilitySettings(context) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Grant Accessibility Access")
                            }
                        }
                    }
                }
            }

            // Live Cognitive Analytics & Compulsive Monitor
            item {
                val (disciplineStatus, statusColor, statusBg) = when {
                    dailyScreenOnMinutes > 240 || dailyUnlocksCount > 60 || dailyShortsBlocked > 6 ->
                        Triple("COMPROMISED", AlertCrimson, AlertCrimson.copy(alpha = 0.15f))
                    dailyScreenOnMinutes > 120 || dailyUnlocksCount > 30 || dailyShortsBlocked > 2 ->
                        Triple("WARNING", AmberFocus, AmberFocus.copy(alpha = 0.15f))
                    else ->
                        Triple("DISCIPLINED", SuccessEmerald, SuccessEmerald.copy(alpha = 0.15f))
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "LIVE COGNITIVE VITALS",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "Focus Discipline Status",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Surface(
                                color = statusBg,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(statusColor, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = disciplineStatus,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Grid Row 1: Daily Screen Time & Compulsive Unlocks
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Screen Time Card
                            val screenHours = dailyScreenOnMinutes / 60
                            val screenMins = dailyScreenOnMinutes % 60
                            val screenTimeText = if (screenHours > 0) "${screenHours}h ${screenMins}m" else "${screenMins}m"
                            val screenProgress = (dailyScreenOnMinutes / 240f).coerceIn(0f, 1f)

                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Daily Screen Time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = screenTimeText,
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (dailyScreenOnMinutes > 180) AlertCrimson else FortressTeal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { screenProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = if (dailyScreenOnMinutes > 180) AlertCrimson else FortressTeal,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Goal: < 2h/day", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Compulsive Unlocks Card
                            val unlockProgress = (dailyUnlocksCount / 50f).coerceIn(0f, 1f)
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Compulsive Unlocks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$dailyUnlocksCount",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (dailyUnlocksCount > 40) AlertCrimson else AmberFocus
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { unlockProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = if (dailyUnlocksCount > 40) AlertCrimson else AmberFocus,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Threshold: 40/day", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Grid Row 2: Doom-loops Intercepted & Cognitive Points
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Doom-loops Intercepted Card
                            val shortsProgress = (dailyShortsBlocked / 10f).coerceIn(0f, 1f)
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Doom-loops Trapped", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$dailyShortsBlocked",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = if (dailyShortsBlocked > 0) SuccessEmerald else FortressTeal
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { shortsProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = SuccessEmerald,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Shorts/Reels ($blockedCount total)", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            // Cognitive Points Card
                            val pointsProgress = ((cognitivePoints % 1000) / 1000f).coerceIn(0f, 1f)
                            Card(
                                modifier = Modifier.weight(1f),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Cognitive Points", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "$cognitivePoints",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AmberFocus
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    LinearProgressIndicator(
                                        progress = { pointsProgress },
                                        modifier = Modifier.fillMaxWidth().height(6.dp),
                                        color = AmberFocus,
                                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Total Shield Pts", style = MaterialTheme.typography.labelSmall, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Main Action Cards
            item {
                Text(
                    text = "Core Pillars",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            // Pillar 0: Local AI Jarvis
            item {
                Card(
                    onClick = onNavigateToCompanion,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "⚡", fontSize = 34.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Jarvis: Local Friend & Teacher",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FortressTeal
                            )
                            Text(
                                text = "On-device voice mentor, Socratic tutor, and phone focus controller.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = "→", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pillar 1: Cognitive Gym
            item {
                Card(
                    onClick = onNavigateToGym,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🧠", fontSize = 34.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Cognitive Gym (Mind Games)",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FortressTeal
                            )
                            Text(
                                text = "Train fluid memory, impulse resistance, and rapid calculations.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = "→", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pillar 2: Distraction Shield Rules
            item {
                Card(
                    onClick = onNavigateToShieldRules,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "🛡️", fontSize = 34.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Shield Rules & Private DNS",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FortressTeal
                            )
                            Text(
                                text = "Adult filter settings, blocked games list, and custom app blocking.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = "→", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Pillar 3: Phone Purification Guide
            item {
                Card(
                    onClick = onNavigateToPurification,
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "📱", fontSize = 34.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Clean Slate Purification Protocol",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = FortressTeal
                            )
                            Text(
                                text = "5 architectural steps to clear all junk and distractions from your phone.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(text = "→", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
