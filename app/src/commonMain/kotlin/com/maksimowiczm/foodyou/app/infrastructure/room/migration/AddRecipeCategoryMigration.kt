package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Gives a recipe a category - in the catalogue and in the diary's own copy - so a dish shows an
 * icon instead of the unknown one, and marks the food entries the assistant logged, which until now
 * only manual entries could say.
 *
 * Existing recipes start without a category (the UI guesses one) and every existing entry counts as
 * logged by hand: nothing recorded who made it before.
 */
internal val addRecipeCategoryMigration =
    object : Migration(43, 44) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `Recipe` ADD COLUMN `category` TEXT")
            database.execSQL("ALTER TABLE `DiaryRecipe` ADD COLUMN `category` TEXT")
            database.execSQL(
                "ALTER TABLE `Measurement` ADD COLUMN `createdByAssistant` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
