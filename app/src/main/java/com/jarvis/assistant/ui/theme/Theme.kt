package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val ArcReactorBlue = Color(0xFF4FC3F7)

private val JarvisDarkScheme = darkColorScheme(
    primary = ArcReactorBlue,
    onPrimary = Color(0xFF002B3D),
    primaryContainer = Color(0xFF004C69),
    onPrimaryContainer = Color(0xFFC9EEFF),
    secondary = Color(0xFF81D4FA),
    onSecondary = Color(0xFF003545),
    secondaryContainer = Color(0xFF17343E),
    onSecondaryContainer = Color(0xFFC9EEFF),
    tertiary = Color(0xFF9CCFD8),
    onTertiary = Color(0xFF00363D),
    background = Color(0xFF061116),
    onBackground = Color(0xFFE2F3FA),
    surface = Color(0xFF0A171D),
    onSurface = Color(0xFFE2F3FA),
    surfaceVariant = Color(0xFF1D3038),
    onSurfaceVariant = Color(0xFFB6CAD3),
    outline = Color(0xFF52676F),
    outlineVariant = Color(0xFF2B414A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5C1A1A),
    onErrorContainer = Color(0xFFFFDAD6),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkScheme,
        typography = JarvisTypography,
        content = content,
    )
}
