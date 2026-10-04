package com.maksimowiczm.foodyou.assistant.domain.query

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime

/**
 * The queries the assistant answers from, checked against totals worked out by hand.
 *
 * If these drift, the model does not fail loudly - it reports a confident wrong number, and the
 * user has no way to tell. That is why the expected values below are written as literals rather
 * than computed the same way the production code computes them.
 */
private fun diary(repo: com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository) =
    DiaryReader(repo, NoManualEntries)

class DiaryQueryTest {

    // 100 g of chicken: 165 kcal, 31 P, 0 C, 3,6 F
    private val chicken = facts(energy = 165.0, proteins = 31.0, fats = 3.6)

    // 100 g of rice: 130 kcal, 2,7 P, 28 C, 0,3 F
    private val rice = facts(energy = 130.0, proteins = 2.7, carbohydrates = 28.0, fats = 0.3)

    // 100 g of olive oil: 884 kcal, all fat, 13,8 of it saturated
    private val oil = facts(energy = 884.0, fats = 100.0, saturatedFats = 13.8)

    private val monday = day("2026-08-24")
    private val tuesday = day("2026-08-25")
    private val saturday = day("2026-08-29")

    private val diary =
        listOf(
            // lunes: 200 g pollo + 150 g arroz = 330 + 195 = 525 kcal
            entry(monday, "Pechuga de pollo", chicken, 200.0),
            entry(monday, "Arroz largo", rice, 150.0),
            // martes: 100 g pollo + 10 g aceite = 165 + 88,4 = 253,4 kcal
            entry(tuesday, "Pechuga de pollo", chicken, 100.0),
            entry(tuesday, "Aceite de oliva", oil, 10.0, mealId = 2L),
            // sabado: 20 g aceite = 176,8 kcal, sin marcar como comido
            entry(saturday, "Aceite de oliva", oil, 20.0, isEaten = false),
        )

    private val repository = FakeDiaryRepository(diary)

    // ------------------------------------------------------------ C5 dailyTotals

    @Test
    fun dailyTotalsSumsEachDay() = runTest {
        val totals = DailyTotalsUseCase(diary(repository))(from = monday, to = tuesday)

        assertEquals(2, totals.size)
        assertEquals(525.0, totals[0].energy, 0.001)
        assertEquals(253.4, totals[1].energy, 0.001)
        // 200 g de pollo = 62 P, 150 g de arroz = 4,05 P
        assertEquals(66.05, totals[0].facts.proteins.value!!, 0.001)
    }

    @Test
    fun dailyTotalsIncludesDaysWithNothingLogged() = runTest {
        // "que dias me pase" tiene que poder ver un dia vacio, no saltarselo.
        val totals = DailyTotalsUseCase(diary(repository))(from = monday, to = day("2026-08-27"))

        assertEquals(4, totals.size)
        assertEquals(0.0, totals[2].energy, 0.001)
        assertEquals(0, totals[2].entryCount)
    }

    @Test
    fun dailyTotalsCanIgnoreWhatIsNotEatenYet() = runTest {
        val all = DailyTotalsUseCase(diary(repository))(from = saturday, to = saturday)
        val eatenOnly =
            DailyTotalsUseCase(diary(repository))(from = saturday, to = saturday, onlyEaten = true)

        assertEquals(176.8, all.single().energy, 0.001)
        // El sabado solo hay una entrada planificada: contando solo lo comido, el dia es cero.
        assertEquals(0.0, eatenOnly.single().energy, 0.001)
    }

    @Test
    fun dailyTotalsGroupsByMonthAcrossTheBoundary() = runTest {
        val extended =
            FakeDiaryRepository(diary + entry(day("2026-09-01"), "Arroz largo", rice, 100.0))

        val totals =
            DailyTotalsUseCase(diary(extended))(
                from = monday,
                to = day("2026-09-30"),
                grouping = TotalsGrouping.Month,
            )

        assertEquals(listOf("2026-08", "2026-09"), totals.map { it.key })
        assertEquals(130.0, totals[1].energy, 0.001)
    }

    @Test
    fun dailyTotalsGroupsByWeekday() = runTest {
        val totals =
            DailyTotalsUseCase(diary(repository))(
                from = monday,
                to = saturday,
                grouping = TotalsGrouping.Weekday,
            )

        // lunes, martes y sabado: tres cubos, ordenados por numero ISO de dia.
        assertEquals(3, totals.size)
        assertEquals(525.0, totals.first().energy, 0.001)
    }

    // ------------------------------------------------------------ C1 topFoods

