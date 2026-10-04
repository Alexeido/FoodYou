package com.maksimowiczm.foodyou.common.domain.food

/**
 * The unit a nutrient is shown in. Values and goals are kept in grams; calcium reads better as
 * 820 mg and vitamin D as 15 µg.
 */
enum class NutrientUnit(val perGram: Double) {
    Gram(1.0),
    Milligram(1_000.0),
    Microgram(1_000_000.0),
}

val NutritionFactsField.displayUnit: NutrientUnit
    get() =
        when (this) {
            NutritionFactsField.Cholesterol,
            NutritionFactsField.Caffeine,
            NutritionFactsField.VitaminB1,
            NutritionFactsField.VitaminB2,
            NutritionFactsField.VitaminB3,
            NutritionFactsField.VitaminB5,
            NutritionFactsField.VitaminB6,
            NutritionFactsField.VitaminC,
            NutritionFactsField.VitaminE,
            NutritionFactsField.Manganese,
            NutritionFactsField.Magnesium,
            NutritionFactsField.Potassium,
            NutritionFactsField.Calcium,
            NutritionFactsField.Copper,
            NutritionFactsField.Zinc,
            NutritionFactsField.Sodium,
            NutritionFactsField.Iron,
            NutritionFactsField.Phosphorus -> NutrientUnit.Milligram

            NutritionFactsField.VitaminA,
            NutritionFactsField.VitaminB7,
            NutritionFactsField.VitaminB9,
            NutritionFactsField.VitaminB12,
            NutritionFactsField.VitaminD,
            NutritionFactsField.VitaminK,
            NutritionFactsField.Selenium,
            NutritionFactsField.Iodine,
            NutritionFactsField.Chromium -> NutrientUnit.Microgram

            else -> NutrientUnit.Gram
        }

/** Energy and the three macros: always on the card, never a "tracked" nutrient. */
val NutritionFactsField.isEnergyOrMacro: Boolean
    get() =
        this == NutritionFactsField.Energy ||
            this == NutritionFactsField.Proteins ||
            this == NutritionFactsField.Fats ||
            this == NutritionFactsField.Carbohydrates

/**
 * Goals that are a ceiling rather than something to reach: going over sugar or salt is the
 * problem, while going over calcium is fine.
 */
val NutritionFactsField.isLimit: Boolean
    get() =
        when (this) {
            NutritionFactsField.Sugars,
            NutritionFactsField.AddedSugars,
            NutritionFactsField.SaturatedFats,
            NutritionFactsField.TransFats,
            NutritionFactsField.Salt,
            NutritionFactsField.Sodium,
            NutritionFactsField.Cholesterol,
            NutritionFactsField.Caffeine -> true
            else -> false
        }
