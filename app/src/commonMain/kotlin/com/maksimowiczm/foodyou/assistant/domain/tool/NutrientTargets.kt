package com.maksimowiczm.foodyou.assistant.domain.tool

import com.maksimowiczm.foodyou.assistant.domain.query.NutrientSelector
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

/**
 * Grams of a food needed to reach [targetAmount] of [nutrient], from its facts per 100 g.
 *
 * This is the "I need 20 g of protein, tell me how much chicken that is" flow: a person doing this
 * by hand picks the food, then works backwards from a nutrient target instead of guessing a weight
 * and checking the result. `addEntries` and `padAdd` offer it as an alternative to a plain amount.
 *
 * Null when the food has no density for that nutrient - asking "how much of this has 20 g of
 * protein" makes no sense for something with zero protein per 100 g.
 */
fun NutritionFacts.gramsFor(nutrient: NutrientSelector, targetAmount: Double): Double? {
    val per100 = nutrient.of(this)
    if (per100 <= 0.0) return null
    return 100.0 * targetAmount / per100
}

/** Shared by every tool that lets an amount be given as a nutrient target instead of a weight. */
val targetNutrientParam =
    "targetNutrient" to
        ToolSchema.string(
            "En vez de una cantidad fija, calcula los gramos necesarios para llegar a esta " +
                "cantidad de un nutriente. Usalo junto con targetAmount cuando la persona pida " +
                "algo como 'necesito 20 g de proteina, ponme el pollo que corresponda' - no " +
                "calcules tu los gramos a mano, deja que lo haga esto.",
            enum = NutrientSelector.wireNames,
        )

val targetAmountParam =
    "targetAmount" to
        ToolSchema.number(
            "Cantidad de targetNutrient que se busca (kcal si el nutriente es energy, gramos " +
                "para el resto). Se ignora si tambien pones amount."
        )
