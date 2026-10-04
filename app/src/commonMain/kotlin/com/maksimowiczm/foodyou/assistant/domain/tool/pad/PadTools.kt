package com.maksimowiczm.foodyou.assistant.domain.tool.pad

import com.maksimowiczm.foodyou.app.ui.food.diary.add.toDiaryFood
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.domain.pad.AssistantPad
import com.maksimowiczm.foodyou.assistant.domain.query.DiaryRangeUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.NutrientSelector
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.date
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.doubleOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.intOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.long
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.longOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.objects
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.gramsFor
import com.maksimowiczm.foodyou.assistant.domain.tool.round1
import com.maksimowiczm.foodyou.assistant.domain.tool.targetAmountParam
import com.maksimowiczm.foodyou.assistant.domain.tool.targetNutrientParam
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import com.maksimowiczm.foodyou.assistant.domain.tool.write.findRecipe
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private fun measurementFor(unit: String?, amount: Double, isLiquid: Boolean): Measurement =
    when (unit?.lowercase()) {
        "serving", "porcion" -> Measurement.Serving(amount)
        "package", "paquete" -> Measurement.Package(amount)
        "milliliter", "ml" -> Measurement.Milliliter(amount)
        "gram", "g" -> Measurement.Gram(amount)
        else -> if (isLiquid) Measurement.Milliliter(amount) else Measurement.Gram(amount)
    }

