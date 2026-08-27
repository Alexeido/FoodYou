package com.maksimowiczm.foodyou.assistant.domain.tool.write

import com.maksimowiczm.foodyou.assistant.domain.journal.ChangeJournal
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.intOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.Args.longOrNull
import com.maksimowiczm.foodyou.assistant.domain.tool.AssistantTool
import com.maksimowiczm.foodyou.assistant.domain.tool.ToolSchema
import com.maksimowiczm.foodyou.assistant.domain.tool.toolError
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** C14. What the assistant has changed, so it can answer "what did you do" honestly. */
class HistoryTool(private val journal: ChangeJournal) : AssistantTool {
    override val name = "history"
    override val description =
        "Los ultimos cambios que has hecho tu en el diario, con su id y si siguen aplicados."
    override val parameters =
        ToolSchema.obj("limit" to ToolSchema.integer("Cuantos devolver. Por defecto 10."))

    override suspend fun call(arguments: JsonObject): JsonElement {
        val changes = journal.recent(arguments.intOrNull("limit") ?: 10)
        return buildJsonArray {
            changes.forEach {
                add(
                    buildJsonObject {
                        put("changeId", it.id)
                        put("at", it.createdAt.toString())
                        put("summary", it.summary)
                        put("undone", it.undone)
                    }
                )
            }
        }
    }
}

/** C15. */
class UndoTool(private val journal: ChangeJournal) : AssistantTool {
    override val name = "undo"
    override val description =
        "Revierte un cambio que hiciste. Sin changeId revierte el ultimo que siga aplicado."
    override val mutates = true
    override val parameters =
        ToolSchema.obj("changeId" to ToolSchema.integer("Id del cambio, de history."))

    override suspend fun call(arguments: JsonObject): JsonElement {
        val change =
            journal.undo(arguments.longOrNull("changeId"))
                ?: return toolError("No hay ningun cambio que revertir.")

        return buildJsonObject {
            put("ok", true)
            put("undone", change.summary)
        }
    }
}

/** C16. */
class RedoTool(private val journal: ChangeJournal) : AssistantTool {
    override val name = "redo"
    override val description = "Vuelve a aplicar el ultimo cambio que se revirtio."
    override val mutates = true
    override val parameters = ToolSchema.none

    override suspend fun call(arguments: JsonObject): JsonElement {
        val change = journal.redo() ?: return toolError("No hay nada que rehacer.")
        return buildJsonObject {
            put("ok", true)
            put("redone", change.summary)
        }
    }
}
