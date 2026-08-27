package com.maksimowiczm.foodyou.assistant.infrastructure.openai

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * The OpenAI chat-completions wire format.
 *
 * Verified against DeepSeek before being written: tool calls come back with
 * finish_reason "tool_calls" and arguments as a JSON *string*, images go inline as a data URI, and
 * a single message can carry both content and tool calls at once - which is why [Message.content]
 * and [Message.toolCalls] are both nullable and both have to be handled.
 */
@Serializable
data class ChatRequest(
    val model: String,
    val messages: List<Message>,
    val tools: List<ToolSpec>? = null,
    val temperature: Double? = null,
    @SerialName("max_tokens") val maxTokens: Int? = null,
    val stream: Boolean = false,
)

@Serializable
data class Message(
    val role: String,
    /**
     * Plain text, or the multimodal parts array. Kept as [kotlinx.serialization.json.JsonElement]
     * because the same field is a string in most turns and an array when an image rides along.
     */
    val content: kotlinx.serialization.json.JsonElement? = null,
    @SerialName("tool_calls") val toolCalls: List<ToolCall>? = null,
    @SerialName("tool_call_id") val toolCallId: String? = null,
    val name: String? = null,
)

@Serializable
data class ToolCall(
    val id: String,
    val type: String = "function",
    val function: FunctionCall,
)

@Serializable
data class FunctionCall(
    val name: String,
    /** A JSON object encoded as a string. Models sometimes emit invalid JSON here. */
    val arguments: String,
)

@Serializable data class ToolSpec(val type: String = "function", val function: FunctionSpec)

@Serializable
data class FunctionSpec(val name: String, val description: String, val parameters: JsonObject)

@Serializable
data class ChatResponse(val choices: List<Choice> = emptyList(), val usage: Usage? = null)

@Serializable
data class Choice(
    val index: Int = 0,
    val message: Message,
    @SerialName("finish_reason") val finishReason: String? = null,
)

@Serializable
data class Usage(
    @SerialName("prompt_tokens") val promptTokens: Int = 0,
    @SerialName("completion_tokens") val completionTokens: Int = 0,
    @SerialName("total_tokens") val totalTokens: Int = 0,
    @SerialName("prompt_cache_hit_tokens") val cacheHitTokens: Int = 0,
)

@Serializable data class ModelList(val data: List<ModelInfo> = emptyList())

@Serializable data class ModelInfo(val id: String)
