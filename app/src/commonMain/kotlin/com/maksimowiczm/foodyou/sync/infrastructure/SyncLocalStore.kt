package com.maksimowiczm.foodyou.sync.infrastructure

import com.maksimowiczm.foodyou.sync.domain.DELETED_FIELD
import com.maksimowiczm.foodyou.sync.domain.GOALS_DOCUMENT_ID
import com.maksimowiczm.foodyou.sync.domain.MEMORY_DOCUMENT_ID
import com.maksimowiczm.foodyou.sync.domain.SyncedGoals
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** What sync keeps in step, in the order documents must be applied (meals before their entries). */
internal enum class SyncKind(val wire: String, val table: String) {
    Meal("meal", "Meal"),
    FoodEntry("food_entry", "Measurement"),
    ManualEntry("manual_entry", "ManualDiaryEntry"),

    /** A recipe of the library, with its ingredients: copies of products, or other recipes. */
    Recipe("recipe", "Recipe"),

    /** The goals: settings, not a table (see [SyncedGoals]). One document, [GOALS_DOCUMENT_ID]. */
    Goals("goals", ""),

    /** What the assistant remembers about the person: one document, one field per thing. */
    Memory("memory", "");

    /** One document per account instead of one per row. */
    val isSingleton: Boolean
        get() = table.isEmpty()

    companion object {
        fun of(wire: String): SyncKind? = entries.firstOrNull { it.wire == wire }
    }
}

/** A field as the server last confirmed it. */
@Serializable internal data class ShadowField(val value: JsonElement, val clock: Long)

/**
 * Rows <-> documents.
 *
 * A document's fields are the row's columns, minus the local ids that mean nothing on another
 * device. Those become references by global id instead: an entry's `meal` is the meal's syncId,
 * and its food - the copy of the product or recipe the entry carries - travels whole as `food`.
 */
internal class SyncLocalStore(private val goals: SyncedGoals? = null) {

    private val columnsCache = mutableMapOf<String, List<String>>()
    private val json = Json { encodeDefaults = true }

    // --- Columns and rows --------------------------------------------------------------------

    suspend fun Sql.columns(table: String): List<String> =
        columnsCache.getOrPut(table) {
            query("PRAGMA table_info($table)").map { it["name"] as String }
        }

    private suspend fun Sql.row(table: String, id: Long): Map<String, Any?>? =
        query("SELECT * FROM $table WHERE id = ?", id).firstOrNull()

    private fun Map<String, Any?>.toJson(vararg without: String): Map<String, JsonElement> =
        filterKeys { it !in without }.mapValues { (_, v) -> v.toJsonElement() }

