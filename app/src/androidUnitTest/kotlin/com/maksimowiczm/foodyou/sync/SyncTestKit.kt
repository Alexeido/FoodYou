package com.maksimowiczm.foodyou.sync

import com.maksimowiczm.foodyou.sync.domain.SyncedGoals
import kotlinx.serialization.json.JsonElement
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.sync.domain.PairingCode
import com.maksimowiczm.foodyou.sync.domain.SyncApi
import com.maksimowiczm.foodyou.sync.domain.SyncEvent
import com.maksimowiczm.foodyou.sync.domain.SyncConfig
import com.maksimowiczm.foodyou.sync.domain.SyncConfigRepository
import com.maksimowiczm.foodyou.sync.domain.SyncDocument
import com.maksimowiczm.foodyou.sync.domain.SyncField
import com.maksimowiczm.foodyou.sync.domain.SyncRequest
import com.maksimowiczm.foodyou.sync.domain.SyncResponse
import com.maksimowiczm.foodyou.sync.domain.SyncServerStatus
import com.maksimowiczm.foodyou.sync.infrastructure.Sql
import com.maksimowiczm.foodyou.sync.infrastructure.SqlExecutor
import com.maksimowiczm.foodyou.sync.infrastructure.SyncEngine
import com.maksimowiczm.foodyou.sync.infrastructure.SyncLocalStore
import com.maksimowiczm.foodyou.sync.infrastructure.SyncSchema
import java.io.File
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Two or more "phones" - each a real SQLite database with the app's own diary tables, created from
 * the exported Room schema, plus the sync tables and triggers - talking to a fake server that
 * merges exactly as docs/sync/protocol.md says (and as sync-server/ does).
 */

/** The diary tables, exactly as Room creates them in the current schema. */
private val diaryTables =
    listOf(
        "Meal",
        "DiaryProduct",
        "DiaryRecipe",
        "DiaryRecipeIngredient",
        "Measurement",
        "ManualDiaryEntry",
        "ManualDiaryEntryIngredient",
        "Product",
        "Recipe",
        "RecipeIngredient",
        "AssistantMemory",
    )

private fun createDiarySchema(db: SQLiteDatabase) {
    val schemaDir = File("schemas/com.maksimowiczm.foodyou.app.infrastructure.room.FoodYouDatabase")
    val latest =
        schemaDir.listFiles()!!.maxBy { it.nameWithoutExtension.toIntOrNull() ?: -1 }
    val entities =
        Json.parseToJsonElement(latest.readText()).jsonObject["database"]!!.jsonObject["entities"]!!
            .jsonArray
    diaryTables.forEach { table ->
        val entity = entities.first { it.jsonObject["tableName"]!!.jsonPrimitive.content == table }
        val create =
            entity.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
        db.execSQL(create)
        entity.jsonObject["indices"]?.jsonArray?.forEach { index ->
            db.execSQL(
                index.jsonObject["createSql"]!!.jsonPrimitive.content.replace("\${TABLE_NAME}", table)
            )
        }
    }
}

internal class TestSqlExecutor(val db: SQLiteDatabase) : SqlExecutor {
    var failNext = false

    override suspend fun <T> transaction(block: suspend Sql.() -> T): T {
        db.beginTransaction()
        try {
            val result = AndroidSql(db).block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }
}

private class AndroidSql(private val db: SQLiteDatabase) : Sql {
    override suspend fun query(sql: String, vararg args: Any?): List<Map<String, Any?>> {
        db.rawQuery(sql, args.map { it?.toString() }.toTypedArray()).use { cursor ->
            val rows = mutableListOf<Map<String, Any?>>()
            while (cursor.moveToNext()) {
                rows += (0 until cursor.columnCount).associate { i -> cursor.getColumnName(i) to cursor.value(i) }
            }
            return rows
        }
    }

    override suspend fun exec(sql: String, vararg args: Any?) {
        db.compileStatement(sql).use { st ->
            args.forEachIndexed { i, v ->
                when (v) {
                    null -> st.bindNull(i + 1)
                    is Long -> st.bindLong(i + 1, v)
                    is Int -> st.bindLong(i + 1, v.toLong())
                    is Double -> st.bindDouble(i + 1, v)
                    is String -> st.bindString(i + 1, v)
                    else -> error("bind $v")
                }
            }
            st.execute()
        }
    }

    override suspend fun insert(sql: String, vararg args: Any?): Long {
        exec(sql, *args)
        return query("SELECT last_insert_rowid() AS id").single()["id"] as Long
    }

