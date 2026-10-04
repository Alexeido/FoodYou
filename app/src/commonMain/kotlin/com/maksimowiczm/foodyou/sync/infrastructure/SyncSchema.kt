package com.maksimowiczm.foodyou.sync.infrastructure

import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * The tables and triggers sync adds to the app's database.
 *
 * None of this is a Room entity on purpose: the diary tables stay exactly as they are, and sync
 * sits beside them. Triggers notice every write to a diary row - whoever makes it: a screen, the
 * assistant, an undo from its history - so nothing has to remember to tell sync about a change.
 *
 * - `SyncControl`: one row. `tracking` is on while sync is enabled; `applying` is on while sync
 *   itself writes what came from the server, so those writes are not sent back as new changes.
 * - `SyncIdMap`: the global id (`syncId`) of each local row. Local ids (1, 2, 3...) collide
 *   between devices; these never do.
 * - `SyncDirty`: one row per local change, with the time it happened. Emptied as changes reach
 *   the server.
 * - `SyncShadow`: each document as the server last confirmed it, with each field's clock. Comparing
 *   a row against it says which fields changed.
 * - `SyncPending`: documents from the server that could not be applied yet (an entry whose meal
 *   has not arrived), retried on the next sync.
 * - `SyncState`: small key/values, such as the server cursor.
 *
 * Everything is `IF NOT EXISTS` and runs on every open: a fresh install and an upgraded one end up
 * the same, without a Room migration.
 */
internal object SyncSchema {

