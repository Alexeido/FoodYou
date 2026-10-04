package com.maksimowiczm.foodyou.assistant.domain

import com.maksimowiczm.foodyou.assistant.domain.tool.ToolArgumentException
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolRegistry
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.ToolCall
import com.maksimowiczm.foodyou.common.log.Logger
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Runs the tool calls the model asked for in one response.
 *
 * The time an assistant turn takes is almost all round trips to the model; a local search is
 * milliseconds. So the win is not faster tools but fewer waits: when the model asks for several
 * reads at once they run side by side, and a burger's bun, patty and cheese cost one wait, not
 * three. Kept apart from the agent loop so that rule can be tested without a network.
 */
class ToolCallRunner(private val registry: ToolRegistry, private val logger: Logger) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * True when every call in [calls] may run at the same time as the others.
     *
     * All or nothing: one write among them and everything goes in order, because a later call may
     * depend on it and the journal has to record changes in the order they happened.
     */
    fun canRunTogether(calls: List<ToolCall>): Boolean =
        calls.size > 1 && calls.all { registry.find(it.function.name)?.runsConcurrently == true }

    /** Runs every call, side by side when [canRunTogether] allows. Results keep the calls' order. */
    suspend fun runAll(calls: List<ToolCall>): List<String> =
        if (canRunTogether(calls)) {
            coroutineScope { calls.map { call -> async { run(call) } }.awaitAll() }
        } else {
            calls.map { run(it) }
        }

    /**
     * Runs one call and returns what goes back to the model. Never throws for a tool's own failure:
     * the model gets the error as text, can read it and correct itself, which is far better than a
     * broken conversation. Cancellation is the exception - leaving the chat is not a tool failure.
     */
    suspend fun run(call: ToolCall): String {
        val tool =
            registry.find(call.function.name)
                ?: return errorPayload("No existe la herramienta ${call.function.name}.")
        return try {
            tool.call(parseArguments(call.function.arguments)).toString()
        } catch (e: ToolArgumentException) {
            errorPayload(e.message ?: "Argumentos invalidos")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logger.e(TAG, e) { "Tool ${call.function.name} failed" }
            errorPayload(e.message ?: "Error ejecutando la herramienta")
        }
    }

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

    private companion object {
        const val TAG = "AgentLoop"
    }
}
