package com.maksimowiczm.foodyou.assistant.domain

import kotlinx.coroutines.flow.Flow

/** Everything about the assistant that is not the secret. */
data class AssistantSettings(
    val baseUrl: String = DEFAULT_BASE_URL,
    val model: String = "",
    /** Model used when an image is attached. Empty means the app hides the camera. */
    val visionModel: String = "",
    /** Hard cap on turns of the agent loop, so a confused model cannot burn the user's credit. */
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

    val supportsVision: Boolean
        get() = visionModel.isNotBlank()

    companion object {
        const val DEFAULT_BASE_URL = "https://api.deepseek.com"
    }
}

interface AssistantPreferences {
    fun observe(): Flow<AssistantSettings>

    suspend fun setBaseUrl(baseUrl: String)

    suspend fun setModel(model: String)

    suspend fun setVisionModel(model: String)

    suspend fun setMaxIterations(value: Int)
}