    private fun Cursor.value(i: Int): Any? =
        when (getType(i)) {
            Cursor.FIELD_TYPE_NULL -> null
            Cursor.FIELD_TYPE_INTEGER -> getLong(i)
            Cursor.FIELD_TYPE_FLOAT -> getDouble(i)
            Cursor.FIELD_TYPE_STRING -> getString(i)
            else -> null
        }
}

/** The protocol's server, in memory: per-field last writer wins, seq per change, paging. */
internal class FakeSyncServer(private val pageSize: Int = 500) {
    private val documents = linkedMapOf<Pair<String, String>, SyncDocument>()
    private var seq = 0L
    var offline = false
    val requests = mutableListOf<SyncRequest>()

    fun documents(kind: String) = documents.values.filter { it.kind == kind }

    fun api(device: String): SyncApi =
        object : SyncApi {
            override suspend fun sync(config: SyncConfig, request: SyncRequest): SyncResponse {
                if (offline) throw IOException("sin red")
                requests += request
                request.changes.forEach { change ->
                    val key = change.kind to change.id
                    val stored = documents[key]?.fields?.toMutableMap() ?: mutableMapOf()
                    var changed = false
                    change.fields.forEach { (name, field) ->
                        val incoming = SyncField(field.value, field.clock, device)
                        val current = stored[name]
                        if (
                            current == null ||
                                field.clock > current.clock ||
                                (field.clock == current.clock && device > (current.device ?: ""))
                        ) {
                            stored[name] = incoming
                            changed = true
                        }
                    }
                    if (changed) {
                        seq++
                        documents.remove(key)
                        documents[key] =
                            SyncDocument(
                                change.kind,
                                change.id,
                                seq,
                                stored["_deleted"]?.value == JsonPrimitive(true),
                                stored,
                            )
                    }
                }
                val cursor = if (request.cursor > seq) 0 else request.cursor
                val newer = documents.values.filter { it.seq > cursor }.sortedBy { it.seq }
                val page = newer.take(pageSize)
                return SyncResponse(page.lastOrNull()?.seq ?: cursor, newer.size > pageSize, page)
            }

            override suspend fun status(config: SyncConfig) =
                SyncServerStatus("test", seq, documents.size.toLong(), System.currentTimeMillis())

            override suspend fun pairingCode(config: SyncConfig) = PairingCode("123456", 600)

            override fun events(config: SyncConfig): Flow<SyncEvent> = emptyFlow()
        }
}

internal class FixedConfig(enabled: Boolean = true) : SyncConfigRepository {
    val flow = MutableStateFlow<SyncConfig?>(SyncConfig("https://sync.test", "ana", "x", enabled))

    override fun observe(): Flow<SyncConfig?> = flow

    override suspend fun save(config: SyncConfig) {
        flow.value = config
    }

    override suspend fun clear() {
        flow.value = null
    }
}

private object QuietLogger : Logger {
    override fun d(tag: String, throwable: Throwable?, message: () -> String) = Unit

    // Un aviso en una prueba suele ser la pista de por qué falla: sale en su salida.
    override fun w(tag: String, throwable: Throwable?, message: () -> String) =
        println("W $tag: ${message()} ${throwable?.let { it::class.simpleName + ": " + it.message } ?: ""}")

    override fun e(tag: String, throwable: Throwable?, message: () -> String) = Unit

    override fun i(tag: String, throwable: Throwable?, message: () -> String) = Unit
}

/** One phone: its database, its sync engine and helpers to write like the app does. */
/** The goals as settings, in memory: what [com.maksimowiczm.foodyou.goals] keeps in DataStore. */
internal class FakeGoals(initial: Map<String, JsonElement>) : SyncedGoals {
    private val state = MutableStateFlow(initial)

    val fields: Map<String, JsonElement>
        get() = state.value

    fun set(name: String, value: JsonElement) {
        state.value = state.value + (name to value)
    }

    override suspend fun read() = state.value

    override suspend fun apply(fields: Map<String, JsonElement>) {
        state.value = state.value + fields
    }

    override val changes: Flow<Any> = state
}

internal class Phone(val name: String, api: SyncApi, val goals: FakeGoals? = null) {
    constructor(name: String, server: FakeSyncServer) : this(name, server.api(name))

    constructor(
        name: String,
        server: FakeSyncServer,
        goals: FakeGoals,
    ) : this(name, server.api(name), goals)

    val db: SQLiteDatabase = SQLiteDatabase.create(null)
    val executor = TestSqlExecutor(db)
    val config = FixedConfig()
    val engine: SyncEngine

    init {
        db.execSQL("PRAGMA foreign_keys = ON")
        createDiarySchema(db)
        SyncSchema.statements.forEach(db::execSQL)
        engine =
            SyncEngine(
                executor = executor,
                store = SyncLocalStore(goals),
                api = api,
                configRepository = config,
                now = System::currentTimeMillis,
                logger = QuietLogger,
            )
    }

    suspend fun sync() = engine.sync()

    // --- Escribir como la app (los disparadores hacen el resto) --------------------------------

