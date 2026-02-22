package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migration that adds a `position` INTEGER column to `Measurement` and `ManualDiaryEntry` tables
 * to support user-defined ordering of diary entries within meals.
 */
internal val addEntryPositionMigration =
    object : Migration(35, 36) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE Measurement ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
            )
            database.execSQL(
                "ALTER TABLE ManualDiaryEntry ADD COLUMN position INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
