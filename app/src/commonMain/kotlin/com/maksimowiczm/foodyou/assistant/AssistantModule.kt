package com.maksimowiczm.foodyou.assistant

import com.maksimowiczm.foodyou.assistant.domain.query.DailyTotalsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.DiaryRangeUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.DiaryReader
import com.maksimowiczm.foodyou.assistant.domain.query.MealTimingStatsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.NutrientAttributionUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.SearchDiaryUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.TopBrandsUseCase
import com.maksimowiczm.foodyou.assistant.domain.query.TopFoodsUseCase
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.infrastructure.journal.RoomChangeJournal
import com.maksimowiczm.foodyou.assistant.infrastructure.room.AssistantDatabase
import kotlinx.serialization.json.Json
import com.maksimowiczm.foodyou.assistant.domain.AgentLoop
import com.maksimowiczm.foodyou.assistant.domain.AssistantCredentialsRepository
import com.maksimowiczm.foodyou.assistant.domain.AssistantMemoryRepository
import com.maksimowiczm.foodyou.assistant.domain.SystemPromptBuilder
import com.maksimowiczm.foodyou.assistant.domain.AssistantConversationRepository
import com.maksimowiczm.foodyou.assistant.infrastructure.RoomAssistantConversationRepository
import com.maksimowiczm.foodyou.assistant.infrastructure.RoomAssistantMemoryRepository
import com.maksimowiczm.foodyou.assistant.domain.AssistantPreferences
import com.maksimowiczm.foodyou.assistant.domain.pad.AssistantPad
import com.maksimowiczm.foodyou.assistant.domain.tool.pad.PadAddTool
import com.maksimowiczm.foodyou.assistant.domain.tool.pad.PadCommitTool
import com.maksimowiczm.foodyou.assistant.domain.tool.pad.PadDiscardTool
import com.maksimowiczm.foodyou.assistant.domain.tool.pad.PadFromDayTool
import com.maksimowiczm.foodyou.assistant.domain.tool.pad.PadRemoveTool
import com.maksimowiczm.foodyou.assistant.domain.tool.pad.PadTotalsTool
import com.maksimowiczm.foodyou.assistant.infrastructure.DataStoreAssistantPreferences
import com.maksimowiczm.foodyou.assistant.infrastructure.assistantKeepAliveDefinition
import com.maksimowiczm.foodyou.assistant.infrastructure.openai.OpenAiCompatibleClient
import com.maksimowiczm.foodyou.common.infrastructure.assistant.SafeAssistantCredentialsRepository
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import org.koin.core.module.Module
import org.koin.core.module.dsl.singleOf
import org.koin.core.qualifier.named
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolRegistry
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolRegistryFactory
import com.maksimowiczm.foodyou.assistant.domain.tool.read.DailyTotalsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.DiaryRangeTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.GoalsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.ListMealsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.MealTimingStatsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.AssistantRemoteFoodFallback
import com.maksimowiczm.foodyou.assistant.domain.tool.read.NutrientAttributionTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.SearchDiaryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.SearchFoodTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.TopBrandsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.read.TopFoodsTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.AddEntriesTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.CreateComposedEntryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.CreateManualEntryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.DeleteEntriesTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.HistoryTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.RedoTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.SetEatenTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.UndoTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.UpdateEntryTool
import com.maksimowiczm.foodyou.app.ui.assistant.chat.AssistantChatViewModel
import com.maksimowiczm.foodyou.app.ui.assistant.history.AssistantConversationsViewModel
import com.maksimowiczm.foodyou.app.ui.assistant.settings.AssistantSettingsViewModel
import com.maksimowiczm.foodyou.assistant.domain.ConversationStore
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.core.scope.Scope
import org.koin.dsl.bind
import org.koin.dsl.onClose

