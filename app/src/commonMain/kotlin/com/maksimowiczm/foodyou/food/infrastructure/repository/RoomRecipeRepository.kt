package com.maksimowiczm.foodyou.food.infrastructure.repository

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.measurement.from
import com.maksimowiczm.foodyou.common.domain.measurement.rawValue
import com.maksimowiczm.foodyou.common.domain.measurement.type
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.repository.RecipeRepository
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeDao
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeEntity
import com.maksimowiczm.foodyou.food.infrastructure.room.RecipeIngredientEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull

private const val RESOLVE_TIMEOUT_MS = 2_000L

internal class RoomRecipeRepository(
    private val recipeDao: RecipeDao,
    private val productDataSource: RoomProductRepository,
) : RecipeRepository {
    override fun observeRecipe(recipeId: FoodId.Recipe): Flow<Recipe?> =
        combine(
                recipeDao.observeRecipe(recipeId.id),
                recipeDao.observeRecipeIngredients(recipeId.id),
            ) { recipeEntity, ingredients ->
                if (recipeEntity == null) {
                    return@combine null
                }

                val ingredients =
                    ingredients.map {
                        val foodId = it.foodId

                        val foodFlow =
                            when (foodId) {
                                is FoodId.Recipe -> observeRecipe(foodId).filterNotNull()
                                is FoodId.Product ->
                                    productDataSource.observeProduct(foodId).filterNotNull()
                            }

                        foodFlow.map { food ->
                            RecipeIngredient(
                                food = food,
                                measurement = Measurement.from(it.measurement, it.quantity),
                            )
                        }
                    }

                recipeEntity to combine(ingredients) { it.toList() }
            }
            .flatMapLatest { pair ->
                if (pair == null) {
                    return@flatMapLatest flowOf(null)
                } else {
                    val (recipeEntity, ingredients) = pair

                    ingredients.map { ingredients ->
                        Recipe(
                            id = FoodId.Recipe(recipeEntity.id),
                            name = recipeEntity.name,
                            servings = recipeEntity.servings,
                            note = recipeEntity.note,
                            isLiquid = recipeEntity.isLiquid,
                            ingredients = ingredients,
                            category = recipeEntity.category,
                        )
                    }
                }
            }

    override suspend fun searchRecipes(query: String, limit: Int): List<Recipe> {
        val match = recipeFtsQuery(query) ?: return emptyList()
        return recipeDao.searchRecipeIds(match, limit).mapNotNull { id ->
            // The timeout is a guard, not a wait: the query already skips empty recipes, but an
            // ingredient whose food vanished would also leave observeRecipe silent forever, and a
            // search must never hang the assistant on one broken recipe.
            withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { observeRecipe(FoodId.Recipe(id)).first() }
        }
    }

    override suspend fun updateFavorite(recipeId: FoodId.Recipe, isFavorite: Boolean) {
        recipeDao.updateFavorite(recipeId.id, isFavorite)
    }

    override suspend fun deleteRecipe(recipe: Recipe) {
        val entity = recipe.toEntity()
        recipeDao.delete(entity)
    }

    override suspend fun insertRecipe(
        name: String,
        servings: Int,
        note: String?,
        isLiquid: Boolean,
        ingredients: List<RecipeIngredient>,
        category: String?,
    ): FoodId.Recipe {
        val recipe =
            Recipe(
                id = FoodId.Recipe(0L),
                name = name,
                servings = servings,
                note = note,
                isLiquid = isLiquid,
                ingredients = ingredients,
                category = category,
            )

        val recipeEntity = recipe.toEntity()

        val ingredients =
            recipe.ingredients.map { (food, measurement) ->
                val foodId = food.id

                RecipeIngredientEntity(
                    ingredientRecipeId = (foodId as? FoodId.Recipe)?.id,
                    ingredientProductId = (foodId as? FoodId.Product)?.id,
                    measurement = measurement.type,
                    quantity = measurement.rawValue,
                )
            }

        val id =
            recipeDao
                .insertRecipeWithIngredients(recipe = recipeEntity, ingredients = ingredients)
                .let(FoodId::Recipe)

        return id
    }

    override suspend fun updateRecipe(recipe: Recipe) {
        // @Update writes every column, and the domain Recipe does not carry the favourite flag: a
        // plain toEntity() would quietly un-favourite every recipe the moment it was edited. The
        // recipe editor does not show the category either, so a null keeps the stored one.
        val stored = recipeDao.getRecipe(recipe.id.id)
        val recipeEntity =
            recipe
                .toEntity()
                .copy(
                    isFavorite = stored?.isFavorite ?: false,
                    category = recipe.category ?: stored?.category,
                )

        val ingredients =
            recipe.ingredients.map { (food, measurement) ->
                val foodId = food.id

                RecipeIngredientEntity(
                    ingredientRecipeId = (foodId as? FoodId.Recipe)?.id,
                    ingredientProductId = (foodId as? FoodId.Product)?.id,
                    measurement = measurement.type,
                    quantity = measurement.rawValue,
                )
            }

        recipeDao.updateRecipeWithIngredients(recipeEntity, ingredients)
    }
}

private fun Recipe.toEntity(): RecipeEntity =
    RecipeEntity(
        id = this.id.id,
        name = this.name,
        servings = this.servings,
        note = this.note,
        isLiquid = this.isLiquid,
        category = this.category,
    )

private val RecipeIngredientEntity.foodId: FoodId
    get() =
        ingredientRecipeId?.let { FoodId.Recipe(it) }
            ?: ingredientProductId?.let { FoodId.Product(it) }
            ?: error(
                "RecipeIngredientEntity must have either ingredientRecipeId or ingredientProductId set"
            )
