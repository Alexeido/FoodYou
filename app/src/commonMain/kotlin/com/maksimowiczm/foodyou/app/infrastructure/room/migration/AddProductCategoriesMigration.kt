package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration that adds a `categories` TEXT column to `Product` table to store comma-separated tags.
 */
internal val addProductCategoriesMigration =
    object : Migration(32, 33) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE Product ADD COLUMN categories TEXT
                """.trimIndent(),
            )
        }
    }
