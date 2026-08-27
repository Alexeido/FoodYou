package com.maksimowiczm.foodyou.assistant

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.test.core.app.ApplicationProvider
import com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.query.DailyTotalsUseCase
import com.maksimowiczm.foodyou.assistant.domain.tool.read.SearchFoodTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.AddEntriesTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.DeleteEntriesTool
import com.maksimowiczm.foodyou.assistant.infrastructure.journal.RoomChangeJournal
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.repository.ProductRepository
import com.maksimowiczm.foodyou.food.infrastructure.repository.RoomProductRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.domain.repository.MealRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.repository.RoomFoodDiaryEntryRepository
import com.maksimowiczm.foodyou.fooddiary.infrastructure.repository.RoomMealRepository
import kotlin.test.AfterTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The assistant against a real database.
 *
 * The unit tests elsewhere use fakes, which proves the aggregation but not the wiring: a wrong
 * column name in a DAO query, a foreign key that rejects the insert, or an undo that restores a row
 * the food no longer exists for would all pass those and fail on a phone. This runs the actual Room
 * database on the JVM, so that whole class of failure is caught here instead of by the user.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class AssistantIntegrationTest {

    private val database =
        Room.inMemoryDatabaseBuilder(
                ApplicationProvider.getApplicationContext(),
                FoodYouDatabase::class.java,
            )
            .setDriver(BundledSQLiteDriver())
            .allowMainThreadQueries()
            .build()

    private val productRepository: ProductRepository = RoomProductRepository(database.productDao)

    private val entryRepository: FoodDiaryEntryRepository =
        RoomFoodDiaryEntryRepository(database, database.measurementDao)

    private val mealRepository: MealRepository = RoomMealRepository(database.mealDao)

    private val journal: ChangeJournal =
        RoomChangeJournal(
            dao = database.assistantDao,
            measurementDao = database.measurementDao,
            json = Json { encodeDefaults = true },
        )

    private val today = LocalDate(2026, 8, 27)

    @AfterTest
    fun tearDown() {
        database.close()
    }

    private suspend fun seedProduct(): Long {
        val id =
            productRepository.insertProduct(
                Product(
                    id = FoodId.Product(0),
                    name = "Pechuga de pollo",
                    brand = "Hacendado",
                    barcode = null,
                    note = null,
                    isLiquid = false,
                    packageWeight = null,
                    servingWeight = null,
                    source = FoodSource(FoodSource.Type.User),
                    nutritionFacts =
                        NutritionFacts(
                            energy = NutrientValue.Complete(165.0),
                            proteins = NutrientValue.Complete(31.0),
                            carbohydrates = NutrientValue.Complete(0.0),
                            fats = NutrientValue.Complete(3.6),
                        ),
                )
            )
        return id
    }

    private suspend fun seedMeal(): Long =
        mealRepository.insertMealWithLastRank(
            name = "Comida",
            from = kotlinx.datetime.LocalTime(13, 0),
            to = kotlinx.datetime.LocalTime(16, 0),
        )

    @Test
    fun `la base de datos arranca en la version 39 con las tablas del asistente`() = runTest {
        // Si la migracion o las entidades estuviesen mal, esto no llegaria ni a abrir.
        val changes = database.assistantDao.recentChanges(5)
        assertTrue(changes.isEmpty())
        assertTrue(database.assistantDao.allMemory().isEmpty())
    }

    @Test
    fun `searchFood encuentra un producto recien insertado`() = runTest {
        seedProduct()

        val result =
            SearchFoodTool(productRepository).call(buildJsonObject { put("query", "pollo") })

        val array = result as JsonArray
        assertTrue(array.isNotEmpty(), "searchFood no devolvio nada: la busqueda FTS no indexa")
        val first = array.first().jsonObject
        assertEquals("Pechuga de pollo (Hacendado)", first["name"]!!.jsonPrimitive.content)
        assertEquals(165.0, first["per100g"]!!.jsonObject["kcal"]!!.jsonPrimitive.content.toDouble())
    }

    @Test
    fun `anadir, contar y deshacer recorre el camino entero`() = runTest {
        val productId = seedProduct()
        val mealId = seedMeal()

        // --- anadir 200 g -> 330 kcal
        val added =
            AddEntriesTool(productRepository, entryRepository, mealRepository, journal)
                .call(
                    buildJsonObject {
                        put("date", today.toString())
                        put("mealId", mealId)
                        put(
                            "items",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("foodId", productId)
                                        put("amount", 200.0)
                                        put("unit", "gram")
                                    }
                                )
                            },
                        )
                    }
                )
                .jsonObject

        assertEquals(true, added["ok"]!!.jsonPrimitive.content.toBoolean())
        assertEquals(1, added["addedUnchecked"]!!.jsonPrimitive.content.toInt())

        // --- los totales lo ven
        val totals = DailyTotalsUseCase(entryRepository)(from = today, to = today)
        assertEquals(330.0, totals.single().energy, 0.01)
        assertEquals(62.0, totals.single().facts.proteins.value!!, 0.01)

        // --- entra SIN marcar como comido, que es la promesa del diseno
        val entries = entryRepository.observeRange(today, today).first()
        assertEquals(1, entries.size)
        assertTrue(!entries.single().isEaten, "la IA no debe marcar nada como comido")

        // --- deshacer lo quita
        val change = journal.recent(1).single()
        assertNotNull(journal.undo(change.id))
        assertTrue(entryRepository.observeRange(today, today).first().isEmpty())

        // --- rehacer lo devuelve
        assertNotNull(journal.redo())
        assertEquals(1, entryRepository.observeRange(today, today).first().size)
    }

    @Test
    fun `deshacer un borrado devuelve la entrada con su alimento intacto`() = runTest {
        val productId = seedProduct()
        val mealId = seedMeal()

        AddEntriesTool(productRepository, entryRepository, mealRepository, journal)
            .call(
                buildJsonObject {
                    put("date", today.toString())
                    put("mealId", mealId)
                    put(
                        "items",
                        buildJsonArray {
                            add(
                                buildJsonObject {
                                    put("foodId", productId)
                                    put("amount", 150.0)
                                }
                            )
                        },
                    )
                }
            )

        val entry = entryRepository.observeRange(today, today).first().single()
        val originalName = entry.food.name
        val originalWeight = entry.weight

        DeleteEntriesTool(entryRepository, journal)
            .call(
                buildJsonObject {
                    put("entryIds", buildJsonArray { add(entry.id.value) })
                }
            )
        assertTrue(entryRepository.observeRange(today, today).first().isEmpty())

        // Este es el caso que no se puede reconstruir si el journal guardo mal: la entrada tiene
        // que volver con su alimento, no como una fila huerfana.
        assertNotNull(journal.undo(null))
        val restored = entryRepository.observeRange(today, today).first().single()
        assertEquals(originalName, restored.food.name)
        assertEquals(originalWeight, restored.weight, 0.01)
        assertEquals(165.0 * 1.5, restored.nutritionFacts.energy.value!!, 0.01)
    }

    @Test
    fun `un liquido no se puede anadir en gramos por accidente`() = runTest {
        val mealId = seedMeal()
        val liquidId =
            productRepository.insertProduct(
                Product(
                    id = FoodId.Product(0),
                    name = "Leche",
                    brand = null,
                    barcode = null,
                    note = null,
                    isLiquid = true,
                    packageWeight = null,
                    servingWeight = null,
                    source = FoodSource(FoodSource.Type.User),
                    nutritionFacts = NutritionFacts(energy = NutrientValue.Complete(46.0)),
                )
            )

        // Sin unidad, el estado liquido del alimento decide: mililitros, no gramos. Es el fallo
        // que ya nos mordio una vez en la pantalla de detalle.
        AddEntriesTool(productRepository, entryRepository, mealRepository, journal)
            .call(
                buildJsonObject {
                    put("date", today.toString())
                    put("mealId", mealId)
                    put(
                        "items",
                        buildJsonArray {
                            add(buildJsonObject {
                                put("foodId", liquidId)
                                put("amount", 250.0)
                            })
                        },
                    )
                }
            )

        val entry = entryRepository.observeRange(today, today).first().single()
        assertTrue(
            entry.measurement is com.maksimowiczm.foodyou.common.domain.measurement.Measurement.Milliliter,
            "un liquido sin unidad explicita debe entrar en mililitros, entro como ${entry.measurement}",
        )
    }
}

private fun kotlinx.serialization.json.JsonArrayBuilder.add(value: Long) {
    add(kotlinx.serialization.json.JsonPrimitive(value))
}

private fun kotlinx.serialization.json.JsonArrayBuilder.add(value: JsonObject) {
    add(value as kotlinx.serialization.json.JsonElement)
}
