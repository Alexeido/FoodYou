package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL

/**
 * Migration that adds a `categories` TEXT column to `DiaryProduct` table to store comma-separated tags.
 */
internal val addDiaryProductCategoriesMigration =
    object : Migration(34, 35) {
        override fun migrate(connection: SQLiteConnection) {
            connection.execSQL(
                """
                ALTER TABLE DiaryProduct ADD COLUMN categories TEXT
                """.trimIndent(),
            )
        }
    }
