package com.maksimowiczm.foodyou.food.search.domain

import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences

data class FoodSearchPreferences(
    val openFoodFacts: OpenFoodFacts,
    val usda: Usda,
    val custom: Custom,
    /**
     * The database the search box queries by default when the user presses search or scans a
     * barcode. `null` means "not chosen yet" — callers fall back to the first enabled remote source.
     */
    val primarySource: FoodSource.Type? = null,
) : UserPreferences {
    data class OpenFoodFacts(val enabled: Boolean)

    data class Usda(val enabled: Boolean, val apiKey: String?)

    /**
     * Non-secret configuration for the user's custom, self-hosted food source. Credentials
     * (username/password) are stored separately, encrypted, via
     * [com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository].
     */
    data class Custom(val enabled: Boolean, val baseUrl: String?)

    val isOpenFoodFactsEnabled: Boolean
        get() = openFoodFacts.enabled

    val isUsdaEnabled: Boolean
        get() = usda.enabled

    val isCustomEnabled: Boolean
        get() = custom.enabled

    /** Enabled remote (live, re-fetchable) sources, in display order. */
    val enabledRemoteSources: List<FoodSource.Type>
        get() = buildList {
            if (isCustomEnabled) add(FoodSource.Type.Custom)
            if (isOpenFoodFactsEnabled) add(FoodSource.Type.OpenFoodFacts)
            if (isUsdaEnabled) add(FoodSource.Type.USDA)
        }

    /** The effective default database source: the chosen [primarySource] if still enabled, else the first enabled one. */
    val effectivePrimarySource: FoodSource.Type?
        get() = primarySource?.takeIf { it in enabledRemoteSources } ?: enabledRemoteSources.firstOrNull()
}
