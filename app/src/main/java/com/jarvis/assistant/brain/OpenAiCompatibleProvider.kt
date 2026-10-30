package com.jarvis.assistant.brain

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * OpenAI Chat Completions adapter. The configurable base URL covers OpenRouter,
 * OpenAI, Groq, Together, a local Ollama server and any compatible endpoint.
 */
class OpenAiCompatibleProvider(private val client: OkHttpClient) : LlmProvider {

    override fun stream(request: ChatRequest, config: ProviderConfig): Flow<LlmEvent> = flow {
        val payload = buildJsonObject {
            put("model", config.model)
            put("stream", true)
            put("temperature", request.temperature)
            put("max_tokens", request.maxTokens)
            put("messages", openAiMessages(request.messages))
            if (request.tools.isNotEmpty()) {
                put("tools", openAiTools(request.tools))
                put("tool_choice", "auto")
            }
        }

        val httpRequest = Request.Builder()
            .url(chatEndpoint(config.baseUrl))
            .header("Authorization", "Bearer ${config.apiKey}")
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .header("User-Agent", USER_AGENT)
            .apply {
                if (config.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                    header("X-Title", "Jarvis")
                }
            }
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()

        val call = client.newCall(httpRequest)
        val cancelHandle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }

        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    emit(LlmEvent.Error(providerErrorMessage(response.code, response.body.string())))
                    return@flow
                }

                val responseBody = response.body

                val contentType = response.header("Content-Type").orEmpty()
                if (!contentType.contains("text/event-stream", ignoreCase = true)) {
                    parseFullResponse(responseBody.string())
                    return@flow
                }

                val reader = SseReader(responseBody.charStream().buffered())
                val toolCalls = sortedMapOf<Int, ToolCallAccumulator>()

                loop@ while (true) {
                    val event = reader.next() ?: break@loop
                    val data = event.data.trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break@loop

                    val root = parseJsonObject(data) ?: continue
                    if (root["error"] != null) {
                        emit(LlmEvent.Error(extractProviderError(data) ?: "The provider returned an error."))
                        return@flow
                    }

                    val choices = root.arrayOrNull("choices")?.objects().orEmpty()
                    for (choice in choices) {
                        val delta = choice.objOrNull("delta")
                        if (delta != null) {
                            delta.stringOrNull("content")
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { emit(LlmEvent.TextDelta(it)) }

                            delta.arrayOrNull("tool_calls")
                                ?.objects()
                                .orEmpty()
                                .forEach { part ->
                                    accumulateToolCall(part, toolCalls)
                                }
                        }

                        // Some compatible servers send a complete message instead
                        // of a delta, especially on the final chunk.
                        choice.objOrNull("message")?.let { message ->
                            message.stringOrNull("content")
                                ?.takeIf { it.isNotEmpty() }
                                ?.let { emit(LlmEvent.TextDelta(it)) }
                            message.arrayOrNull("tool_calls")
                                ?.objects()
                                .orEmpty()
                                .forEach { part ->
                                    accumulateCompleteToolCall(part, toolCalls)
                                }
                        }
                    }
                }

                emitToolCalls(toolCalls)
                emit(LlmEvent.Done)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            emit(LlmEvent.Error(networkErrorMessage(e)))
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            emit(LlmEvent.Error(e.message?.take(400) ?: "The provider request failed."))
        } finally {
            cancelHandle.dispose()
            call.cancel()
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun FlowCollector<LlmEvent>.parseFullResponse(body: String) {
        val root = parseJsonObject(body)
        if (root == null) {
            emit(LlmEvent.Error("The provider returned a response that was not valid JSON."))
            return
        }
        if (root["error"] != null) {
            emit(LlmEvent.Error(extractProviderError(body) ?: "The provider returned an error."))
            return
        }
        val choice = root.arrayOrNull("choices")?.objects()?.firstOrNull()
        val message = choice?.objOrNull("message")
        if (message == null) {
            emit(LlmEvent.Error("The provider returned no message."))
            return
        }
        message.stringOrNull("content")
            ?.takeIf { it.isNotEmpty() }
            ?.let { emit(LlmEvent.TextDelta(it)) }
        message.arrayOrNull("tool_calls")
            ?.objects()
            .orEmpty()
            .forEach { part ->
                val function = part.objOrNull("function") ?: return@forEach
                val name = function.stringOrNull("name")?.takeIf { it.isNotBlank() } ?: return@forEach
                val arguments = function["arguments"]
                    ?.let { arg ->
                        if (arg is JsonPrimitive) arg.contentOrNull else arg.toString()
                    }
                    ?.takeIf { it.isNotBlank() }
                    ?: "{}"
                emit(
                    LlmEvent.ToolCallReady(
                        ToolCall(
                            id = part.stringOrNull("id")?.takeIf { it.isNotBlank() } ?: "call_${name}",
                            name = name,
                            argumentsJson = arguments,
                        ),
                    ),
                )
            }
        emit(LlmEvent.Done)
    }

    private fun accumulateToolCall(
        part: kotlinx.serialization.json.JsonObject,
        accumulator: MutableMap<Int, ToolCallAccumulator>,
    ) {
        val index = part.intOrNull("index") ?: accumulator.size
        val call = accumulator.getOrPut(index) { ToolCallAccumulator(index) }
        part.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { call.id = it }
        val function = part.objOrNull("function") ?: return
        function.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let { call.name = it }
        function["arguments"]?.let { arg ->
            if (arg is JsonPrimitive) {
                call.arguments.append(arg.contentOrNull.orEmpty())
            } else {
                call.arguments.append(arg.toString())
            }
        }
    }

    private fun accumulateCompleteToolCall(
        part: kotlinx.serialization.json.JsonObject,
        accumulator: MutableMap<Int, ToolCallAccumulator>,
    ) {
        val index = part.intOrNull("index") ?: accumulator.size
        val call = accumulator.getOrPut(index) { ToolCallAccumulator(index) }
        part.stringOrNull("id")?.takeIf { it.isNotBlank() }?.let { call.id = it }
        val function = part.objOrNull("function") ?: return
        function.stringOrNull("name")?.takeIf { it.isNotBlank() }?.let { call.name = it }
        function["arguments"]?.let { arg ->
            val text = if (arg is JsonPrimitive) arg.contentOrNull.orEmpty() else arg.toString()
            call.arguments.append(text)
        }
    }

    private suspend fun FlowCollector<LlmEvent>.emitToolCalls(
        accumulator: Map<Int, ToolCallAccumulator>,
    ) {
        accumulator.values.forEach { call ->
            if (call.name.isNotBlank()) {
                emit(
                    LlmEvent.ToolCallReady(
                        ToolCall(
                            id = call.id.ifBlank { "call_${call.index}" },
                            name = call.name,
                            argumentsJson = call.arguments.toString().ifBlank { "{}" },
                        ),
                    ),
                )
            }
        }
    }

    private fun openAiMessages(messages: List<LlmMessage>) = buildJsonArray {
        messages.forEach { message ->
            add(
                buildJsonObject {
                    when (message.role) {
                        LlmRole.SYSTEM -> {
                            put("role", "system")
                            put("content", message.content)
                        }
                        LlmRole.USER -> {
                            put("role", "user")
                            put("content", message.content)
                        }
                        LlmRole.ASSISTANT -> {
                            put("role", "assistant")
                            put("content", message.content)
                            if (message.toolCalls.isNotEmpty()) {
                                put(
                                    "tool_calls",
                                    buildJsonArray {
                                        message.toolCalls.forEach { call ->
                                            add(
                                                buildJsonObject {
                                                    put("id", call.id)
                                                    put("type", "function")
                                                    put(
                                                        "function",
                                                        buildJsonObject {
                                                            put("name", call.name)
                                                            put("arguments", call.argumentsJson.ifBlank { "{}" })
                                                        },
                                                    )
                                                },
                                            )
                                        }
                                    },
                                )
                            }
                        }
                        LlmRole.TOOL -> {
                            put("role", "tool")
                            put("tool_call_id", message.toolCallId.orEmpty())
                            put("content", message.content)
                        }
                    }
                },
            )
        }
    }

    private fun openAiTools(tools: List<ToolDefinition>) = buildJsonArray {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", "function")
                    put(
                        "function",
                        buildJsonObject {
                            put("name", tool.name)
                            put("description", tool.description)
                            put("parameters", tool.parameters)
                        },
                    )
                },
            )
        }
    }

    private fun chatEndpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private class ToolCallAccumulator(val index: Int) {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        const val USER_AGENT = "Jarvis/0.2 (Android)"
    }
}
