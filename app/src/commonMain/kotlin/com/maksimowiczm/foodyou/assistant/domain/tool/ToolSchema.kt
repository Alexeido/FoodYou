package com.maksimowiczm.foodyou.assistant.domain.tool

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * Small builders for the JSON Schema fragments every tool declares. Hand-writing these as raw
 * [JsonObject]s twenty times over is where typos hide, and a malformed schema fails silently: the
 * model simply stops calling the tool.
 */
object ToolSchema {

    fun obj(vararg properties: Pair<String, JsonObject>, required: List<String> = emptyList()) =
        buildJsonObject {
            put("type", "object")
            putJsonObject("properties") { properties.forEach { (k, v) -> put(k, v) } }
            putJsonArray("required") { required.forEach { add(JsonPrimitive(it)) } }
        }

    fun string(description: String, enum: List<String>? = null) = buildJsonObject {
        put("type", "string")
        put("description", description)
        if (enum != null) putJsonArray("enum") { enum.forEach { add(JsonPrimitive(it)) } }
    }

    fun integer(description: String) = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    fun number(description: String) = buildJsonObject {
        put("type", "number")
        put("description", description)
    }

    fun boolean(description: String) = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    fun arrayOf(items: JsonObject, description: String) = buildJsonObject {
        put("type", "array")
        put("description", description)
        put("items", items)
    }

    /** An ISO date argument. Spelled out because the model gets this wrong when left vague. */
    fun date(description: String) = string("$description Formato ISO yyyy-MM-dd.")

    /** No arguments at all. */
    val none: JsonObject = obj()
}
