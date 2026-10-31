package com.jarvis.assistant.tools

/**
 * What the memory tools need. Implemented by the app runtime so tools never
 * touch Room directly.
 */
interface MemoryBridge {
    /** Stores a memory and describes the outcome for the tool result. */
    suspend fun remember(text: String, type: String, importance: Int): String

    /** Returns memorable lines matching [query]. */
    suspend fun search(query: String, limit: Int): List<String>

    /** Deletes matching memories and reports how many were removed. */
    suspend fun forget(query: String): String
}

/** Result of one screen action, with a message fit to be spoken. */
data class ScreenResult(
    val success: Boolean,
    val message: String,
)

/**
 * The Tier 3 control surface exposed to tools. Implemented by the app runtime,
 * which owns the accessibility service, the safety blocklist and the action log.
 */
interface ScreenAccess {
    /** True when the accessibility service is connected and screen control is on. */
    fun ready(): Boolean

    /** The foreground app's label when it is on the blocklist, otherwise null. */
    fun blockedApp(): String?

    suspend fun readScreen(): ScreenResult

    suspend fun tap(text: String): ScreenResult

    suspend fun type(text: String): ScreenResult

    suspend fun scroll(forward: Boolean): ScreenResult

    suspend fun press(key: String): ScreenResult
}
