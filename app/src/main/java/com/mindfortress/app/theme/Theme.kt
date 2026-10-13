package com.mindfortress.app.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = FortressTealLight,
    onPrimary = Color(0xFF003833),
    primaryContainer = FortressTealDark,
    onPrimaryContainer = Color(0xFFCCFBF1),
    secondary = AmberFocusLight,
    onSecondary = Color(0xFF451A03),
    tertiary = FortressTeal,
    background = SlateShieldDark,
    onBackground = Color(0xFFF8FAFC),
    surface = SlateSurfaceDark,
    onSurface = Color(0xFFF8FAFC),
    surfaceVariant = SlateBorderDark,
    onSurfaceVariant = Color(0xFFCBD5E1)
)

private val LightColorScheme = lightColorScheme(
    primary = FortressTeal,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCCFBF1),
    onPrimaryContainer = FortressTealDark,
    secondary = AmberFocus,
    onSecondary = Color.White,
    tertiary = FortressTealDark,
    background = SlateShieldLight,
    onBackground = Color(0xFF0F172A),
    surface = SlateSurfaceLight,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = SlateBorderLight,
    onSurfaceVariant = Color(0xFF475569)
)

@Composable
fun MindFortressTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
