package com.maksimowiczm.foodyou.assistant.domain.tool.write

import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.objects
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
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualEntryIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * One dish, several components - a burger instead of bun, patty, cheese and sauce as four rows.
 *
 * The diary is read by a person, and six rows that only make sense together are noise: what was
 * eaten was a burger. So the entry carries the macros for the whole dish and keeps its components
 * as a breakdown that the row can show when tapped, rather than as entries of their own. That is
 * also why the components carry no macros - they would be double counted the moment they did.
 */
class CreateComposedEntryTool(
    private val manualRepository: ManualDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "createComposedEntry"
    override val description =
        "Anade un plato compuesto: UNA sola entrada en el diario con la lista de lo que lleva " +
            "dentro. Usalo para comida hecha de varias cosas que se comen juntas - una " +
            "hamburguesa, un bocadillo, un plato combinado, una ensalada - en vez de meter cada " +
            "ingrediente por separado, que llena el diario de filas que solo tienen sentido " +
            "juntas. Las macros son las del plato ENTERO; los ingredientes son solo el desglose " +
            "que se ve al pulsar la fila y no suman nada por su cuenta. Si la persona se comio " +
            "un producto suelto de verdad, usa addEntries o createManualEntry, no esto. Entra SIN " +
            "marcar como comido, igual que el resto."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "date" to ToolSchema.date("Dia al que anadir."),
            "mealId" to ToolSchema.integer("Comida a la que anadir."),
            "name" to ToolSchema.string("Nombre del plato entero, p.ej. 'Hamburguesa casera'."),
            "kcal" to ToolSchema.number("Calorias del plato ENTERO, no de un ingrediente."),
            "proteins" to ToolSchema.number("Gramos de proteina del plato entero."),
            "carbohydrates" to ToolSchema.number("Gramos de carbohidratos del plato entero."),
            "fats" to ToolSchema.number("Gramos de grasa del plato entero."),
            "category" to
                ToolSchema.string(
                    "Tipo de plato, para el icono del diario. Elige la que mejor encaje.",
                    enum = COMPOSED_FOOD_CATEGORIES,
                ),
            "ingredients" to
                ToolSchema.arrayOf(
                    items =
                        ToolSchema.obj(
                            "name" to ToolSchema.string("Nombre del ingrediente."),
                            "grams" to
                                ToolSchema.number(
                                    "Gramos de ese ingrediente. Omitelo si no lo sabes."
                                ),
                            required = listOf("name"),
                        ),
                    description =
                        "Lo que lleva dentro, en orden. Minimo dos: con uno solo no es un plato " +
                            "compuesto y deberias usar createManualEntry.",
                ),
            required = listOf("date", "mealId", "name", "kcal", "ingredients"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val mealId = arguments.long("mealId")
        val meal =
            mealRepository.observeMeal(mealId).first()
                ?: return toolError("No existe una comida con id $mealId.")

        val ingredients =
            arguments.objects("ingredients").mapNotNull { obj ->
                val ingredientName = obj.stringOrNull("name")?.takeIf { it.isNotBlank() }
                    ?: return@mapNotNull null
                ManualEntryIngredient(name = ingredientName, grams = obj.doubleOrNull("grams"))
            }

        if (ingredients.isEmpty()) {
            return toolError(
                "Un plato compuesto necesita al menos un ingrediente con nombre. Si no lo " +
                    "tienes, usa createManualEntry."
            )
        }

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
                isEaten = false,
                createdByAssistant = true,
                ingredients = ingredients,
            )

        // Los ingredientes caen solos con la entrada (CASCADE), asi que deshacer es exactamente lo
        // mismo que para una entrada manual normal.
        journal.record(
            summary = "Anadido (plato): $name",
            undo = UndoAction.DeleteManualEntries(listOf(id.value)),
        )

        return buildJsonObject {
            put("ok", true)
            put("name", name)
            put("meal", meal.name)
            put("ingredients", ingredients.size)
        }
    }
}

/** Same vocabulary the rest of the diary uses; an unknown name renders as no icon at all. */
private val COMPOSED_FOOD_CATEGORIES =
    listOf(
        "RESTAURANTES", "PLATOS_PREPARADOS", "COMIDA_INSTANTANEA", "SUPLEMENTOS",
        "CARNES_VEGETALES", "PANADERIA", "DULCES", "CHOCOLATES", "HELADOS", "SNACKS", "GRANOS",
        "BEBIDAS_ALCOHOLICAS", "BEBIDAS_VEGETALES", "CAFE_INFUSIONES", "BEBIDAS", "SALSAS",
        "UNTABLES", "FIAMBRES", "QUESOS", "YOGURT", "LECHE", "PANES", "PASTA", "CEREALES",
        "HARINAS", "POLLO", "CERDO", "RES", "PESCADO", "MARISCOS", "FRUTAS", "VERDURAS",
        "LEGUMBRES", "FRUTOS_SECOS", "SEMILLAS", "ACEITES", "ESPECIAS_HIERBAS", "HUEVO", "OTROS",
    )