    fun meal(name: String, rank: Int): Long {
        db.execSQL(
            "INSERT INTO Meal (name, fromHour, fromMinute, toHour, toMinute, rank) VALUES (?, 8, 0, 10, 0, ?)",
            arrayOf<Any?>(name, rank),
        )
        return lastId()
    }

    fun product(name: String, energy: Double, proteins: Double): Long {
        db.execSQL(
            "INSERT INTO DiaryProduct (name, isLiquid, sourceType, energy, proteins, carbohydrates, fats) " +
                "VALUES (?, 0, 0, ?, ?, 10.0, 5.0)",
            arrayOf<Any?>(name, energy, proteins),
        )
        return lastId()
    }

    fun recipe(name: String, vararg ingredients: Pair<Long, Double>): Long {
        db.execSQL(
            "INSERT INTO DiaryRecipe (name, servings, isLiquid) VALUES (?, 1, 0)",
            arrayOf<Any?>(name),
        )
        val recipeId = lastId()
        ingredients.forEach { (productId, grams) ->
            db.execSQL(
                "INSERT INTO DiaryRecipeIngredient (recipeId, ingredientProductId, measurement, quantity) " +
                    "VALUES (?, ?, 0, ?)",
                arrayOf<Any?>(recipeId, productId, grams),
            )
        }
        return recipeId
    }

    fun entry(mealId: Long, productId: Long? = null, recipeId: Long? = null, grams: Double = 100.0): Long {
        db.execSQL(
            "INSERT INTO Measurement (mealId, epochDay, productId, recipeId, measurement, quantity, " +
                "isEaten, createdAt, updatedAt, position, createdByAssistant) " +
                "VALUES (?, 20000, ?, ?, 0, ?, 0, 1, 1, 0, 0)",
            arrayOf<Any?>(mealId, productId, recipeId, grams),
        )
        return lastId()
    }

    fun manual(mealId: Long, name: String, vararg ingredients: String): Long {
        db.execSQL(
            "INSERT INTO ManualDiaryEntry (mealId, dateEpochDay, name, createdEpochSeconds, " +
                "updatedEpochSeconds, position, isEaten, createdByAssistant, energy, proteins, " +
                "carbohydrates, fats) VALUES (?, 20000, ?, 1, 1, 0, 1, 0, 500.0, 30.0, 40.0, 20.0)",
            arrayOf<Any?>(mealId, name),
        )
        val id = lastId()
        ingredients.forEachIndexed { i, ingredient ->
            db.execSQL(
                "INSERT INTO ManualDiaryEntryIngredient (entryId, name, grams, position) VALUES (?, ?, 50.0, ?)",
                arrayOf<Any?>(id, ingredient, i),
            )
        }
        return id
    }

    fun exec(sql: String, vararg args: Any?) = db.execSQL(sql, args)

    fun rows(sql: String, vararg args: Any?): List<Map<String, Any?>> =
        kotlinx.coroutines.runBlocking { executor.transaction { query(sql, *args) } }

    fun scalar(sql: String, vararg args: Any?): Any? = rows(sql, *args).single().values.single()

    private fun lastId(): Long = scalar("SELECT last_insert_rowid()") as Long

    // --- Biblioteca: productos y recetas ------------------------------------------------------

    fun libraryProduct(name: String, energy: Double, calciumMilli: Double? = null): Long {
        db.execSQL(
            "INSERT INTO Product (name, sourceType, isLiquid, isFavorite, isEdited, energy, proteins, " +
                "carbohydrates, fats, calciumMilli) VALUES (?, 0, 0, 1, 0, ?, 5.0, 10.0, 3.0, ?)",
            arrayOf<Any?>(name, energy, calciumMilli),
        )
        return lastId()
    }

    fun libraryRecipe(name: String, vararg ingredients: Pair<String, Long>): Long {
        db.execSQL(
            "INSERT INTO Recipe (name, servings, isLiquid, isFavorite) VALUES (?, 2, 0, 0)",
            arrayOf<Any?>(name),
        )
        val id = lastId()
        ingredients.forEach { (type, ref) ->
            db.execSQL(
                "INSERT INTO RecipeIngredient (recipeId, ingredientProductId, ingredientRecipeId, " +
                    "measurement, quantity) VALUES (?, ?, ?, 0, 100.0)",
                arrayOf<Any?>(id, ref.takeIf { type == "product" }, ref.takeIf { type == "recipe" }),
            )
        }
        return id
    }

    fun remember(key: String, value: String) =
        db.execSQL(
            "INSERT OR REPLACE INTO AssistantMemory (key, value, updatedAt) VALUES (?, ?, 0)",
            arrayOf<Any?>(key, value),
        )

    fun forget(key: String) = db.execSQL("DELETE FROM AssistantMemory WHERE key = ?", arrayOf<Any?>(key))

    fun memory(): Map<String, String> =
        rows("SELECT key, value FROM AssistantMemory").associate {
            (it["key"] as String) to (it["value"] as String)
        }
}
