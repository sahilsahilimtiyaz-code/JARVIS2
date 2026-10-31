package com.jarvis.assistant.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.memory.MemoryEntity
import com.jarvis.assistant.memory.MemoryType
import com.jarvis.assistant.ui.components.GlassPanel
import com.jarvis.assistant.ui.components.GlowDivider
import com.jarvis.assistant.ui.components.JarvisBackdrop
import com.jarvis.assistant.ui.components.JarvisBottomBar
import com.jarvis.assistant.ui.components.JarvisTopBar
import com.jarvis.assistant.ui.components.ScreenTitle
import com.jarvis.assistant.ui.theme.JarvisPalette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * What Jarvis remembers, in plain language.
 *
 * Everything here is editable: the owner can add a note, change one, delete one,
 * export the lot to JSON, import it on another build, or forget everything.
 */
@Composable
fun MemoryScreen(
    state: JarvisUiState,
    onSelectTab: (JarvisTab) -> Unit,
    onToggleMic: () -> Unit,
    onAdd: (String, String, Int) -> Unit,
    onUpdate: (MemoryEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onForgetAll: () -> Unit,
    onExport: (Uri) -> Unit,
    onImport: (Uri) -> Unit,
    onExtract: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<MemoryEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var confirmForget by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(onExport) }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(onImport) }

    val filtered = remember(state.memories, query) {
        if (query.isBlank()) {
            state.memories
        } else {
            state.memories.filter {
                it.text.contains(query, ignoreCase = true) ||
                    it.type.contains(query, ignoreCase = true)
            }
        }
    }

    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            containerColor = JarvisPalette.Panel,
            titleContentColor = JarvisPalette.Ice,
            textContentColor = JarvisPalette.TextDim,
            title = { Text("Forget everything?") },
            text = {
                Text(
                    "This deletes every memory, profile fact, conversation and summary. " +
                        "The action log is kept, because it records what Jarvis did on your phone. " +
                        "This cannot be undone.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    onForgetAll()
                }) {
                    Text("Forget everything", color = JarvisPalette.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmForget = false }) {
                    Text("Cancel", color = JarvisPalette.Cyan)
                }
            },
        )
    }

    if (adding) {
        MemoryEditorDialog(
            title = "Add a memory",
            initial = null,
            onDismiss = { adding = false },
            onSave = { text, type, importance ->
                adding = false
                onAdd(text, type, importance)
            },
        )
    }

    editing?.let { memory ->
        MemoryEditorDialog(
            title = "Edit memory",
            initial = memory,
            onDismiss = { editing = null },
            onSave = { text, type, importance ->
                editing = null
                onUpdate(memory.copy(text = text, type = type, importance = importance))
            },
        )
    }

    JarvisBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            JarvisTopBar(
                title = "MEMORY",
                status = "${state.memoryCount} NOTES",
                statusColor = JarvisPalette.Cyan,
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item(key = "intro") {
                    Column {
                        ScreenTitle("What Jarvis knows")
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "These notes live only on this phone. Jarvis reads the relevant " +
                                "ones before each reply and adds new ones after a conversation.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = JarvisPalette.TextDim,
                        )
                        Spacer(Modifier.height(12.dp))
                        SearchField(
                            value = query,
                            onValueChange = { query = it },
                            placeholder = "Search memory",
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SmallAction("Add", JarvisPalette.Cyan) { adding = true }
                            SmallAction("Extract now", JarvisPalette.CyanBright, onExtract)
                            SmallAction("Export", JarvisPalette.Cyan) {
                                exportLauncher.launch("jarvis-memory.json")
                            }
                            SmallAction("Import", JarvisPalette.Cyan) {
                                importLauncher.launch(arrayOf("application/json", "text/*"))
                            }
                        }
                    }
                }

                if (state.facts.isNotEmpty()) {
                    item(key = "facts-title") {
                        SectionLabel("Profile facts")
                    }
                    items(state.facts, key = { "fact_${it.key}" }) { fact ->
                        GlassPanel(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = fact.key.replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = JarvisPalette.CyanBright,
                                    modifier = Modifier.width(110.dp),
                                )
                                Text(
                                    text = fact.value,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = JarvisPalette.Ice,
                                )
                            }
                        }
                    }
                }

                item(key = "memories-title") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionLabel("Memories", modifier = Modifier.weight(1f))
                        TextButton(onClick = { confirmForget = true }) {
                            Text("Forget everything", color = JarvisPalette.Danger, fontSize = 12.sp)
                        }
                    }
                }

                if (filtered.isEmpty()) {
                    item(key = "empty") {
                        GlassPanel(modifier = Modifier.fillMaxWidth(), corner = 16.dp) {
                            Text(
                                text = if (query.isBlank()) {
                                    "Nothing stored yet. Tell Jarvis something to remember, or add a note."
                                } else {
                                    "No memories match \"$query\"."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = JarvisPalette.TextDim,
                            )
                        }
                    }
                }

                items(filtered, key = { it.id }) { memory ->
                    MemoryCard(
                        memory = memory,
                        onEdit = { editing = memory },
                        onDelete = { onDelete(memory.id) },
                    )
                }

                item(key = "spacer") {
                    Spacer(Modifier.height(12.dp))
                }
            }

            JarvisBottomBar(
                selected = JarvisTab.MEMORY,
                onSelect = onSelectTab,
                listening = state.listening,
                busy = state.generating,
                onMic = onToggleMic,
            )
        }
    }
}

