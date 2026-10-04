package com.maksimowiczm.foodyou.food.search.infrastructure.room

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface CustomFoodSourcePagingKeyDao {

    @Upsert suspend fun upsertPagingKey(pagingKey: CustomFoodSourcePagingKeyEntity)

    @Query("SELECT * FROM CustomFoodSourcePagingKey WHERE queryString = :query")
    suspend fun getPagingKey(query: String): CustomFoodSourcePagingKeyEntity?
}
