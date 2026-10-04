package com.maksimowiczm.foodyou.assistant.domain.tool.write

import com.maksimowiczm.foodyou.app.ui.food.diary.add.toDiaryFood
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.booleanOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.dateOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.doubleOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.intOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.longOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.objects
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.string
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.round1
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Looks a recipe up by id without ever hanging.
 *
 * The repository builds a recipe by combining the flows of its ingredients, and that never emits
 * for a recipe with none - or with one whose food has gone. A tool awaiting `first()` on such a
 * recipe would freeze the whole turn, so a missing answer is treated as a missing recipe.
 */
internal suspend fun RecipeRepository.findRecipe(id: Long): Recipe? =
    withTimeoutOrNull(RECIPE_LOOKUP_TIMEOUT_MS) { observeRecipe(FoodId.Recipe(id)).first() }

private const val RECIPE_LOOKUP_TIMEOUT_MS = 2_000L

/** What the diary needs to show a food without flagging it as incomplete. */
internal fun NutritionFacts.hasRequiredMacros(): Boolean =
    energy.value != null && proteins.value != null && carbohydrates.value != null &&
        fats.value != null

/**
 * One dish made of real foods - a burger as bun, patty and cheese, each one a product from the
 * catalogue with its own grams.
 *
 * It is a [Recipe], not a diary line with a list of names attached. That is the whole point: the
 * macros come from the ingredients instead of being typed in, so changing the grams of one of them
 * recomputes the dish, and logging a different portion scales every ingredient in proportion -
 * both of which the recipe model already does. It also lands in the catalogue, so the next time the
 * person has the same burger it is one search away instead of being rebuilt from scratch.
 *
 * Optionally logs the dish in the same call, which is what "I just had this" needs: one step, one
 * entry in the history, one undo that takes back both the entry and the recipe.
 */
