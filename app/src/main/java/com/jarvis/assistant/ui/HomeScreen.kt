package com.jarvis.assistant.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.ui.components.ActionTile
import com.jarvis.assistant.ui.components.ArcReactor
import com.jarvis.assistant.ui.components.GlassPanel
import com.jarvis.assistant.ui.components.HudGlyph
import com.jarvis.assistant.ui.components.HudIcon
import com.jarvis.assistant.ui.components.HudIconButton
import com.jarvis.assistant.ui.components.HudPill
import com.jarvis.assistant.ui.components.JarvisBackdrop
import com.jarvis.assistant.ui.components.JarvisBottomBar
import com.jarvis.assistant.ui.components.JarvisTopBar
import com.jarvis.assistant.ui.components.ReactorGlyph
import com.jarvis.assistant.ui.components.ReactorState
import com.jarvis.assistant.ui.components.StatusDot
import com.jarvis.assistant.ui.components.VoiceWave
import com.jarvis.assistant.ui.components.label
import com.jarvis.assistant.ui.theme.JarvisPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    state: JarvisUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleMic: () -> Unit,
    onSelectTab: (JarvisTab) -> Unit,
    onOpenQuickPanel: () -> Unit,
    onRetry: () -> Unit,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.text?.length) {
        if (state.messages.isNotEmpty()) {
            runCatching {
                listState.animateScrollToItem(listState.layoutInfo.totalItemsCount - 1)
            }
        }
    }

    JarvisBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            JarvisTopBar(
                status = when {
                    state.listening -> "LISTENING"
                    state.generating -> "PROCESSING"
                    state.speaking -> "SPEAKING"
                    else -> "ONLINE"
                },
                statusColor = when {
                    state.listening -> JarvisPalette.CyanBright
                    state.generating -> JarvisPalette.Amber
                    state.speaking -> JarvisPalette.Cyan
                    else -> JarvisPalette.Success
                },
                trailing = {
                    HudIconButton(
                        glyph = HudGlyph.BOLT,
                        contentDescription = "Quick panel",
                        onClick = onOpenQuickPanel,
                        size = 36.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    HudIconButton(
                        glyph = HudGlyph.GEAR,
                        contentDescription = "Settings",
                        onClick = { onSelectTab(JarvisTab.SETTINGS) },
                        size = 36.dp,
                    )
                },
            )

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item(key = "header") {
                    HomeHeader(
                        state = state,
                        onSelectTab = onSelectTab,
                        onToggleMic = onToggleMic,
                        onOpenQuickPanel = onOpenQuickPanel,
                    )
                }

                if (state.messages.isEmpty()) {
                    item(key = "starter") {
                        StarterCard(onPick = onInputChange)
                    }
                }

                items(state.messages, key = { it.id }) { message ->
                    MessageCard(
                        message = message,
                        isLastAssistant = message.id == state.messages.lastOrNull()?.id,
                        onRetry = onRetry,
                    )
                }

                item(key = "tail") { Spacer(Modifier.height(4.dp)) }
            }

            AnimatedVisibility(
                visible = state.error != null || state.notice != null,
                enter = fadeIn(tween(220)) + slideInVertically(tween(220)) { it / 2 },
                exit = fadeOut(tween(160)) + slideOutVertically(tween(160)) { it / 2 },
            ) {
                BannerStrip(
                    error = state.error,
                    notice = state.notice,
                    onDismissError = onDismissError,
                    onDismissNotice = onDismissNotice,
                )
            }

            ComposerBar(
                state = state,
                onInputChange = onInputChange,
                onSend = onSend,
                onStop = onStop,
                onToggleMic = onToggleMic,
            )

            JarvisBottomBar(
                selected = JarvisTab.HOME,
                onSelect = onSelectTab,
                listening = state.listening,
                busy = state.generating || state.speaking,
                onMic = onToggleMic,
            )
        }
    }
}

