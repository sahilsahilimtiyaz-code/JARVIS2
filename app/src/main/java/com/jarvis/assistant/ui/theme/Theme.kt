package com.jarvis.assistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * The arc-reactor palette. Deep navy glass, cyan energy, one warm highlight for
 * anything the owner should look at first.
 */
object JarvisPalette {
    val Void = Color(0xFF01060B)
    val Deep = Color(0xFF03101A)
    val Navy = Color(0xFF061826)
    val Panel = Color(0xFF0A1F2E)
    val PanelLight = Color(0xFF0E2A3C)
    val Line = Color(0xFF16394E)
    val Cyan = Color(0xFF30C5F0)
    val CyanBright = Color(0xFF7FE9FF)
    val CyanDim = Color(0xFF1B7B99)
    val Ice = Color(0xFFDEF4FF)
    val TextDim = Color(0xFF8CAEBF)
    val Amber = Color(0xFFFFB571)
    val Danger = Color(0xFFFF8071)
    val Success = Color(0xFF58E7AE)
}

private val JarvisDarkScheme = darkColorScheme(
    primary = JarvisPalette.Cyan,
    onPrimary = Color(0xFF00232F),
    primaryContainer = Color(0xFF0B3D50),
    onPrimaryContainer = JarvisPalette.CyanBright,
    secondary = JarvisPalette.CyanBright,
    onSecondary = Color(0xFF00232F),
    secondaryContainer = Color(0xFF0F2F3F),
    onSecondaryContainer = JarvisPalette.Ice,
    tertiary = JarvisPalette.Amber,
    onTertiary = Color(0xFF2F1A00),
    background = JarvisPalette.Void,
    onBackground = JarvisPalette.Ice,
    surface = JarvisPalette.Navy,
    onSurface = JarvisPalette.Ice,
    surfaceVariant = JarvisPalette.Panel,
    onSurfaceVariant = JarvisPalette.TextDim,
    outline = JarvisPalette.Line,
    outlineVariant = Color(0xFF0E2B3B),
    error = JarvisPalette.Danger,
    onError = Color(0xFF340300),
    errorContainer = Color(0xFF43120D),
    onErrorContainer = Color(0xFFFFDAD4),
)

@Composable
fun JarvisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JarvisDarkScheme,
        typography = JarvisTypography,
        content = content,
    )
}
