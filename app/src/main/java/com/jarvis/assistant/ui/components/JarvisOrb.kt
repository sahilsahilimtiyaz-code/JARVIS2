package com.jarvis.assistant.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

val OrbState.label: String
    get() = when (this) {
        OrbState.IDLE -> "Ready"
        OrbState.LISTENING -> "Listening"
        OrbState.THINKING -> "Thinking"
        OrbState.SPEAKING -> "Speaking"
    }

@Composable
fun JarvisOrb(
    state: OrbState,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    val transition = rememberInfiniteTransition(label = "jarvis orb")
    val pulse by transition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb pulse",
    )
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (state == OrbState.THINKING) 3600 else 16000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "orb spin",
    )
    val flicker by transition.animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orb energy",
    )

    val energy = if (state == OrbState.THINKING) flicker else 1f

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val thin = 1.dp.toPx()
        val heavy = 2.4.dp.toPx()

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    primary.copy(alpha = 0.20f * energy),
                    primary.copy(alpha = 0.05f * energy),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
        )

        drawCircle(
            color = primary.copy(alpha = 0.16f),
            radius = radius * 0.95f,
            center = center,
            style = Stroke(width = thin),
        )
        drawCircle(
            color = primary.copy(alpha = 0.30f),
            radius = radius * 0.78f,
            center = center,
            style = Stroke(width = thin),
        )
        drawCircle(
            color = primary.copy(alpha = 0.10f),
            radius = radius * 0.63f,
            center = center,
            style = Stroke(width = 5.dp.toPx()),
        )

        rotate(degrees = spin, pivot = center) {
            drawArc(
                color = primary.copy(alpha = 0.9f),
                startAngle = -20f,
                sweepAngle = 104f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.86f, center.y - radius * 0.86f),
                size = Size(radius * 1.72f, radius * 1.72f),
                style = Stroke(width = heavy, cap = StrokeCap.Round),
            )
            drawArc(
                color = primary.copy(alpha = 0.36f),
                startAngle = 150f,
                sweepAngle = 46f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.92f, center.y - radius * 0.92f),
                size = Size(radius * 1.84f, radius * 1.84f),
                style = Stroke(width = thin, cap = StrokeCap.Round),
            )
        }

        rotate(degrees = -spin * 0.62f, pivot = center) {
            drawArc(
                color = primary.copy(alpha = 0.55f),
                startAngle = 70f,
                sweepAngle = 62f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.70f, center.y - radius * 0.70f),
                size = Size(radius * 1.40f, radius * 1.40f),
                style = Stroke(width = thin, cap = StrokeCap.Round),
            )
        }

        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White,
                    primary,
                    Color(0xFF00384D),
                ),
                center = center,
                radius = radius * 0.44f,
            ),
            radius = radius * 0.44f * pulse,
            center = center,
        )
        drawCircle(
            color = background.copy(alpha = 0.94f),
            radius = radius * 0.16f,
            center = center,
        )
        drawCircle(
            color = primary.copy(alpha = 0.78f * energy),
            radius = radius * 0.055f,
            center = center,
        )
    }
}
