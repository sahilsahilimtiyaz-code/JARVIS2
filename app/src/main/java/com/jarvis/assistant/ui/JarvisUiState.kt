package com.jarvis.assistant.ui

import com.jarvis.assistant.settings.AppSettings
import com.jarvis.assistant.tools.ActionLogEntry
import com.jarvis.assistant.tools.ToolSummary

enum class ChatAuthor { USER, JARVIS }

enum class JarvisTab { HOME, TOOLS, LOG, SETTINGS }

data class ChatMessageUi(
    val id: String,
    val author: ChatAuthor,
    val text: String,
    val providerName: String? = null,
    val streaming: Boolean = false,
    val isError: Boolean = false,
    val tools: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

sealed interface ProviderTestState {
    data object Testing : ProviderTestState
    data class Success(val message: String) : ProviderTestState
    data class Failure(val message: String) : ProviderTestState
}

/** An action waiting for the owner's explicit yes. */
data class PendingConfirmation(
    val id: String,
    val title: String,
    val detail: String,
)

/** A runtime permission Jarvis needs before it can finish a tool call. */
data class PendingPermission(
    val permission: String,
    val rationale: String,
)

data class JarvisUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val generating: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    val activeProvider: String? = null,
    val toolActivity: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val tab: JarvisTab = JarvisTab.HOME,
    val onboardingBusy: Boolean = false,
    val providerTests: Map<String, ProviderTestState> = emptyMap(),
    val defaultPersonaPrompt: String = "",
    val tools: List<ToolSummary> = emptyList(),
    val actions: List<ActionLogEntry> = emptyList(),
    val pendingConfirmation: PendingConfirmation? = null,
    val pendingPermission: PendingPermission? = null,
    val quickPanelOpen: Boolean = false,
    val torchOn: Boolean = false,
) {
    val showSettings: Boolean
        get() = tab == JarvisTab.SETTINGS

    val hasConfiguredProvider: Boolean
        get() = settings.providers.any { it.enabled && it.hasSavedKey }

    val defaultProviderName: String
        get() = settings.defaultProvider?.name ?: "No provider"

    val defaultProviderModel: String
        get() = settings.defaultProvider?.model ?: "Add an API key in Settings"

    val reactorState: com.jarvis.assistant.ui.components.ReactorState
        get() = when {
            loading -> com.jarvis.assistant.ui.components.ReactorState.BOOT
            generating -> com.jarvis.assistant.ui.components.ReactorState.THINKING
            listening -> com.jarvis.assistant.ui.components.ReactorState.LISTENING
            speaking -> com.jarvis.assistant.ui.components.ReactorState.SPEAKING
            else -> com.jarvis.assistant.ui.components.ReactorState.IDLE
        }
}
