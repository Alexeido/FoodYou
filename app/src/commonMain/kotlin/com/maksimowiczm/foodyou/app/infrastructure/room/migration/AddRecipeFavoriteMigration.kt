package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lets a recipe be a favourite, the same way a product already can.
 *
 * NOT NULL with a default, unlike the product column added in 33->34: the entity field is a
 * non-null Boolean, and the column Room validates against has to say so too. Existing recipes all
 * start as not favourite, which is what they were.
 */
internal val addRecipeFavoriteMigration =
    object : Migration(42, 43) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "ALTER TABLE `Recipe` ADD COLUMN `isFavorite` INTEGER NOT NULL DEFAULT 0"
            )
        }
    }
