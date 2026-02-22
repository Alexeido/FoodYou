package com.maksimowiczm.foodyou.fooddiary.infrastructure.room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ManualDiaryEntryDao {

    @Query("SELECT * FROM ManualDiaryEntry WHERE id = :id")
    fun observe(id: Long): Flow<ManualDiaryEntryEntity?>

    @Query(
        """
            SELECT *
            FROM ManualDiaryEntry
            WHERE mealId = :mealId AND dateEpochDay = :dateEpochDay
            ORDER BY position ASC, id ASC
        """
    )
    fun observeAll(mealId: Long, dateEpochDay: Long): Flow<List<ManualDiaryEntryEntity>>

    @Query(
        """
        SELECT COALESCE(MAX(position), -1)
        FROM ManualDiaryEntry
        WHERE mealId = :mealId AND dateEpochDay = :dateEpochDay
        """
    )
    suspend fun getMaxPosition(mealId: Long, dateEpochDay: Long): Int

    @Query("UPDATE ManualDiaryEntry SET position = :position WHERE id = :id")
    suspend fun updatePosition(id: Long, position: Int)

    @Query(
        """
        UPDATE ManualDiaryEntry
        SET position = :position, mealId = :mealId, updatedEpochSeconds = :updatedAt
        WHERE id = :id
        """
    )
    suspend fun updatePositionAndMeal(
        id: Long,
        mealId: Long,
        position: Int,
        updatedAt: Long,
    )

    @Insert suspend fun insert(entry: ManualDiaryEntryEntity): Long

    @Update suspend fun update(entry: ManualDiaryEntryEntity)

    @Query("DELETE FROM ManualDiaryEntry WHERE id = :id") suspend fun delete(id: Long)

    @Query(
        "SELECT DISTINCT dateEpochDay FROM ManualDiaryEntry WHERE dateEpochDay BETWEEN :from AND :to"
    )
    fun observeActiveDays(from: Long, to: Long): Flow<List<Long>>
}
