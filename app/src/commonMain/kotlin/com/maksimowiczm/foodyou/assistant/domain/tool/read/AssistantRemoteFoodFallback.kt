package com.maksimowiczm.foodyou.assistant.domain.tool.read

import androidx.paging.ExperimentalPagingApi
import androidx.paging.LoadType
import androidx.paging.PagingConfig
import androidx.paging.PagingState
import com.maksimowiczm.foodyou.common.domain.search.searchQuery
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.food.search.domain.FoodRemoteMediatorFactoryAggregate

/**
 * [SearchFoodTool] only reads the local Room mirror - fast, and free of a network call on every
 * turn - but a food nobody has searched for yet through the real search screen simply isn't there,
 * so the assistant sees an empty result for a term the user's own server would happily answer.
 *
 * This replicates, best-effort, what the search screen's [ProductRemoteMediatorFactory][com.maksimowiczm.foodyou.food.search.domain.ProductRemoteMediatorFactory]s
 * do on a cache miss: ask each enabled remote source for one page directly (bypassing the Paging3
 * `Pager`, which the assistant has no use for) and let it cache into Room, so the very next local
 * query can find it. Errors from one source (down, unauthorized, ...) must not block the others.
 */
@OptIn(ExperimentalPagingApi::class)
class AssistantRemoteFoodFallback(
    private val mediators: FoodRemoteMediatorFactoryAggregate,
    private val logger: Logger,
) {
    suspend fun fetchIntoCache(query: String, pageSize: Int) {
        val sq = searchQuery(query)
        val state =
            PagingState<Any, Any>(
                pages = emptyList(),
                anchorPosition = null,
                config = PagingConfig(pageSize = pageSize),
                leadingPlaceholderCount = 0,
            )

        listOf(
                mediators.customRemoteMediatorFactory,
                mediators.openFoodFactsRemoteMediatorFactory,
                mediators.usdaRemoteMediatorFactory,
            )
            .forEach { factory ->
                try {
                    val mediator = factory.create<Any, Any>(sq, pageSize) ?: return@forEach
                    // El fetch real de la primera pagina ocurre en APPEND: REFRESH en estos
                    // mediators no hace red, solo existe para encajar en el contrato de Paging3.
                    mediator.load(LoadType.APPEND, state)
                } catch (e: Exception) {
                    logger.w(TAG) { "Fallo al consultar una fuente remota: ${e.message}" }
                }
            }
    }

    private companion object {
        const val TAG = "AssistantRemoteFoodFallback"
    }
}
