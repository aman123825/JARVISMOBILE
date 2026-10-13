package com.mindfortress.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.mindfortress.app.service.BlockerPolicy
import com.mindfortress.app.theme.AmberFocus
import com.mindfortress.app.theme.FortressTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistractionShieldScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { PreferencesManager.getInstance(context) }

    var isAppBlockerActive by remember { mutableStateOf(prefs.isAppBlockerActive) }
    var isWebShieldActive by remember { mutableStateOf(prefs.isWebShieldActive) }
    var showAddDialog by remember { mutableStateOf(false) }
    var newPackageInput by remember { mutableStateOf("") }
    var blockedPackagesList by remember { mutableStateOf(prefs.getBlockedPackages().toList()) }

    val isAccessibilityGranted = remember {
        AppBlockerAccessibilityService.isAccessibilityServiceEnabled(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Distraction Shield & Rules", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back", color = MaterialTheme.colorScheme.primary) }
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
                // Adult & Explicit Web Filtering Card
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Adult Content & Explicit Web Filter",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FortressTeal
                                )
                                Text(
                                    text = "Blocks 100% of porn domains, explicit search results, and ad networks.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isWebShieldActive,
                                onCheckedChange = { checked ->
                                    isWebShieldActive = checked
                                    prefs.isWebShieldActive = checked
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Permanent Zero-Battery System Setup:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Set Android's Private DNS to Cloudflare Family for bulletproof device-wide protection across Chrome, Firefox, and Incognito:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = BlockerPolicy.RECOMMENDED_PRIVATE_DNS_HOSTNAME,
                                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                TextButton(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("DNS", BlockerPolicy.RECOMMENDED_PRIVATE_DNS_HOSTNAME)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Copied DNS Hostname to clipboard!", Toast.LENGTH_SHORT).show()
                                    }
                                ) {
                                    Text("Copy")
                                }
                            }
                        }
                    }
                }
            }

            // Distraction & Games App Interceptor
            item {
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
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "App & Game Interceptor",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = FortressTeal
                                )
                                Text(
                                    text = "Detects launches of addictive games & short-form video apps, instantly closing them.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = isAppBlockerActive,
                                onCheckedChange = { checked ->
                                    isAppBlockerActive = checked
                                    prefs.isAppBlockerActive = checked
                                }
                            )
                        }

                        if (!isAccessibilityGranted) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AmberFocus.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "⚠️ Accessibility Permission Required",
                                        fontWeight = FontWeight.Bold,
                                        color = AmberFocus
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "To intercept background game launches, enable MindFortress in Android Accessibility settings.",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { AppBlockerAccessibilityService.openAccessibilitySettings(context) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Open Accessibility Settings")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Blocklist header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Blocked Packages (${blockedPackagesList.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = { showAddDialog = true }) {
                        Text("+ Add Custom App")
                    }
                }
            }

            // List of blocked packages
            items(blockedPackagesList.take(20)) { pkg ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = pkg,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        val isCustom = prefs.getCustomBlockedPackages().contains(pkg)
                        if (isCustom) {
                            TextButton(
                                onClick = {
                                    prefs.removeBlockedPackage(pkg)
                                    blockedPackagesList = prefs.getBlockedPackages().toList()
                                    Toast.makeText(context, "Removed $pkg", Toast.LENGTH_SHORT).show()
                                }
                            ) {
                                Text("Remove", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                            }
                        } else {
                            Text(
                                text = "🛡️ Blocked",
                                style = MaterialTheme.typography.labelSmall,
                                color = FortressTeal,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add App to Blocklist") },
            text = {
                Column {
                    Text("Enter package name (e.g., com.example.game):")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newPackageInput,
                        onValueChange = { newPackageInput = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newPackageInput.trim()
                        if (trimmed.isNotEmpty()) {
                            prefs.addBlockedPackage(trimmed)
                            blockedPackagesList = prefs.getBlockedPackages().toList()
                            newPackageInput = ""
                            showAddDialog = false
                        }
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
}
