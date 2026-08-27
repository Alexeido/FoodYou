package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.assistant.domain.tool.ToolArgumentException
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolRegistry
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.ChatRequest
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.FunctionSpec
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.Message
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.OpenAiCompatibleClient
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.ToolSpec
import com.maksimowiczm.foodyou.common.log.Logger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/** What the chat screen shows while and after the loop runs. */
sealed interface AgentEvent {
    /** Ephemeral: the status line that disappears when the turn ends. */
    data class Working(val label: String) : AgentEvent

    /** A tool ran. Kept so the collapsed "what it did" line can be built. */
    data class ToolRan(val name: String, val mutating: Boolean) : AgentEvent

    /** The answer. */
    data class Answer(val text: String, val usedTools: List<String>) : AgentEvent

    data class Failed(val message: String, val isAuthError: Boolean) : AgentEvent
}

/**
 * Send, run whatever tools come back, send the results, repeat until the model answers with text.
 *
 * Two things here are not obvious. The tool schemas are serialized once, in the registry's stable
 * alphabetical order, and sit at the front of every request: DeepSeek only serves them from its
 * prompt cache when that prefix is byte-identical, and a six-call conversation otherwise pays for
 * them six times. And the loop has a hard ceiling, because a model that has lost the plot will
 * happily keep calling tools until the credit runs out.
 */
class AgentLoop(
    private val client: OpenAiCompatibleClient,
    private val registry: ToolRegistry,
    private val promptBuilder: SystemPromptBuilder,
    private val preferences: AssistantPreferences,
    private val logger: Logger,
) {

    private val json = Json { ignoreUnknownKeys = true }

    fun run(
        history: List<Message>,
        userMessage: Message,
        today: LocalDate,
        useVisionModel: Boolean = false,
    ): Flow<AgentEvent> = flow {
        val settings = preferences.observe().first()
        val model =
            if (useVisionModel && settings.supportsVision) settings.visionModel else settings.model

        val toolSpecs =
            registry.tools.map { tool ->
                ToolSpec(
                    function =
                        FunctionSpec(
                            name = tool.name,
                            description = tool.description,
                            parameters = tool.parameters,
                        )
                )
            }

        val messages =
            mutableListOf(
                Message(role = "system", content = JsonPrimitive(promptBuilder.build(today)))
            )
        messages.addAll(history)
        messages.add(userMessage)

        val usedTools = mutableListOf<String>()

        repeat(settings.maxIterations) { iteration ->
            emit(AgentEvent.Working(if (iteration == 0) "Pensando" else "Consultando el diario"))

            val response =
                try {
                    client.chat(
                        ChatRequest(model = model, messages = messages.toList(), tools = toolSpecs)
                    )
                } catch (e: com.maksimowiczm.foodyou.assistant.infrastructure.openai.AssistantApiException) {
                    emit(
                        AgentEvent.Failed(
                            message = e.message ?: "Error",
                            isAuthError =
                                e.error is
                                    com.maksimowiczm.foodyou.assistant.infrastructure.openai
                                        .AssistantApiError.Unauthorized,
                        )
                    )
                    return@flow
                }

            val choice = response.choices.firstOrNull()
            if (choice == null) {
                emit(AgentEvent.Failed("El servidor no devolvio ninguna respuesta.", false))
                return@flow
            }

            val toolCalls = choice.message.toolCalls
            if (toolCalls.isNullOrEmpty()) {
                val text =
                    (choice.message.content as? JsonPrimitive)?.content
                        ?: choice.message.content?.toString().orEmpty()
                emit(AgentEvent.Answer(text, usedTools.toList()))
                return@flow
            }

            // El mensaje del modelo tiene que volver al historial tal cual, con sus tool_calls:
            // sin el, la API rechaza los mensajes de resultado que vienen despues.
            messages.add(choice.message)

            toolCalls.forEach { call ->
                val tool = registry.find(call.function.name)
                usedTools.add(call.function.name)
                emit(AgentEvent.ToolRan(call.function.name, tool?.mutates == true))
                emit(AgentEvent.Working(workingLabel(call.function.name)))

                val result =
                    if (tool == null) {
                        errorPayload("No existe la herramienta ${call.function.name}.")
                    } else {
                        try {
                            val args = parseArguments(call.function.arguments)
                            tool.call(args).toString()
                        } catch (e: ToolArgumentException) {
                            // Devuelto al modelo como texto: puede leerlo y corregirse, que es
                            // mucho mejor que reventar la conversacion.
                            errorPayload(e.message ?: "Argumentos invalidos")
                        } catch (e: Exception) {
                            logger.e(TAG, e) { "Tool ${call.function.name} failed" }
                            errorPayload(e.message ?: "Error ejecutando la herramienta")
                        }
                    }

                messages.add(
                    Message(
                        role = "tool",
                        content = JsonPrimitive(result),
                        toolCallId = call.id,
                        name = call.function.name,
                    )
                )
            }
        }

        emit(
            AgentEvent.Failed(
                "Me he quedado sin pasos antes de terminar. Puedes subir el limite en Ajustes o " +
                    "pedirmelo por partes.",
                false,
            )
        )
    }

    /** Models sometimes emit arguments that are not valid JSON, or an empty string for none. */
    private fun parseArguments(raw: String): JsonObject =
        if (raw.isBlank()) buildJsonObject {}
        else runCatching { json.parseToJsonElement(raw).jsonObject }.getOrElse { buildJsonObject {} }

    /**
     * Serialized properly rather than concatenated: a message carrying a quote or a newline - and
     * several of the tool errors quote a tool name - would otherwise produce malformed JSON, and
     * the model would receive garbage instead of a correction it can act on.
     */
    private fun errorPayload(message: String): String =
        json.encodeToString(
            JsonObject.serializer(),
            buildJsonObject {
                put("ok", false)
                put("error", message)
            },
        )

    private fun workingLabel(toolName: String): String =
        when (toolName) {
            "searchFood" -> "Buscando alimentos"
            "addEntries", "padCommit" -> "Anadiendo al diario"
            "deleteEntries" -> "Quitando del diario"
            "dailyTotals", "diaryRange", "topFoods", "topBrands" -> "Consultando el diario"
            "padTotals", "padAdd", "padRemove", "padFromDay" -> "Calculando"
            "undo", "redo" -> "Deshaciendo"
            else -> "Trabajando"
        }

    private companion object {
        const val TAG = "AgentLoop"
    }
}
