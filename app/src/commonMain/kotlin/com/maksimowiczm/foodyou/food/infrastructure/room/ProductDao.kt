package com.maksimowiczm.foodyou.food.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.maksimowiczm.foodyou.common.infrastructure.room.FoodSourceType
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ProductDao {
    @Query(
        """
        SELECT *
        FROM Product
        WHERE id = :id
        """
    )
    abstract fun observeProduct(id: Long): Flow<ProductEntity?>

    @Query(
        """
        SELECT *
        FROM Product
        LIMIT :limit OFFSET :offset
        """
    )
    abstract fun observeProducts(limit: Int, offset: Int): Flow<List<ProductEntity>>

    @Insert abstract suspend fun insertProduct(product: ProductEntity): Long

    @Update abstract suspend fun updateProduct(product: ProductEntity)

    @Delete abstract suspend fun deleteProduct(product: ProductEntity)

    @Query(
        """
        UPDATE Product
        SET isFavorite = :isFavorite
        WHERE id = :id
        """
    )
    abstract suspend fun updateFavorite(id: Long, isFavorite: Boolean)

    @Query(
        """
        SELECT EXISTS (
            SELECT 1
            FROM Product
            WHERE sourceBarcode = :sourceBarcode AND sourceType = :source
        )
        """
    )
    protected abstract suspend fun existsProductBySourceBarcode(
        sourceBarcode: String,
        source: FoodSourceType,
    ): Boolean

    /**
     * Deletes transient search-mirror products: remote-sourced rows the user never kept. A row is
     * protected if it is favorite, edited, referenced by a measurement suggestion (i.e. it was
     * logged at least once), or used as a recipe ingredient. Only re-fetchable remote [sources]
     * (OFF/USDA/Custom) are eligible — never `User` or the bundled Swiss composition data.
     *
     * Safe because the diary keeps its own embedded snapshot (`DiaryProduct`); deleting a mirror
     * row never affects logged history.
     *
     * @return the number of rows deleted.
     */
    @Query(
        """
        DELETE FROM Product
        WHERE isFavorite = 0
          AND isEdited = 0
          AND sourceType IN (:sources)
          AND id NOT IN (
              SELECT productId FROM MeasurementSuggestion WHERE productId IS NOT NULL
          )
          AND id NOT IN (
              SELECT ingredientProductId FROM RecipeIngredient WHERE ingredientProductId IS NOT NULL
          )
        """
    )
    abstract suspend fun purgeStaleProducts(sources: List<FoodSourceType>): Int

    @Query(
        """
        SELECT EXISTS (
            SELECT 1
            FROM Product
            WHERE name = :name AND
                  (:brand IS NULL OR brand = :brand) AND
                  (:barcode IS NULL OR barcode = :barcode) AND
                  :source = sourceType
        )
        """
    )
    protected abstract suspend fun existsProductByNameAndBrand(
        name: String,
        brand: String?,
        barcode: String?,
        source: FoodSourceType,
    ): Boolean

    @Query(
        """
        SELECT *
        FROM Product
        WHERE sourceBarcode = :sourceBarcode AND sourceType = :source
        LIMIT 1
        """
    )
    protected abstract suspend fun findBySourceBarcode(
        sourceBarcode: String,
        source: FoodSourceType,
    ): ProductEntity?

    @Query(
        """
        SELECT *
        FROM Product
        WHERE name = :name AND brand IS :brand AND sourceType = :source
        LIMIT 1
        """
    )
    protected abstract suspend fun findByNameBrand(
        name: String,
        brand: String?,
        source: FoodSourceType,
    ): ProductEntity?

    /**
     * Inserts the product, or — when it already exists — refreshes the cached copy and returns its
     * id. Used by live network searches so results always resolve to a row (never skipped as a
     * "duplicate") and cached macros stay current.
     *
     * User-edited rows ([ProductEntity.isEdited]) are never overwritten; favorites keep their flag.
     */
    @Transaction
    open suspend fun insertOrRefreshProduct(product: ProductEntity): ProductUpsert {
        val existing =
            product.sourceBarcode?.let { findBySourceBarcode(it, product.sourceType) }
                ?: findByNameBrand(product.name, product.brand, product.sourceType)

        if (existing == null) {
            return ProductUpsert(id = insertProduct(product), created = true)
        }

        if (!existing.isEdited) {
            updateProduct(
                product.copy(
                    id = existing.id,
                    isFavorite = existing.isFavorite,
                    isEdited = false,
                )
            )
        }

        return ProductUpsert(id = existing.id, created = false)
    }

    /**
     * Inserts a single product into the database if it does not already exist.
     *
     * Uniqueness is keyed on the source identity when available: if [ProductEntity.sourceBarcode]
     * is non-null, dedup is `(sourceBarcode, sourceType)` alone — immune to the source renaming the
     * product, and stable even after the user overrides the visible barcode. Otherwise (user-created
     * products without a source EAN) it falls back to `(name, brand, barcode, sourceType)`.
     *
     * @param product The product to be inserted.
     * @return The ID of the inserted product, or null if the product already exists.
     */
    @Transaction
    open suspend fun insertUniqueProduct(product: ProductEntity): Long? {
        val exists =
            if (product.sourceBarcode != null) {
                existsProductBySourceBarcode(product.sourceBarcode, product.sourceType)
            } else {
                existsProductByNameAndBrand(
                    name = product.name,
                    brand = product.brand,
                    barcode = product.barcode,
                    source = product.sourceType,
                )
            }

        return if (!exists) insertProduct(product) else null
    }
}

/** Outcome of [ProductDao.insertOrRefreshProduct]: the row's id and whether it was newly created. */
data class ProductUpsert(val id: Long, val created: Boolean)
