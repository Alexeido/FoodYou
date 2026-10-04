package com.maksimowiczm.foodyou.sync.infrastructure

import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.common.infrastructure.koin.applicationCoroutineScope
import com.maksimowiczm.foodyou.sync.domain.SyncApi
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.time.Clock
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.onClose

/** Binds [BackgroundSync] per platform. */
expect fun Module.backgroundSyncDefinition()

fun Module.syncModule() {
    single(named(KtorSyncApi::class.qualifiedName!!)) {
            HttpClient {
                install(HttpTimeout) {
                    requestTimeoutMillis = 60_000
                    connectTimeoutMillis = 15_000
                }
                install(ContentEncoding) { gzip() }
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        }
        .onClose { it?.close() }
    single<SyncApi> {
        KtorSyncApi(
            client = get(named(KtorSyncApi::class.qualifiedName!!)),
            networkConfig = get(),
            installationId = get(),
        )
    }
    single<SyncConfigRepository> {
        DataStoreSyncConfigRepository(masterCrypto = get(), dataStore = get(), logger = get())
    }
    single<SqlExecutor> { RoomSqlExecutor(get<FoodYouDatabase>()) }
    single { SyncLocalStore(goals = getOrNull()) }
    single {
        SyncEngine(
            executor = get(),
            store = get(),
            api = get(),
            configRepository = get(),
            now = { Clock.System.now().toEpochMilliseconds() },
            logger = get(),
        )
    }
    backgroundSyncDefinition()
    single {
        SyncScheduler(
            engine = get(),
            configRepository = get(),
            api = get(),
            database = get<FoodYouDatabase>(),
            background = get(),
            scope = applicationCoroutineScope(),
            goals = getOrNull(),
        )
    }
}
