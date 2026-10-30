package com.jarvis.assistant.brain

import com.jarvis.assistant.tools.ToolExecutor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow

data class AgentRequest(
    val systemPrompt: String,
    val history: List<LlmMessage>,
    val userMessage: String,
    val providers: List<ProviderConfig>,
    val tools: List<ToolDefinition>,
)

sealed interface AgentEvent {
    data class TextDelta(val text: String, val providerName: String) : AgentEvent
    data class ProviderFallback(val from: String, val to: String, val reason: String) : AgentEvent
    data class ToolStarted(val name: String, val arguments: String) : AgentEvent
    data class ToolFinished(val name: String, val success: Boolean, val summary: String) : AgentEvent
    data class Completed(val providerName: String) : AgentEvent
    data class Error(val message: String) : AgentEvent
}

/**
 * Runs the provider/tool loop. A turn is allowed at most eight provider rounds,
 * which is enough for a short observe-decide-act sequence without risking an
 * endless tool loop on a phone.
 */
class AgentLoop(
    private val providerRegistry: ProviderRegistry,
    private val toolExecutor: ToolExecutor,
) {

    fun run(request: AgentRequest): Flow<AgentEvent> = flow {
        if (request.providers.isEmpty()) {
            emit(
                AgentEvent.Error(
                    "No AI provider is configured. Open Settings, add an API key, and try again.",
                ),
            )
            return@flow
        }

        val messages = mutableListOf<LlmMessage>().apply {
            add(LlmMessage.system(request.systemPrompt))
            addAll(request.history.takeLast(MAX_HISTORY_MESSAGES))
            add(LlmMessage.user(request.userMessage))
        }

        var round = 0
        while (round < MAX_TOOL_ROUNDS) {
            round++
            val result = streamRound(messages, request)
            when (result) {
                is RoundResult.Failure -> {
                    emit(AgentEvent.Error(result.message))
                    return@flow
                }

                is RoundResult.Success -> {
                    if (result.toolCalls.isEmpty()) {
                        if (result.text.isBlank()) {
                            emit(AgentEvent.Error("Jarvis returned an empty reply. Please try again."))
                            return@flow
                        }
                        emit(AgentEvent.Completed(result.providerName))
                        return@flow
                    }

                    messages += LlmMessage.assistant(result.text, result.toolCalls)
                    for (call in result.toolCalls) {
                        emit(
                            AgentEvent.ToolStarted(
                                name = call.name,
                                arguments = call.argumentsJson.take(300),
                            ),
                        )
                        val toolResult = toolExecutor.execute(call.name, call.argumentsJson)
                        val summary = toolResult.content.take(240)
                        emit(
                            AgentEvent.ToolFinished(
                                name = call.name,
                                success = toolResult.success,
                                summary = summary,
                            ),
                        )
                        messages += LlmMessage.tool(call, toolResult.asJsonString().take(MAX_TOOL_RESULT_CHARS))
                    }
                }
            }
        }

        emit(
            AgentEvent.Error(
                "I stopped after eight tool rounds without reaching a final answer.",
            ),
        )
    }

    private suspend fun FlowCollector<AgentEvent>.streamRound(
        messages: List<LlmMessage>,
        request: AgentRequest,
    ): RoundResult {
        var lastError: String? = null

        request.providers.forEachIndexed { index, provider ->
            val text = StringBuilder()
            val toolCalls = mutableListOf<ToolCall>()
            var emittedAnything = false

            try {
                providerRegistry.stream(
                    ChatRequest(
                        messages = messages,
                        tools = request.tools,
                    ),
                    provider,
                ).collect { event ->
                    when (event) {
                        is LlmEvent.TextDelta -> {
                            emittedAnything = true
                            text.append(event.text)
                            emit(AgentEvent.TextDelta(event.text, provider.name))
                        }

                        is LlmEvent.ToolCallReady -> {
                            emittedAnything = true
                            toolCalls += event.call
                        }

                        LlmEvent.Done -> Unit
                        is LlmEvent.Error -> throw ProviderException(event.message)
                    }
                }

                if (text.isBlank() && toolCalls.isEmpty()) {
                    lastError = "${provider.name} returned an empty response."
                } else {
                    return RoundResult.Success(
                        providerName = provider.name,
                        text = text.toString(),
                        toolCalls = toolCalls,
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val message = e.message?.take(400) ?: "The provider request failed."
                if (emittedAnything) {
                    return RoundResult.Failure(message)
                }
                lastError = "${provider.name}: $message"
                val next = request.providers.getOrNull(index + 1)
                if (next != null) {
                    emit(
                        AgentEvent.ProviderFallback(
                            from = provider.name,
                            to = next.name,
                            reason = message,
                        ),
                    )
                }
            }
        }

        return RoundResult.Failure(
            lastError ?: "No AI provider is available. Check your keys in Settings.",
        )
    }

    private sealed interface RoundResult {
        data class Success(
            val providerName: String,
            val text: String,
            val toolCalls: List<ToolCall>,
        ) : RoundResult

        data class Failure(val message: String) : RoundResult
    }

    private companion object {
        const val MAX_TOOL_ROUNDS = 8
        const val MAX_HISTORY_MESSAGES = 40
        const val MAX_TOOL_RESULT_CHARS = 4_000
    }
}
