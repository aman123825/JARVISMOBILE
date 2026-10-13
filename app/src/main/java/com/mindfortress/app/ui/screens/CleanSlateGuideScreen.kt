package com.mindfortress.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mindfortress.app.theme.AmberFocus
import com.mindfortress.app.theme.FortressTeal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanSlateGuideScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Phone Purification Protocol", fontWeight = FontWeight.Bold) },
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
                Text(
                    text = "Total Distraction Cleansing Blueprint",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = FortressTeal
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Follow these 5 architectural steps to transform your phone into a pristine, zero-distraction instrument of high cognitive capability.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Step 1: Encrypted DNS
            item {
                PurificationStepCard(
                    stepNumber = "1",
                    title = "Lock Private DNS (Zero Porn / Zero Ads)",
                    description = "Android has native encrypted DNS built right into the OS. Setting this permanently blocks adult domains and advertising servers across all browsers, incognito tabs, and background processes without draining 1% of battery.",
                    actionTitle = "Settings -> Network & Internet (or Connections) -> More Connection Settings -> Private DNS",
                    codeSnippet = "family.cloudflare-dns.com",
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("DNS", "family.cloudflare-dns.com"))
                        Toast.makeText(context, "Copied DNS Hostname!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Step 2: Grayscale Mode
            item {
                PurificationStepCard(
                    stepNumber = "2",
                    title = "Activate Grayscale / Monochrome Display",
                    description = "Bright saturated app colors and red notification dots are engineered by neuroscientists to trigger compulsive dopamine loops. Turning your screen to black-and-white immediately cuts mindless usage by up to 40%.",
                    actionTitle = "Settings -> Accessibility -> Vision Enhancements -> Color Correction -> Grayscale (or Developer Options -> Simulate Color Space -> Monochromacy)",
                    codeSnippet = null,
                    onCopy = null
                )
            }

            // Step 3: Notification Extermination
            item {
                PurificationStepCard(
                    stepNumber = "3",
                    title = "Eradicate Non-Human Notifications",
                    description = "An incoming notification forces an attentional switch penalty that takes your brain 23 minutes to fully recover from. Silence every single app except direct phone calls from family.",
                    actionTitle = "Settings -> Notifications -> App Notifications -> Turn OFF all social media, news, shopping, and game alerts",
                    codeSnippet = null,
                    onCopy = null
                )
            }

            // Step 4: OEM Game & Bloatware Debloating
            item {
                PurificationStepCard(
                    stepNumber = "4",
                    title = "Debloat Pre-Installed OEM Junk Games",
                    description = "Manufacturers often pre-install junk games and adware stores. If Android will not let you uninstall an app normally, you can remove it cleanly via ADB:",
                    actionTitle = "Connect phone to PC with USB debugging enabled and run:",
                    codeSnippet = "adb shell pm uninstall -k --user 0 <package_name>",
                    onCopy = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("ADB", "adb shell pm uninstall -k --user 0 "))
                        Toast.makeText(context, "Copied ADB command snippet!", Toast.LENGTH_SHORT).show()
                    }
                )
            }

            // Step 5: Minimalist Text Launcher
            item {
                PurificationStepCard(
                    stepNumber = "5",
                    title = "Adopt a Minimalist Text-Only Launcher",
                    description = "Replace standard home screens that tempt you with endless app grids with a minimalist text launcher (such as Olauncher or Minimalist Phone). Your home screen should have only essentials and MindFortress.",
                    actionTitle = "Install Olauncher / Before Launcher -> Set as Default Home App",
                    codeSnippet = null,
                    onCopy = null
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
fun PurificationStepCard(
    stepNumber: String,
    title: String,
    description: String,
    actionTitle: String,
    codeSnippet: String?,
    onCopy: (() -> Unit)?
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = FortressTeal,
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                        Text(
                            text = stepNumber,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontSize = 14.sp
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "HOW TO EXECUTE:",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = AmberFocus
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = actionTitle,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (codeSnippet != null && onCopy != null) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Text(
                            text = codeSnippet,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onCopy) {
                            Text("Copy")
                        }
                    }
                }
            }
        }
    }
}
