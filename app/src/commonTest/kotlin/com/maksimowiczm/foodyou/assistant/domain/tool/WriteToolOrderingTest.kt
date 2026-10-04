package com.maksimowiczm.foodyou.assistant.domain.tool

import com.maksimowiczm.foodyou.assistant.domain.journal.AssistantChange
import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.journal.UndoAction
import com.maksimowiczm.foodyou.assistant.domain.query.day
import com.maksimowiczm.foodyou.assistant.domain.query.entry
import com.maksimowiczm.foodyou.assistant.domain.query.facts
import com.maksimowiczm.foodyou.assistant.domain.tool.write.DeleteEntriesTool
import com.maksimowiczm.foodyou.assistant.domain.tool.write.SetEatenTool
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntry
import com.maksimowiczm.foodyou.fooddiary.domain.entity.FoodDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.entity.RecentMealRef
import com.maksimowiczm.foodyou.fooddiary.domain.repository.FoodDiaryEntryRepository
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The ordering bug that would make undo useless.
 *
 * A delete tool that removes the rows and *then* asks the journal to remember them records nothing:
 * by the time the snapshot is taken there is nothing left to snapshot. It would look completely
 * fine in a manual test - the delete works - and the failure would only surface later, when someone
 * pressed undo and nothing came back.
 */
class WriteToolOrderingTest {

    /** Records the order in which the journal and the repository were touched. */
    private class SpyJournal(private val alive: () -> Set<Long>) : ChangeJournal {
        val calls = mutableListOf<String>()
        var capturedIds: List<Long> = emptyList()

        override suspend fun snapshot(entryIds: List<Long>): UndoAction {
            calls.add("snapshot")
            // Solo puede capturar lo que siga existiendo, igual que el journal de verdad.
            capturedIds = entryIds.filter { it in alive() }
            return UndoAction.DeleteMeasurements(capturedIds)
        }

        override suspend fun record(summary: String, undo: UndoAction): Long {
            calls.add("record:$summary")
            return 1L
        }

        override suspend fun recent(limit: Int): List<AssistantChange> = emptyList()

        override suspend fun undo(changeId: Long?): AssistantChange? = null

        override suspend fun redo(): AssistantChange? = null
    }

    private class MutableDiaryRepository(initial: List<FoodDiaryEntry>) : FoodDiaryEntryRepository {
        val entries = initial.associateBy { it.id.value }.toMutableMap()
        val eaten = mutableMapOf<Long, Boolean>()

        override fun observe(id: FoodDiaryEntryId): Flow<FoodDiaryEntry?> =
            flowOf(entries[id.value])

        override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<FoodDiaryEntry>> =
            flowOf(entries.values.filter { it.date >= from && it.date <= to })

        override fun observeAll(mealId: Long, date: LocalDate): Flow<List<FoodDiaryEntry>> =
            flowOf(entries.values.filter { it.mealId == mealId && it.date == date })

        override fun observeRecentMealRefs(limit: Int): Flow<List<RecentMealRef>> =
            flowOf(emptyList())

        override suspend fun delete(id: FoodDiaryEntryId) {
            entries.remove(id.value)
        }

        override suspend fun setEaten(id: FoodDiaryEntryId, isEaten: Boolean) {
            eaten[id.value] = isEaten
        }

        override suspend fun insert(
            measurement: Measurement,
            mealId: Long,
            date: LocalDate,
            food: DiaryFood,
            createdAt: LocalDateTime,
            createdByAssistant: Boolean,
        ): FoodDiaryEntryId = error("not needed")

        override suspend fun update(entry: FoodDiaryEntry) = error("not needed")

        override suspend fun updatePositions(updates: List<Pair<FoodDiaryEntryId, Int>>) =
            error("not needed")

        override suspend fun moveToMeal(
            id: FoodDiaryEntryId,
            targetMealId: Long,
            date: LocalDate,
        ) = error("not needed")
    }

    private val today = day("2026-08-27")
    private val rice = facts(energy = 130.0, proteins = 2.7, carbohydrates = 28.0)

    private fun fixture(): Pair<MutableDiaryRepository, SpyJournal> {
        val repository =
            MutableDiaryRepository(
                listOf(
                    entry(today, "Arroz largo", rice, 150.0),
                    entry(today, "Pan de trigo", rice, 50.0),
                )
            )
        return repository to SpyJournal { repository.entries.keys }
    }

    @Test
    fun deletingCapturesTheRowsBeforeRemovingThem() = runTest {
        val (repository, journal) = fixture()
        val ids = repository.entries.keys.toList()

        val result =
            DeleteEntriesTool(repository, journal)
                .call(argumentsWithLongs("entryIds", ids))

        // El snapshot tiene que haber cogido las dos entradas: si se hubiese pedido despues del
        // borrado habria capturado cero y el deshacer no traeria nada de vuelta.
        assertEquals(ids.sorted(), journal.capturedIds.sorted())
        assertTrue(journal.calls.first() == "snapshot", journal.calls.toString())
        assertTrue(repository.entries.isEmpty())
        assertEquals(2, result.intField("deleted"))
    }

    @Test
    fun deletingRecordsOneHistoryEntryForTheWholeBatch() = runTest {
        val (repository, journal) = fixture()

        DeleteEntriesTool(repository, journal)
            .call(argumentsWithLongs("entryIds", repository.entries.keys.toList()))

        // Dos alimentos borrados, un solo punto de historial: "deshacer" es un boton, no dos.
        assertEquals(1, journal.calls.count { it.startsWith("record:") })
    }

    @Test
    fun deletingSomethingThatDoesNotExistChangesNothing() = runTest {
        val (repository, journal) = fixture()

        val result = DeleteEntriesTool(repository, journal).call(argumentsWithLongs("entryIds", listOf(9999L)))

        assertEquals(2, repository.entries.size)
        assertTrue(journal.calls.none { it.startsWith("record:") })
        assertTrue(result.toString().contains("existe"), result.toString())
    }

    @Test
    fun markingAsEatenRemembersThePreviousState() = runTest {
        val (repository, journal) = fixture()
        val ids = repository.entries.keys.toList()

        SetEatenTool(repository, journal)
            .call(argumentsWithLongsAndFlag("entryIds", ids, "eaten", false))

        assertEquals(1, journal.calls.count { it.startsWith("record:") })
        assertTrue(ids.all { repository.eaten[it] == false })
    }
}

private fun argumentsWithLongs(key: String, values: List<Long>): JsonObject = buildJsonObject {
    put(key, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
}

private fun argumentsWithLongsAndFlag(
    key: String,
    values: List<Long>,
    flagKey: String,
    flag: Boolean,
): JsonObject = buildJsonObject {
    put(key, buildJsonArray { values.forEach { add(JsonPrimitive(it)) } })
    put(flagKey, JsonPrimitive(flag))
}

private fun JsonElement.intField(name: String): Int =
    (this as JsonObject).getValue(name).jsonPrimitive.content.toInt()
