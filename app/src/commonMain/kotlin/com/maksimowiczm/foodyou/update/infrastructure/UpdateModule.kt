package com.maksimowiczm.foodyou.update.infrastructure

import com.maksimowiczm.foodyou.app.BuildConfig
import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.applicationCoroutineScope
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.settings.domain.entity.Settings
import com.maksimowiczm.foodyou.update.domain.DismissedUpdateStore
import com.maksimowiczm.foodyou.update.domain.StableRelease
import com.maksimowiczm.foodyou.update.domain.UpdateController
import com.maksimowiczm.foodyou.update.domain.UpdateSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.statement.readRawBytes
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import io.ktor.http.userAgent
import io.ktor.serialization.kotlinx.json.json
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.onClose

/** Binds [com.maksimowiczm.foodyou.update.domain.UpdateInstaller] per platform. */
expect fun Module.updateInstallerDefinition()

fun Module.updateModule() {
    single(named(KtorUpdateSource::class.qualifiedName!!)) {
            HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        .onClose { it?.close() }
    single<UpdateSource> {
        KtorUpdateSource(
            client = get(named(KtorUpdateSource::class.qualifiedName!!)),
            networkConfig = get(),
            url = BuildConfig.UPDATE_URL,
        )
    }
    single<DismissedUpdateStore> { SettingsDismissedUpdateStore(userPreferencesRepository()) }
    updateInstallerDefinition()
    single {
        UpdateController(
            source = get(),
            installer = get(),
            dismissed = get(),
            currentBuild = BuildConfig.BUILD_NUMBER,
            scope = applicationCoroutineScope(),
            logger = get(),
        )
    }
}

internal class KtorUpdateSource(
    private val client: HttpClient,
    private val networkConfig: NetworkConfig,
    private val url: String,
) : UpdateSource {
    override suspend fun stable(): StableRelease? {
        val response = client.get(url) { userAgent(networkConfig.userAgent) }
        return when {
            // Sin versión estable publicada: no es un error, simplemente no hay nada que ofrecer.
            response.status == HttpStatusCode.NotFound -> null
            !response.status.isSuccess() -> error("Update server answered ${response.status}")
            else -> response.body<StableRelease>()
        }
    }

    override suspend fun image(url: String): ByteArray? =
        try {
            val response = client.get(url) { userAgent(networkConfig.userAgent) }
            if (response.status.isSuccess()) response.readRawBytes() else null
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // Sin foto el aviso sale igual: no merece la pena perder la actualización por ella.
            null
        }
}

internal class SettingsDismissedUpdateStore(
    private val settings: UserPreferencesRepository<Settings>
) : DismissedUpdateStore {
    override suspend fun get(): Int? = settings.observe().first().dismissedUpdateBuild

    override suspend fun set(build: Int) {
        settings.update { copy(dismissedUpdateBuild = build) }
    }
}
