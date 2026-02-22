package com.maksimowiczm.foodyou.food.search.infrastructure.openfoodfacts

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.OpenFoodFactsProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.OpenFoodFactsRemoteDataSource
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch

internal class OpenFoodFactsNetworkPagingSource(
    private val query: String,
    private val country: String?,
    private val remoteDataSource: OpenFoodFactsRemoteDataSource,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val offMapper: OpenFoodFactsProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
    private val baseUrl: String = OpenFoodFactsRemoteDataSource.API_URL,
) : PagingSource<Int, FoodSearch>() {

    override fun getRefreshKey(state: PagingState<Int, FoodSearch>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FoodSearch> {
        val page = params.key ?: 1
        return try {
            val response = remoteDataSource.queryProducts(
                query = query,
                countries = country,
                page = page,
                pageSize = params.loadSize,
                baseUrl = baseUrl,
            )
            val now = dateProvider.nowInstant()
            val foods = response.products.mapNotNull { offProduct ->
                runCatching {
                    val product = offMapper.toRemoteProduct(offProduct).let(remoteMapper::toModel)

                    // Skip products with null macros — they'd render as red error cards in the UI
                    val nf = product.nutritionFacts
                    if (listOf(nf.proteins, nf.carbohydrates, nf.fats, nf.energy)
                            .any { it is NutrientValue.Incomplete && it.value == null }
                    ) return@runCatching null

                    val id = productRepository.insertUniqueProduct(
                        name = product.name,
                        brand = product.brand,
                        barcode = product.barcode,
                        note = product.note,
                        isLiquid = product.isLiquid,
                        packageWeight = product.packageWeight,
                        servingWeight = product.servingWeight,
                        source = product.source,
                        nutritionFacts = product.nutritionFacts,
                        categories = product.categories,
                    ) ?: return@runCatching null

                    foodHistoryRepository.insert(
                        foodId = id,
                        history = FoodHistory.Downloaded(timestamp = now, url = product.source.url),
                    )

                    product.toFoodSearch(id)
                }.getOrElse { e ->
                    logger.d(TAG) { "Skipping product: ${e.message}" }
                    null
                }
            }
            LoadResult.Page(
                data = foods,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (response.products.size < params.loadSize) null else page + 1,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private companion object {
        const val TAG = "OpenFoodFactsNetworkPagingSource"
    }
}

internal class OpenFoodFactsBarcodePagingSource(
    private val barcode: String,
    private val country: String?,
    private val remoteDataSource: OpenFoodFactsRemoteDataSource,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val offMapper: OpenFoodFactsProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
    private val baseUrl: String = OpenFoodFactsRemoteDataSource.API_URL,
) : PagingSource<Int, FoodSearch>() {

    override fun getRefreshKey(state: PagingState<Int, FoodSearch>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FoodSearch> {
        if (params is LoadParams.Append) {
            return LoadResult.Page(emptyList(), null, null)
        }
        return try {
            val offProduct = remoteDataSource.getProduct(
                barcode = barcode,
                countries = country,
                baseUrl = baseUrl,
            ).getOrElse { e ->
                return if (e is RemoteFoodException.ProductNotFoundException) {
                    LoadResult.Page(emptyList(), null, null)
                } else {
                    LoadResult.Error(e as Exception)
                }
            }

            val now = dateProvider.nowInstant()
            val product = offMapper.toRemoteProduct(offProduct).let(remoteMapper::toModel)

            val id = productRepository.insertUniqueProduct(
                name = product.name,
                brand = product.brand,
                barcode = product.barcode,
                note = product.note,
                isLiquid = product.isLiquid,
                packageWeight = product.packageWeight,
                servingWeight = product.servingWeight,
                source = product.source,
                nutritionFacts = product.nutritionFacts,
                categories = product.categories,
            ) ?: return LoadResult.Page(emptyList(), null, null)

            foodHistoryRepository.insert(
                foodId = id,
                history = FoodHistory.Downloaded(timestamp = now, url = product.source.url),
            )

            LoadResult.Page(
                data = listOf(product.toFoodSearch(id)),
                prevKey = null,
                nextKey = null,
            )
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private companion object {
        @Suppress("unused")
        const val TAG = "OpenFoodFactsBarcodePagingSource"
    }
}

private fun Product.toFoodSearch(foodId: FoodId.Product): FoodSearch.Product {
    val suggestedMeasurement = when {
        servingWeight != null -> Measurement.Serving(1.0)
        packageWeight != null -> Measurement.Package(1.0)
        isLiquid -> Measurement.Milliliter(100.0)
        else -> Measurement.Gram(100.0)
    }
    return FoodSearch.Product(
        id = foodId,
        headline = headline,
        isLiquid = isLiquid,
        isFavorite = false,
        nutritionFacts = nutritionFacts,
        totalWeight = packageWeight,
        servingWeight = servingWeight,
        categories = categories,
        suggestedMeasurement = suggestedMeasurement,
    )
}
