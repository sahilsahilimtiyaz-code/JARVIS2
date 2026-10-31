package com.jarvis.assistant.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.tools.ToolSummary
import com.jarvis.assistant.ui.components.GlassPanel
import com.jarvis.assistant.ui.components.HudGlyph
import com.jarvis.assistant.ui.components.HudIcon
import com.jarvis.assistant.ui.components.HudPill
import com.jarvis.assistant.ui.components.JarvisBackdrop
import com.jarvis.assistant.ui.components.JarvisBottomBar
import com.jarvis.assistant.ui.components.JarvisTopBar
import com.jarvis.assistant.ui.components.ScreenTitle
import com.jarvis.assistant.ui.components.StatusDot
import com.jarvis.assistant.ui.theme.JarvisPalette

@Composable
fun ToolsScreen(
    state: JarvisUiState,
    onSelectTab: (JarvisTab) -> Unit,
    onToggleMic: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf<String?>(null) }

    val categories = remember(state.tools) {
        state.tools.map { it.category }.distinct().sorted()
    }
    val filtered = state.tools.filter { tool ->
        (category == null || tool.category == category) &&
            (query.isBlank() ||
                tool.label.contains(query, ignoreCase = true) ||
                tool.description.contains(query, ignoreCase = true))
    }
    val readyCount = state.tools.count { it.statusReady }

    JarvisBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            JarvisTopBar(
                title = "PHONE TOOLS",
                status = "$readyCount OF ${state.tools.size} READY",
                statusColor = JarvisPalette.Success,
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    Column {
                        ScreenTitle("What Jarvis can do")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Every tool here runs on this phone. Calls and messages always " +
                                "ask for your yes first, and each run lands in the action log.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisPalette.TextDim,
                        )
                        Spacer(Modifier.height(14.dp))
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search tools",
                        )
                        Spacer(Modifier.height(10.dp))
                    }
                }

                item(key = "filters") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            label = "All",
                            selected = category == null,
                            onClick = { category = null },
                        )
                        categories.forEach { name ->
                            FilterChip(
                                label = name,
                                selected = category == name,
                                onClick = { category = if (category == name) null else name },
                            )
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        GlassPanel(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "No tool matches that search.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisPalette.TextDim,
                            )
                        }
                    }
                }

                items(filtered, key = { it.name }) { tool ->
                    ToolRow(tool)
                }

                item(key = "tail") { Spacer(Modifier.height(12.dp)) }
            }

            JarvisBottomBar(
                selected = JarvisTab.TOOLS,
                onSelect = onSelectTab,
                listening = state.listening,
                busy = state.generating || state.speaking,
                onMic = onToggleMic,
            )
        }
    }
}

@Composable
private fun ToolRow(tool: ToolSummary) {
    GlassPanel(
        modifier = Modifier.fillMaxWidth(),
        corner = 18.dp,
        accent = if (tool.statusReady) JarvisPalette.Cyan else JarvisPalette.Amber,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(
                                JarvisPalette.Cyan.copy(alpha = 0.20f),
                                JarvisPalette.Cyan.copy(alpha = 0.04f),
                            ),
                        ),
                    )
                    .border(
                        1.dp,
                        JarvisPalette.Cyan.copy(alpha = 0.22f),
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                HudIcon(
                    glyph = glyphFor(tool.category),
                    color = JarvisPalette.Cyan,
                    modifier = Modifier.size(19.dp),
                    weight = 1.7.dp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tool.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = JarvisPalette.Ice,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = tool.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = JarvisPalette.TextDim,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HudPill(text = tool.category, color = JarvisPalette.CyanDim)
                    Spacer(Modifier.width(8.dp))
                    StatusDot(
                        color = if (tool.statusReady) JarvisPalette.Success else JarvisPalette.Amber,
                        pulsing = !tool.statusReady,
                        size = 5.dp,
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = tool.status,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (tool.statusReady) {
                            JarvisPalette.Success
                        } else {
                            JarvisPalette.Amber
                        },
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(
                if (selected) {
                    JarvisPalette.Cyan.copy(alpha = 0.18f)
                } else {
                    JarvisPalette.Panel.copy(alpha = 0.7f)
                },
            )
            .border(
                1.dp,
                if (selected) {
                    JarvisPalette.Cyan.copy(alpha = 0.55f)
                } else {
                    JarvisPalette.Line.copy(alpha = 0.6f)
                },
                shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) JarvisPalette.CyanBright else JarvisPalette.TextDim,
        )
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JarvisPalette.Panel.copy(alpha = 0.8f))
            .border(1.dp, JarvisPalette.Line.copy(alpha = 0.7f), shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudIcon(
            glyph = HudGlyph.SEARCH,
            color = JarvisPalette.TextDim,
            modifier = Modifier.size(16.dp),
            weight = 1.8.dp,
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = JarvisPalette.Ice),
            cursorBrush = SolidColor(JarvisPalette.Cyan),
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = JarvisPalette.TextDim.copy(alpha = 0.7f),
                    )
                }
                inner()
            },
        )
    }
}

private fun glyphFor(category: String): HudGlyph = when (category) {
    "Apps" -> HudGlyph.HOME
    "Media" -> HudGlyph.NOTE
    "Time" -> HudGlyph.BOLT
    "Calls & SMS" -> HudGlyph.SEND
    "Info" -> HudGlyph.CHIP
    else -> HudGlyph.TORCH
}
