package com.maksimowiczm.foodyou.common.domain.search

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Who started a food search: the person on the search screen, or the in-app assistant. Carried
 * in the coroutine context so the search stack doesn't need a parameter threaded through every
 * mediator; the custom food source reports it to its server (X-Search-Origin), which tells manual
 * searches apart from AI ones in its admin panel.
 *
 * No element in the context means a manual search.
 */
class SearchOrigin(val value: String) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<SearchOrigin> {
        val Assistant = SearchOrigin("assistant")
    }
}
