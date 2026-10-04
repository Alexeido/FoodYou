package com.maksimowiczm.foodyou.assistant.domain.tool

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

/**
 * A capability exposed to the language model as a callable function.
 *
 * Declared as a contract - name, JSON Schema, and whether it mutates - rather than as an ad-hoc
 * Kotlin call wired into the chat screen. Three things follow from that: the schema sent to the
 * model is generated from this declaration, the journal knows which calls to record without anyone
 * remembering to, and an assistant living outside the app could call the same tools unchanged.
 */
interface AssistantTool {

    /** Stable identifier the model calls. Must be unique across the registry. */
    val name: String

    /** What the tool does, in the model's terms. This is prompt text: it decides when it is used. */
    val description: String

    /** JSON Schema describing [call]'s arguments. Sent to the model verbatim. */
    val parameters: JsonObject

    /**
     * True when invoking this changes the user's data. Mutating tools are recorded in the journal so
     * the change can be undone; read-only tools are not.
     */
    val mutates: Boolean
        get() = false

    /**
     * True when this tool can run at the same time as other calls that also say so - which is what
     * lets "search the bun, the patty and the cheese" cost one wait instead of three.
     *
     * Opt-in, and only for pure reads. [mutates] = false is not enough: the draft tools write nothing
     * to the diary but do change the in-memory pad, and two of those at once would race.
     */
    val runsConcurrently: Boolean
        get() = false

    /**
     * Runs the tool. The returned element is serialized straight back to the model, so keep it small
     * and self-describing - it is charged as input tokens on every following turn.
     */
    suspend fun call(arguments: JsonObject): JsonElement
}
