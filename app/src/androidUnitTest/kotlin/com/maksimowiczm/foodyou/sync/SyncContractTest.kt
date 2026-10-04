package com.maksimowiczm.foodyou.sync

import com.maksimowiczm.foodyou.common.config.NetworkConfig
import com.maksimowiczm.foodyou.sync.infrastructure.KtorSyncApi
import com.maksimowiczm.foodyou.sync.infrastructure.SyncOutcome
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assume.assumeTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The app's real HTTP client against the real sync server (sync-server/), so the two sides are
 * known to speak the same protocol. Runs only when a server is given:
 *
 *     FOODYOU_SYNC_E2E_URL=http://127.0.0.1:8765 FOODYOU_SYNC_E2E_USER=ana \
 *     FOODYOU_SYNC_E2E_PASSWORD=... ./gradlew :app:testDebugUnitTest --tests '*SyncContractTest*'
 *
 * The account should be empty: the test fills it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = android.app.Application::class)
class SyncContractTest {

    private val url: String? = System.getenv("FOODYOU_SYNC_E2E_URL")

    private fun phone(name: String): Phone {
        val client =
            HttpClient(OkHttp) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val api =
            KtorSyncApi(
                client = client,
                networkConfig = object : NetworkConfig { override val userAgent = "FoodYou-test" },
                installationId = { "test-$name" },
            )
        return Phone(name, api).apply {
            config.flow.value =
                config.flow.value!!.copy(
                    serverUrl = url!!,
                    username = System.getenv("FOODYOU_SYNC_E2E_USER")!!,
                    password = System.getenv("FOODYOU_SYNC_E2E_PASSWORD")!!,
                )
        }
    }

    @Test
    fun twoPhonesThroughTheRealServer() = runBlocking {
        assumeTrue("Sin servidor de pruebas", url != null)

        val a = phone("a")
        val breakfast = a.meal("Desayuno", 0)
        val bread = a.product("Pan", 250.0, 8.0)
        val cheese = a.product("Queso", 400.0, 25.0)
        a.entry(breakfast, recipeId = a.recipe("Tostada", bread to 60.0, cheese to 30.0), grams = 90.0)
        a.manual(breakfast, "Café", "café", "leche")
        assertIs<SyncOutcome.Done>(a.sync())

        val b = phone("b")
        b.meal("Desayuno", 0)
        assertIs<SyncOutcome.Done>(b.sync())
        assertEquals(1L, b.scalar("SELECT COUNT(*) FROM Meal"))
        assertEquals(1L, b.scalar("SELECT COUNT(*) FROM Measurement"))
        assertEquals(2L, b.scalar("SELECT COUNT(*) FROM DiaryRecipeIngredient"))
        assertEquals(2L, b.scalar("SELECT COUNT(*) FROM ManualDiaryEntryIngredient"))

        Thread.sleep(5)
        b.exec("UPDATE Measurement SET isEaten = 1")
        b.sync()
        a.sync()
        assertEquals(1L, a.scalar("SELECT isEaten FROM Measurement"))

        Thread.sleep(5)
        a.exec("DELETE FROM ManualDiaryEntry")
        a.sync()
        b.sync()
        assertEquals(0L, b.scalar("SELECT COUNT(*) FROM ManualDiaryEntry"))
    }
}
