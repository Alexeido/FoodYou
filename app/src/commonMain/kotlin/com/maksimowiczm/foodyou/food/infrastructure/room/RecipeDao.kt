package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
abstract class RecipeDao {

    @Query("SELECT * FROM Recipe WHERE id = :recipeId")
    abstract fun observeRecipe(recipeId: Long): Flow<RecipeEntity?>

    @Query("SELECT * FROM RecipeIngredient WHERE recipeId = :recipeId")
    abstract fun observeRecipeIngredients(recipeId: Long): Flow<List<RecipeIngredientEntity>>

    @Insert protected abstract suspend fun insertRecipe(recipe: RecipeEntity): Long

    @Insert
    protected abstract suspend fun insertRecipeIngredient(ingredient: RecipeIngredientEntity): Long

    @Transaction
    open suspend fun insertRecipeWithIngredients(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity>,
    ): Long {
        val recipeId = insertRecipe(recipe)

        ingredients.forEach { ingredient ->
            val recipeIngredient = ingredient.copy(recipeId = recipeId)
            insertRecipeIngredient(recipeIngredient)
        }

        return recipeId
    }

    @Update protected abstract suspend fun updateRecipeEntity(recipeEntity: RecipeEntity)

    @Query("DELETE FROM RecipeIngredient WHERE recipeId = :recipeId")
    protected abstract suspend fun deleteRecipeIngredientsByRecipeId(recipeId: Long)

    // Deletes all ingredients for the recipe and then inserts new ones
    @Transaction
    open suspend fun updateRecipeWithIngredients(
        recipe: RecipeEntity,
        ingredients: List<RecipeIngredientEntity>,
    ) {
        updateRecipeEntity(recipe)
        deleteRecipeIngredientsByRecipeId(recipe.id)

        ingredients.forEach { ingredient ->
            val recipeIngredientEntity = ingredient.copy(recipeId = recipe.id)
            insertRecipeIngredient(recipeIngredientEntity)
        }
    }

    @Delete abstract suspend fun delete(recipe: RecipeEntity)

    /**
     * Recipes matching an FTS query built by `recipeFtsQuery`, newest first.
     *
     * Through the full-text index rather than LIKE so accents and case do not matter. Only recipes
     * with at least one ingredient: the repository builds a recipe by combining the flows of its
     * ingredients, and `combine` over an empty list never emits - anything awaiting the first value
     * of an empty recipe would hang forever.
     */
    @Query(
        """
        SELECT r.id FROM Recipe r JOIN RecipeFts fts ON r.id = fts.rowid
        WHERE RecipeFts MATCH :match
          AND EXISTS (SELECT 1 FROM RecipeIngredient WHERE recipeId = r.id)
        ORDER BY r.id DESC
        LIMIT :limit
        """
    )
    abstract suspend fun searchRecipeIds(match: String, limit: Int): List<Long>

    @Query("SELECT * FROM Recipe WHERE id = :recipeId")
    abstract suspend fun getRecipe(recipeId: Long): RecipeEntity?

    @Query("SELECT * FROM RecipeIngredient WHERE recipeId = :recipeId")
    abstract suspend fun getRecipeIngredients(recipeId: Long): List<RecipeIngredientEntity>

    @Query("DELETE FROM Recipe WHERE id = :recipeId")
    abstract suspend fun deleteById(recipeId: Long)

    @Query("UPDATE Recipe SET isFavorite = :isFavorite WHERE id = :recipeId")
    abstract suspend fun updateFavorite(recipeId: Long, isFavorite: Boolean)
}