@Composable
private fun HomeHeader(
    state: JarvisUiState,
    onSelectTab: (JarvisTab) -> Unit,
    onToggleMic: () -> Unit,
    onOpenQuickPanel: () -> Unit,
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ReadoutCard(
                modifier = Modifier.weight(1f),
                label = "AI MODEL",
                value = state.defaultProviderModel,
                sub = state.defaultProviderName,
                accent = JarvisPalette.Cyan,
                onClick = { onSelectTab(JarvisTab.SETTINGS) },
            )
            ReadoutCard(
                modifier = Modifier.weight(1f),
                label = "VOICE",
                value = state.settings.voice.provider.label,
                sub = if (state.settings.voice.hasSavedKey) "Neural voice ready" else "System fallback",
                accent = JarvisPalette.CyanBright,
                onClick = { onSelectTab(JarvisTab.SETTINGS) },
            )
        }

        Spacer(Modifier.height(18.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(198.dp)
                    .clip(CircleShape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onToggleMic,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                ArcReactor(
                    state = state.reactorState,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusDot(
                    color = when (state.reactorState) {
                        ReactorState.LISTENING -> JarvisPalette.CyanBright
                        ReactorState.THINKING -> JarvisPalette.Amber
                        ReactorState.SPEAKING -> JarvisPalette.Cyan
                        ReactorState.BOOT -> JarvisPalette.CyanDim
                        ReactorState.IDLE -> JarvisPalette.Success
                    },
                    pulsing = state.reactorState != ReactorState.IDLE,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = state.reactorState.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.Ice.copy(alpha = 0.85f),
                    letterSpacing = 2.4.sp,
                )
            }

            Spacer(Modifier.height(8.dp))

            if (state.listening || state.speaking) {
                VoiceWave(
                    active = true,
                    modifier = Modifier
                        .fillMaxWidth(0.62f)
                        .height(26.dp),
                    color = if (state.listening) JarvisPalette.CyanBright else JarvisPalette.Cyan,
                )
            } else {
                Text(
                    text = state.toolActivity
                        ?: if (state.generating) "Working on it..." else "Tap the reactor or the mic to speak",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim,
                    letterSpacing = 0.8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ActionTile(
                glyph = HudGlyph.SLIDERS,
                label = "Toolkit",
                onClick = { onSelectTab(JarvisTab.TOOLS) },
                modifier = Modifier.weight(1f),
            )
            ActionTile(
                glyph = HudGlyph.BOLT,
                label = "Quick panel",
                onClick = onOpenQuickPanel,
                modifier = Modifier.weight(1f),
                accent = JarvisPalette.Amber,
            )
            ActionTile(
                glyph = HudGlyph.LOG,
                label = "Action log",
                onClick = { onSelectTab(JarvisTab.LOG) },
                modifier = Modifier.weight(1f),
                accent = JarvisPalette.Success,
            )
            ActionTile(
                glyph = HudGlyph.GEAR,
                label = "Settings",
                onClick = { onSelectTab(JarvisTab.SETTINGS) },
                modifier = Modifier.weight(1f),
                accent = JarvisPalette.CyanBright,
            )
        }

        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ReadoutCard(
    label: String,
    value: String,
    sub: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassPanel(
        modifier = modifier.clickable(onClick = onClick),
        corner = 18.dp,
        accent = accent,
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = JarvisPalette.TextDim,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = JarvisPalette.Ice,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = sub,
            style = MaterialTheme.typography.labelSmall,
            color = accent.copy(alpha = 0.75f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun StarterCard(onPick: (String) -> Unit) {
    val suggestions = listOf(
        "Open YouTube and search lo-fi",
        "Set a timer for ten minutes",
        "Turn on the flashlight",
        "Pause the music",
    )

    GlassPanel(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ReactorGlyph(modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Ready when you are",
                    style = MaterialTheme.typography.titleSmall,
                    color = JarvisPalette.Ice,
                )
                Text(
                    text = "Try one of these, or just say what you need.",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim,
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        suggestions.forEach { suggestion ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(suggestion) }
                    .padding(horizontal = 8.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudIcon(
                    glyph = HudGlyph.CHEVRON_RIGHT,
                    color = JarvisPalette.Cyan.copy(alpha = 0.7f),
                    modifier = Modifier.size(14.dp),
                    weight = 2.dp,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodyMedium,
                    color = JarvisPalette.Ice.copy(alpha = 0.9f),
                )
            }
        }
    }
}

@Composable
private fun MessageCard(
    message: ChatMessageUi,
    isLastAssistant: Boolean,
    onRetry: () -> Unit,
) {
    val context = LocalContext.current
    val isUser = message.author == ChatAuthor.USER

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        GlassPanel(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.96f),
            accent = when {
                message.isError -> JarvisPalette.Danger
                isUser -> JarvisPalette.CyanBright
                else -> JarvisPalette.Cyan
            },
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isUser) {
                    ReactorGlyph(
                        modifier = Modifier.size(16.dp),
                        color = if (message.isError) JarvisPalette.Danger else JarvisPalette.Cyan,
                    )
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = "J.A.R.V.I.S.",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (message.isError) JarvisPalette.Danger else JarvisPalette.Cyan,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Text(
                        text = "YOU",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisPalette.CyanBright,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (!isUser && message.providerName != null) {
                    HudPill(text = message.providerName, color = JarvisPalette.TextDim)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = formatTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim.copy(alpha = 0.7f),
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                text = if (message.streaming) message.text + " \u258D" else message.text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (message.isError) {
                    JarvisPalette.Danger.copy(alpha = 0.95f)
                } else {
                    JarvisPalette.Ice
                },
            )

            if (message.tools.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    message.tools.forEach { tool ->
                        HudPill(text = tool.replace('_', ' '), color = JarvisPalette.Success)
                    }
                }
            }

            if (!isUser && !message.streaming && message.text.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Row {
                    MessageAction(
                        glyph = HudGlyph.COPY,
                        label = "Copy",
                        onClick = { copyToClipboard(context, message.text) },
                    )
                    if (isLastAssistant && !message.isError) {
                        MessageAction(
                            glyph = HudGlyph.REFRESH,
                            label = "Regenerate",
                            onClick = onRetry,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageAction(
    glyph: HudGlyph,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HudIcon(
            glyph = glyph,
            color = JarvisPalette.TextDim,
            modifier = Modifier.size(13.dp),
            weight = 1.8.dp,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = JarvisPalette.TextDim,
        )
    }
}

@Composable
private fun BannerStrip(
    error: String?,
    notice: String?,
    onDismissError: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
        if (error != null) {
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = JarvisPalette.Danger,
                corner = 16.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisPalette.Danger,
                        modifier = Modifier.weight(1f),
                    )
                    HudIconButton(
                        glyph = HudGlyph.CLOSE,
                        contentDescription = "Dismiss error",
                        onClick = onDismissError,
                        size = 26.dp,
                        tint = JarvisPalette.Danger,
                    )
                }
            }
        }
        if (notice != null) {
            Spacer(Modifier.height(if (error != null) 8.dp else 0.dp))
            GlassPanel(
                modifier = Modifier.fillMaxWidth(),
                accent = JarvisPalette.Success,
                corner = 16.dp,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelMedium,
                        color = JarvisPalette.Success,
                        modifier = Modifier.weight(1f),
                    )
                    HudIconButton(
                        glyph = HudGlyph.CLOSE,
                        contentDescription = "Dismiss notice",
                        onClick = onDismissNotice,
                        size = 26.dp,
                        tint = JarvisPalette.Success,
                    )
                }
            }
        }
    }
}

@Composable
private fun ComposerBar(
    state: JarvisUiState,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onToggleMic: () -> Unit,
) {
    val shape = RoundedCornerShape(26.dp)
    val canSend = state.input.isNotBlank() && !state.generating

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(
                        JarvisPalette.Panel.copy(alpha = 0.96f),
                        JarvisPalette.Deep.copy(alpha = 0.96f),
                    ),
                ),
            )
            .border(1.dp, JarvisPalette.Cyan.copy(alpha = 0.22f), shape)
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = state.input,
            onValueChange = onInputChange,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = JarvisPalette.Ice),
            cursorBrush = SolidColor(JarvisPalette.Cyan),
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            decorationBox = { inner ->
                if (state.input.isEmpty()) {
                    Text(
                        text = "Ask Jarvis, or tap the mic",
                        style = MaterialTheme.typography.bodyLarge,
                        color = JarvisPalette.TextDim.copy(alpha = 0.75f),
                    )
                }
                inner()
            },
        )

        Spacer(Modifier.width(6.dp))

        HudIconButton(
            glyph = HudGlyph.MIC,
            contentDescription = if (state.listening) "Stop listening" else "Start listening",
            onClick = onToggleMic,
            size = 38.dp,
            tint = if (state.listening) JarvisPalette.CyanBright else JarvisPalette.TextDim,
        )

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            when {
                                state.generating -> JarvisPalette.Danger.copy(alpha = 0.95f)
                                canSend -> JarvisPalette.Cyan.copy(alpha = 0.95f)
                                else -> JarvisPalette.PanelLight
                            },
                            when {
                                state.generating -> JarvisPalette.Danger.copy(alpha = 0.55f)
                                canSend -> JarvisPalette.CyanDim.copy(alpha = 0.6f)
                                else -> JarvisPalette.Panel
                            },
                        ),
                    ),
                )
                .alpha(if (canSend || state.generating) 1f else 0.65f)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { if (state.generating) onStop() else if (canSend) onSend() },
                ),
            contentAlignment = Alignment.Center,
        ) {
            HudIcon(
                glyph = if (state.generating) HudGlyph.STOP else HudGlyph.SEND,
                color = if (state.generating || canSend) {
                    JarvisPalette.Void
                } else {
                    JarvisPalette.TextDim
                },
                modifier = Modifier.size(18.dp),
                weight = 2.dp,
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))

private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText("Jarvis", text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}