    private const val NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000.0 AS INTEGER)"

    private const val ACTIVE =
        "(SELECT tracking FROM SyncControl WHERE id = 1) = 1 " +
            "AND (SELECT applying FROM SyncControl WHERE id = 1) = 0"

    private const val NEW_ID = "lower(hex(randomblob(16)))"

    /**
     * Every trigger is dropped and created again on each open, so a fix to one reaches phones
     * that already have the old version (CREATE ... IF NOT EXISTS alone would keep it).
     */
    val statements: List<String>
        get() =
            definitions.flatMap { statement ->
                val trigger = TRIGGER_NAME.find(statement)?.groupValues?.get(1)
                if (trigger == null) listOf(statement)
                else listOf("DROP TRIGGER IF EXISTS $trigger", statement)
            }

    private val TRIGGER_NAME = Regex("^CREATE TRIGGER IF NOT EXISTS (\\w+)")

    private val definitions: List<String> = buildList {
        add(
            "CREATE TABLE IF NOT EXISTS SyncControl (id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "tracking INTEGER NOT NULL DEFAULT 0, applying INTEGER NOT NULL DEFAULT 0)"
        )
        add("INSERT OR IGNORE INTO SyncControl (id, tracking, applying) VALUES (1, 0, 0)")
        // Si la app murió a mitad de aplicar cambios, que no se quede sin vigilar para siempre.
        add("UPDATE SyncControl SET applying = 0")
        add(
            "CREATE TABLE IF NOT EXISTS SyncIdMap (kind TEXT NOT NULL, localId INTEGER NOT NULL, " +
                "syncId TEXT NOT NULL, PRIMARY KEY (kind, localId))"
        )
        add("CREATE UNIQUE INDEX IF NOT EXISTS SyncIdMap_syncId ON SyncIdMap (kind, syncId)")
        add(
            "CREATE TABLE IF NOT EXISTS SyncDirty (seq INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "kind TEXT NOT NULL, syncId TEXT NOT NULL, changedAt INTEGER NOT NULL)"
        )
        add(
            "CREATE TABLE IF NOT EXISTS SyncShadow (kind TEXT NOT NULL, syncId TEXT NOT NULL, " +
                "fields TEXT NOT NULL, PRIMARY KEY (kind, syncId))"
        )
        add(
            "CREATE TABLE IF NOT EXISTS SyncPending (kind TEXT NOT NULL, syncId TEXT NOT NULL, " +
                "document TEXT NOT NULL, PRIMARY KEY (kind, syncId))"
        )
        add("CREATE TABLE IF NOT EXISTS SyncState (key TEXT PRIMARY KEY, value TEXT)")

        addAll(rowTriggers("sync_meal", "Meal", SyncKind.Meal.wire))
        addAll(rowTriggers("sync_entry", "Measurement", SyncKind.FoodEntry.wire))
        addAll(rowTriggers("sync_manual", "ManualDiaryEntry", SyncKind.ManualEntry.wire))
        addAll(childTriggers())
        addAll(rowTriggers("sync_recipe", "Recipe", SyncKind.Recipe.wire))
        addAll(recipeTriggers())
        addAll(memoryTriggers())
    }

    /**
     * A recipe's document carries its ingredients, and each ingredient product's figures: a
     * change to either is a change to the recipe.
     */
    private fun recipeTriggers(): List<String> {
        val kind = SyncKind.Recipe.wire
        val markRecipe = { ref: String ->
            "INSERT INTO SyncDirty (kind, syncId, changedAt) " +
                "SELECT '$kind', syncId, $NOW_MS FROM SyncIdMap " +
                "WHERE kind = '$kind' AND localId = $ref.recipeId;"
        }
        return listOf(
            "CREATE TRIGGER IF NOT EXISTS sync_recipe_ingredient_ai AFTER INSERT ON " +
                "RecipeIngredient WHEN $ACTIVE BEGIN ${markRecipe("NEW")} END",
            "CREATE TRIGGER IF NOT EXISTS sync_recipe_ingredient_au AFTER UPDATE ON " +
                "RecipeIngredient WHEN $ACTIVE BEGIN ${markRecipe("NEW")} END",
            "CREATE TRIGGER IF NOT EXISTS sync_recipe_ingredient_ad AFTER DELETE ON " +
                "RecipeIngredient WHEN $ACTIVE BEGIN ${markRecipe("OLD")} END",
            "CREATE TRIGGER IF NOT EXISTS sync_recipe_product_au AFTER UPDATE ON Product " +
                "WHEN $ACTIVE BEGIN INSERT INTO SyncDirty (kind, syncId, changedAt) " +
                "SELECT '$kind', m.syncId, $NOW_MS FROM RecipeIngredient ri " +
                "JOIN SyncIdMap m ON m.kind = '$kind' AND m.localId = ri.recipeId " +
                "WHERE ri.ingredientProductId = NEW.id; END",
        )
    }

    /** The assistant's memory is a single document: any change to it marks that one. */
    private fun memoryTriggers(): List<String> {
        val mark =
            "INSERT INTO SyncDirty (kind, syncId, changedAt) VALUES " +
                "('${SyncKind.Memory.wire}', '${com.maksimowiczm.foodyou.sync.domain.MEMORY_DOCUMENT_ID}', $NOW_MS);"
        return listOf("INSERT", "UPDATE", "DELETE").map { event ->
            "CREATE TRIGGER IF NOT EXISTS sync_memory_${event.lowercase()} AFTER $event ON " +
                "AssistantMemory WHEN $ACTIVE BEGIN $mark END"
        }
    }

    /** Insert, update and delete of a row that is a document of its own. */
    private fun rowTriggers(prefix: String, table: String, kind: String): List<String> {
        val markRow = { ref: String ->
            "INSERT INTO SyncDirty (kind, syncId, changedAt) " +
                "SELECT '$kind', syncId, $NOW_MS FROM SyncIdMap " +
                "WHERE kind = '$kind' AND localId = $ref.id;"
        }
        // Sin OR IGNORE a propósito: dentro de un disparador manda la política de conflicto de la
        // sentencia de fuera, y Room actualiza con UPDATE OR ABORT. Un OR IGNORE aquí se volvía
        // ABORT y cambiar una comida petaba. Mirando antes si ya tiene id no hay conflicto.
        val assignId = { ref: String ->
            "INSERT INTO SyncIdMap (kind, localId, syncId) " +
                "SELECT '$kind', $ref.id, $NEW_ID WHERE NOT EXISTS " +
                "(SELECT 1 FROM SyncIdMap WHERE kind = '$kind' AND localId = $ref.id);"
        }
        return listOf(
            "CREATE TRIGGER IF NOT EXISTS ${prefix}_ai AFTER INSERT ON $table WHEN $ACTIVE " +
                "BEGIN ${assignId("NEW")} ${markRow("NEW")} END",
            "CREATE TRIGGER IF NOT EXISTS ${prefix}_au AFTER UPDATE ON $table WHEN $ACTIVE " +
                "BEGIN ${assignId("NEW")} ${markRow("NEW")} END",
            // El borrado se anota con el syncId de la fila y luego se olvida el mapeo: la
            // próxima sincronización ve que la fila ya no existe y manda _deleted.
            "CREATE TRIGGER IF NOT EXISTS ${prefix}_ad AFTER DELETE ON $table WHEN $ACTIVE " +
                "BEGIN ${markRow("OLD")} " +
                "DELETE FROM SyncIdMap WHERE kind = '$kind' AND localId = OLD.id; END",
        )
    }

    /** A manual entry's ingredients are part of the entry's document. */
    private fun childTriggers(): List<String> {
        val kind = SyncKind.ManualEntry.wire
        val markParent = { ref: String ->
            "INSERT INTO SyncDirty (kind, syncId, changedAt) " +
                "SELECT '$kind', syncId, $NOW_MS FROM SyncIdMap " +
                "WHERE kind = '$kind' AND localId = $ref.entryId;"
        }
        return listOf(
            "CREATE TRIGGER IF NOT EXISTS sync_manual_ingredient_ai AFTER INSERT ON " +
                "ManualDiaryEntryIngredient WHEN $ACTIVE BEGIN ${markParent("NEW")} END",
            "CREATE TRIGGER IF NOT EXISTS sync_manual_ingredient_au AFTER UPDATE ON " +
                "ManualDiaryEntryIngredient WHEN $ACTIVE BEGIN ${markParent("NEW")} END",
            "CREATE TRIGGER IF NOT EXISTS sync_manual_ingredient_ad AFTER DELETE ON " +
                "ManualDiaryEntryIngredient WHEN $ACTIVE BEGIN ${markParent("OLD")} END",
        )
    }
}

/** Creates the sync tables and triggers every time the database opens. */
internal class SyncSchemaCallback : RoomDatabase.Callback() {
    override fun onOpen(connection: SQLiteConnection) {
        SyncSchema.statements.forEach(connection::execSQL)
    }
}
