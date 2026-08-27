package com.maksimowiczm.foodyou.assistant.domain.tool.read

import com.maksimowiczm.foodyou.assistant.domain.query.DailyTotalsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.DiaryRangeUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.MealTimingStatsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.NutrientAttributionUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.NutrientSelector
import com.maksimowiczm.foodyou.assistant.domain.query.SearchDiaryUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.TopBrandsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.TopFoodsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.TotalsGrouping
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.date
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.intOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.longOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.string
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.round1
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private val fromTo =
    arrayOf(
        "from" to ToolSchema.date("Primer dia del rango, incluido."),
        "to" to ToolSchema.date("Ultimo dia del rango, incluido."),
    )

/** C5. The workhorse: one call answers most questions about a range. */
class DailyTotalsTool(private val useCase: DailyTotalsUseCase) : AssistantTool {
    override val name = "dailyTotals"
    override val description =
        "Totales nutricionales del diario entre dos fechas. Agrupa por dia, dia de la semana, " +
            "semana o mes. Los dias sin nada registrado tambien salen, con ceros."
    override val parameters =
        ToolSchema.obj(
            *fromTo,
            "groupBy" to
                ToolSchema.string(
                    "Como agrupar. Por defecto day.",
                    enum = listOf("day", "weekday", "week", "month"),
                ),
            "onlyEaten" to
                ToolSchema.boolean(
                    "Si es true solo cuenta lo marcado como comido, ignorando lo planificado."
                ),
            required = listOf("from", "to"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val totals =
            useCase(
                from = arguments.date("from"),
                to = arguments.date("to"),
                grouping = TotalsGrouping.fromWireName(arguments.stringOrNull("groupBy")),
                onlyEaten =
                    com.maksimowiczm.foodyou.assistant.domain.tool.Args.run {
                        arguments.booleanOrNull("onlyEaten")
                    } ?: false,
            )

        return buildJsonArray {
            totals.forEach { period ->
                add(
                    buildJsonObject {
                        put("key", period.key)
                        put("kcal", period.energy.round1())
                        put("proteins", (period.facts.proteins.value ?: 0.0).round1())
                        put("carbohydrates", (period.facts.carbohydrates.value ?: 0.0).round1())
                        put("fats", (period.facts.fats.value ?: 0.0).round1())
                        put("fiber", (period.facts.dietaryFiber.value ?: 0.0).round1())
                        put("entries", period.entryCount)
                        put("eaten", period.eatenCount)
                    }
                )
            }
        }
    }
}

/** C1. */
class TopFoodsTool(private val useCase: TopFoodsUseCase) : AssistantTool {
    override val name = "topFoods"
    override val description =
        "Los alimentos que mas aparecen en el diario en un rango. Usalo antes de planificar: " +
            "proponer lo que la persona ya come de verdad acierta mas que elegir por tu cuenta."
    override val parameters =
        ToolSchema.obj(
            *fromTo,
            "limit" to ToolSchema.integer("Cuantos devolver. Por defecto 15."),
            "mealId" to ToolSchema.integer("Restringe a una comida concreta."),
            required = listOf("from", "to"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val foods =
            useCase(
                from = arguments.date("from"),
                to = arguments.date("to"),
                limit = arguments.intOrNull("limit") ?: 15,
                mealId = arguments.longOrNull("mealId"),
            )

        return buildJsonArray {
            foods.forEach {
                add(
                    buildJsonObject {
                        put("name", it.name)
                        put("times", it.times)
                        put("totalGrams", it.totalGrams.round1())
                        put("totalKcal", it.totalEnergy.round1())
                    }
                )
            }
        }
    }
}

/** C2. */
class TopBrandsTool(private val useCase: TopBrandsUseCase) : AssistantTool {
    override val name = "topBrands"
    override val description =
        "Las marcas que mas aparecen en el diario. Sirve para proponer productos de las tiendas " +
            "donde la persona compra de verdad."
    override val parameters =
        ToolSchema.obj(
            *fromTo,
            "limit" to ToolSchema.integer("Cuantas devolver. Por defecto 10."),
            required = listOf("from", "to"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val brands =
            useCase(
                from = arguments.date("from"),
                to = arguments.date("to"),
                limit = arguments.intOrNull("limit") ?: 10,
            )
        return buildJsonArray {
            brands.forEach {
                add(
                    buildJsonObject {
                        put("brand", it.brand)
                        put("times", it.times)
                        put("totalKcal", it.totalEnergy.round1())
                    }
                )
            }
        }
    }
}

/** C3. */
class NutrientAttributionTool(private val useCase: NutrientAttributionUseCase) : AssistantTool {
    override val name = "nutrientAttribution"
    override val description =
        "Que alimentos aportaron un nutriente concreto en un rango, de mayor a menor. Es lo que " +
            "hace falta para responder de donde viene la grasa o que recortar sin perder proteina."
    override val parameters =
        ToolSchema.obj(
            "nutrient" to
                ToolSchema.string("Nutriente a atribuir.", enum = NutrientSelector.wireNames),
            *fromTo,
            "limit" to ToolSchema.integer("Cuantos devolver. Por defecto 10."),
            required = listOf("nutrient", "from", "to"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val raw = arguments.string("nutrient")
        val nutrient =
            NutrientSelector.fromWireName(raw)
                ?: return toolError(
                    "Nutriente desconocido '$raw'. Validos: ${NutrientSelector.wireNames.joinToString()}"
                )

        val contributions =
            useCase(
                nutrient = nutrient,
                from = arguments.date("from"),
                to = arguments.date("to"),
                limit = arguments.intOrNull("limit") ?: 10,
            )

        return buildJsonArray {
            contributions.forEach {
                add(
                    buildJsonObject {
                        put("name", it.name)
                        put("amount", it.amount.round1())
                        put("unit", nutrient.unit)
                        put("sharePercent", (it.share * 100).round1())
                    }
                )
            }
        }
    }
}

/** C4. */
class SearchDiaryTool(private val useCase: SearchDiaryUseCase) : AssistantTool {
    override val name = "searchDiary"
    override val description =
        "Busca un alimento dentro del historial ya registrado. No confundir con searchFood, que " +
            "busca en el catalogo: esto responde cuando comi salmon por ultima vez."
    override val parameters =
        ToolSchema.obj(
            "query" to ToolSchema.string("Texto a buscar en el nombre del alimento."),
            *fromTo,
            required = listOf("query", "from", "to"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val hits =
            useCase(
                query = arguments.string("query"),
                from = arguments.date("from"),
                to = arguments.date("to"),
            )
        return buildJsonArray {
            hits.forEach {
                add(
                    buildJsonObject {
                        put("date", it.date.toString())
                        put("mealId", it.mealId)
                        put("name", it.name)
                        put("grams", it.grams.round1())
                        put("kcal", it.energy.round1())
                    }
                )
            }
        }
    }
}

/** C6. */
class MealTimingStatsTool(private val useCase: MealTimingStatsUseCase) : AssistantTool {
    override val name = "mealTimingStats"
    override val description =
        "A que hora se suele REGISTRAR cada comida, promediado. Ojo: es la hora de registro, no " +
            "la de comer. Si respondes con esto, dilo."
    override val parameters = ToolSchema.obj(*fromTo, required = listOf("from", "to"))

