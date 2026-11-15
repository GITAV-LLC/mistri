package org.gitav.mistri.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The mistri palette: dark green, leaf green, ink, and an off-white ground.
val DarkGreen = Color(0xFF237228)
val LeafGreen = Color(0xFF9EBC40)
val Ink = Color(0xFF1B2A1C)
val Ground = Color(0xFFF6F8F0)
val Warn = Color(0xFFA3471E)
val WarnDark = Color(0xFFE48A5D)

// Every text/background pair below meets WCAG AA contrast (4.5:1) or better.
private val Light = lightColorScheme(
    primary = DarkGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCEBC0),
    onPrimaryContainer = Ink,
    secondary = Color(0xFF55692A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE6EED2),
    onSecondaryContainer = Ink,
    background = Ground,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE9EEDD),
    onSurfaceVariant = Color(0xFF445244),
    surfaceContainerLow = Color.White,
    surfaceContainer = Color(0xFFF0F3E8),
    surfaceContainerHigh = Color(0xFFE9EEDD),
    outline = Color(0xFF6F7C6C),
    outlineVariant = Color(0xFFD5DDC6),
    error = Warn,
    onError = Color.White,
)

private val Dark = darkColorScheme(
    primary = LeafGreen,
    onPrimary = Ink,
    primaryContainer = Color(0xFF2F4A22),
    onPrimaryContainer = Color(0xFFE2EFC4),
    secondary = Color(0xFFB9CC84),
    onSecondary = Ink,
    secondaryContainer = Color(0xFF32402A),
    onSecondaryContainer = Color(0xFFE2EFC4),
    background = Color(0xFF121A13),
    onBackground = Color(0xFFE8EEDF),
    surface = Ink,
    onSurface = Color(0xFFE8EEDF),
    surfaceVariant = Color(0xFF2A3A2A),
    onSurfaceVariant = Color(0xFFBCC9B4),
    surfaceContainerLow = Ink,
    surfaceContainer = Color(0xFF223323),
    surfaceContainerHigh = Color(0xFF2A3A2A),
    outline = Color(0xFF8A9884),
    outlineVariant = Color(0xFF3A4A38),
    error = WarnDark,
    onError = Ink,
)

@Composable
fun MistriTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
