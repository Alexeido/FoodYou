package com.maksimowiczm.foodyou.sync

import kotlinx.coroutines.flow.first
import com.maksimowiczm.foodyou.sync.domain.SyncRequest
import com.maksimowiczm.foodyou.sync.domain.SyncFieldChange
import com.maksimowiczm.foodyou.sync.domain.SyncChange
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonArray
import com.maksimowiczm.foodyou.sync.infrastructure.SyncFailure
import com.maksimowiczm.foodyou.sync.infrastructure.SyncOutcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Sync between real databases: the app's diary tables, the sync triggers and the engine, with
 * two or three phones sharing one account on a server that merges as the protocol says.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34], application = android.app.Application::class)
class SyncEngineTest {

    private val server = FakeSyncServer()

    /** A phone with the default meals and a small diary, written before sync was ever on. */
    private fun phoneWithDiary(name: String): Phone =
        Phone(name, server).apply {
            val breakfast = meal("Desayuno", 0)
            meal("Comida", 1)
            val oats = product("Copos de avena", 372.0, 13.0)
            entry(breakfast, productId = oats, grams = 60.0)
            val bread = product("Pan", 250.0, 8.0)
            val cheese = product("Queso", 400.0, 25.0)
            entry(breakfast, recipeId = recipe("Tostada con queso", bread to 60.0, cheese to 30.0), grams = 90.0)
            manual(breakfast, "Café del bar", "café", "leche")
        }

    private fun Phone.entryRows() =
        rows(
            "SELECT m.quantity, m.isEaten, meal.name AS meal, p.name AS product, r.name AS recipe " +
                "FROM Measurement m JOIN Meal meal ON meal.id = m.mealId " +
                "LEFT JOIN DiaryProduct p ON p.id = m.productId " +
                "LEFT JOIN DiaryRecipe r ON r.id = m.recipeId ORDER BY m.quantity"
        )

    private fun pause() = Thread.sleep(3) // relojes distintos para cambios distintos

    @Test
    fun theFirstPhoneUploadsItsWholeDiary() = runTest {
        val a = phoneWithDiary("a")

        val outcome = a.sync()

        assertIs<SyncOutcome.Done>(outcome)
        assertEquals(2, server.documents("meal").size)
        assertEquals(2, server.documents("food_entry").size)
        assertEquals(1, server.documents("manual_entry").size)
        // La entrada lleva su alimento entero: la receta con sus ingredientes dentro.
        val toast = server.documents("food_entry").first { "recipe" in it.fields["food"]!!.value.toString() }
        assertEquals(true, toast.fields["food"]!!.value.toString().contains("Queso"))
        // Lo que ya está confirmado no se vuelve a mandar.
        assertEquals(0, a.engine.pendingChanges())
        val before = server.requests.size
        a.sync()
        assertEquals(emptyList(), server.requests.drop(before).flatMap { it.changes })
    }

    @Test
    fun aSecondPhoneJoinsTheAccountWithoutDuplicatingMeals() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        // Un móvil nuevo trae sus comidas por defecto con los mismos nombres.
        val b = Phone("b", server).apply {
            val breakfast = meal("Desayuno", 0)
            meal("Comida", 1)
            entry(breakfast, productId = product("Plátano", 89.0, 1.1), grams = 120.0)
        }

        b.sync()
        a.sync()

