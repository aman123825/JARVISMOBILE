package com.mindfortress.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.mindfortress.app.theme.ArcReactorCyan
import com.mindfortress.app.theme.CyberCardSurface
import com.mindfortress.app.theme.DeepSpaceObsidian
import com.mindfortress.app.theme.NeutralTextMuted
import com.mindfortress.app.ui.screens.*
import com.mindfortress.app.ui.screens.games.*

private data class BottomNavItem(
    val key: NavKey,
    val label: String,
    val icon: String,
    val isSelected: (NavKey?) -> Boolean
)

private val navItems = listOf(
    BottomNavItem(
        key = JarvisHudKey,
        label = "HUD",
        icon = "🛡️",
        isSelected = { it == JarvisHudKey }
    ),
    BottomNavItem(
        key = CognitiveGymKey,
        label = "Study",
        icon = "🎓",
        isSelected = { it == CognitiveGymKey || it == DualNBackKey || it == StroopKey || it == MentalMathKey || it == BoxBreathingKey }
    ),
    BottomNavItem(
        key = ShieldRulesKey,
        label = "Shield",
        icon = "🔒",
        isSelected = { it == ShieldRulesKey }
    ),
    BottomNavItem(
        key = LocalCompanionKey,
        label = "Chat",
        icon = "🤖",
        isSelected = { it == LocalCompanionKey }
    ),
    BottomNavItem(
        key = HearingCompanionKey,
        label = "Hear",
        icon = "🦻",
        isSelected = { it == HearingCompanionKey }
    ),
    BottomNavItem(
        key = SettingsKey,
        label = "Settings",
        icon = "⚙️",
        isSelected = { it == SettingsKey || it == DashboardKey || it == CleanSlateKey }
    )
)

@Composable
fun MainNavigation(initialKey: NavKey = JarvisHudKey) {
    val backStack = rememberNavBackStack(initialKey)
    val currentKey = backStack.lastOrNull()

    Scaffold(
        containerColor = DeepSpaceObsidian,
        bottomBar = {
            NavigationBar(
                containerColor = CyberCardSurface,
                contentColor = ArcReactorCyan
            ) {
                navItems.forEach { item ->
                    val isSelected = item.isSelected(currentKey)
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = {
                            if (currentKey != item.key) {
                                backStack.add(item.key)
                            }
                        },
                        icon = {
                            Text(
                                text = item.icon,
                                fontSize = 20.sp
                            )
                        },
                        label = {
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ArcReactorCyan,
                            selectedTextColor = ArcReactorCyan,
                            indicatorColor = ArcReactorCyan.copy(alpha = 0.2f),
                            unselectedIconColor = NeutralTextMuted,
                            unselectedTextColor = NeutralTextMuted
                        )
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            NavDisplay(
                backStack = backStack,
                onBack = { backStack.removeLastOrNull() },
                entryProvider = entryProvider {
            entry<JarvisHudKey> {
                JarvisMainHudScreen(
                    onNavigateToGym = { backStack.add(CognitiveGymKey) },
                    onNavigateToShieldRules = { backStack.add(ShieldRulesKey) },
                    onNavigateToPurification = { backStack.add(CleanSlateKey) }
                )
            }
            entry<DashboardKey> {
                DashboardScreen(
                    onNavigateToGym = { backStack.add(CognitiveGymKey) },
                    onNavigateToShieldRules = { backStack.add(ShieldRulesKey) },
                    onNavigateToPurification = { backStack.add(CleanSlateKey) },
                    onNavigateToCompanion = { backStack.add(LocalCompanionKey) }
                )
            }
            entry<LocalCompanionKey> {
                LocalCompanionScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<HearingCompanionKey> {
                HearingCompanionScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<CognitiveGymKey> {
                CognitiveGymScreen(
                    onPlayDualNBack = { backStack.add(DualNBackKey) },
                    onPlayStroop = { backStack.add(StroopKey) },
                    onPlayMentalMath = { backStack.add(MentalMathKey) },
                    onPlayBoxBreathing = { backStack.add(BoxBreathingKey) },
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<ShieldRulesKey> {
                DistractionShieldScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<CleanSlateKey> {
                CleanSlateGuideScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<DualNBackKey> {
                DualNBackGame(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<StroopKey> {
                StroopTestGame(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<MentalMathKey> {
                MentalMathGame(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<BoxBreathingKey> {
                BoxBreathingScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
            entry<SettingsKey> {
                SettingsScreen(
                    onBack = { backStack.removeLastOrNull() }
                )
            }
        }
    )
        }
    }
}
