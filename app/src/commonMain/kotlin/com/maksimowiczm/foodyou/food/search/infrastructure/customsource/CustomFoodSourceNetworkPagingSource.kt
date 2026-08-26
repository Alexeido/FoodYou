package com.maksimowiczm.foodyou.food.search.infrastructure.customsource

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import com.maksimowiczm.foodyou.common.domain.database.TransactionProvider
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.domain.repository.FoodHistoryRepository
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceProductMapper
import com.maksimowiczm.foodyou.food.infrastructure.customsource.CustomFoodSourceRemoteDataSource
import com.maksimowiczm.foodyou.food.infrastructure.network.RemoteProductMapper
import com.maksimowiczm.foodyou.food.search.domain.FoodSearch
import kotlinx.coroutines.flow.first

/**
 * Streams text-search results straight from the user's self-hosted server, preserving the **server's
 * own ordering/relevance**.
 *
 * The mirror-backed path ([CustomFoodSourceRemoteMediator] + Room) can't do that: it re-queries the
 * whole local cache with `ORDER BY headline`, so results come back alphabetically and mixed with
 * everything previously downloaded. Products are still cached (and refreshed) as they stream, so
 * offline fallback keeps working.
 */
internal class CustomFoodSourceNetworkPagingSource(
    private val query: String,
    private val preferencesRepository:
        UserPreferencesRepository<com.maksimowiczm.foodyou.food.search.domain.FoodSearchPreferences>,
    private val credentialsRepository: CustomFoodSourceCredentialsRepository,
    private val remoteDataSource: CustomFoodSourceRemoteDataSource,
    private val productRepository: ProductRepository,
    private val foodHistoryRepository: FoodHistoryRepository,
    private val transactionProvider: TransactionProvider,
    private val productMapper: CustomFoodSourceProductMapper,
    private val remoteMapper: RemoteProductMapper,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) : PagingSource<Int, FoodSearch>() {

    /**
     * Local ids already emitted by this source. Several distinct server rows can collapse onto the
     * same cached product (same source EAN, or same name+brand when the source sends no EAN), and
     * emitting that id twice crashes LazyColumn with a duplicate-key error. Pages load sequentially
     * on one source instance, and a refresh builds a new instance, so a plain set is enough.
     */
    private val emittedIds = mutableSetOf<Long>()

    override fun getRefreshKey(state: PagingState<Int, FoodSearch>): Int? = null

    override suspend fun load(params: LoadParams<Int>): LoadResult<Int, FoodSearch> {
        val page = params.key ?: 1

        return try {
            val baseUrl =
                preferencesRepository.observe().first().custom.baseUrl?.takeIf { it.isNotBlank() }
                    ?: return LoadResult.Page(emptyList(), null, null)
            val credentials =
                credentialsRepository.observeCredentials().first()
                    ?: return LoadResult.Page(emptyList(), null, null)

            val response =
                remoteDataSource.queryProducts(
                    query = query,
                    page = page,
                    pageSize = params.loadSize,
                    baseUrl = baseUrl,
                    username = credentials.username,
                    password = credentials.password,
                )

            val now = dateProvider.nowInstant()

            // Keep the server's order; cache each product as it streams through. All the writes for
            // a page run in one transaction — a transaction per row makes a 24-result page notably
            // slower on device (each one also re-indexes the FTS table).
            val foods =
                transactionProvider.withTransaction {
                    response.products.mapNotNull { remoteProduct ->
                        runCatching {
                            val product =
                                remoteProduct
                                    .let(productMapper::toRemoteProduct)
                                    .let(remoteMapper::toModel)

                            val upsert =
                                productRepository.insertOrRefreshProduct(
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
                                )

                            // Two server rows can resolve to the same cached product — show once.
                            if (!emittedIds.add(upsert.id.id)) return@runCatching null

                            // Only newly cached products get a "downloaded" event; re-recording it
                            // on every search would grow the history table without adding meaning.
                            if (upsert.created) {
                                foodHistoryRepository.insert(
                                    foodId = upsert.id,
                                    history =
                                        FoodHistory.Downloaded(
                                            timestamp = now,
                                            url = product.source.url,
                                        ),
                                )
                            }

                            product.toFoodSearch(upsert.id)
                        }
                            .getOrElse { e ->
                                logger.d(TAG) { "Skipping product: ${e.message}" }
                                null
                            }
                    }
                }

            val reachedEnd = response.products.size < params.loadSize
            LoadResult.Page(
                data = foods,
                prevKey = if (page == 1) null else page - 1,
                nextKey = if (reachedEnd) null else page + 1,
            )
        } catch (e: RemoteFoodException) {
            LoadResult.Error(e)
        } catch (e: Exception) {
            LoadResult.Error(e)
        }
    }

    private companion object {
        const val TAG = "CustomFoodSourceNetworkPagingSource"
    }
}

private fun Product.toFoodSearch(foodId: FoodId.Product): FoodSearch.Product {
    val suggestedMeasurement =
        when {
            servingWeight != null -> Measurement.Serving(1.0)
            packageWeight != null -> Measurement.Package(1.0)
            isLiquid -> Measurement.Milliliter(100.0)
            else -> Measurement.Gram(100.0)
        }

    return FoodSearch.Product(
        id = foodId,
        headline = headline,
        isLiquid = isLiquid,
        isFavorite = isFavorite,
        nutritionFacts = nutritionFacts,
        totalWeight = packageWeight,
        servingWeight = servingWeight,
        categories = categories,
        suggestedMeasurement = suggestedMeasurement,
    )
}