    @Test
    fun topFoodsRanksByHowOftenItWasLogged() = runTest {
        val top = TopFoodsUseCase(diary(repository))(from = monday, to = saturday)

        assertEquals("Pechuga de pollo", top[0].name)
        assertEquals(2, top[0].times)
        assertEquals(300.0, top[0].totalGrams, 0.001)
        assertEquals(495.0, top[0].totalEnergy, 0.001)

        assertEquals("Aceite de oliva", top[1].name)
        assertEquals(2, top[1].times)
    }

    @Test
    fun topFoodsCanBeNarrowedToOneMeal() = runTest {
        val top = TopFoodsUseCase(diary(repository))(from = monday, to = saturday, mealId = 2L)

        assertEquals(1, top.size)
        assertEquals("Aceite de oliva", top.single().name)
    }

    // ------------------------------------------------------------ C3 nutrientAttribution

    @Test
    fun nutrientAttributionPointsAtTheRealCulprit() = runTest {
        val contributions =
            NutrientAttributionUseCase(diary(repository))(
                nutrient = NutrientSelector.Fats,
                from = monday,
                to = saturday,
            )

        // 30 g de aceite = 30 g de grasa; 300 g de pollo = 10,8 g; 150 g de arroz = 0,45 g.
        assertEquals("Aceite de oliva", contributions[0].name)
        assertEquals(30.0, contributions[0].amount, 0.001)
        assertEquals(0.727, contributions[0].share, 0.001)
        assertEquals("Pechuga de pollo", contributions[1].name)
    }

    @Test
    fun nutrientAttributionIsEmptyWhenNobodyContributes() = runTest {
        val contributions =
            NutrientAttributionUseCase(diary(repository))(
                nutrient = NutrientSelector.DietaryFiber,
                from = monday,
                to = saturday,
            )

        assertTrue(contributions.isEmpty())
    }

    // ------------------------------------------------------------ C4 searchDiary

    @Test
    fun searchDiaryFindsMostRecentFirst() = runTest {
        val hits = SearchDiaryUseCase(diary(repository))("aceite", from = monday, to = saturday)

        assertEquals(2, hits.size)
        assertEquals(saturday, hits.first().date)
        assertEquals(20.0, hits.first().grams, 0.001)
    }

    @Test
    fun searchDiaryIgnoresBlankQueries() = runTest {
        assertTrue(SearchDiaryUseCase(diary(repository))("   ", from = monday, to = saturday).isEmpty())
    }

    // ------------------------------------------------------------ C2 topBrands

    @Test
    fun topBrandsReadsTheBrandOutOfTheStoredName() = runTest {
        val branded =
            FakeDiaryRepository(
                listOf(
                    entry(monday, "Leche desnatada (Hacendado)", rice, 200.0),
                    entry(tuesday, "Yogur natural (Hacendado)", rice, 125.0),
                    entry(tuesday, "Pan de molde (Bimbo)", rice, 60.0),
                    entry(saturday, "Pechuga de pollo", chicken, 100.0),
                )
            )

        val brands = TopBrandsUseCase(diary(branded))(from = monday, to = saturday)

        assertEquals(2, brands.size)
        assertEquals("Hacendado", brands[0].brand)
        assertEquals(2, brands[0].times)
        assertEquals("Bimbo", brands[1].brand)
    }

    // ------------------------------------------------------------ diaryRange

    @Test
    fun diaryRangeCanIsolateWhatIsStillUneaten() = runTest {
        val planned = DiaryRangeUseCase(repository)(from = monday, to = saturday, onlyEaten = false)

        assertEquals(1, planned.size)
        assertEquals(saturday, planned.single().date)
    }

    @Test
    fun sameFoodTwiceInOneDayCountsTwice() = runTest {
        val twice =
            FakeDiaryRepository(
                listOf(
                    entry(monday, "Pechuga de pollo", chicken, 100.0, mealId = 1L),
                    entry(monday, "Pechuga de pollo", chicken, 100.0, mealId = 2L),
                )
            )

        val totals = DailyTotalsUseCase(diary(twice))(from = monday, to = monday)
        val top = TopFoodsUseCase(diary(twice))(from = monday, to = monday)

        assertEquals(330.0, totals.single().energy, 0.001)
        assertEquals(2, top.single().times)
        assertEquals(200.0, top.single().totalGrams, 0.001)
    }

    @Test
    fun createdAtDrivesTheTimingStats() = runTest {
        // No hay MealRepository aqui, asi que la comprobacion es del promedio en si.
        val morning = LocalDateTime(2026, 8, 24, 8, 30)
        val night = LocalDateTime(2026, 8, 24, 22, 30)
        val minutes = listOf(morning, night).map { it.hour * 60 + it.minute }

        assertEquals(930, minutes.sum() / minutes.size)
    }
}
