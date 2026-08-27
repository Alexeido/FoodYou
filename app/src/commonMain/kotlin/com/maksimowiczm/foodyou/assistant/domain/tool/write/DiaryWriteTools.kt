package com.maksimowiczm.foodyou.assistant.domain.tool.write

import com.maksimowiczm.foodyou.app.ui.food.diary.add.toDiaryFood
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.EatenState
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.date
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.dateOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.double
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.doubleOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.long
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.longOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.longs
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.objects
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.string
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.round1
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.extension.now
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Builds a measurement from the loose way a model describes one. */
private fun measurementFrom(unit: String?, amount: Double, isLiquid: Boolean): Measurement =
    when (unit?.lowercase()) {
        "serving", "porcion", "racion" -> Measurement.Serving(amount)
        "package", "paquete", "envase" -> Measurement.Package(amount)
        "milliliter", "ml" -> Measurement.Milliliter(amount)
        "ounce", "oz" -> Measurement.Ounce(amount)
        "fluidounce", "floz" -> Measurement.FluidOunce(amount)
        "gram", "g" -> Measurement.Gram(amount)
        // Sin unidad: el estado liquido del alimento decide, que es el fallo que ya nos mordio.
        else -> if (isLiquid) Measurement.Milliliter(amount) else Measurement.Gram(amount)
    }

private val unitSchema =
    ToolSchema.string(
        "Unidad de la cantidad. Si la omites se usa gramos para solidos y mililitros para " +
            "liquidos, que es casi siempre lo correcto.",
        enum = listOf("gram", "milliliter", "serving", "package", "ounce", "fluidOunce"),
    )

/**
 * Adds one or more foods to the diary in a single change.
 *
 * Everything lands unchecked. A plan the assistant wrote is a proposal until the person ticks it
 * off, and that is what keeps generated food out of today's totals.
 */
class AddEntriesTool(
    private val productRepository: ProductRepository,
    private val entryRepository: FoodDiaryEntryRepository,
    private val mealRepository: MealRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "addEntries"
    override val description =
        "Anade alimentos al diario. Los foodId tienen que venir de searchFood. Se anaden SIN " +
            "marcar como comidos, para que la persona los marque segun se los coma."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "date" to ToolSchema.date("Dia al que anadir."),
            "mealId" to ToolSchema.integer("Comida a la que anadir. Sacalo de listMeals."),
            "items" to
                ToolSchema.arrayOf(
                    ToolSchema.obj(
                        "foodId" to ToolSchema.integer("El foodId devuelto por searchFood."),
                        "amount" to ToolSchema.number("Cantidad en la unidad indicada."),
                        "unit" to unitSchema,
                        required = listOf("foodId", "amount"),
                    ),
                    "Los alimentos a anadir.",
                ),
            required = listOf("date", "mealId", "items"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val date = arguments.date("date")
        val mealId = arguments.long("mealId")
        val items = arguments.objects("items")
        if (items.isEmpty()) return toolError("No has indicado ningun alimento en 'items'.")

        val meal =
            mealRepository.observeMeal(mealId).first()
                ?: return toolError("No existe una comida con id $mealId. Consulta listMeals.")

        val created = mutableListOf<Long>()
        val added = mutableListOf<Triple<String, Double, Double>>()

        items.forEach { item ->
            val foodId = item.longOrNull("foodId") ?: return@forEach
            val product =
                productRepository.observeProduct(FoodId.Product(foodId)).first() ?: return@forEach
            val amount = item.doubleOrNull("amount") ?: return@forEach

            val measurement = measurementFrom(item.stringOrNull("unit"), amount, product.isLiquid)
            val food = product.toDiaryFood()

            val id =
                entryRepository.insert(
                    measurement = measurement,
                    mealId = mealId,
                    date = date,
                    food = food,
                    createdAt = LocalDateTime.now(),
                )
            created.add(id.value)

            val grams = food.weight(measurement)
            added.add(
                Triple(product.headline, grams, (food.nutritionFacts.energy.value ?: 0.0) * grams / 100)
            )
        }

        if (created.isEmpty()) {
            return toolError(
                "Ninguno de los foodId existe. Vuelve a buscarlos con searchFood antes de anadir."
            )
        }

        // Una sola entrada de historial, aunque sean quince alimentos: deshacer es un boton.
        journal.record(
            summary = "Anadido a ${meal.name}: ${added.size} alimento(s)",
            undo = UndoAction.DeleteMeasurements(created),
        )

        return buildJsonObject {
            put("ok", true)
            put("meal", meal.name)
            put("addedUnchecked", added.size)
            put("entryIds", buildJsonArray { created.forEach { add(it) } })
            put("totalKcal", added.sumOf { it.third }.round1())
        }
    }
}

