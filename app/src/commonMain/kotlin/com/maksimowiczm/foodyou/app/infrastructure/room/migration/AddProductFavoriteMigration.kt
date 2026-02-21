package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration that adds an `isFavorite` INTEGER column to `Product` table to mark favorites.
 */
internal val addProductFavoriteMigration =
    object : Migration(33, 34) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                ALTER TABLE Product ADD COLUMN isFavorite INTEGER DEFAULT 0
                """.trimIndent()
            )
        }
    }
