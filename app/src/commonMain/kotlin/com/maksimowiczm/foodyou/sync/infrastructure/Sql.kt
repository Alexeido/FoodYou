package com.maksimowiczm.foodyou.sync.infrastructure

import androidx.room.RoomDatabase
import androidx.room.TransactionScope
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.SQLITE_DATA_FLOAT
import androidx.sqlite.SQLITE_DATA_INTEGER
import androidx.sqlite.SQLITE_DATA_NULL
import androidx.sqlite.SQLITE_DATA_TEXT
import androidx.sqlite.SQLiteStatement

/**
 * Plain SQL over whatever database is underneath.
 *
 * Sync works on rows, not on the domain models: it copies every column of a diary row as it is,
 * so a column added to a table later travels without touching this code. Values are `Long`,
 * `Double`, `String` or `null`, which is all SQLite stores in those tables.
 */
internal interface Sql {
    suspend fun query(sql: String, vararg args: Any?): List<Map<String, Any?>>

    suspend fun exec(sql: String, vararg args: Any?)

    /** Runs an INSERT and returns the new row's id. */
    suspend fun insert(sql: String, vararg args: Any?): Long
}

internal interface SqlExecutor {
    suspend fun <T> transaction(block: suspend Sql.() -> T): T
}

/** The app's Room database, through its writer connection. */
internal class RoomSqlExecutor(private val database: RoomDatabase) : SqlExecutor {
    override suspend fun <T> transaction(block: suspend Sql.() -> T): T =
        database.useWriterConnection { connection ->
            connection.immediateTransaction { RoomSql(this).block() }
        }
}

private class RoomSql(private val scope: TransactionScope<*>) : Sql {
    override suspend fun query(sql: String, vararg args: Any?): List<Map<String, Any?>> =
        scope.usePrepared(sql) { statement ->
            statement.bindAll(args)
            val rows = mutableListOf<Map<String, Any?>>()
            while (statement.step()) {
                rows +=
                    (0 until statement.getColumnCount()).associate { i ->
                        statement.getColumnName(i) to statement.valueAt(i)
                    }
            }
            rows
        }

    override suspend fun exec(sql: String, vararg args: Any?) {
        scope.usePrepared(sql) { statement ->
            statement.bindAll(args)
            statement.step()
        }
    }

    override suspend fun insert(sql: String, vararg args: Any?): Long {
        exec(sql, *args)
        return query("SELECT last_insert_rowid() AS id").single()["id"] as Long
    }
}

private fun SQLiteStatement.bindAll(args: Array<out Any?>) {
    args.forEachIndexed { i, value ->
        val index = i + 1
        when (value) {
            null -> bindNull(index)
            is Long -> bindLong(index, value)
            is Int -> bindLong(index, value.toLong())
            is Boolean -> bindLong(index, if (value) 1 else 0)
            is Double -> bindDouble(index, value)
            is Float -> bindDouble(index, value.toDouble())
            is String -> bindText(index, value)
            else -> error("Cannot bind ${value::class}")
        }
    }
}

private fun SQLiteStatement.valueAt(i: Int): Any? =
    when (getColumnType(i)) {
        SQLITE_DATA_NULL -> null
        SQLITE_DATA_INTEGER -> getLong(i)
        SQLITE_DATA_FLOAT -> getDouble(i)
        SQLITE_DATA_TEXT -> getText(i)
        else -> null // BLOB: none of the synced tables have one
    }
