package com.jarvis.assistant.ui

import com.jarvis.assistant.settings.AppSettings

enum class ChatAuthor { USER, JARVIS }

data class ChatMessageUi(
    val id: String,
    val author: ChatAuthor,
    val text: String,
    val providerName: String? = null,
    val streaming: Boolean = false,
    val isError: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

sealed interface ProviderTestState {
    data object Testing : ProviderTestState
    data class Success(val message: String) : ProviderTestState
    data class Failure(val message: String) : ProviderTestState
}

data class JarvisUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val settings: AppSettings = AppSettings(),
    val messages: List<ChatMessageUi> = emptyList(),
    val input: String = "",
    val generating: Boolean = false,
    val activeProvider: String? = null,
    val toolActivity: String? = null,
    val error: String? = null,
    val notice: String? = null,
    val showSettings: Boolean = false,
    val onboardingBusy: Boolean = false,
    val providerTests: Map<String, ProviderTestState> = emptyMap(),
    val defaultPersonaPrompt: String = "",
) {
    val hasConfiguredProvider: Boolean
        get() = settings.providers.any { it.enabled && it.hasSavedKey }

    val defaultProviderName: String
        get() = settings.defaultProvider?.name ?: "No provider"

    val defaultProviderModel: String
        get() = settings.defaultProvider?.model ?: "Add an API key in Settings"
}