/** P1 - start the draft from a real day, so the model works from what is already there. */
class PadFromDayTool(
    private val pad: AssistantPad,
    private val diaryRange: DiaryRangeUseCase,
) : AssistantTool {

    override val name = "padFromDay"
    override val description =
        "Empieza un borrador copiando lo que ya hay en un dia. Trabaja siempre en el borrador " +
            "antes de tocar el diario: puedes probar, sumar y descartar sin consecuencias."
    override val parameters =
        ToolSchema.obj(
            "date" to ToolSchema.date("Dia del que partir."),
            required = listOf("date"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val date = arguments.date("date")
        pad.clear()

        diaryRange(from = date, to = date).forEach { entry ->
            pad.add(
                mealId = entry.mealId,
                date = entry.date,
                name = entry.food.name,
                foodId = null,
                measurement = entry.measurement,
                facts = entry.nutritionFacts,
                grams = entry.weight,
                fromDiary = true,
            )
        }

        return buildJsonObject {
            put("ok", true)
            put("copied", pad.current.size)
            put("kcalSoFar", (pad.totals().energy.value ?: 0.0).round1())
        }
    }
}

/** P2 - put something in the draft. */
class PadAddTool(
    private val pad: AssistantPad,
    private val productRepository: ProductRepository,
    private val recipeRepository: RecipeRepository,
) : AssistantTool {

    override val name = "padAdd"
    override val description =
        "Anade un alimento o una receta al borrador. El foodId o el recipeId tienen que venir " +
            "de searchFood. Devuelve un ref " +
            "que sirve para quitarlo luego. Acepta una cantidad fija (amount+unit) o un objetivo " +
            "de nutriente (targetNutrient+targetAmount) para que la cantidad salga calculada."
    override val parameters =
        ToolSchema.obj(
            "date" to ToolSchema.date("Dia del borrador."),
            "mealId" to ToolSchema.integer("Comida a la que iria."),
            "foodId" to ToolSchema.integer("Un producto: el foodId de searchFood."),
            "recipeId" to
                ToolSchema.integer("Una receta de la persona: el recipeId de searchFood."),
            "amount" to
                ToolSchema.number(
                    "Cantidad. Omitelo si usas targetNutrient en su lugar."
                ),
            "unit" to ToolSchema.string("Unidad. Por defecto gramos, o mililitros si es liquido."),
            targetNutrientParam,
            targetAmountParam,
            required = listOf("date", "mealId"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val recipeId = arguments.longOrNull("recipeId")
        val foodId = if (recipeId == null) arguments.longOrNull("foodId") else null
        val product: Food =
            when {
                recipeId != null ->
                    recipeRepository.findRecipe(recipeId)
                        ?: return toolError("No existe el recipeId $recipeId. Buscalo con searchFood.")
                foodId != null ->
                    productRepository.observeProduct(FoodId.Product(foodId)).first()
                        ?: return toolError("No existe el foodId $foodId. Buscalo con searchFood.")
                else -> return toolError("Indica 'foodId' (un producto) o 'recipeId' (una receta).")
            }
        val food = product.toDiaryFood()

        val amount = arguments.doubleOrNull("amount")
        val measurement =
            if (amount != null) {
                measurementFor(arguments.stringOrNull("unit"), amount, product.isLiquid)
            } else {
                val nutrient =
                    arguments.stringOrNull("targetNutrient")?.let(NutrientSelector::fromWireName)
                        ?: return toolError("Indica 'amount' o 'targetNutrient'+'targetAmount'.")
                val target =
                    arguments.doubleOrNull("targetAmount")
                        ?: return toolError("Falta 'targetAmount'.")
                val grams =
                    food.nutritionFacts.gramsFor(nutrient, target)
                        ?: return toolError(
                            "${product.headline} no tiene ${nutrient.wireName} suficiente para " +
                                "calcular una cantidad."
                        )
                Measurement.Gram(grams)
            }
        val grams = food.weight(measurement)

        val item =
            pad.add(
                mealId = arguments.long("mealId"),
                date = arguments.date("date"),
                name = product.headline,
                foodId = foodId,
                recipeId = recipeId,
                measurement = measurement,
                facts = food.nutritionFacts * (grams / 100),
                grams = grams,
            )

        return buildJsonObject {
            put("ok", true)
            put("ref", item.ref)
            put("name", item.name)
            put("kcal", (item.facts.energy.value ?: 0.0).round1())
        }
    }
}

/** P2 - take something back out. */
class PadRemoveTool(private val pad: AssistantPad) : AssistantTool {
    override val name = "padRemove"
    override val description = "Quita un alimento del borrador por su ref."
    override val parameters =
        ToolSchema.obj("ref" to ToolSchema.integer("El ref que devolvio padAdd."), required = listOf("ref"))

    override suspend fun call(arguments: JsonObject): JsonElement {
        val ref = arguments.intOrNull("ref") ?: return toolError("Falta 'ref'.")
        val removed = pad.remove(ref)
        return buildJsonObject {
            put("ok", removed)
            if (!removed) put("error", "No hay nada con ref $ref en el borrador.")
        }
    }
}

/**
 * P3 - the arithmetic, done properly.
 *
 * This is the tool that raises the quality of a generated plan more than changing model would: the
 * assistant stops adding macros in its head and asks for the total instead.
 */
class PadTotalsTool(private val pad: AssistantPad) : AssistantTool {
    override val name = "padTotals"
    override val description =
        "Los totales del borrador ahora mismo, y lo que queda para el objetivo. Usalo despues de " +
            "cada cambio en vez de sumar tu: las sumas las hace la app y no se equivoca."
    override val parameters =
        ToolSchema.obj(
            "targetKcal" to ToolSchema.number("Objetivo de calorias, para que te diga la diferencia."),
            "targetProteins" to ToolSchema.number("Objetivo de proteina en gramos."),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val totals = pad.totals()
        val kcal = totals.energy.value ?: 0.0
        val proteins = totals.proteins.value ?: 0.0

        return buildJsonObject {
            put("items", pad.current.size)
            put("kcal", kcal.round1())
            put("proteins", proteins.round1())
            put("carbohydrates", (totals.carbohydrates.value ?: 0.0).round1())
            put("fats", (totals.fats.value ?: 0.0).round1())
            arguments.doubleOrNull("targetKcal")?.let { put("kcalRemaining", (it - kcal).round1()) }
            arguments.doubleOrNull("targetProteins")?.let {
                put("proteinsRemaining", (it - proteins).round1())
            }
            put("byRef", buildJsonArray {
                pad.current.forEach { item ->
                    add(
                        buildJsonObject {
                            put("ref", item.ref)
                            put("name", item.name)
                            put("mealId", item.mealId)
                            put("grams", item.grams.round1())
                            put("kcal", (item.facts.energy.value ?: 0.0).round1())
                            put("fromDiary", item.fromDiary)
                        }
                    )
                }
            })
        }
    }
}

/**
 * P5 - write the draft to the diary.
 *
 * Only what the model proposed is written; rows copied from the real day are left alone. That is
 * what makes a coffee the user added while the draft was open survive the commit, and it is why
 * none of this needs conflict resolution.
 */
class PadCommitTool(
    private val pad: AssistantPad,
    private val productRepository: ProductRepository,
    private val recipeRepository: RecipeRepository,
    private val entryRepository: FoodDiaryEntryRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "padCommit"
    override val description =
        "Escribe en el diario lo que has anadido al borrador, sin marcar como comido. Lo que ya " +
            "estaba en el dia no se toca. Deja un unico punto de historial, asi que la persona " +
            "puede deshacer el plan entero de una vez."
    override val mutates = true
    override val parameters = ToolSchema.none

    override suspend fun call(arguments: JsonObject): JsonElement {
        val proposed = pad.proposed
        if (proposed.isEmpty()) return toolError("El borrador no tiene nada nuevo que aplicar.")

        val created = mutableListOf<Long>()
        proposed.forEach { item ->
            val product: Food =
                item.recipeId?.let { recipeRepository.findRecipe(it) }
                    ?: item.foodId?.let {
                        productRepository.observeProduct(FoodId.Product(it)).first()
                    }
                    ?: return@forEach

            val id =
                entryRepository.insert(
                    measurement = item.measurement,
                    mealId = item.mealId,
                    date = item.date,
                    food = product.toDiaryFood(),
                    createdAt = LocalDateTime.now(),
                    createdByAssistant = true,
                )
            created.add(id.value)
        }

        if (created.isEmpty()) return toolError("No se pudo escribir nada del borrador.")

        journal.record(
            summary = "Plan aplicado: ${created.size} alimento(s)",
            undo = UndoAction.DeleteMeasurements(created),
        )
        pad.clear()

        return buildJsonObject {
            put("ok", true)
            put("written", created.size)
            put("entryIds", buildJsonArray { created.forEach { add(it) } })
        }
    }
}

/** P5 - throw the draft away. Nothing to undo, because nothing was written. */
class PadDiscardTool(private val pad: AssistantPad) : AssistantTool {
    override val name = "padDiscard"
    override val description = "Descarta el borrador entero. No deja rastro en el diario."
    override val parameters = ToolSchema.none

    override suspend fun call(arguments: JsonObject): JsonElement {
        pad.clear()
        return buildJsonObject { put("ok", true) }
    }
}
