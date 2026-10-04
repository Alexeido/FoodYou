package com.maksimowiczm.foodyou.assistant.domain.tool

import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/** Raised when the model calls a tool with arguments that cannot be used. */
class ToolArgumentException(message: String) : IllegalArgumentException(message)

/**
 * Reading arguments out of what the model sent.
 *
 * Models send numbers as strings, strings as numbers, and omit optional fields at random, so every
 * accessor here is forgiving about the type and strict about the meaning. When something really is
 * unusable it throws [ToolArgumentException], which the agent loop turns into a tool error the model
 * can read and correct - that is a far better outcome than a silent default.
 */
object Args {

    fun JsonObject.stringOrNull(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.takeIf { it.isNotBlank() }

    fun JsonObject.string(key: String): String =
        stringOrNull(key) ?: throw ToolArgumentException("Falta el argumento obligatorio '$key'")

    fun JsonObject.longOrNull(key: String): Long? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.longOrNull ?: primitive.content.toLongOrNull()
    }

    fun JsonObject.long(key: String): Long =
        longOrNull(key) ?: throw ToolArgumentException("Falta el argumento numerico '$key'")

    fun JsonObject.intOrNull(key: String): Int? = longOrNull(key)?.toInt()

    fun JsonObject.doubleOrNull(key: String): Double? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.doubleOrNull ?: primitive.content.replace(',', '.').toDoubleOrNull()
    }

    fun JsonObject.double(key: String): Double =
        doubleOrNull(key) ?: throw ToolArgumentException("Falta el argumento numerico '$key'")

    fun JsonObject.booleanOrNull(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.booleanOrNull ?: primitive.content.lowercase().toBooleanStrictOrNull()
    }

    fun JsonObject.dateOrNull(key: String): LocalDate? {
        val raw = stringOrNull(key) ?: return null
        return runCatching { LocalDate.parse(raw.take(10)) }.getOrNull()
    }

    fun JsonObject.date(key: String): LocalDate =
        dateOrNull(key)
            ?: throw ToolArgumentException("'$key' debe ser una fecha ISO yyyy-MM-dd")

    fun JsonObject.objects(key: String): List<JsonObject> =
        (this[key] as? JsonArray)?.mapNotNull { it as? JsonObject } ?: emptyList()

    /** Non-blank strings of an array argument; a model that sends a bare string gets a list of one. */
    fun JsonObject.strings(key: String): List<String> =
        when (val value = this[key]) {
            // contentOrNull y no content: JsonNull tambien es un JsonPrimitive, y su content es
            // la palabra "null" - que acabaria buscandose como si fuera un alimento.
            is JsonArray -> value.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            is JsonPrimitive -> listOfNotNull(value.contentOrNull?.trim())
            else -> emptyList()
        }.filter { it.isNotBlank() }

    fun JsonObject.longs(key: String): List<Long> =
        (this[key] as? JsonArray)?.mapNotNull { element ->
            (element as? JsonPrimitive)?.let { it.longOrNull ?: it.content.toLongOrNull() }
        } ?: emptyList()
}

/** A short, uniform failure the model can act on instead of guessing. */
fun toolError(message: String): JsonElement = buildJsonObject {
    put("ok", false)
    put("error", message)
}

/** Numbers the model reads back are rounded: it does not need six decimals, and they cost tokens. */
fun Double.round1(): Double = kotlin.math.round(this * 10.0) / 10.0
