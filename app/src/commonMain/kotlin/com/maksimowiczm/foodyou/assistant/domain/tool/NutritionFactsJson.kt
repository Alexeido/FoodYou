package com.maksimowiczm.foodyou.assistant.domain.tool

import com.maksimowiczm.foodyou.assistant.domain.query.DetailLevel
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/** Shared across every tool that reports [NutritionFacts], so `basic`/`extended`/`full` mean the same thing everywhere. */
val detailLevelParam =
    "detailLevel" to
        ToolSchema.string(
            "Cuanto detalle nutricional devolver. 'basic' (por defecto) da kcal, proteinas, " +
                "carbohidratos y grasas - es lo que hace falta para casi cualquier pregunta. " +
                "'extended' anade grasas saturadas, azucares, fibra, sal, colesterol y cafeina - " +
                "usalo solo si preguntan por alguno de esos. 'full' anade tambien vitaminas y " +
                "minerales - usalo solo si preguntan por un micronutriente concreto.",
            enum = DetailLevel.wireNames,
        )

/**
 * Macros always, and grows with [level].
 *
 * `extended` and `full` are additive, not alternative views: a question about saturated fat still
 * wants to see the calories next to it, so nothing already written by a lower level is ever dropped.
 */
fun JsonObjectBuilder.putNutritionFacts(facts: NutritionFacts, level: DetailLevel = DetailLevel.Basic) {
    put("kcal", (facts.energy.value ?: 0.0).round1())
    put("proteins", (facts.proteins.value ?: 0.0).round1())
    put("carbohydrates", (facts.carbohydrates.value ?: 0.0).round1())
    put("fats", (facts.fats.value ?: 0.0).round1())

    if (level == DetailLevel.Basic) return

    put("saturatedFats", (facts.saturatedFats.value ?: 0.0).round1())
    put("sugars", (facts.sugars.value ?: 0.0).round1())
    put("addedSugars", (facts.addedSugars.value ?: 0.0).round1())
    put("dietaryFiber", (facts.dietaryFiber.value ?: 0.0).round1())
    put("salt", (facts.salt.value ?: 0.0).round1())
    put("cholesterol", (facts.cholesterol.value ?: 0.0).round1())
    put("caffeine", (facts.caffeine.value ?: 0.0).round1())

    if (level == DetailLevel.Extended) return

    putJsonObject("micronutrients") {
        put("vitaminA", (facts.vitaminA.value ?: 0.0).round1())
        put("vitaminB1", (facts.vitaminB1.value ?: 0.0).round1())
        put("vitaminB2", (facts.vitaminB2.value ?: 0.0).round1())
        put("vitaminB3", (facts.vitaminB3.value ?: 0.0).round1())
        put("vitaminB5", (facts.vitaminB5.value ?: 0.0).round1())
        put("vitaminB6", (facts.vitaminB6.value ?: 0.0).round1())
        put("vitaminB7", (facts.vitaminB7.value ?: 0.0).round1())
        put("vitaminB9", (facts.vitaminB9.value ?: 0.0).round1())
        put("vitaminB12", (facts.vitaminB12.value ?: 0.0).round1())
        put("vitaminC", (facts.vitaminC.value ?: 0.0).round1())
        put("vitaminD", (facts.vitaminD.value ?: 0.0).round1())
        put("vitaminE", (facts.vitaminE.value ?: 0.0).round1())
        put("vitaminK", (facts.vitaminK.value ?: 0.0).round1())
        put("manganese", (facts.manganese.value ?: 0.0).round1())
        put("magnesium", (facts.magnesium.value ?: 0.0).round1())
        put("potassium", (facts.potassium.value ?: 0.0).round1())
        put("calcium", (facts.calcium.value ?: 0.0).round1())
        put("copper", (facts.copper.value ?: 0.0).round1())
        put("zinc", (facts.zinc.value ?: 0.0).round1())
        put("sodium", (facts.sodium.value ?: 0.0).round1())
        put("iron", (facts.iron.value ?: 0.0).round1())
        put("phosphorus", (facts.phosphorus.value ?: 0.0).round1())
        put("selenium", (facts.selenium.value ?: 0.0).round1())
        put("iodine", (facts.iodine.value ?: 0.0).round1())
        put("chromium", (facts.chromium.value ?: 0.0).round1())
    }
}
