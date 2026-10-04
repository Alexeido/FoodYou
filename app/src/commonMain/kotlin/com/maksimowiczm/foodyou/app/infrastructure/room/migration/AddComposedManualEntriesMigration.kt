package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lets a manual entry say what it is made of, so a burger is one row in the diary instead of six.
 *
 * Purely additive: entries that already exist simply have no ingredients, which is exactly what an
 * ordinary quick-add is. The rows cascade with their parent entry - an ingredient list has no
 * meaning once the thing it describes is gone.
 */
internal val addComposedManualEntriesMigration =
    object : Migration(41, 42) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ManualDiaryEntryIngredient` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `entryId` INTEGER NOT NULL,
                    `name` TEXT NOT NULL,
                    `grams` REAL,
                    `position` INTEGER NOT NULL,
                    FOREIGN KEY(`entryId`) REFERENCES `ManualDiaryEntry`(`id`)
                        ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """
                    .trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ManualDiaryEntryIngredient_entryId` " +
                    "ON `ManualDiaryEntryIngredient` (`entryId`)"
            )
        }
    }
