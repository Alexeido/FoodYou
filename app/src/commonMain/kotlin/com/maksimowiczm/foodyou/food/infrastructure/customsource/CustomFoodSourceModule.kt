package com.maksimowiczm.foodyou.food.infrastructure.customsource

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.food.domain.repository.RemoteProductRefresher
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.onClose

internal fun Module.customFoodSourceModule() {
    single(named(CustomFoodSourceRemoteDataSource::class.qualifiedName!!)) {
            HttpClient {
                install(HttpTimeout)
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
                install(ContentEncoding) { gzip() }
                install(HttpRequestRetry) {
                    retryOnServerErrors(maxRetries = 1)
                    retryOnException(maxRetries = 1, retryOnTimeout = true)
                    exponentialDelay()
                }
            }
        }
        .onClose { it?.close() }
    factory {
        CustomFoodSourceRemoteDataSource(
            client = get(named(CustomFoodSourceRemoteDataSource::class.qualifiedName!!)),
            get(),
            get(),
        )
    }
    factory {
        CustomFoodSourceFacade(
            dataSource = get(),
            mapper = get(),
            preferencesRepository = userPreferencesRepository(),
            credentialsRepository = get(),
            logger = get(),
        )
    }
    factoryOf(::CustomFoodSourceProductMapper)
    factory<RemoteProductRefresher> {
        CustomRemoteProductRefresher(
            preferencesRepository = userPreferencesRepository(),
            credentialsRepository = get(),
            remoteDataSource = get(),
            productMapper = get(),
            remoteMapper = get(),
        )
    }
}
