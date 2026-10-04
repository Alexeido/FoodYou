package com.maksimowiczm.foodyou.assistant.domain.query

/**
 * How much of [com.maksimowiczm.foodyou.common.domain.food.NutritionFacts] a diary query writes
 * out.
 *
 * `basic` is the default everywhere on purpose: kcal/proteins/carbohydrates/fats is what almost
 * every question needs, and sending the other ~35 fields on every call would triple the size of a
 * week's worth of entries for no reason. The model is expected to ask for `extended` only when the
 * question is actually about saturated fat, sugar, fibre or salt, and `full` only when it is about
 * a specific vitamin or mineral.
 */
enum class DetailLevel(val wireName: String) {
    Basic("basic"),
    Extended("extended"),
    Full("full");

    companion object {
        fun fromWireName(value: String?): DetailLevel =
            entries.firstOrNull { it.wireName.equals(value, ignoreCase = true) } ?: Basic

        val wireNames: List<String>
            get() = entries.map { it.wireName }
    }
}
