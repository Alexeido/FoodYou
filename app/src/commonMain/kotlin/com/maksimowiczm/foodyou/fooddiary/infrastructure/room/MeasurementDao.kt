package com.maksimowiczm.foodyou.fooddiary.infrastructure.room

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

@Dao
abstract class MeasurementDao {

    @Insert abstract suspend fun insertMeasurement(measurement: MeasurementEntity): Long

    @Insert abstract suspend fun insertMeasurements(measurements: List<MeasurementEntity>)

    @Transaction
    open suspend fun replaceMeasurement(
        measurementId: Long,
        measurements: List<MeasurementEntity>,
    ) {
        val measurement = observeMeasurementById(measurementId).first()

        if (measurement == null) {
            return
        }

        deleteMeasurement(measurement)
        insertMeasurements(measurements)
    }

    @Update abstract suspend fun updateMeasurement(measurement: MeasurementEntity)

    @Delete protected abstract suspend fun deleteMeasurement(measurement: MeasurementEntity)

    @Transaction
    open suspend fun deleteMeasurement(id: Long) {
        val measurement = observeMeasurementById(id).first() ?: return
        deleteMeasurement(measurement)
    }

    @Query(
        """
        SELECT *
        FROM Measurement
        WHERE id = :measurementId 
        """
    )
    abstract fun observeMeasurementById(measurementId: Long): Flow<MeasurementEntity?>

    @Query(
        """
        SELECT *
        FROM Measurement
        WHERE
            mealId = :mealId
            AND epochDay = :epochDay
        ORDER BY position ASC, id ASC
        """
    )
    abstract fun observeMeasurements(mealId: Long, epochDay: Long): Flow<List<MeasurementEntity>>

    @Query(
        """
        SELECT COALESCE(MAX(position), -1)
        FROM Measurement
        WHERE mealId = :mealId AND epochDay = :epochDay
        """
    )
    abstract suspend fun getMaxPosition(mealId: Long, epochDay: Long): Int

    @Query("UPDATE Measurement SET position = :position WHERE id = :id")
    abstract suspend fun updatePosition(id: Long, position: Int)

    @Query(
        """
        UPDATE Measurement
        SET position = :position, mealId = :mealId, updatedAt = :updatedAt
        WHERE id = :id
        """
    )
    abstract suspend fun updatePositionAndMeal(
        id: Long,
        mealId: Long,
        position: Int,
        updatedAt: Long,
    )

    @Query(
        """
        UPDATE Measurement
        SET isEaten = :isEaten
        WHERE id = :id
        """
    )
    protected abstract suspend fun updateMeasurementIsEaten(id: Long, isEaten: Boolean)

    open suspend fun setEaten(id: Long, isEaten: Boolean) {
        updateMeasurementIsEaten(id, isEaten)
    }

    @Query(
        """
        SELECT *
        FROM DiaryProduct
        WHERE id = :id
        """
    )
    abstract fun observeDiaryProduct(id: Long): Flow<DiaryProductEntity?>

    @Insert abstract suspend fun insertDiaryProduct(diaryProduct: DiaryProductEntity): Long

    @Insert
    abstract suspend fun insertDiaryRecipeIngredient(
        diaryRecipeIngredient: DiaryRecipeIngredientEntity
    )

    @Insert abstract suspend fun insertDiaryRecipe(diaryRecipe: DiaryRecipeEntity): Long

    @Query(
        """
        SELECT *
        FROM DiaryRecipe
        WHERE id = :id
        """
    )
    abstract fun observeDiaryRecipe(id: Long): Flow<DiaryRecipeEntity?>

    @Query(
        """
        SELECT *
        FROM DiaryRecipeIngredient
        WHERE recipeId = :id
        """
    )
    abstract fun observeDiaryRecipeIngredients(id: Long): Flow<List<DiaryRecipeIngredientEntity>>

    @Delete protected abstract suspend fun deleteDiaryProduct(diaryProduct: DiaryProductEntity)

    @Transaction
    open suspend fun deleteDiaryProduct(id: Long) {
        val diaryProduct = observeDiaryProduct(id).first() ?: return
        deleteDiaryProduct(diaryProduct)
    }

    @Delete protected abstract suspend fun deleteDiaryRecipe(diaryRecipe: DiaryRecipeEntity)

    @Transaction
    open suspend fun deleteDiaryRecipe(id: Long) {
        val diaryRecipe = observeDiaryRecipe(id).first() ?: return
        deleteDiaryRecipe(diaryRecipe)
    }
}