        assertEquals(2L, b.scalar("SELECT COUNT(*) FROM Meal"))
        assertEquals(2, server.documents("meal").size)
        // Los dos móviles acaban con lo de los dos.
        for (phone in listOf(a, b)) {
            assertEquals(
                listOf("Copos de avena" to "Desayuno", "Tostada con queso" to "Desayuno", "Plátano" to "Desayuno"),
                phone.entryRows().map { (it["product"] ?: it["recipe"]) to it["meal"] },
                phone.name,
            )
        }
        // La receta llega con sus ingredientes como filas de verdad.
        assertEquals(
            listOf("Pan", "Queso"),
            b.rows(
                    "SELECT p.name FROM DiaryRecipeIngredient i JOIN DiaryProduct p " +
                        "ON p.id = i.ingredientProductId ORDER BY p.name"
                )
                .map { it["name"] },
        )
        assertEquals(
            listOf("café", "leche"),
            b.rows("SELECT name FROM ManualDiaryEntryIngredient ORDER BY position").map { it["name"] },
        )
    }

    @Test
    fun aTickOnOnePhoneShowsOnTheOther() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        val b = Phone("b", server)
        b.sync()

        pause()
        a.exec("UPDATE Measurement SET isEaten = 1 WHERE quantity = 60.0")
        a.sync()
        b.sync()

        assertEquals(1L, b.scalar("SELECT isEaten FROM Measurement WHERE quantity = 60.0"))
        // Aplicar lo que llega no cuenta como cambio de B: no hay ida y vuelta.
        assertEquals(0, b.engine.pendingChanges())
    }

    @Test
    fun changesToDifferentFieldsOfTheSameEntryBothSurvive() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        val b = Phone("b", server)
        b.sync()

        pause()
        a.exec("UPDATE Measurement SET quantity = 75.0 WHERE quantity = 60.0") // gramos en A
        pause()
        b.exec("UPDATE Measurement SET isEaten = 1 WHERE quantity = 60.0") // tick en B, sin ver lo de A
        a.sync()
        b.sync()
        a.sync()

        for (phone in listOf(a, b)) {
            val row = phone.rows("SELECT quantity, isEaten FROM Measurement WHERE productId IS NOT NULL").single()
            assertEquals(75.0, row["quantity"], phone.name)
            assertEquals(1L, row["isEaten"], phone.name)
        }
    }

    @Test
    fun theSameFieldKeepsTheNewestChange() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        val b = Phone("b", server)
        b.sync()

        a.exec("UPDATE Measurement SET quantity = 70.0 WHERE quantity = 60.0")
        pause()
        b.exec("UPDATE Measurement SET quantity = 80.0 WHERE quantity = 60.0") // más tarde
        // B sincroniza antes, pero el cambio de A es más viejo: no pisa el de B.
        b.sync()
        a.sync()
        b.sync()

        assertEquals(80.0, a.scalar("SELECT quantity FROM Measurement WHERE productId IS NOT NULL"))
        assertEquals(80.0, b.scalar("SELECT quantity FROM Measurement WHERE productId IS NOT NULL"))
    }

    @Test
    fun deletingTravelsAndOldEditsDoNotBringItBack() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        val b = Phone("b", server)
        b.sync()

        a.exec("UPDATE Measurement SET quantity = 61.0 WHERE quantity = 60.0") // edición vieja
        pause()
        b.exec("DELETE FROM Measurement WHERE quantity = 60.0")
        b.sync()
        a.sync()
        b.sync()

        for (phone in listOf(a, b)) {
            assertEquals(1L, phone.scalar("SELECT COUNT(*) FROM Measurement"), phone.name)
        }
        assertEquals(true, server.documents("food_entry").single { it.deleted }.deleted)
    }

    @Test
    fun replacingAnEntrysFoodSendsTheNewCopy() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        val b = Phone("b", server)
        b.sync()

        // Lo que hace la app al editar una receta del diario: copia nueva, y la vieja fuera.
        pause()
        val old = a.scalar("SELECT recipeId FROM Measurement WHERE recipeId IS NOT NULL") as Long
        val bread = a.product("Pan", 250.0, 8.0)
        val cheese = a.product("Queso", 400.0, 25.0)
        val updated = a.recipe("Tostada con queso", bread to 60.0, cheese to 60.0) // más queso
        a.exec("UPDATE Measurement SET recipeId = ? WHERE recipeId = ?", updated, old)
        a.exec("DELETE FROM DiaryRecipe WHERE id = ?", old)
        a.sync()
        b.sync()

        assertEquals(
            listOf(60.0, 60.0),
            b.rows(
                    "SELECT i.quantity FROM Measurement m JOIN DiaryRecipeIngredient i " +
                        "ON i.recipeId = m.recipeId ORDER BY i.id"
                )
                .map { it["quantity"] },
        )
        // La copia vieja no se queda huérfana en B.
        assertEquals(1L, b.scalar("SELECT COUNT(*) FROM DiaryRecipe"))
    }

    @Test
    fun withoutNetworkNothingIsLostAndItCatchesUpLater() = runTest {
        val a = phoneWithDiary("a")
        a.sync()

        server.offline = true
        a.exec("UPDATE Measurement SET isEaten = 1")
        val failed = a.sync()

        assertEquals(SyncOutcome.Failed(SyncFailure.Network), failed)
        assertEquals(2, a.engine.pendingChanges())

        server.offline = false
        a.sync()
        assertEquals(0, a.engine.pendingChanges())
        assertEquals(
            listOf(JsonPrimitive(1L), JsonPrimitive(1L)).map { it.content },
            server.documents("food_entry").map { it.fields["isEaten"]!!.value.toString() },
        )
    }

    @Test
    fun anEntryWhoseMealIsMissingWaitsForIt() = runTest {
        val paged = FakeSyncServer(pageSize = 1)
        val a = Phone("a", paged)
        a.meal("Desayuno", 0)
        a.sync()
        val b = Phone("b", paged)
        b.sync()

        // A crea una comida nueva con una entrada y luego renombra la comida: en el servidor la
        // entrada queda antes que su comida, y B la recibe en una página en la que la comida
        // todavía no está.
        val dinner = a.meal("Cena", 2)
        a.entry(dinner, productId = a.product("Sopa", 40.0, 2.0), grams = 300.0)
        a.sync()
        pause()
        a.exec("UPDATE Meal SET name = 'Cena ligera' WHERE id = ?", dinner)
        a.sync()

        b.sync()

        assertEquals(
            "Cena ligera",
            b.scalar(
                "SELECT meal.name FROM Measurement m JOIN Meal meal ON meal.id = m.mealId " +
                    "WHERE m.quantity = 300.0"
            ),
        )
        assertEquals(0L, b.scalar("SELECT COUNT(*) FROM SyncPending"))
    }

    @Test
    fun turningSyncOffStopsTrackingAndOnAgainLetsTheAccountWin() = runTest {
        val a = phoneWithDiary("a")
        a.sync()

        a.engine.disable()
        a.config.save(a.config.flow.value!!.copy(enabled = false))
        a.exec("UPDATE Measurement SET quantity = 999.0 WHERE quantity = 60.0") // sin vigilar
        assertEquals(0, a.engine.pendingChanges())
        assertEquals(SyncOutcome.Disabled, a.sync())

        a.config.save(a.config.flow.value!!.copy(enabled = true))
        a.sync()

        // La cuenta tenía 60 g y manda: lo hecho con la sincronización apagada no se sube.
        assertEquals(60.0, a.scalar("SELECT quantity FROM Measurement WHERE productId IS NOT NULL"))
        assertEquals(2L, a.scalar("SELECT COUNT(*) FROM Meal"))
    }

    @Test
    fun manyChangesArePagedBothWays() = runTest {
        val pagedServer = FakeSyncServer(pageSize = 3)
        val a = Phone("a", pagedServer)
        val breakfast = a.meal("Desayuno", 0)
        repeat(10) { i -> a.entry(breakfast, productId = a.product("P$i", 100.0, 1.0), grams = 10.0 + i) }
        a.sync()

        val b = Phone("b", pagedServer)
        b.sync()

        assertEquals(10L, b.scalar("SELECT COUNT(*) FROM Measurement"))
    }

    @Test
    fun aNewLocalChangeDuringASyncIsNotOverwritten() = runTest {
        val a = phoneWithDiary("a")
        a.sync()
        val b = Phone("b", server)
        b.sync()

        pause()
        a.exec("UPDATE Measurement SET quantity = 65.0 WHERE quantity = 60.0")
        a.sync()
        // B cambia el mismo campo después de que A haya subido, sin haber sincronizado aún.
        pause()
        b.exec("UPDATE Measurement SET quantity = 66.0 WHERE quantity = 60.0")
        b.sync()

        assertEquals(66.0, b.scalar("SELECT quantity FROM Measurement WHERE productId IS NOT NULL"))
        a.sync()
        assertEquals(66.0, a.scalar("SELECT quantity FROM Measurement WHERE productId IS NOT NULL"))
    }

    @Test
    fun notConfiguredDoesNothing() = runTest {
        val a = phoneWithDiary("a")
        a.config.clear()
        assertEquals(SyncOutcome.NotConfigured, a.sync())
        assertNull(server.documents("meal").firstOrNull())
    }

    // --- Metas ------------------------------------------------------------------------------

    private fun goals(vararg tracked: String) =
        FakeGoals(
            mapOf(
                "separateDays" to JsonPrimitive(false),
                "days" to buildJsonObject { put("monday", buildJsonObject { put("isDistribution", true) }) },
                "tracked" to JsonArray(tracked.map { JsonPrimitive(it) }),
            )
        )

    @Test
    fun goalsAndTrackedNutrientsTravelBetweenPhones() = runTest {
        val server = FakeSyncServer()
        val mum = Phone("madre", server, goals("Calcium"))
        mum.sync()
        assertEquals(1, server.documents("goals").size)

        // Un móvil nuevo de la misma cuenta: la cuenta manda, recibe el calcio.
        val tablet = Phone("tablet", server, goals())
        tablet.sync()
        assertEquals(JsonArray(listOf(JsonPrimitive("Calcium"))), tablet.goals!!.fields["tracked"])

        // Añade el hierro en la tablet y llega al móvil.
        tablet.goals!!.set("tracked", JsonArray(listOf(JsonPrimitive("Calcium"), JsonPrimitive("Iron"))))
        tablet.engine.goalsChanged()
        val sent = tablet.sync() as SyncOutcome.Done
        assertEquals(1, sent.sent)
        mum.sync()
        assertEquals(
            JsonArray(listOf(JsonPrimitive("Calcium"), JsonPrimitive("Iron"))),
            mum.goals!!.fields["tracked"],
        )

        // Aplicar lo que llega no cuenta como un cambio propio: no se reenvía.
        mum.engine.goalsChanged()
        assertEquals(0, (mum.sync() as SyncOutcome.Done).sent)
    }

    // --- Recetas y memoria del asistente ---------------------------------------------------

    @Test
    fun aRecipeWithItsIngredientsTravelsAndStaysPut() = runTest {
        val server = FakeSyncServer()
        val a = Phone("a", server)
        a.sync()
        val milk = a.libraryProduct("Leche", 46.0, calciumMilli = 120.0)
        val oats = a.libraryProduct("Avena", 372.0)
        val base = a.libraryRecipe("Gachas base", "product" to oats)
        a.libraryRecipe("Gachas con leche", "recipe" to base, "product" to milk)
        a.sync()
        assertEquals(2, server.documents("recipe").size)

        val b = Phone("b", server)
        b.sync()
        assertEquals(
            listOf("Gachas base", "Gachas con leche"),
            b.rows("SELECT name FROM Recipe ORDER BY name").map { it["name"] },
        )
        val full = b.scalar("SELECT id FROM Recipe WHERE name = 'Gachas con leche'") as Long
        assertEquals(2L, b.scalar("SELECT COUNT(*) FROM RecipeIngredient WHERE recipeId = ?", full))
        assertEquals(120.0, b.scalar("SELECT calciumMilli FROM Product WHERE name = 'Leche'"))
        // El favorito es cosa de cada móvil.
        assertEquals(0L, b.scalar("SELECT isFavorite FROM Product WHERE name = 'Leche'"))

        // Lo recibido, leído de vuelta, es lo mismo: nada rebota.
        assertEquals(0, (b.sync() as SyncOutcome.Done).sent)
        assertEquals(0, (a.sync() as SyncOutcome.Done).sent)

        // Renombrar en uno llega al otro; borrar también.
        a.db.execSQL("UPDATE Recipe SET name = 'Gachas de mamá' WHERE name = 'Gachas con leche'")
        a.sync()
        b.sync()
        assertEquals(1L, b.scalar("SELECT COUNT(*) FROM Recipe WHERE name = 'Gachas de mamá'"))
        b.db.execSQL("DELETE FROM Recipe WHERE name = 'Gachas de mamá'")
        b.sync()
        a.sync()
        assertEquals(listOf("Gachas base"), a.rows("SELECT name FROM Recipe").map { it["name"] })
    }

    @Test
    fun theAssistantsMemoryIsSharedAndForgettingTravels() = runTest {
        val server = FakeSyncServer()
        val a = Phone("a", server)
        a.remember("dieta", "sin lactosa")
        a.sync()

        val b = Phone("b", server)
        b.remember("objetivo", "más calcio")
        b.sync()
        assertEquals(mapOf("dieta" to "sin lactosa", "objetivo" to "más calcio"), b.memory())
        a.sync()
        assertEquals(mapOf("dieta" to "sin lactosa", "objetivo" to "más calcio"), a.memory())

        a.forget("dieta")
        a.sync()
        b.sync()
        assertEquals(mapOf("objetivo" to "más calcio"), b.memory())
    }

    @Test
    fun aRecipeMadeByTheMcpIsAppliedAndDoesNotBounce() = runTest {
        val server = FakeSyncServer()
        val phone = Phone("madre", server)
        phone.sync()

        // Lo que manda el MCP: el producto con todas las columnas de la biblioteca, nulos incluidos.
        val columns =
            phone.rows("PRAGMA table_info(Product)").map { it["name"] as String } -
                setOf("id", "isFavorite", "isEdited")
        val product = buildJsonObject {
            columns.forEach { column ->
                when (column) {
                    "name" -> put(column, "Copos de avena")
                    "brand" -> put(column, "Hacendado")
                    "sourceType" -> put(column, 4)
                    "isLiquid" -> put(column, 0)
                    "energy" -> put(column, 372.0)
                    "proteins" -> put(column, 13.0)
                    else -> put(column, JsonNull)
                }
            }
        }
        val ingredient = buildJsonObject {
            put("measurement", 0)
            put("quantity", 80.0)
            put("product", product)
        }
        fun field(value: kotlinx.serialization.json.JsonElement) = SyncFieldChange(value, 1)
        server.api("mcp").sync(
            FixedConfig().observe().first()!!,
            SyncRequest(
                0,
                listOf(
                    SyncChange(
                        "recipe",
                        "r1",
                        mapOf(
                            "name" to field(JsonPrimitive("Gachas")),
                            "servings" to field(JsonPrimitive(2)),
                            "note" to field(JsonNull),
                            "isLiquid" to field(JsonPrimitive(0)),
                            "isFavorite" to field(JsonPrimitive(0)),
                            "ingredients" to field(JsonArray(listOf(ingredient))),
                            "_deleted" to field(JsonPrimitive(false)),
                        ),
                    )
                ),
            ),
        )

        phone.sync()
        assertEquals("Gachas", phone.scalar("SELECT name FROM Recipe"))
        assertEquals("Hacendado", phone.scalar("SELECT brand FROM Product WHERE name = 'Copos de avena'"))
        // Lo que lee de vuelta es lo que llegó: no lo reenvía.
        assertEquals(0, (phone.sync() as SyncOutcome.Done).sent)
    }

    @Test
    fun roomUpdatesWithTheirOwnConflictPolicyDoNotBreakTheTriggers() = runTest {
        // Room guarda con UPDATE OR ABORT / INSERT OR REPLACE, y en SQLite la política de la
        // sentencia de fuera manda sobre la de dentro de un disparador: un INSERT OR IGNORE del
        // disparador se volvía ABORT y la app petaba al cambiar el icono de una comida.
        val server = FakeSyncServer()
        val phone = Phone("madre", server)
        val meal = phone.meal("Desayuno", 0)
        phone.sync()
        phone.db.execSQL("UPDATE OR ABORT Meal SET icon = 'coffee', rank = 3 WHERE id = ?", arrayOf<Any?>(meal))
        phone.db.execSQL(
            "INSERT OR REPLACE INTO Meal (id, name, fromHour, fromMinute, toHour, toMinute, rank, icon) " +
                "VALUES (?, 'Desayuno', 7, 0, 10, 0, 0, 'egg')",
            arrayOf<Any?>(meal),
        )
        phone.sync()
        val doc = server.documents("meal").single()
        assertEquals(JsonPrimitive("egg"), doc.fields["icon"]?.value)
    }
}
