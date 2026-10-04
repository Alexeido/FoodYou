package com.maksimowiczm.foodyou.assistant.domain.tool

/**
 * The set of tools available to the model in one conversation.
 *
 * Order is stable and alphabetical on purpose: the schemas are resent on every turn of the agent
 * loop, and DeepSeek (like most OpenAI-compatible providers) only serves them from its prompt cache
 * when the prefix is byte-identical. A registry that shuffled would quietly multiply the cost of a
 * six-call conversation.
 */
class ToolRegistry(tools: List<AssistantTool>) {

    val tools: List<AssistantTool> = tools.sortedBy { it.name }

    private val byName: Map<String, AssistantTool> = buildMap {
        tools.forEach { tool ->
            require(tool.name.isNotBlank()) { "Tool name cannot be blank" }
            val previous = put(tool.name, tool)
            require(previous == null) { "Duplicate tool name: ${tool.name}" }
        }
    }

    fun find(name: String): AssistantTool? = byName[name]

    val names: List<String>
        get() = tools.map { it.name }
}