    /** Inserts [values] into [table], ignoring keys that are not columns of it. */
    private suspend fun Sql.insertRow(table: String, values: Map<String, Any?>): Long {
        val columns = columns(table).toSet()
        val usable = values.filterKeys { it in columns && it != "id" }
        val names = usable.keys.toList()
        return insert(
            "INSERT INTO $table (${names.joinToString(",") { "\"$it\"" }}) " +
                "VALUES (${names.joinToString(",") { "?" }})",
            *names.map { usable[it] }.toTypedArray(),
        )
    }

    private suspend fun Sql.updateRow(table: String, id: Long, values: Map<String, Any?>) {
        val columns = columns(table).toSet()
        val usable = values.filterKeys { it in columns && it != "id" }
        if (usable.isEmpty()) return
        val names = usable.keys.toList()
        exec(
            "UPDATE $table SET ${names.joinToString(",") { "\"$it\" = ?" }} WHERE id = ?",
            *(names.map { usable[it] } + id).toTypedArray(),
        )
    }

    // --- Ids ----------------------------------------------------------------------------------

    suspend fun Sql.localId(kind: SyncKind, syncId: String): Long? =
        query("SELECT localId FROM SyncIdMap WHERE kind = ? AND syncId = ?", kind.wire, syncId)
            .firstOrNull()
            ?.get("localId") as Long?

    suspend fun Sql.syncId(kind: SyncKind, localId: Long): String? =
        query("SELECT syncId FROM SyncIdMap WHERE kind = ? AND localId = ?", kind.wire, localId)
            .firstOrNull()
            ?.get("syncId") as String?

    /** Gives every existing row a global id, and forgets ids of rows that no longer exist. */
    suspend fun Sql.assignMissingIds() {
        SyncKind.entries.filter { it.table.isNotEmpty() }.forEach { kind ->
            exec(
                "DELETE FROM SyncIdMap WHERE kind = ? AND localId NOT IN (SELECT id FROM ${kind.table})",
                kind.wire,
            )
            exec(
                "INSERT OR IGNORE INTO SyncIdMap (kind, localId, syncId) " +
                    "SELECT ?, id, lower(hex(randomblob(16))) FROM ${kind.table}",
                kind.wire,
            )
        }
    }

    /** Whether this device syncs its goals (always in the app; tests may leave them out). */
    val syncsGoals: Boolean
        get() = goals != null

    suspend fun Sql.localRows(kind: SyncKind): List<Pair<Long, String>> =
        query("SELECT localId, syncId FROM SyncIdMap WHERE kind = ?", kind.wire).map {
            (it["localId"] as Long) to (it["syncId"] as String)
        }

    // --- Reading documents -------------------------------------------------------------------

    /** The document for a row as it is now, or null when the row does not exist. */
    suspend fun Sql.readDocument(kind: SyncKind, syncId: String): Map<String, JsonElement>? {
        if (kind == SyncKind.Goals) return if (syncId == GOALS_DOCUMENT_ID) goals?.read() else null
        if (kind == SyncKind.Memory) return if (syncId == MEMORY_DOCUMENT_ID) readMemory() else null
        val id = localId(kind, syncId) ?: return null
        val row = row(kind.table, id) ?: return null
        return when (kind) {
            SyncKind.Meal -> row.toJson("id")
            SyncKind.FoodEntry ->
                row.toJson("id", "mealId", "productId", "recipeId") +
                    mapOf(
                        "meal" to mealRef(row["mealId"] as Long),
                        "food" to readFood(row["productId"] as Long?, row["recipeId"] as Long?),
                    )
            SyncKind.ManualEntry ->
                row.toJson("id", "mealId") +
                    mapOf(
                        "meal" to mealRef(row["mealId"] as Long),
                        "ingredients" to
                            JsonArray(
                                query(
                                        "SELECT * FROM ManualDiaryEntryIngredient " +
                                            "WHERE entryId = ? ORDER BY position, id",
                                        id,
                                    )
                                    .map { JsonObject(it.toJson("id", "entryId")) }
                            ),
                    )
            SyncKind.Recipe ->
                row.toJson("id") +
                    mapOf(
                        "ingredients" to
                            JsonArray(
                                query("SELECT * FROM RecipeIngredient WHERE recipeId = ? ORDER BY id", id)
                                    .map { readRecipeIngredient(it) }
                            )
                    )
            SyncKind.Goals,
            SyncKind.Memory -> null
        }
    }

    private suspend fun Sql.mealRef(mealId: Long): JsonElement =
        syncId(SyncKind.Meal, mealId)?.let(::JsonPrimitive) ?: JsonNull

    private suspend fun Sql.readFood(productId: Long?, recipeId: Long?): JsonElement {
        if (productId != null) {
            val product = row("DiaryProduct", productId) ?: return JsonNull
            return JsonObject(mapOf("product" to JsonObject(product.toJson("id"))))
        }
        if (recipeId != null) {
            val recipe = row("DiaryRecipe", recipeId) ?: return JsonNull
            val ingredients =
                query("SELECT * FROM DiaryRecipeIngredient WHERE recipeId = ? ORDER BY id", recipeId)
                    .map { ingredient ->
                        JsonObject(
                            ingredient.toJson(
                                "id",
                                "recipeId",
                                "ingredientProductId",
                                "ingredientRecipeId",
                            ) +
                                (
                                    "food" to
                                        readFood(
                                            ingredient["ingredientProductId"] as Long?,
                                            ingredient["ingredientRecipeId"] as Long?,
                                        )
                                )
                        )
                    }
            return JsonObject(
                mapOf(
                    "recipe" to JsonObject(recipe.toJson("id") + ("ingredients" to JsonArray(ingredients)))
                )
            )
        }
        return JsonNull
    }

    // --- Writing documents -------------------------------------------------------------------

    /**
     * Makes the local rows match [fields]. Returns false when it cannot yet - an entry whose
     * meal has not arrived - so the caller keeps the document and tries again later.
     */
    suspend fun Sql.applyDocument(
        kind: SyncKind,
        syncId: String,
        fields: Map<String, JsonElement>,
        deleted: Boolean,
    ): Boolean {
        if (kind == SyncKind.Goals) {
            // Las metas no se borran: un documento borrado (no debería haberlo) no cambia nada.
            if (!deleted && syncId == GOALS_DOCUMENT_ID) goals?.apply(fields)
            return true
        }
        if (kind == SyncKind.Memory) {
            if (!deleted && syncId == MEMORY_DOCUMENT_ID) applyMemory(fields)
            return true
        }
        val existing = localId(kind, syncId)
        if (deleted) {
            if (existing != null) deleteLocal(kind, existing)
            return true
        }
        return when (kind) {
            SyncKind.Meal -> {
                val values = fields.columnValues("meal", "food", "ingredients")
                if (existing != null) updateRow("Meal", existing, values)
                else mapId(kind, insertRow("Meal", values), syncId)
                true
            }
            SyncKind.FoodEntry -> applyFoodEntry(syncId, fields, existing)
            SyncKind.ManualEntry -> applyManualEntry(syncId, fields, existing)
            SyncKind.Recipe -> applyRecipe(syncId, fields, existing)
            SyncKind.Goals,
            SyncKind.Memory -> true
        }
    }

    // --- Recipes ------------------------------------------------------------------------------

    /**
     * A product as it travels inside a recipe: every column but the local id and what only
     * matters on this phone (favourite, edited). Always the same set, so a product read back
     * after being applied is exactly what arrived and nothing bounces between devices.
     */
    private suspend fun Sql.productColumns(): List<String> =
        columns("Product").filter { it !in LOCAL_PRODUCT_COLUMNS }

    private suspend fun Sql.readRecipeIngredient(row: Map<String, Any?>): JsonObject {
        val values = mutableMapOf<String, JsonElement>(
            "measurement" to row["measurement"].toJsonElement(),
            "quantity" to row["quantity"].toJsonElement(),
        )
        val productId = row["ingredientProductId"] as Long?
        val recipeId = row["ingredientRecipeId"] as Long?
        if (productId != null) {
            val product = row("Product", productId)
            val columns = productColumns()
            values["product"] =
                JsonObject(columns.associateWith { (product?.get(it)).toJsonElement() })
        } else if (recipeId != null) {
            values["recipe"] = syncId(SyncKind.Recipe, recipeId)?.let { JsonPrimitive(it) } ?: JsonNull
        }
        return JsonObject(values)
    }

    private suspend fun Sql.applyRecipe(
        syncId: String,
        fields: Map<String, JsonElement>,
        existing: Long?,
    ): Boolean {
        val ingredients = (fields["ingredients"] as? JsonArray)?.filterIsInstance<JsonObject>().orEmpty()
        // Una receta que lleva otra espera a que esa otra exista aquí.
        val nested =
            ingredients.map { ingredient ->
                val ref = (ingredient["recipe"] as? JsonPrimitive)?.contentOrNull
                if (ref != null) localId(SyncKind.Recipe, ref) ?: return false else null
            }
        val values = fields.columnValues("ingredients")
        val id =
            if (existing == null) {
                insertRow("Recipe", values).also { mapId(SyncKind.Recipe, it, syncId) }
            } else {
                updateRow("Recipe", existing, values)
                exec("DELETE FROM RecipeIngredient WHERE recipeId = ?", existing)
                existing
            }
        ingredients.forEachIndexed { index, ingredient ->
            val productId =
                (ingredient["product"] as? JsonObject)?.let { findOrCreateProduct(it) }
            val recipeId = nested[index]
            if (productId == null && recipeId == null) return@forEachIndexed
            insertRow(
                "RecipeIngredient",
                mapOf(
                    "recipeId" to id,
                    "ingredientProductId" to productId,
                    "ingredientRecipeId" to recipeId,
                    "measurement" to ingredient["measurement"]?.toSqlValue(),
                    "quantity" to ingredient["quantity"]?.toSqlValue(),
                ),
            )
        }
        return true
    }

    /**
     * The library product a recipe ingredient is: the same one if this phone already has it
     * (same name, brand and figures), or a new one. Products are never deleted from here - they
     * may be in other recipes or in the person's searches.
     */
    private suspend fun Sql.findOrCreateProduct(product: JsonObject): Long {
        val columns = productColumns()
        val values = columns.associateWith { product[it]?.toSqlValue() }
        // Los nulos van escritos (IS NULL), no como parámetro: no todos los drivers los enlazan.
        val (nulls, known) = columns.partition { values[it] == null }
        val match =
            query(
                    "SELECT id FROM Product WHERE " +
                        (known.map { "\"$it\" = ?" } + nulls.map { "\"$it\" IS NULL" })
                            .joinToString(" AND ") +
                        " LIMIT 1",
                    *known.map { values[it] }.toTypedArray(),
                )
                .firstOrNull()
                ?.get("id") as Long?
        return match ?: insertRow("Product", values + mapOf("isFavorite" to 0L, "isEdited" to 0L))
    }

    // --- The assistant's memory --------------------------------------------------------------

    private suspend fun Sql.readMemory(): Map<String, JsonElement> =
        query("SELECT key, value FROM AssistantMemory").associate {
            (it["key"] as String) to JsonPrimitive(it["value"] as String)
        }

    /** Each field is one remembered thing; null means it was forgotten. */
    private suspend fun Sql.applyMemory(fields: Map<String, JsonElement>) {
        fields.forEach { (key, value) ->
            val text = (value as? JsonPrimitive)?.contentOrNull
            if (value is JsonNull || text == null) {
                exec("DELETE FROM AssistantMemory WHERE key = ?", key)
            } else {
                exec(
                    "INSERT OR REPLACE INTO AssistantMemory (key, value, updatedAt) VALUES (?, ?, ?)",
                    key,
                    text,
                    nowEpochSeconds(),
                )
            }
        }
    }

    private suspend fun Sql.applyFoodEntry(
        syncId: String,
        fields: Map<String, JsonElement>,
        existing: Long?,
    ): Boolean {
        val mealId = mealIdFrom(fields) ?: return false
        val food = fields["food"]?.takeIf { it is JsonObject } ?: return false
        val values = fields.columnValues("meal", "food") + ("mealId" to mealId)

        if (existing == null) {
            val (productId, recipeId) = writeFood(food.jsonObject)
            val id =
                insertRow("Measurement", values + mapOf("productId" to productId, "recipeId" to recipeId))
            mapId(SyncKind.FoodEntry, id, syncId)
            return true
        }

        val current = row("Measurement", existing) ?: return false
        val oldProduct = current["productId"] as Long?
        val oldRecipe = current["recipeId"] as Long?
        // La copia del alimento solo se rehace si ha cambiado: marcar como comido no la toca.
        if (sameJson(readFood(oldProduct, oldRecipe), food)) {
            updateRow("Measurement", existing, values)
        } else {
            val (productId, recipeId) = writeFood(food.jsonObject)
            updateRow(
                "Measurement",
                existing,
                values + mapOf("productId" to productId, "recipeId" to recipeId),
            )
            deleteFood(oldProduct, oldRecipe)
        }
        return true
    }

    private suspend fun Sql.applyManualEntry(
        syncId: String,
        fields: Map<String, JsonElement>,
        existing: Long?,
    ): Boolean {
        val mealId = mealIdFrom(fields) ?: return false
        val values = fields.columnValues("meal", "ingredients") + ("mealId" to mealId)
        val id =
            if (existing == null) {
                insertRow("ManualDiaryEntry", values).also { mapId(SyncKind.ManualEntry, it, syncId) }
            } else {
                updateRow("ManualDiaryEntry", existing, values)
                exec("DELETE FROM ManualDiaryEntryIngredient WHERE entryId = ?", existing)
                existing
            }
        (fields["ingredients"] as? JsonArray)?.forEach { ingredient ->
            if (ingredient is JsonObject) {
                insertRow(
                    "ManualDiaryEntryIngredient",
                    ingredient.mapValues { it.value.toSqlValue() } + ("entryId" to id),
                )
            }
        }
        return true
    }

    private suspend fun Sql.writeFood(food: JsonObject): Pair<Long?, Long?> {
        food["product"]?.let { product ->
            val values = product.jsonObject.mapValues { it.value.toSqlValue() }
            return insertRow("DiaryProduct", values) to null
        }
        val recipe = food["recipe"]?.jsonObject ?: error("A food is a product or a recipe")
        val recipeId =
            insertRow(
                "DiaryRecipe",
                recipe.filterKeys { it != "ingredients" }.mapValues { it.value.toSqlValue() },
            )
        (recipe["ingredients"] as? JsonArray)?.forEach { element ->
            val ingredient = element.jsonObject
            val (productId, nestedRecipeId) =
                writeFood((ingredient["food"] as? JsonObject) ?: error("Ingredient without food"))
            insertRow(
                "DiaryRecipeIngredient",
                ingredient.filterKeys { it != "food" }.mapValues { it.value.toSqlValue() } +
                    mapOf(
                        "recipeId" to recipeId,
                        "ingredientProductId" to productId,
                        "ingredientRecipeId" to nestedRecipeId,
                    ),
            )
        }
        return null to recipeId
    }

    /** As the diary does when an entry's food is replaced: the old copy goes. */
    private suspend fun Sql.deleteFood(productId: Long?, recipeId: Long?) {
        productId?.let { exec("DELETE FROM DiaryProduct WHERE id = ?", it) }
        recipeId?.let { exec("DELETE FROM DiaryRecipe WHERE id = ?", it) }
    }

    private suspend fun Sql.deleteLocal(kind: SyncKind, localId: Long) {
        exec("DELETE FROM ${kind.table} WHERE id = ?", localId)
        exec("DELETE FROM SyncIdMap WHERE kind = ? AND localId = ?", kind.wire, localId)
    }

    private suspend fun Sql.mapId(kind: SyncKind, localId: Long, syncId: String) {
        exec(
            "INSERT OR REPLACE INTO SyncIdMap (kind, localId, syncId) VALUES (?, ?, ?)",
            kind.wire,
            localId,
            syncId,
        )
    }

    /** Points a local row at another global id: a meal joined with the account's same meal. */
    suspend fun Sql.remap(kind: SyncKind, localId: Long, syncId: String) {
        exec("DELETE FROM SyncIdMap WHERE kind = ? AND syncId = ?", kind.wire, syncId)
        mapId(kind, localId, syncId)
    }

    /** The local id of the meal an entry document points at, if that meal is here. */
    private suspend fun Sql.mealIdFrom(fields: Map<String, JsonElement>): Long? =
        (fields["meal"] as? JsonPrimitive)?.contentOrNull?.let { localId(SyncKind.Meal, it) }

    // --- Shadow, pending, dirty, state ------------------------------------------------------

    suspend fun Sql.shadow(kind: SyncKind, syncId: String): Map<String, ShadowField>? =
        query("SELECT fields FROM SyncShadow WHERE kind = ? AND syncId = ?", kind.wire, syncId)
            .firstOrNull()
            ?.let { json.decodeFromString<Map<String, ShadowField>>(it["fields"] as String) }

    suspend fun Sql.saveShadow(kind: SyncKind, syncId: String, fields: Map<String, ShadowField>) {
        exec(
            "INSERT OR REPLACE INTO SyncShadow (kind, syncId, fields) VALUES (?, ?, ?)",
            kind.wire,
            syncId,
            json.encodeToString(fields),
        )
    }

    suspend fun Sql.state(key: String): String? =
        query("SELECT value FROM SyncState WHERE key = ?", key).firstOrNull()?.get("value") as String?

    suspend fun Sql.setState(key: String, value: String?) {
        if (value == null) exec("DELETE FROM SyncState WHERE key = ?", key)
        else exec("INSERT OR REPLACE INTO SyncState (key, value) VALUES (?, ?)", key, value)
    }

    suspend fun Sql.setTracking(on: Boolean) =
        exec("UPDATE SyncControl SET tracking = ? WHERE id = 1", if (on) 1L else 0L)

    suspend fun Sql.isTracking(): Boolean =
        query("SELECT tracking FROM SyncControl WHERE id = 1").firstOrNull()?.get("tracking") == 1L

    suspend fun Sql.setApplying(on: Boolean) =
        exec("UPDATE SyncControl SET applying = ? WHERE id = 1", if (on) 1L else 0L)

    companion object {
        /** Product columns that mean something only on the phone that has them. */
        private val LOCAL_PRODUCT_COLUMNS = setOf("id", "isFavorite", "isEdited")

        private fun nowEpochSeconds(): Long =
            kotlin.time.Clock.System.now().epochSeconds

        private fun Map<String, JsonElement>.columnValues(vararg without: String): Map<String, Any?> =
            filterKeys { it !in without && it != DELETED_FIELD }.mapValues { it.value.toSqlValue() }
    }
}

