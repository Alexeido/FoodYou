package com.maksimowiczm.foodyou.assistant.domain.tool.read

import com.maksimowiczm.foodyou.assistant.domain.query.NutrientSelector
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.intOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.string
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.round1
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * C9 - the catalogue search, and the single most load-bearing tool in the set.
 *
 * The rule the whole design rests on is that the model may not name a food that did not come out of
 * here. If it writes "chicken breast, 150 g, 248 kcal" from memory, the week adds up on paper and is
 * fiction. Coming through this tool, the macros are the database's and the model only chooses the
 * amount.
 */
class SearchFoodTool(
    private val productRepository: ProductRepository,
    private val remoteFallback: AssistantRemoteFoodFallback,
) : AssistantTool {

    override val name = "searchFood"
    override val description =
        "Busca alimentos en la base de datos local y devuelve su foodId y sus macros por 100 g. " +
            "OBLIGATORIO antes de anadir nada al diario: no inventes alimentos ni macros, usa " +
            "unicamente resultados de esta herramienta. Si no encuentras algo, usa " +
            "createManualEntry en su lugar."
    override val parameters =
        ToolSchema.obj(
            "query" to ToolSchema.string("Que buscar. Un termino corto acierta mas que una frase."),
            "limit" to ToolSchema.integer("Cuantos devolver. Por defecto 8."),
            "sortBy" to
                ToolSchema.string(
                    "Ordena por densidad de este nutriente por 100 g, de mayor a menor. " +
                        "Util para encontrar alimentos ricos en proteina.",
                    enum = NutrientSelector.wireNames,
                ),
            required = listOf("query"),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val limit = (arguments.intOrNull("limit") ?: 8).coerceIn(1, 25)
        val sortBy = arguments.stringOrNull("sortBy")?.let { NutrientSelector.fromWireName(it) }

        // Se pide de mas cuando hay que ordenar, para que el orden se aplique sobre un conjunto
        // decente y no solo sobre los ocho primeros que devuelva el indice.
        val fetch = if (sortBy == null) limit else (limit * 4).coerceAtMost(60)
        val query = arguments.string("query")

        var products = productRepository.searchProducts(query, fetch)
        if (products.isEmpty()) {
            // Nadie ha buscado esto antes en la pantalla de busqueda real: el espejo local no
            // tiene nada que devolver. Se intenta una vez contra las fuentes remotas activadas
            // antes de rendirse - si no, el modelo cree que el alimento no existe y se lo inventa.
            remoteFallback.fetchIntoCache(query, pageSize = fetch.coerceAtLeast(24))
            products = productRepository.searchProducts(query, fetch)
        }

        val ordered =
            if (sortBy == null) products
            else products.sortedByDescending { sortBy.of(it.nutritionFacts) }

        return buildJsonArray {
            ordered.take(limit).forEach { product ->
                add(
                    buildJsonObject {
                        put("foodId", product.id.id)
                        put("name", product.headline)
                        put("isLiquid", product.isLiquid)
                        product.servingWeight?.let { put("servingWeight", it.round1()) }
                        product.totalWeight?.let { put("packageWeight", it.round1()) }
                        put("per100g", buildJsonObject {
                            put("kcal", (product.nutritionFacts.energy.value ?: 0.0).round1())
                            put("proteins", (product.nutritionFacts.proteins.value ?: 0.0).round1())
                            put(
                                "carbohydrates",
                                (product.nutritionFacts.carbohydrates.value ?: 0.0).round1(),
                            )
                            put("fats", (product.nutritionFacts.fats.value ?: 0.0).round1())
                        })
                    }
                )
            }
        }
    }
}