@Composable
private fun MemoryCard(
    memory: MemoryEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onEdit,
            ),
        corner = 16.dp,
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = memory.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = JarvisPalette.Ice,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = memory.type.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisPalette.Cyan,
                        letterSpacing = 1.2.sp,
                    )
                    Text(
                        text = "importance ${memory.importance}/5",
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisPalette.TextDim,
                    )
                    Text(
                        text = SimpleDateFormat("d MMM", Locale.UK).format(Date(memory.createdAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = JarvisPalette.TextDim,
                    )
                }
            }
            TextButton(onClick = onDelete) {
                Text("Forget", color = JarvisPalette.Danger, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun MemoryEditorDialog(
    title: String,
    initial: MemoryEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String, Int) -> Unit,
) {
    var text by remember { mutableStateOf(initial?.text.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: MemoryType.FACT) }
    var importance by remember { mutableStateOf((initial?.importance ?: 3).toFloat()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = JarvisPalette.Panel,
        titleContentColor = JarvisPalette.Ice,
        textContentColor = JarvisPalette.TextDim,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("What should Jarvis remember?") },
                    minLines = 2,
                    maxLines = 5,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        MemoryType.FACT,
                        MemoryType.PREFERENCE,
                        MemoryType.PERSON,
                        MemoryType.PROJECT,
                    ).forEach { option ->
                        val selected = type == option
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (selected) JarvisPalette.Cyan.copy(alpha = 0.2f)
                                    else JarvisPalette.PanelLight,
                                )
                                .border(
                                    1.dp,
                                    JarvisPalette.Cyan.copy(alpha = if (selected) 0.7f else 0.2f),
                                    RoundedCornerShape(50),
                                )
                                .clickable { type = option }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        ) {
                            Text(
                                text = option,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) JarvisPalette.CyanBright else JarvisPalette.TextDim,
                            )
                        }
                    }
                }
                Text(
                    text = "Importance ${importance.toInt()}/5",
                    style = MaterialTheme.typography.labelSmall,
                    color = JarvisPalette.TextDim,
                )
                Slider(
                    value = importance,
                    onValueChange = { importance = it },
                    valueRange = 1f..5f,
                    steps = 3,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim(), type, importance.toInt()) },
                enabled = text.trim().length >= 3,
            ) {
                Text("Save", color = JarvisPalette.Cyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = JarvisPalette.TextDim)
            }
        },
    )
}

@Composable
private fun SmallAction(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.45f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, fontSize = 11.sp)
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = JarvisPalette.CyanBright,
            letterSpacing = 2.sp,
        )
        Spacer(Modifier.height(3.dp))
        GlowDivider()
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(JarvisPalette.Panel.copy(alpha = 0.75f))
            .border(1.dp, JarvisPalette.Cyan.copy(alpha = 0.20f), shape)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = JarvisPalette.Ice),
            cursorBrush = SolidColor(JarvisPalette.Cyan),
            modifier = Modifier.fillMaxWidth(),
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
