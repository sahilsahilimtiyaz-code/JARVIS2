package com.jarvis.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.components.ArcReactor
import com.jarvis.assistant.ui.components.EnergyBar
import com.jarvis.assistant.ui.components.HudGlyph
import com.jarvis.assistant.ui.components.HudIcon
import com.jarvis.assistant.ui.components.JarvisBackdrop
import com.jarvis.assistant.ui.components.ReactorState
import com.jarvis.assistant.ui.components.StatusDot
import com.jarvis.assistant.ui.theme.JarvisPalette
import kotlinx.coroutines.delay

private val BootSteps = listOf(
    "Loading assistant core",
    "Mounting phone tools",
    "Calibrating voice engine",
    "Restoring memory bank",
    "All systems online",
)

/**
 * The startup sequence: the reactor spins up while the subsystems come online.
 * It never blocks the app; it hands over as soon as both the boot animation and
 * the real settings load are finished.
 */
@Composable
fun BootScreen(
    ready: Boolean,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibleSteps by remember { mutableIntStateOf(0) }
    var visibleLetters by remember { mutableIntStateOf(0) }
    val title = "J.A.R.V.I.S."

    LaunchedEffect(Unit) {
        title.indices.forEach { index ->
            delay(95)
            visibleLetters = index + 1
        }
        BootSteps.indices.forEach { index ->
            delay(if (index == 0) 260 else 340)
            visibleSteps = index + 1
        }
    }

    LaunchedEffect(ready, visibleSteps) {
        if (ready && visibleSteps >= BootSteps.size) {
            delay(420)
            onFinished()
        }
    }

    JarvisBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            ArcReactor(
                state = ReactorState.BOOT,
                modifier = Modifier.size(196.dp),
            )

            Spacer(Modifier.height(30.dp))

            Row {
                title.forEachIndexed { index, character ->
                    Text(
                        text = character.toString(),
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (index < visibleLetters) {
                            JarvisPalette.Ice
                        } else {
                            JarvisPalette.Ice.copy(alpha = 0.06f)
                        },
                        fontWeight = FontWeight.Light,
                        letterSpacing = 6.sp,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            Text(
                text = "PERSONAL ASSISTANT SYSTEM",
                style = MaterialTheme.typography.labelSmall,
                color = JarvisPalette.Cyan.copy(alpha = 0.75f),
                letterSpacing = 3.sp,
            )

            Spacer(Modifier.height(46.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BootSteps.forEachIndexed { index, step ->
                    AnimatedVisibility(
                        visible = index < visibleSteps,
                        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 3 },
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(
                                color = if (index == BootSteps.lastIndex) {
                                    JarvisPalette.Success
                                } else {
                                    JarvisPalette.Cyan
                                },
                                pulsing = index >= visibleSteps - 1,
                                size = 6.dp,
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = step.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisPalette.TextDim,
                                letterSpacing = 1.8.sp,
                                modifier = Modifier.weight(1f),
                            )
                            HudIcon(
                                glyph = HudGlyph.CHECK,
                                color = JarvisPalette.Success.copy(alpha = 0.8f),
                                modifier = Modifier.size(13.dp),
                                weight = 2.4.dp,
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(34.dp))

            EnergyBar(
                progress = visibleSteps.toFloat() / BootSteps.size,
                modifier = Modifier.fillMaxWidth(),
                color = JarvisPalette.Cyan,
            )

            Spacer(Modifier.height(10.dp))

            Box(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (ready) "SYSTEMS READY" else "RESTORING CONFIGURATION",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim.copy(alpha = 0.7f),
                    letterSpacing = 2.sp,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
        }
    }
}
