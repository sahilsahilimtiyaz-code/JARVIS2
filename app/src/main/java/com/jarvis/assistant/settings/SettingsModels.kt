package com.jarvis.assistant.settings

import com.jarvis.assistant.brain.ProviderKind
import kotlinx.serialization.Serializable

@Serializable
enum class VoiceProvider(val label: String) {
    SYSTEM("Android system voice"),
    FISH_AUDIO("Fish Audio"),
    ELEVENLABS("ElevenLabs"),
}

@Serializable
data class OwnerProfile(
    val name: String = "",
    val title: String = "sir",
    val occupation: String = "",
    val preferences: String = "",
)

@Serializable
data class ProviderSettings(
    val id: String,
    val name: String,
    val kind: ProviderKind,
    val baseUrl: String,
    val model: String,
    val encryptedApiKey: String = "",
    val enabled: Boolean = true,
) {
    val hasSavedKey: Boolean get() = encryptedApiKey.isNotBlank()
}

@Serializable
data class PersonaSettings(
    val tone: String = "dry British wit, calm and understated",
    val customPrompt: String? = null,
    val lastGreetingId: String? = null,
    /** Epoch millis of the last spoken greeting, so Jarvis does not repeat himself. */
    val lastGreetingAt: Long = 0L,
)

@Serializable
data class VoiceSettings(
    val provider: VoiceProvider = VoiceProvider.SYSTEM,
    val encryptedApiKey: String = "",
    val voiceId: String = "",
    /** Cloud model name. Fish Audio defaults to the free S2.1 model. */
    val model: String = "",
    val speed: Float = 1.0f,
    val fallbackToSystem: Boolean = true,
    val language: String = "en",
    /** Resume listening automatically after Jarvis finishes a reply. */
    val continuousListening: Boolean = true,
    /** Keep the microphone service running in the background. */
    val alwaysListening: Boolean = false,
    /** Require the wake word before treating speech as a command. */
    val requireWakeWord: Boolean = true,
    val wakeWord: String = "jarvis",
) {
    val hasSavedKey: Boolean get() = encryptedApiKey.isNotBlank()
}

@Serializable
data class SafetySettings(
    /** Master switch for screen control through the accessibility service. */
    val screenControlEnabled: Boolean = true,
    /**
     * Package-name and app-label keywords that are never automated. The default
     * list covers banking, payments, password managers and authenticators.
     */
    val blocklistKeywords: List<String> = DEFAULT_BLOCKLIST,
)

@Serializable
data class AppSettings(
    val profile: OwnerProfile = OwnerProfile(),
    val providers: List<ProviderSettings> = defaultProviderSettings(),
    val defaultProviderId: String = "openrouter",
    val persona: PersonaSettings = PersonaSettings(),
    val voice: VoiceSettings = VoiceSettings(),
    val safety: SafetySettings = SafetySettings(),
) {
    val defaultProvider: ProviderSettings?
        get() = providers.firstOrNull { it.id == defaultProviderId }
            ?: providers.firstOrNull()

    val configuredProviders: List<ProviderSettings>
        get() = providers.filter { it.enabled && it.hasSavedKey }
}

/** Keywords are matched case-insensitively against the package name and label. */
val DEFAULT_BLOCKLIST: List<String> = listOf(
    "bank",
    "banking",
    "neobank",
    "pay",
    "payment",
    "wallet",
    "upi",
    "venmo",
    "paypal",
    "cash app",
    "google pay",
    "samsung pay",
    "password",
    "1password",
    "bitwarden",
    "lastpass",
    "keepass",
    "keeper",
    "authenticator",
    "authy",
    "otp",
)

fun defaultProviderSettings(): List<ProviderSettings> = listOf(
    ProviderSettings(
        id = "openrouter",
        name = "OpenRouter",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        baseUrl = ProviderKind.OPENAI_COMPATIBLE.defaultBaseUrl,
        model = "openai/gpt-4o-mini",
    ),
    ProviderSettings(
        id = "gemini",
        name = "Google Gemini",
        kind = ProviderKind.GEMINI,
        baseUrl = ProviderKind.GEMINI.defaultBaseUrl,
        model = "gemini-2.5-flash",
    ),
    ProviderSettings(
        id = "anthropic",
        name = "Anthropic",
        kind = ProviderKind.ANTHROPIC,
        baseUrl = ProviderKind.ANTHROPIC.defaultBaseUrl,
        model = "claude-haiku-4-5",
    ),
)
