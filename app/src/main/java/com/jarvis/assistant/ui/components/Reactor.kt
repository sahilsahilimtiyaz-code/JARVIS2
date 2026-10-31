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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.jarvis.assistant.ui.theme.JarvisPalette
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class ReactorState {
    BOOT,
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
}

val ReactorState.label: String
    get() = when (this) {
        ReactorState.BOOT -> "Initializing"
        ReactorState.IDLE -> "Standing by"
        ReactorState.LISTENING -> "Listening"
        ReactorState.THINKING -> "Thinking"
        ReactorState.SPEAKING -> "Speaking"
    }

/**
 * The arc reactor. Everything is drawn in one Canvas so the rings, ticks, core
 * and ripples stay perfectly concentric at any size: outer tick ring, three
 * counter-rotating arc rings, a glass core, the triangle mark, and listening
 * ripples.
 */
@Composable
fun ArcReactor(
    state: ReactorState,
    modifier: Modifier = Modifier,
    accent: Color = JarvisPalette.Cyan,
) {
    val thinking = state == ReactorState.THINKING
    val active = state != ReactorState.IDLE && state != ReactorState.BOOT

    val transition = rememberInfiniteTransition(label = "reactor")
    val spin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    ReactorState.THINKING -> 4200
                    ReactorState.LISTENING -> 12000
                    ReactorState.SPEAKING -> 9000
                    else -> 26000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "spin",
    )
    val counterSpin by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = when (state) {
                    ReactorState.THINKING -> 6200
                    ReactorState.LISTENING -> 15000
                    else -> 20000
                },
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "counter spin",
    )
    val pulse by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 1100 else 2600,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    val energy by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (thinking) 420 else 1500,
                easing = FastOutSlowInEasing,
            ),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "energy",
    )
    val ripple by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ripple",
    )

    val glow = when (state) {
        ReactorState.BOOT -> 0.55f
        ReactorState.IDLE -> 0.6f
        ReactorState.LISTENING -> 0.95f
        ReactorState.THINKING -> 0.8f
        ReactorState.SPEAKING -> 0.9f
    }

    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val hairline = 1.dp.toPx()
        val medium = 1.8.dp.toPx()
        val heavy = 2.6.dp.toPx()

        // Ambient halo.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    accent.copy(alpha = 0.22f * glow),
                    accent.copy(alpha = 0.06f * glow),
                    Color.Transparent,
                ),
                center = center,
                radius = radius,
            ),
            radius = radius,
            center = center,
        )

        // Ripple rings while listening.
        if (state == ReactorState.LISTENING) {
            repeat(3) { index ->
                val progress = ((ripple + index * 0.333f) % 1f)
                drawCircle(
                    color = accent.copy(alpha = (1f - progress) * 0.30f),
                    radius = radius * (0.55f + progress * 0.45f),
                    center = center,
                    style = Stroke(width = 1.2.dp.toPx()),
                )
            }
        }

        // Outer tick ring.
        rotate(degrees = spin * 0.25f, pivot = center) {
            val ticks = 72
            repeat(ticks) { index ->
                val angle = (index.toFloat() / ticks) * 360f * (PI.toFloat() / 180f)
                val long = index % 6 == 0
                val outer = radius * 0.995f
                val inner = radius * if (long) 0.955f else 0.972f
                val startX = center.x + cos(angle) * inner
                val startY = center.y + sin(angle) * inner
                val endX = center.x + cos(angle) * outer
                val endY = center.y + sin(angle) * outer
                drawLine(
                    color = accent.copy(alpha = if (long) 0.55f else 0.18f),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (long) medium else hairline,
                    cap = StrokeCap.Round,
                )
            }
        }

        drawCircle(
            color = accent.copy(alpha = 0.16f),
            radius = radius * 0.94f,
            center = center,
            style = Stroke(width = hairline),
        )

        // Primary rotating arcs.
        rotate(degrees = spin, pivot = center) {
            drawArc(
                color = accent.copy(alpha = 0.85f),
                startAngle = -26f,
                sweepAngle = 96f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.88f),
                size = Size(radius * 1.76f, radius * 1.76f),
                style = Stroke(width = heavy, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent.copy(alpha = 0.30f),
                startAngle = 120f,
                sweepAngle = 52f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.88f, center.y - radius * 0.88f),
                size = Size(radius * 1.76f, radius * 1.76f),
                style = Stroke(width = hairline, cap = StrokeCap.Round),
            )
        }

        // Secondary counter-rotating arc.
        rotate(degrees = -counterSpin, pivot = center) {
            drawArc(
                color = accent.copy(alpha = 0.55f),
                startAngle = 58f,
                sweepAngle = 118f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.74f, center.y - radius * 0.74f),
                size = Size(radius * 1.48f, radius * 1.48f),
                style = Stroke(width = hairline, cap = StrokeCap.Round),
            )
            drawArc(
                color = accent.copy(alpha = 0.22f),
                startAngle = 230f,
                sweepAngle = 38f,
                useCenter = false,
                topLeft = Offset(center.x - radius * 0.74f, center.y - radius * 0.74f),
                size = Size(radius * 1.48f, radius * 1.48f),
                style = Stroke(width = medium, cap = StrokeCap.Round),
            )
        }

        // Segmented inner ring.
        rotate(degrees = spin * 0.7f, pivot = center) {
            repeat(18) { index ->
                drawArc(
                    color = accent.copy(alpha = if (index % 3 == 0) 0.42f else 0.18f),
                    startAngle = index * 20f,
                    sweepAngle = 11f,
                    useCenter = false,
                    topLeft = Offset(center.x - radius * 0.60f, center.y - radius * 0.60f),
                    size = Size(radius * 1.20f, radius * 1.20f),
                    style = Stroke(width = if (index % 3 == 0) medium else hairline),
                )
            }
        }

        // Core sphere.
        val coreRadius = radius * 0.46f * pulse
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color.White.copy(alpha = 0.95f * energy),
                    accent.copy(alpha = 0.92f),
                    Color(0xFF0A3E52),
                    Color(0xFF06202C),
                ),
                center = center,
                radius = coreRadius,
            ),
            radius = coreRadius,
            center = center,
        )

        // Triangle mark, the arc-reactor signature.
        val triangle = Path().apply {
            val height = coreRadius * 0.62f
            moveTo(center.x, center.y - height * 0.62f)
            lineTo(center.x + height * 0.58f, center.y + height * 0.44f)
            lineTo(center.x - height * 0.58f, center.y + height * 0.44f)
            close()
        }
        drawPath(
            path = triangle,
            color = Color.White.copy(alpha = 0.75f * energy),
            style = Stroke(width = medium, cap = StrokeCap.Round),
        )
        drawCircle(
            color = Color.White.copy(alpha = 0.9f * energy),
            radius = coreRadius * 0.16f,
            center = center,
        )

        // Hot inner ring around the core.
        drawCircle(
            color = accent.copy(alpha = 0.35f * energy),
            radius = radius * 0.52f,
            center = center,
            style = Stroke(width = hairline),
        )
    }
}

