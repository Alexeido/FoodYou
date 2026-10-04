package com.maksimowiczm.foodyou.assistant.domain.tool.write

import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.date
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.double
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.doubleOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.long
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.string
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
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
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "createManualEntry"
    override val description =
        "Anade una entrada suelta con macros estimadas, para comida que no esta en la base de " +
            "datos. Usalo solo cuando searchFood no encuentre nada razonable: una entrada " +
            "referenciada a un producto real siempre es preferible. Igual que addEntries, entra " +
            "SIN marcar como comida: es una propuesta hasta que la persona la confirme. Pon " +
            "siempre la categoria que mejor encaje: sin ella el diario dibuja un interrogante " +
            "gris en vez de un icono."
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
            "category" to
                ToolSchema.string(
                    "Tipo de alimento, para el icono del diario. Elige la que mejor encaje.",
                    enum = FOOD_CATEGORIES,
                ),
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
        val id =
            manualRepository.insert(
                name = name,
                mealId = mealId,
                date = arguments.date("date"),
                nutritionFacts = facts,
                createdAt = LocalDateTime.now(),
                category = arguments.stringOrNull("category"),
                // Lo que anade la IA queda pendiente de que la persona lo marque, igual que con
                // addEntries. Antes no habia columna donde guardarlo y nacia marcado como comido.
                isEaten = false,
                createdByAssistant = true,
            )

        journal.record(
            summary = "Anadido (estimado): $name",
            undo = UndoAction.DeleteManualEntries(listOf(id.value)),
        )

        return buildJsonObject {
            put("ok", true)
            put("name", name)
            put("meal", meal.name)
        }
    }
}

/**
 * The category names the diary knows how to draw an icon for.
 *
 * Offered as an enum so the model cannot invent a name that maps to nothing - an unrecognised
 * category renders exactly like no category at all.
 */
internal val FOOD_CATEGORIES =
    listOf(
        "RESTAURANTES", "PLATOS_PREPARADOS", "COMIDA_INSTANTANEA", "SUPLEMENTOS",
        "CARNES_VEGETALES", "PANADERIA", "DULCES", "CHOCOLATES", "HELADOS", "SNACKS", "GRANOS",
        "BEBIDAS_ALCOHOLICAS", "BEBIDAS_VEGETALES", "CAFE_INFUSIONES", "BEBIDAS", "SALSAS",
        "UNTABLES", "FIAMBRES", "QUESOS", "YOGURT", "LECHE", "PANES", "PASTA", "CEREALES",
        "HARINAS", "POLLO", "CERDO", "RES", "PESCADO", "MARISCOS", "FRUTAS", "VERDURAS",
        "LEGUMBRES", "FRUTOS_SECOS", "SEMILLAS", "ACEITES", "ESPECIAS_HIERBAS", "HUEVO", "OTROS",
    )
