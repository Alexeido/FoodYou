package com.maksimowiczm.foodyou.assistant.domain.tool.read

import com.maksimowiczm.foodyou.assistant.domain.query.NutrientSelector
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.intOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.stringOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.async
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.strings
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.round1
import com.maksimowiczm.foodyou.assistant.domain.tool.write.hasRequiredMacros
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
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
    private val recipeRepository: RecipeRepository,
    private val remoteFallback: AssistantRemoteFoodFallback,
) : AssistantTool {

    override val name = "searchFood"
    override val runsConcurrently = true
    override val description =
        "Busca alimentos en la base de datos local y devuelve su foodId y sus macros por 100 g. " +
            "OBLIGATORIO antes de anadir nada al diario: no inventes alimentos ni macros, usa " +
            "unicamente resultados de esta herramienta. Devuelve tambien las RECETAS de la " +
            "persona (kind=recipe, con recipeId): si el plato que te describen ya existe, " +
            "reutilizalo con addEntries+recipeId en vez de crear otra receta igual. Si no " +
            "encuentras algo, usa createManualEntry en su lugar. Si necesitas VARIOS alimentos " +
            "(los ingredientes de un plato, un dia entero), pasalos todos juntos en 'queries': " +
            "se buscan a la vez en una sola llamada, que es mucho mas rapido que uno por uno."
    override val parameters =
        ToolSchema.obj(
            "query" to ToolSchema.string("Que buscar. Un termino corto acierta mas que una frase."),
            "queries" to
                ToolSchema.arrayOf(
                    ToolSchema.string("Un termino corto."),
                    "Varias busquedas a la vez, p.ej. ['pan de hamburguesa', 'carne picada', " +
                        "'queso cheddar']. Usalo en vez de 'query' cuando busques mas de una " +
                        "cosa. Maximo $MAX_QUERIES.",
                ),
            "limit" to
                ToolSchema.integer(
                    "Cuantos devolver por cada busqueda. Por defecto 8 con 'query' y " +
                        "$BATCH_DEFAULT_LIMIT con 'queries'."
                ),
            "sortBy" to
                ToolSchema.string(
                    "Ordena por densidad de este nutriente por 100 g, de mayor a menor. " +
                        "Util para encontrar alimentos ricos en proteina.",
                    enum = NutrientSelector.wireNames,
                ),
        )

    override suspend fun call(arguments: JsonObject): JsonElement {
        val sortBy = arguments.stringOrNull("sortBy")?.let { NutrientSelector.fromWireName(it) }
        val queries = arguments.strings("queries").distinct()

        if (queries.isNotEmpty()) {
            if (queries.size > MAX_QUERIES) {
                return toolError(
                    "Como mucho $MAX_QUERIES busquedas por llamada. Parte la lista en dos llamadas."
                )
            }
            // Menos resultados por busqueda: son varias a la vez y todo vuelve al modelo como
            // tokens de entrada en cada turno siguiente.
            val limit = (arguments.intOrNull("limit") ?: BATCH_DEFAULT_LIMIT).coerceIn(1, 25)
            val found =
                coroutineScope {
                    queries.map { query -> async { query to searchOne(query, limit, sortBy) } }.awaitAll()
                }
            return buildJsonObject {
                put(
                    "results",
                    buildJsonArray {
                        found.forEach { (query, items) ->
                            add(
                                buildJsonObject {
                                    put("query", query)
                                    put("items", items)
                                }
                            )
                        }
                    },
                )
            }
        }

        val query =
            arguments.stringOrNull("query")
                ?: return toolError("Indica 'query' (una busqueda) o 'queries' (varias a la vez).")
        val limit = (arguments.intOrNull("limit") ?: 8).coerceIn(1, 25)
        return searchOne(query, limit, sortBy)
    }

    /** One search: the person's recipes first, then catalogue products, remote as a last resort. */
    private suspend fun searchOne(
        query: String,
        limit: Int,
        sortBy: NutrientSelector?,
    ): JsonArray {
        // Se pide de mas cuando hay que ordenar, para que el orden se aplique sobre un conjunto
        // decente y no solo sobre los ocho primeros que devuelva el indice.
        val fetch = if (sortBy == null) limit else (limit * 4).coerceAtMost(60)

        // Las recetas van primero: son platos que la persona ya ha montado, y lo mas probable es
        // que si pide "hamburguesa" quiera la suya y no una del catalogo. Pocas, para no tapar los
        // productos, y nunca cuando se ordena por nutriente, que es buscar ingredientes.
        val recipes =
            if (sortBy != null) emptyList()
            else recipeRepository.searchRecipes(query, limit.coerceAtMost(MAX_RECIPES))

        var products = productRepository.searchProducts(query, fetch)
        // Si ya hay una receta suya con ese nombre no hace falta ir a buscar fuera, que es lento.
        if (products.isEmpty() && recipes.isEmpty()) {
            // Nadie ha buscado esto antes en la pantalla de busqueda real: el espejo local no
            // tiene nada que devolver. Se intenta una vez contra las fuentes remotas activadas
            // antes de rendirse - si no, el modelo cree que el alimento no existe y se lo inventa.
            remoteFallback.fetchIntoCache(query, pageSize = fetch.coerceAtLeast(24))
            products = productRepository.searchProducts(query, fetch)
        }

        // Los productos a los que les faltan calorias o macros van al final y marcados: antes se
        // enseñaban con 0 y el modelo elegia, por ejemplo, una lechuga vacia que luego dejaba el
        // plato entero en rojo en el diario.
        val ordered =
            (if (sortBy == null) products
                else products.sortedByDescending { sortBy.of(it.nutritionFacts) })
                .sortedBy { !it.nutritionFacts.hasRequiredMacros() }

        return buildJsonArray {
            recipes.forEach { recipe ->
                add(
                    buildJsonObject {
                        put("kind", "recipe")
                        put("recipeId", recipe.id.id)
                        put("name", recipe.name)
                        put("isLiquid", recipe.isLiquid)
                        put("totalWeight", recipe.totalWeight.round1())
                        put("servings", recipe.servings)
                        put("servingWeight", recipe.servingWeight.round1())
                        put("per100g", per100g(recipe.nutritionFacts))
                        put(
                            "ingredients",
                            buildJsonArray {
                                recipe.ingredients.forEach { ingredient ->
                                    add(
                                        "${ingredient.food.headline} " +
                                            "${(ingredient.weight ?: 0.0).round1()} g"
                                    )
                                }
                            },
                        )
                    }
                )
            }
            ordered.take(limit).forEach { product ->
                add(
                    buildJsonObject {
                        put("kind", "product")
                        put("foodId", product.id.id)
                        put("name", product.headline)
                        put("isLiquid", product.isLiquid)
                        product.servingWeight?.let { put("servingWeight", it.round1()) }
                        product.totalWeight?.let { put("packageWeight", it.round1()) }
                        put("per100g", per100g(product.nutritionFacts))
                        if (!product.nutritionFacts.hasRequiredMacros()) {
                            put(
                                "incomplete",
                                "Faltan calorias o macros: no lo uses si hay otro resultado.",
                            )
                        }
                    }
                )
            }
        }
    }
}

private const val MAX_RECIPES = 5

/** More than this and the model is planning a week in one call; it should split it. */
private const val MAX_QUERIES = 10

private const val BATCH_DEFAULT_LIMIT = 5

/** A missing value goes out as null, never as 0: zero fat and "nobody knows" are not the same. */
private fun per100g(facts: NutritionFacts) = buildJsonObject {
    put("kcal", facts.energy.value?.round1())
    put("proteins", facts.proteins.value?.round1())
    put("carbohydrates", facts.carbohydrates.value?.round1())
    put("fats", facts.fats.value?.round1())
}
