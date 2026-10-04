package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Adds product-identity columns to `Product` (`sourceBarcode`, `isEdited`, `lastUsedAt`) and an
 * `icon` column to `Meal`.
 *
 * - `sourceBarcode` is backfilled from the current `barcode` so existing rows keep a stable
 *   identity anchor (see [com.maksimowiczm.foodyou.food.infrastructure.room.ProductDao]).
 * - `isEdited` marks user-edited products as durable (protected from the stale-mirror purge).
 * - `Meal.icon` stores a `mat:<id>` or `emoji:<char>` reference for the meal section.
 */
internal val addProductIdentityAndMealIconMigration =
    object : Migration(37, 38) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE Product ADD COLUMN sourceBarcode TEXT")
            database.execSQL("ALTER TABLE Product ADD COLUMN isEdited INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE Product SET sourceBarcode = barcode")
            database.execSQL("ALTER TABLE Meal ADD COLUMN icon TEXT")
        }
    }