/** Changes the amount, the meal or the date of an entry already in the diary. */
class UpdateEntryTool(
    private val entryRepository: FoodDiaryEntryRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "updateEntry"
    override val description =
        "Cambia la cantidad, la comida o la fecha de una entrada existente. El entryId sale de " +
            "diaryRange."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "entryId" to ToolSchema.integer("Id de la entrada, de diaryRange."),
            "amount" to ToolSchema.number("Cantidad nueva."),
            "unit" to unitSchema,
            "mealId" to ToolSchema.integer("Mover a otra comida."),
            "date" to ToolSchema.date("Mover a otro dia."),
            required = listOf("entryId"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val entryId = FoodDiaryEntryId(arguments.long("entryId"))
        val entry =
            entryRepository.observe(entryId).first()
                ?: return toolError("No existe la entrada ${entryId.value}.")

        val amount = arguments.doubleOrNull("amount")
        val newMeal = arguments.longOrNull("mealId")
        val newDate = arguments.dateOrNull("date")
        if (amount == null && newMeal == null && newDate == null) {
            return toolError("Indica al menos amount, mealId o date.")
        }

        journal.record(
            summary = "Modificado: ${entry.food.name}",
            // Restaurar deja la fila exactamente como estaba, incluidos posicion y fechas.
            // Capturado del DAO, no del objeto de dominio: la fila lleva el productId al que
            // apunta, y sin el la entrada restaurada no encontraria su alimento.
            undo = journal.snapshot(listOf(entryId.value)),
        )

        val measurement =
            if (amount == null) entry.measurement
            else measurementFrom(arguments.stringOrNull("unit"), amount, entry.food.isLiquid)

        entryRepository.update(
            entry.copy(
                measurement = measurement,
                mealId = newMeal ?: entry.mealId,
                date = newDate ?: entry.date,
            )
        )

        return buildJsonObject {
            put("ok", true)
            put("entryId", entryId.value)
            put("name", entry.food.name)
        }
    }
}

/** Removes entries. The rows are captured first, which is what makes this reversible. */
class DeleteEntriesTool(
    private val entryRepository: FoodDiaryEntryRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "deleteEntries"
    override val description = "Borra entradas del diario. Los entryId salen de diaryRange."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "entryIds" to
                ToolSchema.arrayOf(ToolSchema.integer("Id de entrada."), "Entradas a borrar."),
            required = listOf("entryIds"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val ids = arguments.longs("entryIds")
        if (ids.isEmpty()) return toolError("No has indicado ninguna entrada.")

        val entries = ids.mapNotNull { entryRepository.observe(FoodDiaryEntryId(it)).first() }
        if (entries.isEmpty()) return toolError("Ninguna de esas entradas existe.")

        // El journal se escribe ANTES de borrar: despues ya no habria nada que capturar.
        journal.record(
            summary = "Borrado: ${entries.joinToString { it.food.name }}".take(120),
            undo = journal.snapshot(entries.map { it.id.value }),
        )

        entries.forEach { entryRepository.delete(it.id) }

        return buildJsonObject {
            put("ok", true)
            put("deleted", entries.size)
        }
    }
}

/** Ticking things off, which is the one thing the watch app will eventually do too. */
class SetEatenTool(
    private val entryRepository: FoodDiaryEntryRepository,
    private val journal: ChangeJournal,
) : AssistantTool {

    override val name = "setEaten"
    override val description =
        "Marca o desmarca entradas como comidas. Solo lo marcado cuenta en los totales del dia."
    override val mutates = true
    override val parameters =
        ToolSchema.obj(
            "entryIds" to
                ToolSchema.arrayOf(ToolSchema.integer("Id de entrada."), "Entradas a cambiar."),
            "eaten" to ToolSchema.boolean("true para marcar como comido, false para desmarcar."),
            required = listOf("entryIds", "eaten"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val ids = arguments.longs("entryIds")
        val eaten =
            com.maksimowiczm.foodyou.assistant.domain.tool.Args.run {
                arguments.booleanOrNull("eaten")
            } ?: return toolError("Falta 'eaten'.")

        val entries = ids.mapNotNull { entryRepository.observe(FoodDiaryEntryId(it)).first() }
        if (entries.isEmpty()) return toolError("Ninguna de esas entradas existe.")

        journal.record(
            summary = if (eaten) "Marcado como comido: ${entries.size}" else "Desmarcado: ${entries.size}",
            undo = UndoAction.SetEaten(entries.map { EatenState(it.id.value, it.isEaten) }),
        )

        entries.forEach { entryRepository.setEaten(it.id, eaten) }

        return buildJsonObject {
            put("ok", true)
            put("changed", entries.size)
        }
    }
}

