package com.jarvis.assistant.tools

import android.content.Context
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

@Serializable
data class ActionLogEntry(
    val id: String,
    val toolName: String,
    val label: String,
    val detail: String,
    val success: Boolean,
    val timestamp: Long,
)

/**
 * The local action log required by the safety rules: what Jarvis did, when and
 * with which tool. Stored as one small JSON file in the app's private storage so
 * it survives restarts without needing the Room database (that arrives with the
 * memory phase).
 */
class ActionLogStore(context: Context) {

    private val file = File(context.applicationContext.filesDir, "action_log.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(ActionLogEntry.serializer())

    private val _entries = MutableStateFlow<List<ActionLogEntry>>(emptyList())
    val entries: StateFlow<List<ActionLogEntry>> = _entries.asStateFlow()

    suspend fun load() {
        val loaded = withContext(Dispatchers.IO) {
            if (!file.exists()) return@withContext emptyList()
            runCatching { json.decodeFromString(serializer, file.readText()) }
                .getOrDefault(emptyList())
        }
        _entries.value = loaded.sortedByDescending { it.timestamp }
    }

    suspend fun record(entry: ActionLogEntry) {
        val updated = (listOf(entry) + _entries.value).take(MAX_ENTRIES)
        _entries.value = updated
        persist(updated)
    }

    suspend fun clear() {
        _entries.value = emptyList()
        persist(emptyList())
    }

    private suspend fun persist(entries: List<ActionLogEntry>) {
        withContext(Dispatchers.IO) {
            runCatching {
                file.parentFile?.mkdirs()
                file.writeText(json.encodeToString(serializer, entries))
            }
        }
    }

    private companion object {
        const val MAX_ENTRIES = 200
    }
}