/**
 * The voice wave: a mirrored bar spectrum that breathes with the assistant's
 * state. It is decorative but always reflects a real state flag, never a timer.
 */
@Composable
fun VoiceWave(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = JarvisPalette.CyanBright,
    bars: Int = 29,
) {
    val transition = rememberInfiniteTransition(label = "voice wave")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (active) 1100 else 2600,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave phase",
    )

    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val step = width / bars
        val barWidth = (step * 0.42f).coerceAtLeast(1.2f)
        val amplitude = if (active) height * 0.42f else height * 0.12f

        repeat(bars) { index ->
            val normalized = index.toFloat() / (bars - 1).coerceAtLeast(1)
            val envelope = 1f - abs(normalized - 0.5f) * 1.35f
            val wave = abs(sin(phase + index * 0.55f)) * 0.65f + 0.35f
            val barHeight = (amplitude * wave * envelope.coerceIn(0.12f, 1f))
                .coerceAtLeast(2f)
            val alpha = (0.30f + 0.60f * envelope).coerceIn(0.15f, 0.95f)
            drawLine(
                color = color.copy(alpha = alpha),
                start = Offset(step * index + barWidth / 2f, centerY - barHeight),
                end = Offset(step * index + barWidth / 2f, centerY + barHeight),
                strokeWidth = barWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Expanding rings around the microphone button. */
@Composable
fun PulseRings(
    active: Boolean,
    modifier: Modifier = Modifier,
    color: Color = JarvisPalette.Cyan,
    rings: Int = 2,
) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "pulse rings")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "ring progress",
    )
    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val max = size.minDimension / 2f
        repeat(rings) { index ->
            val value = ((progress + index.toFloat() / rings) % 1f)
            drawCircle(
                color = color.copy(alpha = (1f - value) * 0.45f),
                radius = max * (0.45f + value * 0.55f),
                center = center,
                style = Stroke(width = 1.4.dp.toPx()),
            )
        }
    }
}
