package org.gitav.paindu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// The paindu palette, taken from the Punjabi countryside: field green, sarson
// (mustard-flower) yellow, clay, and the cream of unbleached cotton on dark soil.
val FieldGreen = Color(0xFF1F5A32)
val DeepGreen = Color(0xFF0F4A2A)
val Mustard = Color(0xFFE0A82E)
val Clay = Color(0xFFA1401C)
val Cream = Color(0xFFF7F2E7)
val Soil = Color(0xFF2A2118)

// Every text/background pair below meets WCAG AA contrast (4.5:1) or better.
private val Light = lightColorScheme(
    primary = FieldGreen,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8CF),
    onPrimaryContainer = Color(0xFF14321D),
    secondary = Color(0xFF7A5A10),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF3E3B5),
    onSecondaryContainer = Color(0xFF3A2A05),
    tertiary = Color(0xFF8C4A26),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF6DDCF),
    onTertiaryContainer = Color(0xFF3B1A08),
    background = Cream,
    onBackground = Soil,
    surface = Color(0xFFFFFDF8),
    onSurface = Soil,
    surfaceVariant = Color(0xFFECE5D3),
    onSurfaceVariant = Color(0xFF574E3F),
    surfaceContainerLowest = Color(0xFFFFFDF8),
    surfaceContainerLow = Color(0xFFFBF7EE),
    surfaceContainer = Color(0xFFF3EDDF),
    surfaceContainerHigh = Color(0xFFECE5D3),
    surfaceContainerHighest = Color(0xFFE5DDC8),
    outline = Color(0xFF7D725E),
    outlineVariant = Color(0xFFD9D0BA),
    error = Clay,
    onError = Color.White,
    errorContainer = Color(0xFFF8DDD1),
    onErrorContainer = Color(0xFF3F1505),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFA8CE8C),
    onPrimary = Color(0xFF12301B),
    primaryContainer = Color(0xFF2B4A30),
    onPrimaryContainer = Color(0xFFD6EBC6),
    secondary = Color(0xFFE6C36A),
    onSecondary = Color(0xFF3A2A05),
    secondaryContainer = Color(0xFF4A3A12),
    onSecondaryContainer = Color(0xFFF6E6B8),
    tertiary = Color(0xFFE9A27E),
    onTertiary = Color(0xFF3B1A08),
    tertiaryContainer = Color(0xFF5A2F18),
    onTertiaryContainer = Color(0xFFF8DDD1),
    background = Color(0xFF15130E),
    onBackground = Color(0xFFEDE6D6),
    surface = Color(0xFF1F1B14),
    onSurface = Color(0xFFEDE6D6),
    surfaceVariant = Color(0xFF332D22),
    onSurfaceVariant = Color(0xFFCBC1AB),
    surfaceContainerLowest = Color(0xFF100E0A),
    surfaceContainerLow = Color(0xFF1B1812),
    surfaceContainer = Color(0xFF26211A),
    surfaceContainerHigh = Color(0xFF302A21),
    surfaceContainerHighest = Color(0xFF3B3429),
    outline = Color(0xFF9A8F79),
    outlineVariant = Color(0xFF453E30),
    error = Color(0xFFF0916B),
    onError = Color(0xFF3F1505),
    errorContainer = Color(0xFF5E2610),
    onErrorContainer = Color(0xFFF8DDD1),
)

@Composable
fun PainduTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
