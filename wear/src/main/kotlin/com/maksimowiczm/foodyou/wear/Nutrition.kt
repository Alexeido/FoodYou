package com.maksimowiczm.foodyou.wear

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Las kcal de una entrada, como las calcula la app: el producto guarda sus valores por 100 g;
 * una receta suma sus ingredientes y se reparte según la parte que se come.
 */
object Nutrition {
    private const val GRAM = 0
    private const val PACKAGE = 1
    private const val SERVING = 2
    private const val MILLILITER = 3
    private const val OUNCE = 4
    private const val FLUID_OUNCE = 5

    private fun JsonElement?.number(): Double? = (this as? JsonPrimitive)?.takeIf { !it.isString }?.doubleOrNull

    /** (peso de referencia, kcal en ese peso): 100 g para un producto, el plato para una receta. */
    fun totals(food: JsonObject): Pair<Double, Double> {
        (food["product"] as? JsonObject)?.let { return 100.0 to (it["energy"].number() ?: 0.0) }
        val recipe = food["recipe"] as? JsonObject ?: return 0.0 to 0.0
        var weight = 0.0
        var kcal = 0.0
        (recipe["ingredients"] as? JsonArray)?.forEach { element ->
            val ingredient = element as? JsonObject ?: return@forEach
            val sub = ingredient["food"] as? JsonObject ?: return@forEach
            val grams =
                weightOf(
                    sub,
                    ingredient["measurement"]?.jsonPrimitive?.intOrNull ?: GRAM,
                    ingredient["quantity"].number() ?: 0.0,
                ) ?: return@forEach
            val (subWeight, subKcal) = totals(sub)
            if (subWeight > 0) {
                weight += grams
                kcal += subKcal * grams / subWeight
            }
        }
        return weight to kcal
    }

    fun weightOf(food: JsonObject, measurement: Int, quantity: Double): Double? =
        when (measurement) {
            GRAM,
            MILLILITER -> quantity
            OUNCE -> quantity * 28.3495
            FLUID_OUNCE -> quantity * 29.5735
            else -> {
                val product = food["product"] as? JsonObject
                val unit =
                    if (product != null) {
                        product[if (measurement == SERVING) "servingWeight" else "packageWeight"].number()
                    } else {
                        val (total, _) = totals(food)
                        val servings =
                            ((food["recipe"] as? JsonObject)?.get("servings")).number() ?: 1.0
                        if (measurement == SERVING) total / servings else total
                    }
                unit?.let { it * quantity }
            }
        }

    /** kcal de una entrada `food_entry` (sus campos), o null si no se puede saber. */
    fun entryKcal(fields: Map<String, JsonElement>): Double? {
        val food = fields["food"] as? JsonObject ?: return null
        val grams =
            weightOf(
                food,
                fields["measurement"]?.jsonPrimitive?.intOrNull ?: GRAM,
                fields["quantity"].number() ?: 0.0,
            ) ?: return null
        val (weight, kcal) = totals(food)
        return if (weight > 0) kcal * grams / weight else null
    }

    fun foodName(fields: Map<String, JsonElement>): String {
        val food = fields["food"] as? JsonObject ?: return "?"
        val inner = (food["product"] ?: food["recipe"]) as? JsonObject ?: return "?"
        return (inner["name"] as? JsonPrimitive)?.content ?: "?"
    }
}
