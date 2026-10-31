package com.jarvis.assistant.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.tools.DeviceStatus
import com.jarvis.assistant.tools.readDeviceStatus
import com.jarvis.assistant.ui.components.EnergyBar
import com.jarvis.assistant.ui.components.GlassPanel
import com.jarvis.assistant.ui.components.HudGlyph
import com.jarvis.assistant.ui.components.HudIcon
import com.jarvis.assistant.ui.components.HudIconButton
import com.jarvis.assistant.ui.components.HudSectionLabel
import com.jarvis.assistant.ui.components.StatReadout
import com.jarvis.assistant.ui.components.StatusDot
import com.jarvis.assistant.ui.theme.JarvisPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * The right-hand quick panel: live phone readings plus the two switches that are
 * genuinely useful one-handed (torch and media). Everything shown here is read
 * from the device right now.
 */
@Composable
fun QuickPanelOverlay(
    visible: Boolean,
    state: JarvisUiState,
    onClose: () -> Unit,
    onSelectTab: (JarvisTab) -> Unit,
    onToggleTorch: () -> Unit,
    onToggleMedia: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf<DeviceStatus?>(null) }

    // Poll only while the panel is on screen; the last reading stays visible
    // during the slide-out.
    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        while (true) {
            status = withContext(Dispatchers.IO) {
                runCatching { readDeviceStatus(context) }.getOrNull()
            }
            delay(4000)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(180)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onClose),
            )
        }

        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(240)),
            exit = slideOutHorizontally(tween(250)) { it } + fadeOut(tween(180)),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(330.dp)
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                JarvisPalette.Navy.copy(alpha = 0.98f),
                                JarvisPalette.Void.copy(alpha = 0.99f),
                            ),
                        ),
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(
                            listOf(JarvisPalette.Cyan.copy(alpha = 0.35f), Color.Transparent),
                        ),
                        shape = RoundedCornerShape(topStart = 22.dp, bottomStart = 22.dp),
                    )
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "QUICK ACCESS",
                            style = MaterialTheme.typography.titleSmall,
                            color = JarvisPalette.Ice,
                            letterSpacing = 3.sp,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            StatusDot(
                                color = if (status?.networkConnected == true) {
                                    JarvisPalette.Success
                                } else {
                                    JarvisPalette.Amber
                                },
                            )
                            Spacer(Modifier.width(5.dp))
                            Text(
                                text = status?.network ?: "Reading phone",
                                style = MaterialTheme.typography.labelSmall,
                                color = JarvisPalette.TextDim,
                                letterSpacing = 1.sp,
                            )
                        }
                    }
                    HudIconButton(
                        glyph = HudGlyph.CLOSE,
                        contentDescription = "Close quick panel",
                        onClick = onClose,
                        size = 32.dp,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickToggle(
                        modifier = Modifier.weight(1f),
                        glyph = HudGlyph.TORCH,
                        label = "Torch",
                        active = state.torchOn,
                        onClick = onToggleTorch,
                    )
                    QuickToggle(
                        modifier = Modifier.weight(1f),
                        glyph = if (status?.musicActive == true) HudGlyph.PAUSE else HudGlyph.PLAY,
                        label = if (status?.musicActive == true) "Pause" else "Play",
                        active = status?.musicActive == true,
                        onClick = onToggleMedia,
                    )
                }

                HudSectionLabel("SYSTEM")
                GlassPanel(modifier = Modifier.fillMaxWidth(), corner = 18.dp) {
                    StatRow(
                        label = "BATTERY",
                        value = status?.let { "${it.batteryPercent}%" } ?: "--",
                        progress = (status?.batteryPercent ?: 0) / 100f,
                        accent = if ((status?.batteryPercent ?: 100) <= 20) {
                            JarvisPalette.Danger
                        } else {
                            JarvisPalette.Success
                        },
                        note = when {
                            status == null -> null
                            status?.charging == true -> "Charging"
                            else -> "On battery"
                        },
                    )
                    Spacer(Modifier.height(12.dp))
                    StatRow(
                        label = "MEMORY",
                        value = status?.let { "${it.memoryUsedPercent}%" } ?: "--",
                        progress = (status?.memoryUsedPercent ?: 0) / 100f,
                    )
                    Spacer(Modifier.height(12.dp))
                    StatRow(
                        label = "STORAGE",
                        value = status?.let { "${it.storageUsedPercent}%" } ?: "--",
                        progress = (status?.storageUsedPercent ?: 0) / 100f,
                        note = status?.let { "%.1f GB free".format(it.storageFreeGb) },
                    )
                    Spacer(Modifier.height(12.dp))
                    StatReadout(
                        label = "TEMPERATURE",
                        value = status?.batteryTemperatureC?.let { "%.1f °C".format(it) } ?: "--",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    StatReadout(
                        label = "RINGER",
                        value = status?.ringerMode ?: "--",
                        modifier = Modifier.fillMaxWidth(),
                        accent = JarvisPalette.Amber,
                    )
                    Spacer(Modifier.height(8.dp))
                    StatReadout(
                        label = "TOOLS READY",
                        value = "${state.tools.count { it.statusReady }} / ${state.tools.size}",
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                HudSectionLabel("GO TO")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PanelLink(HudGlyph.HOME, "Home") { onSelectTab(JarvisTab.HOME) }
                    PanelLink(HudGlyph.SLIDERS, "Phone tools") { onSelectTab(JarvisTab.TOOLS) }
                    PanelLink(HudGlyph.LOG, "Action log") { onSelectTab(JarvisTab.LOG) }
                    PanelLink(HudGlyph.GEAR, "Settings") { onSelectTab(JarvisTab.SETTINGS) }
                }

                Text(
                    text = "Every reading above comes from this phone. Nothing is uploaded.",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim.copy(alpha = 0.6f),
                    letterSpacing = 0.4.sp,
                )
            }
        }
    }
}

