package com.maksimowiczm.foodyou.wear

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

class WearStateTest {
    private fun f(value: JsonElement, clock: Long = 1) = Field(value, clock, "phone")

    private fun meal(id: String, name: String, rank: Int) =
        Doc(MEAL, id, fields = mapOf("name" to f(JsonPrimitive(name)), "rank" to f(JsonPrimitive(rank))))

    private val oats =
        Json.parseToJsonElement("""{"product":{"name":"Avena","energy":389.0,"servingWeight":40.0}}""")

    private fun entry(id: String, day: Long, eaten: Int, measurement: Int = 0, quantity: Double = 50.0) =
        Doc(
            FOOD_ENTRY,
            id,
            fields =
                mapOf(
                    "meal" to f(JsonPrimitive("m1")),
                    "epochDay" to f(JsonPrimitive(day)),
                    "measurement" to f(JsonPrimitive(measurement)),
                    "quantity" to f(JsonPrimitive(quantity)),
                    "isEaten" to f(JsonPrimitive(eaten)),
                    "food" to f(oats),
                ),
        )

    @Test
    fun productKcalByGramsAndServing() {
        val grams = entry("e", 0, 0).fields.mapValues { it.value.value }
        assertEquals(194.5, Nutrition.entryKcal(grams)!!, 0.01)
        val serving = entry("e", 0, 0, measurement = 2, quantity = 1.0).fields.mapValues { it.value.value }
        assertEquals(155.6, Nutrition.entryKcal(serving)!!, 0.01)
    }

    @Test
    fun recipeSplitsByServing() {
        val recipe =
            Json.parseToJsonElement(
                """{"recipe":{"name":"Gachas","servings":2,"ingredients":[
                    {"measurement":0,"quantity":100,"food":{"product":{"name":"Avena","energy":389}}},
                    {"measurement":3,"quantity":200,"food":{"product":{"name":"Leche","energy":50}}}]}}"""
            )
        val fields = mapOf("food" to recipe, "measurement" to JsonPrimitive(2), "quantity" to JsonPrimitive(1))
        // (389 + 100) / 2
        assertEquals(244.5, Nutrition.entryKcal(fields)!!, 0.01)
        assertEquals("Gachas", Nutrition.foodName(fields))
    }

    @Test
    fun dayViewTotalsAndToggle() {
        val state =
            WearState(token = "t", deviceId = "watch")
                .merge(listOf(meal("m1", "Desayuno", 0), entry("a", 10, 1), entry("b", 10, 0), entry("c", 9, 1)), 5, 10, emptySet())
        val view = state.dayView(10)
        assertEquals(1, view.meals.size)
        assertEquals(2, view.meals[0].entries.size)
        assertEquals(195, view.kcalEaten)
        assertEquals(390, view.kcalPlanned)

        val toggled = state.toggleEaten(FOOD_ENTRY, "b", now = 1000)
        assertEquals(390, toggled.dayView(10).kcalEaten)
        assertEquals(1, toggled.pending.size)
        // Desmarcar enseguida deja un único cambio pendiente, el último.
        val back = toggled.toggleEaten(FOOD_ENTRY, "b", now = 1000)
        assertEquals(1, back.pending.size)
        assertEquals(JsonPrimitive(0), back.pending[0].fields["isEaten"]!!.value)
        assertEquals(1001, back.pending[0].fields["isEaten"]!!.clock)
    }

    @Test
    fun pendingChangeWinsOverServerAndOldDaysArePruned() {
        val state =
            WearState(token = "t", deviceId = "watch")
                .merge(listOf(meal("m1", "Desayuno", 0), entry("a", 10, 0)), 1, 10, emptySet())
                .toggleEaten(FOOD_ENTRY, "a", now = 50)
        val merged = state.merge(listOf(entry("a", 10, 0), entry("old", 2, 0)), 2, 10, setOf("food_entry:a"))
        assertEquals(195, merged.dayView(10).kcalEaten)
        assertEquals(null, merged.docs["food_entry:old"])
        assertEquals(2, merged.cursor)
    }
}
