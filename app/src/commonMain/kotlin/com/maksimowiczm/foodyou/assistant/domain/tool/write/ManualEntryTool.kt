package com.maksimowiczm.foodyou.assistant.domain.tool.write

import com.maksimowiczm.foodyou.assistant.domain.tool.Args.date
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.double
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.doubleOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.long
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.string
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.common.extension.now
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * C7 - the escape hatch for food that simply is not in the database.
 *
 * "A slice of tortilla from the bar" has no product and never will. The app already models this
 * separately, which is exactly the right place for a figure the model estimated rather than looked
 * up - it stays visibly distinct from a real product for anyone reading the diary later.
 */
class CreateManualEntryTool(
    private val manualRepository: ManualDiaryEntryRepository,
    private val mealRepository: MealRepository,
) : AssistantTool {

    override val name = "createManualEntry"
    override val description =
        "Anade una entrada suelta con macros estimadas, para comida que no esta en la base de " +
            "datos. Usalo solo cuando searchFood no encuentre nada razonable: una entrada " +
            "referenciada a un producto real siempre es preferible."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "date" to ToolSchema.date("Dia al que anadir."),
            "mealId" to ToolSchema.integer("Comida a la que anadir."),
            "name" to ToolSchema.string("Como llamarla. Se veta tal cual en el diario."),
            "kcal" to ToolSchema.number("Calorias totales de la racion, no por 100 g."),
            "proteins" to ToolSchema.number("Gramos de proteina totales."),
            "carbohydrates" to ToolSchema.number("Gramos de carbohidratos totales."),
            "fats" to ToolSchema.number("Gramos de grasa totales."),
            required = listOf("date", "mealId", "name", "kcal"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val mealId = arguments.long("mealId")
        val meal =
            mealRepository.observeMeal(mealId).first()
                ?: return toolError("No existe una comida con id $mealId.")

        val facts =
            NutritionFacts(
                energy = NutrientValue.Complete(arguments.double("kcal")),
                proteins = NutrientValue.Complete(arguments.doubleOrNull("proteins") ?: 0.0),
                carbohydrates =
                    NutrientValue.Complete(arguments.doubleOrNull("carbohydrates") ?: 0.0),
                fats = NutrientValue.Complete(arguments.doubleOrNull("fats") ?: 0.0),
            )

        val name = arguments.string("name")
        manualRepository.insert(
            name = name,
            mealId = mealId,
            date = arguments.date("date"),
            nutritionFacts = facts,
            createdAt = LocalDateTime.now(),
        )

        return buildJsonObject {
            put("ok", true)
            put("name", name)
            put("meal", meal.name)
        }
    }
}
