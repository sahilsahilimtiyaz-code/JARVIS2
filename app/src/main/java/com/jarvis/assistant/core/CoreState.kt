package com.jarvis.assistant.core

enum class ChatAuthor { USER, JARVIS }

/**
 * The runtime's own view of a conversation. It is deliberately free of Compose
 * types so the foreground service, the workers and the UI can share it.
 */
data class CoreMessage(
    val id: String,
    val author: ChatAuthor,
    val text: String,
    val providerName: String? = null,
    val streaming: Boolean = false,
    val isError: Boolean = false,
    val tools: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
)

data class PendingConfirmation(
    val id: String,
    val title: String,
    val detail: String,
)

data class PendingPermission(
    val permission: String,
    val rationale: String,
)

/** Everything the UI and the notification reflect. */
data class CoreState(
    val messages: List<CoreMessage> = emptyList(),
    val generating: Boolean = false,
    val listening: Boolean = false,
    val speaking: Boolean = false,
    /** Live recogniser text, shown under the reactor while the owner talks. */
    val partialTranscript: String = "",
    val activeProvider: String? = null,
    val toolActivity: String? = null,
    val error: String? = null,
    val notice: String? = null,
    /** Outcome of the last voice action, shown in Voice settings. */
    val voiceStatus: String? = null,
    val pendingConfirmation: PendingConfirmation? = null,
    val pendingPermission: PendingPermission? = null,
    /** True while the foreground listening service owns the microphone. */
    val serviceActive: Boolean = false,
    val memoryCount: Int = 0,
    val historyLoaded: Boolean = false,
)