@Composable
private fun QuickToggle(
    glyph: HudGlyph,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(18.dp)
    val accent = if (active) JarvisPalette.CyanBright else JarvisPalette.TextDim
    Column(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        if (active) {
                            JarvisPalette.Cyan.copy(alpha = 0.16f)
                        } else {
                            JarvisPalette.Panel.copy(alpha = 0.9f)
                        },
                        JarvisPalette.Deep.copy(alpha = 0.9f),
                    ),
                ),
            )
            .border(
                width = 1.dp,
                color = if (active) {
                    JarvisPalette.Cyan.copy(alpha = 0.5f)
                } else {
                    JarvisPalette.Line.copy(alpha = 0.6f)
                },
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        HudIcon(
            glyph = glyph,
            color = accent,
            modifier = Modifier.size(22.dp),
            weight = 1.7.dp,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            letterSpacing = 0.6.sp,
        )
    }
}

@Composable
private fun StatRow(
    label: String,
    value: String,
    progress: Float,
    accent: Color = JarvisPalette.Cyan,
    note: String? = null,
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = JarvisPalette.TextDim,
                letterSpacing = 1.2.sp,
            )
            Spacer(Modifier.weight(1f))
            if (note != null) {
                Text(
                    text = note,
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim.copy(alpha = 0.7f),
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = accent,
            )
        }
        Spacer(Modifier.height(6.dp))
        EnergyBar(progress = progress, modifier = Modifier.fillMaxWidth(), color = accent)
    }
}

@Composable
private fun PanelLink(
    glyph: HudGlyph,
    label: String,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JarvisPalette.Panel.copy(alpha = 0.72f))
            .border(1.dp, JarvisPalette.Line.copy(alpha = 0.55f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudIcon(
            glyph = glyph,
            color = JarvisPalette.Cyan,
            modifier = Modifier.size(17.dp),
            weight = 1.7.dp,
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = JarvisPalette.Ice,
            modifier = Modifier.weight(1f),
        )
        HudIcon(
            glyph = HudGlyph.CHEVRON_RIGHT,
            color = JarvisPalette.TextDim,
            modifier = Modifier.size(13.dp),
            weight = 2.dp,
        )
    }
}
