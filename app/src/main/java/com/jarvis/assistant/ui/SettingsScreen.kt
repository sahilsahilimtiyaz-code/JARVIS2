package com.jarvis.assistant.ui

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jarvis.assistant.brain.ProviderKind
import com.jarvis.assistant.settings.OwnerProfile
import com.jarvis.assistant.settings.ProviderPreset
import com.jarvis.assistant.settings.ProviderSettings
import com.jarvis.assistant.settings.defaultPresetFor
import com.jarvis.assistant.settings.presetsFor

@Composable
fun SettingsScreen(
    state: JarvisUiState,
    onBack: () -> Unit,
    viewModel: JarvisViewModel,
) {
    var showAddDialog by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .imePadding(),
        ) {
            SettingsHeader(onBack = onBack)

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Light,
                    )
                }

                if (state.error != null) {
                    item {
                        SettingsBanner(
                            message = state.error,
                            isError = true,
                            onDismiss = viewModel::dismissError,
                        )
                    }
                }
                if (state.notice != null) {
                    item {
                        SettingsBanner(
                            message = state.notice,
                            isError = false,
                            onDismiss = viewModel::dismissNotice,
                        )
                    }
                }

                item {
                    SectionTitle("Providers")
                }
                item {
                    Text(
                        text = "Jarvis tries your default provider first, then the others in this list. " +
                            "Not every model supports tool calling; on OpenRouter choose a model with the tools badge. " +
                            "Chat works without tools, but phone control will need a model that supports them.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.settings.providers.isEmpty()) {
                    item {
                        Text(
                            text = "No providers yet. Add one below.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                itemsIndexed(
                    items = state.settings.providers,
                    key = { _, provider -> provider.id },
                ) { index, provider ->
                    ProviderCard(
                        provider = provider,
                        index = index,
                        lastIndex = state.settings.providers.lastIndex,
                        isDefault = provider.id == state.settings.defaultProviderId,
                        state = state,
                        viewModel = viewModel,
                    )
                }

                item {
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text("Add provider")
                    }
                }

                item {
                    SectionTitle("Persona")
                }
                item {
                    PersonaEditor(state = state, viewModel = viewModel)
                }

                item {
                    SectionTitle("About")
                }
                item {
                    AboutCard()
                }
            }
        }
    }

    if (showAddDialog) {
        AddProviderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { kind, name, baseUrl, model, apiKey ->
                viewModel.addProvider(kind, name, baseUrl, model, apiKey)
                showAddDialog = false
            },
        )
    }
}

@Composable
private fun SettingsHeader(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.CenterStart),
        ) {
            Text("Back")
        }
        Text(
            text = "Settings",
            modifier = Modifier.align(Alignment.Center),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SectionTitle(title: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium,
        )
        HorizontalDivider(
            modifier = Modifier.padding(top = 6.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun SettingsBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val content = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = container,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = content,
            )
            TextButton(onClick = onDismiss) {
                Text(text = "Dismiss", color = content)
            }
        }
    }
}

