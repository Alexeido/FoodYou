package com.maksimowiczm.foodyou.food.search.infrastructure.customsource

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingState
import androidx.paging.RemoteMediator
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceRemoteDataSource
import com.maksimowiczm.foodyou.food.infrastructure.customsource.model.CustomFoodProduct
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.search.infrastructure.room.CustomFoodSourcePagingKeyDao
import com.maksimowiczm.foodyou.food.search.infrastructure.room.CustomFoodSourcePagingKeyEntity
import kotlin.time.Instant

@OptIn(ExperimentalPagingApi::class)
internal class CustomFoodSourceRemoteMediator<K : Any, T : Any>(
    private val query: String,
    private val isBarcode: Boolean,
    private val baseUrl: String,
    private val username: String,
    private val password: String,
    private val transactionProvider: TransactionProvider,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val remoteDataSource: CustomFoodSourceRemoteDataSource,
    private val pagingKeyDao: CustomFoodSourcePagingKeyDao,
    private val productMapper: CustomFoodSourceProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) : RemoteMediator<K, T>() {

    override suspend fun initialize(): InitializeAction = InitializeAction.SKIP_INITIAL_REFRESH

    override suspend fun load(loadType: LoadType, state: PagingState<K, T>): MediatorResult {
        try {
            val page =
                when (loadType) {
                    LoadType.REFRESH -> {
                        return MediatorResult.Success(endOfPaginationReached = false)
                    }

                    LoadType.PREPEND -> {
                        return MediatorResult.Success(endOfPaginationReached = true)
                    }

                    // Handle barcode search as a special case.
                    LoadType.APPEND if (isBarcode) -> {
                        val response =
                            remoteDataSource
                                .getProduct(
                                    barcode = query,
                                    baseUrl = baseUrl,
                                    username = username,
                                    password = password,
                                )
                                .getOrElse {
                                    return if (it is RemoteFoodException.ProductNotFoundException) {
                                        MediatorResult.Success(endOfPaginationReached = true)
                                    } else {
                                        MediatorResult.Error(it)
                                    }
                                }

                        val product = response.toDomainProduct()

                        if (product != null) {
                            transactionProvider.withTransaction { product.insert() }
                        } else {
                            logger.d(TAG) {
                                "Failed to convert product: (name=${response.name}, code=${response.barcode})"
                            }
                        }

                        return MediatorResult.Success(endOfPaginationReached = true)
                    }

                    LoadType.APPEND -> {
                        val pagingKey = pagingKeyDao.getPagingKey(query)

                        if (pagingKey != null && pagingKey.totalCount <= pagingKey.fetchedCount) {
                            logger.d(TAG) { "No more pages to load for query: $query" }
                            return MediatorResult.Success(endOfPaginationReached = true)
                        }

                        (pagingKey?.fetchedCount?.div(PAGE_SIZE) ?: 0) + 1
                    }
                }

            logger.d(TAG) { "Loading page $page" }

            val response =
                remoteDataSource.queryProducts(
                    query = query,
                    page = page,
                    pageSize = PAGE_SIZE,
                    baseUrl = baseUrl,
                    username = username,
                    password = password,
                )

            val fetchedCount =
                ((response.page - 1) * response.pageSize).coerceAtLeast(0) + response.products.size

            pagingKeyDao.upsertPagingKey(
                CustomFoodSourcePagingKeyEntity(
                    queryString = query,
                    fetchedCount = fetchedCount,
                    totalCount = response.count,
                )
            )

            val products =
                response.products.map { remoteProduct ->
                    remoteProduct.toDomainProduct().also {
                        if (it == null) {
                            logger.d(TAG) {
                                "Failed to convert product: (name=${remoteProduct.name}, code=${remoteProduct.barcode})"
                            }
                        }
                    }
                }

            val now = dateProvider.nowInstant()
            transactionProvider.withTransaction {
                products.filterNotNull().forEach { product -> product.insert(now) }
            }

            val skipped = products.count { it == null }
            val endOfPaginationReached = (products.size + skipped) < PAGE_SIZE

            // Load until there is anything inserted
            return if (skipped == PAGE_SIZE) {
                logger.d(TAG) { "All products skipped, trying to load next page" }
                load(loadType, state)
            } else {
                MediatorResult.Success(endOfPaginationReached)
            }
        } catch (e: Exception) {
            logger.e(TAG, e) { "Error loading page" }
            return MediatorResult.Error(e)
        }
    }

    private fun CustomFoodProduct.toDomainProduct(): Product? =
        runCatching { this.let(productMapper::toRemoteProduct).let(remoteMapper::toModel) }
            .getOrNull()

    private suspend fun Product.insert(now: Instant = dateProvider.nowInstant()) {
        val id =
            productRepository.insertUniqueProduct(
                name = this.name,
                brand = this.brand,
                barcode = this.barcode,
                note = this.note,
                isLiquid = this.isLiquid,
                packageWeight = this.packageWeight,
                servingWeight = this.servingWeight,
                source = this.source,
                nutritionFacts = this.nutritionFacts,
                categories = this.categories,
            )

        if (id != null) {
            foodHistoryRepository.insert(
                foodId = id,
                history = FoodHistory.Downloaded(timestamp = now, url = this.source.url),
            )
        }
    }

    private companion object {
        private const val TAG = "CustomFoodSourceRemoteMediator"
        private const val PAGE_SIZE = 24
    }
}
