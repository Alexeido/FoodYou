package com.maksimowiczm.foodyou.assistant.domain

import kotlinx.coroutines.flow.Flow

/** Everything about the assistant that is not the secret. */
data class AssistantSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = "",
    /**
     * Whether [model] can see images.
     *
     * There used to be a second, separate model for vision, switched to per-turn depending on
     * whether that turn had a photo. That mixing is what caused a plain follow-up after a photo to
     * fail with "This model does not support image": the history still carried the image, but the
     * turn without one routed to a model that could not read it. One model, always used, removes
     * the failure mode entirely instead of working around it - the camera simply does not show up
     * unless this is true.
     */
    val supportsVision: Boolean = false,
    /**
     * Cap on turns of the agent loop, so a confused model cannot burn the user's credit.
     *
     * `0` or less means no cap at all - a deliberate choice made in Settings, not a default. The
     * loop still has a real exit even then: it ends the moment the model answers with text instead
     * of another tool call, same as with any other limit.
     */
    val maxIterations: Int = 8,
) {
    /**
     * Whether the assistant shows up in the app at all.
     *
     * The key is stored separately and is checked alongside this: with no key configured there is no
     * assistant icon, no empty state and nothing to explain.
     */
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && model.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}

interface AssistantPreferences {
    fun observe(): Flow<AssistantSettings>

    suspend fun setBaseUrl(baseUrl: String)

    suspend fun setModel(model: String)

    suspend fun setSupportsVision(value: Boolean)

    suspend fun setMaxIterations(value: Int)
}