class CreateRecipeTool(
    private val productRepository: ProductRepository,
    private val recipeRepository: RecipeRepository,
    private val entryRepository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "createRecipe"
    override val description =
        "Crea una RECETA: un plato hecho de varios alimentos reales del catalogo, cada uno con " +
            "sus gramos - una hamburguesa, un bocadillo, un plato combinado, una ensalada. Las " +
            "macros salen de los ingredientes, no las pongas tu. Queda guardada en el catalogo, " +
            "asi que ANTES mira con searchFood si ya existe una (kind=recipe) y reutilizala con " +
            "addEntries+recipeId. Con date y mealId ademas la anade al diario en el mismo paso, " +
            "SIN marcar como comida. Los ingredientes tienen que venir de searchFood."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "name" to ToolSchema.string("Nombre del plato, p.ej. 'Hamburguesa casera'."),
            "ingredients" to
                ToolSchema.arrayOf(
                    items =
                        ToolSchema.obj(
                            "foodId" to
                                ToolSchema.integer(
                                    "Un producto de searchFood. Usa foodId o recipeId, no los dos."
                                ),
                            "recipeId" to
                                ToolSchema.integer(
                                    "Otra receta como ingrediente, p.ej. una salsa casera."
                                ),
                            "amount" to ToolSchema.number("Cuanto lleva el plato de esto."),
                            "unit" to unitSchema,
                            required = listOf("amount"),
                        ),
                    description = "Lo que lleva el plato ENTERO, en el orden en que se dice.",
                ),
            "servings" to
                ToolSchema.integer(
                    "Cuantas raciones salen del plato entero. Por defecto 1: lo que se describe " +
                        "es lo que se come una persona."
                ),
            "category" to
                ToolSchema.string(
                    "Tipo de plato, para su icono en el diario. PLATOS_PREPARADOS si es comida " +
                        "casera o un plato combinado, RESTAURANTES si es de un restaurante.",
                    enum = FOOD_CATEGORIES,
                ),
            "isLiquid" to ToolSchema.boolean("true si es una bebida o una sopa."),
            "note" to ToolSchema.string("Nota opcional para la receta."),
            "date" to ToolSchema.date("Para anadirla tambien al diario: el dia."),
            "mealId" to ToolSchema.integer("Para anadirla tambien al diario: la comida."),
            "eatenAmount" to
                ToolSchema.number(
                    "Cuanto se ha comido, si no es el plato entero. Omitelo y se anade entero."
                ),
            "eatenUnit" to unitSchema,
            required = listOf("name", "ingredients"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val name = arguments.string("name").trim()
        if (name.isBlank()) return toolError("El plato necesita un nombre.")

        val date = arguments.dateOrNull("date")
        val mealId = arguments.longOrNull("mealId")
        if ((date == null) != (mealId == null)) {
            return toolError(
                "Para anadir el plato al diario hacen falta date Y mealId. Pasa los dos, o " +
                    "ninguno si solo quieres guardar la receta."
            )
        }
        val meal =
            mealId?.let {
                mealRepository.observeMeal(it).first()
                    ?: return toolError("No existe una comida con id $it. Consulta listMeals.")
            }

        // Todo o nada. Un ingrediente que no se encuentra y se salta en silencio daria un plato con
        // las macros mal, y nadie se enteraria: mejor que el modelo lo sepa y lo arregle.
        val problems = mutableListOf<String>()
        val ingredients =
            arguments.objects("ingredients").mapIndexedNotNull { index, item ->
                val label = "ingrediente ${index + 1}"
                val food: Food =
                    item.longOrNull("recipeId")?.let { recipeRepository.findRecipe(it) }
                        ?: item.longOrNull("foodId")?.let {
                            productRepository.observeProduct(FoodId.Product(it)).first()
                        }
                        ?: run {
                            problems += "$label: no existe ese foodId/recipeId"
                            return@mapIndexedNotNull null
                        }
                val amount =
                    item.doubleOrNull("amount")?.takeIf { it > 0 }
                        ?: run {
                            problems += "$label (${food.headline}): falta una cantidad positiva"
                            return@mapIndexedNotNull null
                        }
                val measurement = measurementFrom(item.stringOrNull("unit"), amount, food.isLiquid)
                // Una racion o un envase solo tienen peso si el alimento lo sabe. Sin el, el
                // ingrediente sumaria cero sin decir nada.
                if (food.weight(measurement) == null) {
                    problems +=
                        "$label (${food.headline}): no tiene peso de racion ni de envase, ponlo en gramos"
                    return@mapIndexedNotNull null
                }
                // Un producto sin calorias o sin alguna macro deja el plato entero marcado como
                // incompleto en el diario. Mejor elegir otro resultado de la busqueda.
                if (!food.nutritionFacts.hasRequiredMacros()) {
                    problems +=
                        "$label (${food.headline}): le faltan calorias o macros, elige otro " +
                            "resultado de searchFood"
                    return@mapIndexedNotNull null
                }
                RecipeIngredient(food = food, measurement = measurement)
            }

        if (problems.isNotEmpty()) {
            return toolError("No se ha creado nada. " + problems.joinToString("; ") + ".")
        }
        if (ingredients.isEmpty()) {
            return toolError(
                "Una receta necesita al menos un ingrediente del catalogo. Buscalos con searchFood."
            )
        }

        val isLiquid = arguments.booleanOrNull("isLiquid") ?: false
        val servings = (arguments.intOrNull("servings") ?: 1).coerceAtLeast(1)
        val recipeId =
            recipeRepository.insertRecipe(
                name = name,
                servings = servings,
                note = arguments.stringOrNull("note")?.takeIf { it.isNotBlank() },
                isLiquid = isLiquid,
                ingredients = ingredients,
                category =
                    arguments.stringOrNull("category")?.takeIf { it in FOOD_CATEGORIES }
                        ?: "PLATOS_PREPARADOS",
            )
        // Releida del repositorio y no montada a mano: asi las macros que se devuelven son
        // exactamente las que vera la persona en la app.
        val recipe =
            recipeRepository.findRecipe(recipeId.id)
                ?: return toolError("La receta se ha guardado pero no se ha podido leer.")

        var entryId: Long? = null
        var eatenGrams = 0.0
        if (date != null && meal != null) {
            val eatenAmount = arguments.doubleOrNull("eatenAmount")?.takeIf { it > 0 }
            // Sin cantidad, el plato entero: en gramos (o ml), que en el diario se lee mejor que
            // "1 envase" y se edita igual.
            val measurement =
                if (eatenAmount != null) {
                    measurementFrom(arguments.stringOrNull("eatenUnit"), eatenAmount, isLiquid)
                } else if (isLiquid) {
                    Measurement.Milliliter(recipe.totalWeight)
                } else {
                    Measurement.Gram(recipe.totalWeight)
                }
            val food = recipe.toDiaryFood()
            entryId =
                entryRepository
                    .insert(
                        measurement = measurement,
                        mealId = meal.id,
                        date = date,
                        food = food,
                        createdAt = LocalDateTime.now(),
                        createdByAssistant = true,
                    )
                    .value
            eatenGrams = food.weight(measurement)
        }

        // Una sola entrada de historial para las dos cosas: deshacer se lleva la entrada del
        // diario y la receta de una vez. El orden es el inverso al de creacion.
        journal.record(
            summary =
                if (meal != null) "Receta creada y anadida a ${meal.name}: $name"
                else "Receta creada: $name",
            undo =
                if (entryId != null) {
                    UndoAction.Batch(
                        listOf(
                            UndoAction.DeleteMeasurements(listOf(entryId)),
                            UndoAction.DeleteRecipes(listOf(recipeId.id)),
                        )
                    )
                } else {
                    UndoAction.DeleteRecipes(listOf(recipeId.id))
                },
        )

        return buildJsonObject {
            put("ok", true)
            put("recipeId", recipeId.id)
            put("name", recipe.name)
            put("totalWeight", recipe.totalWeight.round1())
            put("servings", recipe.servings)
            put("per100g", macros(recipe.nutritionFacts, 1.0))
            put("wholeDish", macros(recipe.nutritionFacts, recipe.totalWeight / 100.0))
            put(
                "ingredients",
                buildJsonArray {
                    recipe.ingredients.forEach { ingredient ->
                        add(
                            buildJsonObject {
                                put("name", ingredient.food.headline)
                                put("grams", (ingredient.weight ?: 0.0).round1())
                                put(
                                    "kcal",
                                    (ingredient.nutritionFacts?.let { it.energy.value } ?: 0.0)
                                        .round1(),
                                )
                            }
                        )
                    }
                },
            )
            if (entryId != null && meal != null) {
                put("entryId", entryId)
                put("meal", meal.name)
                put("addedUnchecked", true)
                put("eatenGrams", eatenGrams.round1())
                put("eatenKcal", ((recipe.nutritionFacts.energy.value ?: 0.0) * eatenGrams / 100).round1())
            }
        }
    }
}

private fun macros(facts: NutritionFacts, factor: Double): JsonObject = buildJsonObject {
    putMacro("kcal", facts.energy.value, factor)
    putMacro("proteins", facts.proteins.value, factor)
    putMacro("carbohydrates", facts.carbohydrates.value, factor)
    putMacro("fats", facts.fats.value, factor)
}

private fun JsonObjectBuilder.putMacro(key: String, per100: Double?, factor: Double) {
    put(key, ((per100 ?: 0.0) * factor).round1())
}
