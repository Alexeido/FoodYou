package com.maksimowiczm.foodyou.food.infrastructure.customsource

import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.domain.search.SearchOrigin
import com.maksimowiczm.foodyou.common.system.InstallationId
import com.maksimowiczm.foodyou.food.domain.entity.RemoteFoodException
import com.maksimowiczm.foodyou.food.infrastructure.customsource.model.CustomFoodPageResponse
import com.maksimowiczm.foodyou.food.infrastructure.customsource.model.CustomFoodProduct
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.basicAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.userAgent
import io.ktor.utils.io.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Talks to the user's self-hosted custom food source. Unlike [Open Food
 * Facts][com.maksimowiczm.foodyou.food.infrastructure.openfoodfacts.OpenFoodFactsRemoteDataSource]
 * or USDA, there is no fixed base URL and no rate limiting (it's the user's own server) — base URL
 * and Basic Auth credentials are resolved by the caller (see
 * [com.maksimowiczm.foodyou.food.search.infrastructure.customsource.CustomFoodSourceRemoteMediatorFactory])
 * and passed in per request.
 */
internal class CustomFoodSourceRemoteDataSource(
    private val client: HttpClient,
    private val networkConfig: NetworkConfig,
    private val logger: Logger,
    private val installationId: InstallationId,
) {
    suspend fun getProduct(
        barcode: String,
        baseUrl: String,
        username: String,
        password: String,
    ): Result<CustomFoodProduct> =
        try {
            val origin = searchOrigin()
            val response =
                client.get("$baseUrl/api/v2/product/$barcode") {
                    userAgent(networkConfig.userAgent)
                    basicAuth(username, password)
                    installationId.get()?.let { header(DEVICE_ID_HEADER, it) }
                    origin?.let { header(ORIGIN_HEADER, it) }
                }

            when (response.status) {
                HttpStatusCode.OK -> Result.success(response.body<CustomFoodProduct>())

                HttpStatusCode.NotFound -> {
                    logger.d(TAG) { "Product not found for code: $barcode" }
                    Result.failure(RemoteFoodException.ProductNotFoundException())
                }

                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden -> {
                    logger.w(TAG) { "Unauthorized request to custom food source" }
                    Result.failure(RemoteFoodException.Custom.Unauthorized())
                }

                else -> {
                    logger.e(TAG) { "Unexpected response: ${response.status}" }
                    Result.failure(
                        RemoteFoodException.Unknown("Unexpected response: ${response.status}")
                    )
                }
            }
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            handleException(e)
        }

    suspend fun queryProducts(
        query: String,
        page: Int?,
        pageSize: Int,
        baseUrl: String,
        username: String,
        password: String,
    ): CustomFoodPageResponse =
        try {
            val origin = searchOrigin()
            val response =
                client.get("$baseUrl/search") {
                    userAgent(networkConfig.userAgent)
                    basicAuth(username, password)
                    installationId.get()?.let { header(DEVICE_ID_HEADER, it) }
                    origin?.let { header(ORIGIN_HEADER, it) }
                    parameter("query", query)
                    parameter("page", page)
                    parameter("page_size", pageSize)
                }

            when (response.status) {
                HttpStatusCode.Unauthorized,
                HttpStatusCode.Forbidden -> throw RemoteFoodException.Custom.Unauthorized()

                else -> response.body<CustomFoodPageResponse>()
            }
        } catch (e: Exception) {
            when (e) {
                is CancellationException -> throw e
                is RemoteFoodException -> throw e
                is HttpRequestTimeoutException ->
                    throw RemoteFoodException.Unknown("Search timed out. Check your connection and try again.")
                else -> throw RemoteFoodException.Unknown(e.message)
            }
        }

    /** Set by the in-app assistant around its searches; none means the person searched by hand. */
    private suspend fun searchOrigin(): String? = currentCoroutineContext()[SearchOrigin]?.value

    private fun <T> handleException(e: Exception): Result<T> =
        when (e) {
            is RemoteFoodException -> {
                logger.e(TAG) { "Request failed: ${e.message}" }
                Result.failure(e)
            }

            else -> {
                logger.e(TAG) { "Request failed: ${e.message}" }
                Result.failure(RemoteFoodException.Unknown(e.message))
            }
        }

    private companion object {
        private const val TAG = "CustomFoodSourceRemoteDataSource"

        /**
         * Lets the server owner see how many installs use one account (and block one). Without
         * it, a server that requires it rejects the request as not coming from the app.
         */
        private const val DEVICE_ID_HEADER = "X-Device-Id"

        /** Lets the server owner tell manual searches from the assistant's. */
        private const val ORIGIN_HEADER = "X-Search-Origin"
    }
}
