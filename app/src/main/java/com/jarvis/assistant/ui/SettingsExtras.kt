package com.jarvis.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The parts of the voice that only matter once the cloud voice works: the model
 * header, the wake word, and the switches for hands-free and always-on listening.
 */
@Composable
fun BackgroundVoiceCard(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
) {
    val voice = state.settings.voice
    var model by remember(voice.model) { mutableStateOf(voice.model) }
    var wakeWord by remember(voice.wakeWord) { mutableStateOf(voice.wakeWord) }
    var requireWake by remember(voice.requireWakeWord) { mutableStateOf(voice.requireWakeWord) }
    var alwaysOn by remember(voice.alwaysListening) { mutableStateOf(voice.alwaysListening) }

    LaunchedEffect(state.serviceActive, voice.alwaysListening) {
        alwaysOn = state.serviceActive || voice.alwaysListening
    }

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
                text = "Listening and model",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            OutlinedTextField(
                value = model,
                onValueChange = { model = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Fish Audio model header") },
                placeholder = { Text("s2.1-pro-free") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                text = "Use s2.1-pro-free on a zero balance. A paid model needs credit on the " +
                    "Fish Audio account. ElevenLabs ignores this field and uses its Flash model.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = wakeWord,
                onValueChange = { wakeWord = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Wake word") },
                placeholder = { Text("jarvis") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = requireWake, onCheckedChange = { requireWake = it })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Only answer when called by name", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "With this off, every sentence heard is treated as a request.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = alwaysOn, onCheckedChange = { alwaysOn = it })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Listen in the background", style = MaterialTheme.typography.bodySmall)
                    Text(
                        text = if (state.serviceActive) {
                            "Running now. Say “$wakeWord” with the screen off."
                        } else {
                            "Starts a microphone service with an ongoing notification and Stop."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            state.voiceStatus?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        viewModel.saveVoiceSettings(
                            provider = voice.provider,
                            voiceId = voice.voiceId,
                            speed = voice.speed,
                            fallbackToSystem = voice.fallbackToSystem,
                            language = voice.language,
                            apiKey = "",
                            continuousListening = voice.continuousListening,
                            model = model,
                            alwaysListening = alwaysOn,
                            requireWakeWord = requireWake,
                            wakeWord = wakeWord,
                        )
                    },
                ) {
                    Text("Save listening settings")
                }
                TextButton(onClick = viewModel::testVoice) {
                    Text("Test voice")
                }
            }
        }
    }
}

@Composable
fun PermissionsCard(
    state: JarvisUiState,
    onOpenWizard: () -> Unit,
) {
    val permissions = state.permissions
    val missing = buildList {
        if (!permissions.microphone) add("Microphone")
        if (!permissions.notifications) add("Notifications")
        if (!permissions.accessibility) add("Accessibility")
        if (!permissions.batteryUnrestricted) add("Battery")
    }

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
                text = if (missing.isEmpty()) "All access granted" else "${missing.size} to grant",
                style = MaterialTheme.typography.titleSmall,
                color = if (missing.isEmpty()) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.tertiary
                },
            )
            Text(
                text = if (missing.isEmpty()) {
                    "Microphone, notifications, accessibility and battery are all set."
                } else {
                    "Still needed: ${missing.joinToString(", ")}. The wizard explains each one in " +
                        "plain words, including the restricted-settings step for a sideloaded app."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onOpenWizard) {
                Text("Open permission wizard")
            }
        }
    }
}

@Composable
fun SafetyCard(
    state: JarvisUiState,
    viewModel: JarvisViewModel,
) {
    val safety = state.settings.safety
    var enabled by remember(safety.screenControlEnabled) {
        mutableStateOf(safety.screenControlEnabled)
    }
    var blocklist by remember(safety.blocklistKeywords) {
        mutableStateOf(safety.blocklistKeywords.joinToString(", "))
    }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = enabled, onCheckedChange = { enabled = it })
                Spacer(Modifier.width(8.dp))
                Column {
                    Text("Allow screen control", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "When off, Jarvis refuses every accessibility action.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            OutlinedTextField(
                value = blocklist,
                onValueChange = { blocklist = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Blocked app keywords") },
                supportingText = {
                    Text("Comma separated. Matched against the app's package name and label.")
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
            )

            TextButton(
                onClick = { viewModel.saveSafety(enabled, blocklist) },
                contentPadding = PaddingValues(horizontal = 4.dp),
            ) {
                Text("Save safety settings")
            }
        }
    }
}

@Composable
fun ActionLogCard(
    state: JarvisUiState,
    onOpenLog: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.32f),
        ),
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Action log", style = MaterialTheme.typography.titleSmall)
                Text(
                    "${state.actions.size} recorded, stored on this phone only.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpenLog) {
                Text("Open")
            }
        }
    }
}