    override suspend fun call(arguments: JsonObject): JsonElement {
        val timings = useCase(from = arguments.date("from"), to = arguments.date("to"))
        return buildJsonArray {
            timings.forEach {
                add(
                    buildJsonObject {
                        put("mealId", it.mealId)
                        put("meal", it.mealName)
                        put(
                            "averageLoggedAt",
                            it.averageHour.toString().padStart(2, '0') +
                                ":" +
                                it.averageMinute.toString().padStart(2, '0'),
                        )
                        put("samples", it.samples)
                    }
                )
            }
        }
    }
}

/** The raw entries, for when a total is not enough. */
class DiaryRangeTool(private val useCase: DiaryRangeUseCase) : AssistantTool {
    override val name = "diaryRange"
    override val description =
        "Las entradas del diario entre dos fechas, con su id. Necesitas el id de una entrada para " +
            "poder cambiarla o borrarla."
    override val parameters =
        ToolSchema.obj(
            *fromTo,
            "mealId" to ToolSchema.integer("Restringe a una comida."),
            "onlyEaten" to
                ToolSchema.boolean("true solo lo comido, false solo lo planificado sin marcar."),
            required = listOf("from", "to"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val entries =
            useCase(
                from = arguments.date("from"),
                to = arguments.date("to"),
                mealId = arguments.longOrNull("mealId"),
                onlyEaten =
                    com.maksimowiczm.foodyou.assistant.domain.tool.Args.run {
                        arguments.booleanOrNull("onlyEaten")
                    },
            )

        return buildJsonArray {
            entries.forEach { entry ->
                add(
                    buildJsonObject {
                        put("entryId", entry.id.value)
                        put("date", entry.date.toString())
                        put("mealId", entry.mealId)
                        put("name", entry.food.name)
                        put("grams", entry.weight.round1())
                        put("kcal", (entry.nutritionFacts.energy.value ?: 0.0).round1())
                        put("isEaten", entry.isEaten)
                    }
                )
            }
        }
    }
}

/** The meals the user has configured. Without this the model cannot address a meal at all. */
class ListMealsTool(private val mealRepository: MealRepository) : AssistantTool {
    override val name = "listMeals"
    override val description =
        "Las comidas configuradas por la persona, con su id y su franja horaria. Necesitas el id " +
            "para anadir nada al diario."
    override val parameters = ToolSchema.none

    override suspend fun call(arguments: JsonObject): JsonElement {
        val meals = mealRepository.observeMeals().first()
        return buildJsonArray {
            meals.forEach {
                add(
                    buildJsonObject {
                        put("mealId", it.id)
                        put("name", it.name)
                        put("from", it.from.toString())
                        put("to", it.to.toString())
                    }
                )
            }
        }
    }
}

/** The goals in force, so the model can compare totals against something. */
class GoalsTool(private val goalsRepository: GoalsRepository) : AssistantTool {
    override val name = "goals"
    override val description = "Los objetivos nutricionales para una fecha concreta."
    override val parameters =
        ToolSchema.obj(
            "date" to ToolSchema.date("Dia cuyos objetivos quieres."),
            required = listOf("date"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val goal = goalsRepository.observeDailyGoals(arguments.date("date")).first()
        return buildJsonObject {
            putJsonObject("targets") {
                goal.map.forEach { (field, value) -> put(field.name, value.round1()) }
            }
        }
    }
}