@Composable
private fun ProviderCard(
    provider: ProviderSettings,
    index: Int,
    lastIndex: Int,
    isDefault: Boolean,
    state: JarvisUiState,
    viewModel: JarvisViewModel,
) {
    var name by remember(provider.id) { mutableStateOf(provider.name) }
    var baseUrl by remember(provider.id) { mutableStateOf(provider.baseUrl) }
    var model by remember(provider.id) { mutableStateOf(provider.model) }
    var apiKey by remember(provider.id) { mutableStateOf("") }
    var revealKey by remember(provider.id) { mutableStateOf(false) }
    var showRemoveDialog by remember(provider.id) { mutableStateOf(false) }
    val presets = remember(provider.kind) { presetsFor(provider.kind) }
    val draft = provider.copy(
        name = name.trim(),
        baseUrl = baseUrl.trim(),
        model = model.trim(),
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = provider.name,
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = provider.kind.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (isDefault) {
                        Spacer(Modifier.height(4.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape,
                        ) {
                            Text(
                                text = "Default",
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }
                Switch(
                    checked = provider.enabled,
                    onCheckedChange = { viewModel.setProviderEnabled(provider.id, it) },
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Provider name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = baseUrl,
                onValueChange = { baseUrl = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Base URL") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Model") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(presets, key = { it.label }) { preset ->
                    AssistChip(
                        onClick = {
                            name = preset.name
                            baseUrl = preset.baseUrl
                            model = preset.model
                        },
                        label = { Text(preset.label) },
                    )
                }
            }
            Text(
                text = "Presets are starting points. Type the exact model ID from your provider.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                modifier = Modifier.fillMaxWidth(),
                label = {
                    Text(if (provider.hasSavedKey) "API key (saved)" else "API key")
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (revealKey) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    TextButton(
                        onClick = { revealKey = !revealKey },
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        Text(
                            text = if (revealKey) "Hide" else "Show",
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                },
            )
            if (provider.hasSavedKey) {
                Text(
                    text = "A key is saved. Leave the field blank to keep it.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                text = "Stored encrypted with AES-GCM and the Android Keystore. Never written to logs.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { viewModel.saveProvider(draft, apiKey) },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Save")
                }
                TextButton(onClick = { viewModel.testProvider(draft, apiKey) }) {
                    Text("Test")
                }
                if (!isDefault) {
                    TextButton(onClick = { viewModel.setDefaultProvider(provider.id) }) {
                        Text("Default")
                    }
                }
                TextButton(
                    onClick = { viewModel.moveProvider(provider.id, -1) },
                    enabled = index > 0,
                ) {
                    Text("Up")
                }
                TextButton(
                    onClick = { viewModel.moveProvider(provider.id, 1) },
                    enabled = index < lastIndex,
                ) {
                    Text("Down")
                }
                TextButton(
                    onClick = { showRemoveDialog = true },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Remove")
                }
            }

            when (val test = state.providerTests[provider.id]) {
                is ProviderTestState.Testing -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = "Testing connection...",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                is ProviderTestState.Success -> {
                    Text(
                        text = test.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                is ProviderTestState.Failure -> {
                    Text(
                        text = test.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                null -> Unit
            }
        }
    }

    if (showRemoveDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveDialog = false },
            title = { Text("Remove ${provider.name}?") },
            text = {
                Text("Its saved key and settings will be deleted from this phone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.removeProvider(provider.id)
                        showRemoveDialog = false
                    },
                ) {
                    Text(
                        text = "Remove",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveDialog = false }) {
                    Text("Cancel")
                }
            },
        )
    }
}

@Composable
private fun PersonaEditor(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
) {
    val profile = state.settings.profile
    val persona = state.settings.persona

    var name by remember(profile.name) { mutableStateOf(profile.name) }
    var title by remember(profile.title) { mutableStateOf(profile.title) }
    var occupation by remember(profile.occupation) { mutableStateOf(profile.occupation) }
    var preferences by remember(profile.preferences) { mutableStateOf(profile.preferences) }
    var tone by remember(persona.tone) { mutableStateOf(persona.tone) }
    var prompt by remember(persona.customPrompt) { mutableStateOf(persona.customPrompt.orEmpty()) }
    var showDefaultPrompt by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Owner profile",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Preferred title") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = occupation,
                onValueChange = { occupation = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Occupation (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = preferences,
                onValueChange = { preferences = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Preferences (optional)") },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
            )
            Button(
                onClick = {
                    viewModel.saveProfile(
                        OwnerProfile(
                            name = name,
                            title = title,
                            occupation = occupation,
                            preferences = preferences,
                        ),
                    )
                },
                enabled = !state.onboardingBusy,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Save profile")
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )

            Text(
                text = "Voice and prompt",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            OutlinedTextField(
                value = tone,
                onValueChange = { tone = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Tone") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Custom persona prompt (optional)") },
                minLines = 8,
                maxLines = 14,
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                text = "Leave blank to use the built-in Jarvis prompt. The owner profile, current context " +
                    "and non-negotiable safety rules are always added and cannot be edited away.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = {
                        viewModel.savePersona(
                            tone = tone,
                            customPrompt = prompt.ifBlank { null },
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text("Save persona")
                }
                TextButton(
                    onClick = {
                        prompt = ""
                        viewModel.resetPersonaPrompt(tone)
                    },
                ) {
                    Text("Use default")
                }
                if (state.defaultPersonaPrompt.isNotBlank()) {
                    TextButton(onClick = { showDefaultPrompt = !showDefaultPrompt }) {
                        Text(if (showDefaultPrompt) "Hide default" else "View default")
                    }
                }
            }

            if (showDefaultPrompt && state.defaultPersonaPrompt.isNotBlank()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    color = MaterialTheme.colorScheme.background,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = state.defaultPersonaPrompt,
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddProviderDialog(
    onDismiss: () -> Unit,
    onAdd: (ProviderKind, String, String, String, String) -> Unit,
) {
    var kind by remember { mutableStateOf(ProviderKind.OPENAI_COMPATIBLE) }
    var preset by remember { mutableStateOf(defaultPresetFor(kind)) }
    var name by remember { mutableStateOf(preset.name) }
    var baseUrl by remember { mutableStateOf(preset.baseUrl) }
    var model by remember { mutableStateOf(preset.model) }
    var apiKey by remember { mutableStateOf("") }

    fun applyPreset(next: ProviderPreset) {
        kind = next.kind
        preset = next
        name = next.name
        baseUrl = next.baseUrl
        model = next.model
    }

    val valid = name.isNotBlank() &&
        (baseUrl.trim().startsWith("http://") || baseUrl.trim().startsWith("https://")) &&
        model.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add provider") },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Choose a preset, or enter any OpenAI-compatible endpoint.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(ProviderKind.entries.toList(), key = { it.name }) { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { applyPreset(defaultPresetFor(option)) },
                            label = { Text(option.label) },
                        )
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(presetsFor(kind), key = { it.label }) { option ->
                        AssistChip(
                            onClick = { applyPreset(option) },
                            label = { Text(option.label) },
                        )
                    }
                }

                if (preset.note.isNotBlank()) {
                    Text(
                        text = preset.note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Provider name") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Base URL") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                OutlinedTextField(
                    value = model,
                    onValueChange = { model = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Model") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("API key (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                )
                Text(
                    text = "You can add the key now or later. It is encrypted with the Android Keystore.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onAdd(
                        kind,
                        name.trim(),
                        baseUrl.trim(),
                        model.trim(),
                        apiKey.trim(),
                    )
                },
                enabled = valid,
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

@Composable
private fun AboutCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Jarvis 0.2.0 · Phase 1",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Streaming chat, encrypted provider keys, onboarding, persona and greeting. " +
                    "Chat history is kept in memory for now; persistent memory arrives in Phase 4.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "No API keys are written to the repository, BuildConfig or logs.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
