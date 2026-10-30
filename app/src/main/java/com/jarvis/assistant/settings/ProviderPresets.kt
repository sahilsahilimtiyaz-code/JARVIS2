package com.jarvis.assistant.settings

import com.jarvis.assistant.brain.ProviderKind

/**
 * Convenience presets only. The model field stays free text because provider
 * catalogues change faster than app releases.
 */
data class ProviderPreset(
    val label: String,
    val kind: ProviderKind,
    val name: String,
    val baseUrl: String,
    val model: String,
    val note: String = "",
)

val providerPresets: List<ProviderPreset> = listOf(
    ProviderPreset(
        label = "OpenRouter",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        name = "OpenRouter",
        baseUrl = "https://openrouter.ai/api/v1",
        model = "openai/gpt-4o-mini",
        note = "Pick a model with tools support for phone control.",
    ),
    ProviderPreset(
        label = "OpenAI",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        name = "OpenAI",
        baseUrl = "https://api.openai.com/v1",
        model = "gpt-4o-mini",
    ),
    ProviderPreset(
        label = "Groq",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        name = "Groq",
        baseUrl = "https://api.groq.com/openai/v1",
        model = "llama-3.3-70b-versatile",
    ),
    ProviderPreset(
        label = "Together",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        name = "Together",
        baseUrl = "https://api.together.xyz/v1",
        model = "meta-llama/Llama-3.3-70B-Instruct-Turbo",
    ),
    ProviderPreset(
        label = "Ollama",
        kind = ProviderKind.OPENAI_COMPATIBLE,
        name = "Ollama",
        baseUrl = "http://127.0.0.1:11434/v1",
        model = "llama3.3",
        note = "Plain HTTP is allowed only for localhost.",
    ),
    ProviderPreset(
        label = "Gemini Flash",
        kind = ProviderKind.GEMINI,
        name = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        model = "gemini-2.5-flash",
    ),
    ProviderPreset(
        label = "Gemini Pro",
        kind = ProviderKind.GEMINI,
        name = "Google Gemini",
        baseUrl = "https://generativelanguage.googleapis.com/v1beta",
        model = "gemini-2.5-pro",
    ),
    ProviderPreset(
        label = "Claude Sonnet",
        kind = ProviderKind.ANTHROPIC,
        name = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/v1",
        model = "claude-sonnet-4-5",
    ),
    ProviderPreset(
        label = "Claude Haiku",
        kind = ProviderKind.ANTHROPIC,
        name = "Anthropic Claude",
        baseUrl = "https://api.anthropic.com/v1",
        model = "claude-haiku-4-5",
    ),
)

fun presetsFor(kind: ProviderKind): List<ProviderPreset> =
    providerPresets.filter { it.kind == kind }

fun defaultPresetFor(kind: ProviderKind): ProviderPreset =
    presetsFor(kind).first()
