package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Gives `ManualDiaryEntry` the three columns it was always missing.
 *
 * A manual entry could not say what kind of food it was (so the diary drew the grey "?" placeholder
 * for every single one), could not remember whether it had been eaten (`isEaten` existed in the
 * domain model but was never persisted, so it silently defaulted to true and nothing could ever
 * leave it pending), and could not say whether a person or the assistant created it - which is what
 * makes it possible to mark the assistant's own entries in the list.
 *
 * Defaults preserve today's behaviour for rows that already exist: unknown category, eaten, and
 * created by a person.
 */
internal val addManualEntryCategoryMigration =
    object : Migration(39, 40) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE ManualDiaryEntry ADD COLUMN category TEXT")
            database.execSQL(
                "ALTER TABLE ManualDiaryEntry ADD COLUMN isEaten INTEGER NOT NULL DEFAULT 1"
            )
            database.execSQL(
                "ALTER TABLE ManualDiaryEntry ADD COLUMN createdByAssistant INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
