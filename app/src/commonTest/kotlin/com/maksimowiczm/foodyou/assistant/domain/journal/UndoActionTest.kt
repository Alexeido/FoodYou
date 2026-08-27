package com.maksimowiczm.foodyou.assistant.domain.journal

import com.maksimowiczm.foodyou.assistant.infrastructure.journal.toEntity
import com.maksimowiczm.foodyou.assistant.infrastructure.journal.toSnapshot
import com.maksimowiczm.foodyou.common.domain.measurement.MeasurementType
import com.maksimowiczm.foodyou.fooddiary.infrastructure.room.MeasurementEntity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json

/**
 * The two ways undo dies quietly.
 *
 * Either the payload does not survive a trip through the database as text, or the snapshot drops a
 * field and the restored entry comes back subtly different from the one that was deleted. Both fail
 * without an exception, which is why they are worth pinning down here rather than trusting to a
 * manual pass on a phone.
 */
class UndoActionTest {

    private val json = Json { encodeDefaults = true }

    private val row =
        MeasurementEntity(
            id = 412,
            mealId = 3,
            epochDay = 20_692,
            productId = 991,
            recipeId = null,
            measurement = MeasurementType.Gram,
            quantity = 137.5,
            isEaten = true,
            createdAt = 1_756_000_000,
            updatedAt = 1_756_000_500,
            position = 7,
        )

    @Test
    fun aMeasurementSurvivesTheRoundTripThroughASnapshot() {
        // Un campo perdido aqui es una entrada restaurada que no es la que se borro.
        assertEquals(row, row.toSnapshot().toEntity())
    }

    @Test
    fun restoringADeletionSerializesLosslessly() {
        val action = UndoAction.RestoreMeasurements(listOf(row.toSnapshot()))

        val encoded = json.encodeToString(UndoAction.serializer(), action)
        val decoded = json.decodeFromString(UndoAction.serializer(), encoded)

        assertEquals(action, decoded)
        assertEquals(row, (decoded as UndoAction.RestoreMeasurements).rows.single().toEntity())
    }

    @Test
    fun everyVariantIsPolymorphicallySerializable() {
        val actions =
            listOf(
                UndoAction.DeleteMeasurements(listOf(1, 2, 3)),
                UndoAction.RestoreMeasurements(listOf(row.toSnapshot())),
                UndoAction.SetEaten(listOf(EatenState(1, true), EatenState(2, false))),
                UndoAction.Batch(
                    listOf(
                        UndoAction.DeleteMeasurements(listOf(9)),
                        UndoAction.SetEaten(listOf(EatenState(9, false))),
                    )
                ),
            )

        actions.forEach { action ->
            val encoded = json.encodeToString(UndoAction.serializer(), action)
            assertEquals(action, json.decodeFromString(UndoAction.serializer(), encoded))
        }
    }

    @Test
    fun aBatchKeepsItsOrder() {
        // Deshacer un plan entero depende de que el orden sobreviva: primero lo ultimo.
        val batch =
            UndoAction.Batch(
                listOf(
                    UndoAction.DeleteMeasurements(listOf(1)),
                    UndoAction.DeleteMeasurements(listOf(2)),
                    UndoAction.DeleteMeasurements(listOf(3)),
                )
            )

        val decoded =
            json.decodeFromString(
                UndoAction.serializer(),
                json.encodeToString(UndoAction.serializer(), batch),
            ) as UndoAction.Batch

        assertEquals(
            listOf(1L, 2L, 3L),
            decoded.actions.map { (it as UndoAction.DeleteMeasurements).ids.single() },
        )
    }

    @Test
    fun theEncodedFormNamesItsVariant() {
        // El discriminador es lo que permite leer un journal escrito por una version anterior.
        val encoded =
            json.encodeToString(
                UndoAction.serializer(),
                UndoAction.DeleteMeasurements(listOf(5)),
            )

        assertTrue(encoded.contains("deleteMeasurements"), encoded)
    }
}