/** Wiring for the assistant feature. Aggregated from AppModule like every other feature. */
fun Module.assistantModule() {
    assistantKeepAliveDefinition()

    factoryOf(::DiaryReader)
    factoryOf(::DailyTotalsUseCase)
    factoryOf(::DiaryRangeUseCase)
    factoryOf(::TopFoodsUseCase)
    factoryOf(::TopBrandsUseCase)
    factoryOf(::NutrientAttributionUseCase)
    factoryOf(::SearchDiaryUseCase)
    factoryOf(::MealTimingStatsUseCase)

    factory { database.assistantDao }

    // Herramientas de lectura
    factoryOf(::DailyTotalsTool)
    factoryOf(::DiaryRangeTool)
    factoryOf(::TopFoodsTool)
    factoryOf(::TopBrandsTool)
    factoryOf(::NutrientAttributionTool)
    factoryOf(::SearchDiaryTool)
    factoryOf(::MealTimingStatsTool)
    factoryOf(::ListMealsTool)
    factoryOf(::GoalsTool)
    factoryOf(::AssistantRemoteFoodFallback)
    factoryOf(::SearchFoodTool)

    // Herramientas de escritura
    factoryOf(::AddEntriesTool)
    factoryOf(::UpdateEntryTool)
    factoryOf(::DeleteEntriesTool)
    factoryOf(::SetEatenTool)
    factoryOf(::CreateManualEntryTool)
    factoryOf(::CreateComposedEntryTool)

    // Historial
    factoryOf(::HistoryTool)
    factoryOf(::UndoTool)
    factoryOf(::RedoTool)

    // El pad vive mientras dure la conversacion, y solo hay una a la vez.
    single { AssistantPad() }
    factoryOf(::PadFromDayTool)
    factoryOf(::PadAddTool)
    factoryOf(::PadRemoveTool)
    factoryOf(::PadTotalsTool)
    factoryOf(::PadCommitTool)
    factoryOf(::PadDiscardTool)

    // Cliente y ajustes
    single<AssistantPreferences> { DataStoreAssistantPreferences(get()) }
    single<AssistantCredentialsRepository> {
        SafeAssistantCredentialsRepository(masterCrypto = get(), dataStore = get(), logger = get())
    }
    single(named("assistantHttpClient")) {
            HttpClient {
                install(HttpTimeout) {
                    // Sin esto, el motor (OkHttp en Android) aplica su propio limite de ~10s,
                    // insuficiente para un modelo de razonamiento en una cadena de herramientas.
                    requestTimeoutMillis = 90_000
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 90_000
                }
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            // Los valores por defecto SI se envian: "type": "function" de cada
                            // herramienta es uno de ellos, y sin el la API rechaza la peticion
                            // entera con un 400.
                            encodeDefaults = true
                            // Los nulos no: content, tool_calls y temperature van vacios en la
                            // mayoria de los mensajes y no deben aparecer.
                            explicitNulls = false
                        }
                    )
                }
                install(ContentEncoding) { gzip() }
            }
        }
        .onClose { it?.close() }
    single {
        OpenAiCompatibleClient(
            httpClient = get(named("assistantHttpClient")),
            credentials = get(),
            preferences = get(),
            logger = get(),
        )
    }

    single<AssistantMemoryRepository> { RoomAssistantMemoryRepository(get()) }
    factoryOf(::SystemPromptBuilder)
    factoryOf(::AgentLoop)

    single { ConversationStore() }
    single<AssistantConversationRepository> {
        RoomAssistantConversationRepository(get(), get(named("assistantJson")))
    }
    viewModelOf(::AssistantChatViewModel)
    viewModelOf(::AssistantConversationsViewModel)
    viewModelOf(::AssistantSettingsViewModel)

    factoryOf(::ToolRegistryFactory)
    factory<ToolRegistry> {
        ToolRegistry(
            get<ToolRegistryFactory>().create().tools +
                listOf(
                    get<PadFromDayTool>(),
                    get<PadAddTool>(),
                    get<PadRemoveTool>(),
                    get<PadTotalsTool>(),
                    get<PadCommitTool>(),
                    get<PadDiscardTool>(),
                )
        )
    }
    single(qualifier = org.koin.core.qualifier.named("assistantJson")) {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
    factory {
        RoomChangeJournal(
            dao = get(),
            measurementDao = get(),
            manualRepository = get(),
            conversationStore = get(),
            json = get(org.koin.core.qualifier.named("assistantJson")),
        )
    }
        .bind<ChangeJournal>()
}

private val Scope.database: AssistantDatabase
    get() = get()