// --- JSON <-> SQLite values -----------------------------------------------------------------

internal fun Any?.toJsonElement(): JsonElement =
    when (this) {
        null -> JsonNull
        is Long -> JsonPrimitive(this)
        is Double -> JsonPrimitive(this)
        is String -> JsonPrimitive(this)
        else -> JsonPrimitive(toString())
    }

internal fun JsonElement.toSqlValue(): Any? =
    when (this) {
        is JsonNull -> null
        is JsonPrimitive ->
            when {
                isString -> content
                booleanOrNull != null -> if (booleanOrNull == true) 1L else 0L
                else -> longOrNull ?: content.toDoubleOrNull()
            }
        else -> toString()
    }

/**
 * Equality that does not care how a number was written: `150` and `150.0` are the same value,
 * whether it came from SQLite's REAL column or from another client's JSON.
 */
internal fun sameJson(a: JsonElement?, b: JsonElement?): Boolean =
    when {
        a == null || b == null -> a == b
        a is JsonObject && b is JsonObject ->
            a.keys == b.keys && a.keys.all { sameJson(a[it], b[it]) }
        a is JsonArray && b is JsonArray -> a.size == b.size && a.indices.all { sameJson(a[it], b[it]) }
        a is JsonPrimitive && b is JsonPrimitive && !a.isString && !b.isString -> {
            val x = a.contentOrNull?.toDoubleOrNull()
            val y = b.contentOrNull?.toDoubleOrNull()
            if (x != null && y != null) x == y else a.contentOrNull == b.contentOrNull
        }
        else -> a == b
    }
