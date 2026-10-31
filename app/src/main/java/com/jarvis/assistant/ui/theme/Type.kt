package com.jarvis.assistant.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val Default = Typography()

/**
 * Roboto is fine for body copy; the HUD chrome (titles, labels, status) leans on
 * wide tracking and light weights, which is what gives the reference its
 * instrument-panel feel.
 */
val JarvisTypography = Typography(
    displayLarge = Default.displayLarge.copy(fontWeight = FontWeight.Light),
    displayMedium = Default.displayMedium.copy(fontWeight = FontWeight.Light),
    headlineLarge = Default.headlineLarge.copy(fontWeight = FontWeight.Light),
    headlineMedium = Default.headlineMedium.copy(fontWeight = FontWeight.Light),
    headlineSmall = Default.headlineSmall.copy(fontWeight = FontWeight.Light),
    titleLarge = Default.titleLarge.copy(
        fontWeight = FontWeight.Normal,
        letterSpacing = 2.sp,
    ),
    titleMedium = Default.titleMedium.copy(letterSpacing = 1.2.sp),
    titleSmall = Default.titleSmall.copy(letterSpacing = 1.2.sp),
    labelLarge = Default.labelLarge.copy(letterSpacing = 0.8.sp),
    labelMedium = Default.labelMedium.copy(letterSpacing = 1.sp),
    labelSmall = Default.labelSmall.copy(letterSpacing = 1.4.sp),
    bodyLarge = Default.bodyLarge.copy(
        lineHeight = 23.sp,
        fontFamily = FontFamily.Default,
    ),
)

/** Mono-ish style reserved for numbers and status readouts. */
val HudNumerals: TextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontWeight = FontWeight.Normal,
    letterSpacing = 0.sp,
)
