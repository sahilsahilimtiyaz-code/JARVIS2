package com.jarvis.assistant.tools

import android.content.Context
import com.jarvis.assistant.brain.ToolDefinition
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class ToolContext(val appContext: Context)

data class ToolResult(
    val success: Boolean,
    val content: String,
) {
    fun asJsonString(): String = buildJsonObject {
        put("ok", success)
        put("result", content)
    }.toString()
}

interface Tool {
    val definition: ToolDefinition
    suspend fun execute(arguments: JsonObject, context: ToolContext): ToolResult
}

interface ToolExecutor {
    fun definitions(): List<ToolDefinition>
    suspend fun execute(name: String, argumentsJson: String): ToolResult
}

class ToolRegistry(
    context: Context,
    private val tools: List<Tool> = listOf(CurrentTimeTool()),
) : ToolExecutor {
    private val toolContext = ToolContext(context.applicationContext)

    override fun definitions(): List<ToolDefinition> = tools.map { it.definition }

    override suspend fun execute(name: String, argumentsJson: String): ToolResult {
        val tool = tools.firstOrNull { it.definition.name == name }
            ?: return ToolResult(false, "Unknown tool: $name")
        val arguments = parseArguments(argumentsJson)
            ?: return ToolResult(false, "The tool arguments were not valid JSON.")
        return try {
            tool.execute(arguments, toolContext)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            ToolResult(false, "Tool failed: ${e.message?.take(300) ?: "unknown error"}")
        }
    }

    private fun parseArguments(raw: String): JsonObject? {
        if (raw.isBlank()) return JsonObject(emptyMap())
        return com.jarvis.assistant.brain.parseJsonObject(raw)
    }
}
