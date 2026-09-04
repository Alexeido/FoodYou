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

    data class Failed(
        val message: String,
        val isAuthError: Boolean,
        /** Offers a "Continue" button instead of leaving the person to retype the same ask. */
        val isTimeout: Boolean = false,
    ) : AgentEvent
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
    ): Flow<AgentEvent> = flow {
        val settings = preferences.observe().first()

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
        // Un solo modelo para toda la conversacion - nunca uno de texto y otro de vision segun el
        // turno, que es lo que antes tumbaba un mensaje sin foto justo despues de uno con foto
        // ("This model does not support image", HTTP 400). Si el modelo configurado no ve
        // imagenes, la camara ni siquiera aparece en el chat; esto es solo una red de seguridad
        // por si queda una foto en el historial de antes de que alguien desactivara la vision.
        if (settings.supportsVision) {
            messages.addAll(history)
            messages.add(userMessage)
        } else {
            history.mapTo(messages) { it.withoutImages() }
            messages.add(userMessage.withoutImages())
        }

        val usedTools = mutableListOf<String>()

        // maxIterations <= 0 significa "sin limite", elegido a proposito por el usuario en
        // Ajustes. Sigue habiendo una salida natural: el bucle termina en cuanto el modelo
        // responde con texto en vez de tool_calls, igual que con un limite normal.
        val unlimited = settings.maxIterations <= 0
        var iteration = 0
        while (unlimited || iteration < settings.maxIterations) {
            emit(AgentEvent.Working(if (iteration == 0) "Pensando" else "Consultando el diario"))

            val response =
                try {
                    client.chat(
                        ChatRequest(
                            model = settings.model,
                            messages = messages.toList(),
                            tools = toolSpecs,
                        )
                    )
                } catch (e: com.maksimowiczm.foodyou.assistant.infrastructure.openai.AssistantApiException) {
                    emit(
                        AgentEvent.Failed(
                            message = e.message ?: "Error",
                            isAuthError =
                                e.error is
                                    com.maksimowiczm.foodyou.assistant.infrastructure.openai
                                        .AssistantApiError.Unauthorized,
                            isTimeout =
                                e.error is
                                    com.maksimowiczm.foodyou.assistant.infrastructure.openai
                                        .AssistantApiError.Timeout,
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

            iteration++
        }

        emit(
            AgentEvent.Failed(
                "Me he quedado sin pasos antes de terminar. Puedes subir el limite en Ajustes o " +
                    "pedirmelo por partes.",
                false,
            )
        )
    }

    /**
     * The same message with its image parts dropped, keeping only the text the person wrote.
     *
     * A multimodal message is an array of `{type: text|image_url}` parts; anything else (a plain
     * string, a tool result) is already image-free and comes back untouched.
     */
    private fun Message.withoutImages(): Message {
        val parts = content as? kotlinx.serialization.json.JsonArray ?: return this
        val text =
            parts
                .mapNotNull { part ->
                    val obj = part as? JsonObject ?: return@mapNotNull null
                    val type = (obj["type"] as? JsonPrimitive)?.content
                    if (type != "text") null else (obj["text"] as? JsonPrimitive)?.content
                }
                .joinToString("\n")
        return copy(content = JsonPrimitive(text))
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

    /**
     * One label per tool, not per group - a plan with several steps used to sit on "Consultando el
     * diario" for four different tools in a row and looked stuck even while it was working fine.
     */
    private fun workingLabel(toolName: String): String =
        when (toolName) {
            "dailyTotals" -> "Sumando el diario"
            "diaryRange" -> "Revisando lo registrado"
            "topFoods" -> "Mirando lo que sueles comer"
            "topBrands" -> "Mirando tus marcas habituales"
            "nutrientAttribution" -> "Analizando nutrientes"
            "searchDiary" -> "Buscando en el historial"
            "mealTimingStats" -> "Mirando tus horarios"
            "listMeals" -> "Consultando tus comidas"
            "goals" -> "Consultando tus objetivos"
            "searchFood" -> "Buscando en el catalogo"
            "addEntries" -> "Anadiendo al diario"
            "updateEntry" -> "Actualizando la entrada"
            "deleteEntries" -> "Quitando del diario"
            "setEaten" -> "Marcando como comido"
            "createManualEntry" -> "Apuntando una estimacion"
            "createComposedEntry" -> "Montando el plato"
            "history" -> "Revisando los cambios"
            "undo" -> "Deshaciendo"
            "redo" -> "Rehaciendo"
            "padFromDay" -> "Copiando el dia al borrador"
            "padAdd" -> "Anadiendo al borrador"
            "padRemove" -> "Quitando del borrador"
            "padTotals" -> "Sumando el borrador"
            "padCommit" -> "Guardando el plan"
            "padDiscard" -> "Descartando el borrador"
            else -> "Trabajando"
        }

    private companion object {
        const val TAG = "AgentLoop"
    }
}
