package com.maksimowiczm.foodyou.wear

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Todo lo que el reloj guarda: con qué cuenta está emparejado, el diario de los últimos días
 * y los cambios hechos aquí que aún no han llegado al servidor.
 */
@Serializable
data class WearState(
    val serverUrl: String = DEFAULT_SERVER,
    val account: String? = null,
    val token: String? = null,
    val deviceId: String = "",
    val cursor: Long = 0,
    val docs: Map<String, Doc> = emptyMap(),
    val pending: List<Change> = emptyList(),
) {
    val paired: Boolean
        get() = token != null

    companion object {
        const val DEFAULT_SERVER = "https://sync.alexeido.com"
        /** Días de diario que se quedan en el reloj; lo más viejo no hace falta en la muñeca. */
        const val KEEP_DAYS = 7

        fun key(kind: String, id: String) = "$kind:$id"
    }
}

/** Una línea de la pantalla de hoy. */
data class EntryRow(
    val kind: String,
    val id: String,
    val name: String,
    val kcal: Int?,
    val eaten: Boolean,
)

data class MealSection(val name: String, val entries: List<EntryRow>)

data class TodayView(val meals: List<MealSection>, val kcalEaten: Int, val kcalPlanned: Int) {
    companion object {
        val Empty = TodayView(emptyList(), 0, 0)
    }
}

private fun Doc.value(name: String): JsonElement? = fields[name]?.value

private fun Doc.values(): Map<String, JsonElement> = fields.mapValues { it.value.value }

/** Lo que se enseña de un día: sus comidas en orden, cada una con sus entradas. */
fun WearState.dayView(epochDay: Long): TodayView {
    val live = docs.values.filter { !it.deleted }
    val meals =
        live.filter { it.kind == MEAL }
            .sortedBy { it.value("rank")?.jsonPrimitive?.intOrNull ?: 0 }
    val entries =
        live.filter {
            (it.kind == FOOD_ENTRY && it.value("epochDay")?.jsonPrimitive?.longOrNull == epochDay) ||
                (it.kind == MANUAL_ENTRY && it.value("dateEpochDay")?.jsonPrimitive?.longOrNull == epochDay)
        }
    var eaten = 0
    var planned = 0
    val sections =
        meals.mapNotNull { meal ->
            val rows =
                entries
                    .filter { it.value("meal")?.jsonPrimitive?.contentOrNull == meal.id }
                    .sortedBy { it.value("position")?.jsonPrimitive?.intOrNull ?: 0 }
                    .map { doc ->
                        val values = doc.values()
                        val kcal =
                            if (doc.kind == FOOD_ENTRY) Nutrition.entryKcal(values)
                            else (values["energy"] as? JsonPrimitive)?.contentOrNull?.toDoubleOrNull()
                        val isEaten = values["isEaten"]?.jsonPrimitive?.intOrNull == 1
                        val rounded = kcal?.let { Math.round(it).toInt() }
                        planned += rounded ?: 0
                        if (isEaten) eaten += rounded ?: 0
                        EntryRow(
                            kind = doc.kind,
                            id = doc.id,
                            name =
                                if (doc.kind == FOOD_ENTRY) Nutrition.foodName(values)
                                else (values["name"] as? JsonPrimitive)?.contentOrNull ?: "?",
                            kcal = rounded,
                            eaten = isEaten,
                        )
                    }
            if (rows.isEmpty()) null
            else MealSection((meal.value("name") as? JsonPrimitive)?.contentOrNull ?: "?", rows)
        }
    return TodayView(sections, eaten, planned)
}

/** Aplica lo que llega del servidor y olvida el diario de hace más de [WearState.KEEP_DAYS]. */
fun WearState.merge(incoming: List<Doc>, cursor: Long, today: Long, keepLocal: Set<String>): WearState {
    val merged = docs.toMutableMap()
    incoming.forEach { doc ->
        val key = WearState.key(doc.kind, doc.id)
        // Un cambio hecho aquí que aún no ha salido manda sobre lo que trae el servidor.
        if (key !in keepLocal) merged[key] = doc
    }
    val oldest = today - WearState.KEEP_DAYS
    val pruned =
        merged.filterValues { doc ->
            when (doc.kind) {
                FOOD_ENTRY -> (doc.value("epochDay")?.jsonPrimitive?.longOrNull ?: today) >= oldest
                MANUAL_ENTRY -> (doc.value("dateEpochDay")?.jsonPrimitive?.longOrNull ?: today) >= oldest
                else -> true
            }
        }
    return copy(docs = pruned, cursor = cursor)
}

/** Marca o desmarca una entrada aquí mismo y deja el cambio listo para enviar. */
fun WearState.toggleEaten(kind: String, id: String, now: Long): WearState {
    val key = WearState.key(kind, id)
    val doc = docs[key] ?: return this
    val current = doc.fields["isEaten"]?.value?.jsonPrimitive?.intOrNull == 1
    val clock = maxOf(now, (doc.fields["isEaten"]?.clock ?: 0) + 1)
    val value = JsonPrimitive(if (current) 0 else 1)
    val updated = doc.copy(fields = doc.fields + ("isEaten" to Field(value, clock, deviceId)))
    val change = Change(kind, id, mapOf("isEaten" to ChangeField(value, clock)))
    // Si ya había un cambio pendiente de esa entrada, este lo sustituye.
    return copy(
        docs = docs + (key to updated),
        pending = pending.filterNot { it.kind == kind && it.id == id } + change,
    )
}
