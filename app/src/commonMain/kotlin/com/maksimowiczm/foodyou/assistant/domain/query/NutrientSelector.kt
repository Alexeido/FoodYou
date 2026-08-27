package com.maksimowiczm.foodyou.assistant.domain.query

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

/**
 * The nutrients a query can be asked about by name.
 *
 * Kept as an explicit list rather than reflection over [NutritionFacts] so the model is given a
 * closed set it can pick from, and so an unknown name fails loudly instead of silently returning
 * zeroes.
 */
enum class NutrientSelector(val wireName: String, val unit: String) {
    Energy("energy", "kcal"),
    Proteins("proteins", "g"),
    Carbohydrates("carbohydrates", "g"),
    Fats("fats", "g"),
    SaturatedFats("saturatedFats", "g"),
    Sugars("sugars", "g"),
    AddedSugars("addedSugars", "g"),
    DietaryFiber("dietaryFiber", "g"),
    Salt("salt", "g"),
    Cholesterol("cholesterol", "g"),
    Caffeine("caffeine", "g");

    fun of(facts: NutritionFacts): Double =
        when (this) {
            Energy -> facts.energy.value
            Proteins -> facts.proteins.value
            Carbohydrates -> facts.carbohydrates.value
            Fats -> facts.fats.value
            SaturatedFats -> facts.saturatedFats.value
            Sugars -> facts.sugars.value
            AddedSugars -> facts.addedSugars.value
            DietaryFiber -> facts.dietaryFiber.value
            Salt -> facts.salt.value
            Cholesterol -> facts.cholesterol.value
            Caffeine -> facts.caffeine.value
        } ?: 0.0

    companion object {
        fun fromWireName(value: String): NutrientSelector? =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) }

        val wireNames: List<String>
            get() = entries.map { it.wireName }
    }
}
