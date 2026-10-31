package com.jarvis.assistant.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.JarvisTab
import com.jarvis.assistant.ui.theme.JarvisPalette

/**
 * The bottom instrument bar: four destinations and the reactor microphone in the
 * middle. The centre button is the same action as the composer mic, so it always
 * reflects the real listening and busy state.
 */
@Composable
fun JarvisBottomBar(
    selected: JarvisTab,
    onSelect: (JarvisTab) -> Unit,
    listening: Boolean,
    busy: Boolean,
    onMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 14.dp, bottom = 10.dp)
                .height(64.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            JarvisPalette.Panel.copy(alpha = 0.97f),
                            JarvisPalette.Deep.copy(alpha = 0.97f),
                        ),
                    ),
                )
                .border(1.dp, JarvisPalette.Cyan.copy(alpha = 0.18f), RoundedCornerShape(28.dp))
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NavItem(
                glyph = HudGlyph.HOME,
                label = "Home",
                selected = selected == JarvisTab.HOME,
                onClick = { onSelect(JarvisTab.HOME) },
                modifier = Modifier.weight(1f),
            )
            NavItem(
                glyph = HudGlyph.SLIDERS,
                label = "Tools",
                selected = selected == JarvisTab.TOOLS,
                onClick = { onSelect(JarvisTab.TOOLS) },
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(64.dp))
            NavItem(
                glyph = HudGlyph.LOG,
                label = "Log",
                selected = selected == JarvisTab.LOG,
                onClick = { onSelect(JarvisTab.LOG) },
                modifier = Modifier.weight(1f),
            )
            NavItem(
                glyph = HudGlyph.GEAR,
                label = "Settings",
                selected = selected == JarvisTab.SETTINGS,
                onClick = { onSelect(JarvisTab.SETTINGS) },
                modifier = Modifier.weight(1f),
            )
        }

        // Reactor microphone button, half above the bar like the reference.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(bottom = 0.dp)
                .size(66.dp),
            contentAlignment = Alignment.Center,
        ) {
            PulseRings(
                active = listening,
                modifier = Modifier.size(80.dp),
                color = if (listening) JarvisPalette.Cyan else Color.Transparent,
            )
            val pulseScale by animateFloatAsState(
                targetValue = if (listening || busy) 1.03f else 1f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
                label = "mic scale",
            )
            Box(
                modifier = Modifier
                    .size(62.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                JarvisPalette.Cyan.copy(alpha = 0.30f),
                                JarvisPalette.Deep,
                            ),
                        ),
                    )
                    .border(
                        width = 1.2.dp,
                        color = if (listening || busy) {
                            JarvisPalette.CyanBright.copy(alpha = 0.85f)
                        } else {
                            JarvisPalette.Cyan.copy(alpha = 0.40f)
                        },
                        shape = CircleShape,
                    )
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onMic,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ArcReactor(
                    state = when {
                        listening -> ReactorState.LISTENING
                        busy -> ReactorState.THINKING
                        else -> ReactorState.IDLE
                    },
                    modifier = Modifier.size(54.dp),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    glyph: HudGlyph,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint by animateColorAsState(
        targetValue = if (selected) JarvisPalette.CyanBright else JarvisPalette.TextDim.copy(alpha = 0.75f),
        animationSpec = tween(durationMillis = 260),
        label = "nav tint",
    )
    val lift by animateFloatAsState(
        targetValue = if (selected) 1f else 0.94f,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "nav lift",
    )
    Column(
        modifier = modifier
            .scale(lift)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        HudIcon(
            glyph = glyph,
            color = tint,
            modifier = Modifier.size(20.dp),
            weight = 1.6.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 9.sp,
            letterSpacing = 0.6.sp,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .size(width = if (selected) 14.dp else 0.dp, height = 2.dp)
                .background(
                    color = if (selected) JarvisPalette.Cyan else Color.Transparent,
                    shape = CircleShape,
                ),
        )
    }
}
