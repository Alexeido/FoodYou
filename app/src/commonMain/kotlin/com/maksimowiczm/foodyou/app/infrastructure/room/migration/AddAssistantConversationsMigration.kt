package com.maksimowiczm.foodyou.app.infrastructure.room.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Lets the assistant keep more than one conversation and group its change log by thread.
 *
 * Before this, there was exactly one conversation, held in memory and lost on process death, and
 * `AssistantChange` rows were a single undifferentiated timeline. `conversationId` is left null on
 * rows that already exist - they predate conversations having an identity at all, so there is
 * nothing truthful to backfill them with.
 */
internal val addAssistantConversationsMigration =
    object : Migration(40, 41) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `AssistantConversation` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `turnsJson` TEXT NOT NULL,
                    `apiMessagesJson` TEXT NOT NULL
                )
                """
                    .trimIndent()
            )
            database.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_AssistantConversation_updatedAt` " +
                    "ON `AssistantConversation` (`updatedAt`)"
            )
            database.execSQL("ALTER TABLE AssistantChange ADD COLUMN conversationId INTEGER")
        }
    }
