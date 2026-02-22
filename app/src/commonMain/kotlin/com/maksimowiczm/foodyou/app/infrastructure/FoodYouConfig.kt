package com.maksimowiczm.foodyou.app.infrastructure

import com.maksimowiczm.foodyou.app.BuildConfig
import com.maksimowiczm.foodyou.common.config.AppConfig
import com.maksimowiczm.foodyou.common.config.NetworkConfig

internal class FoodYouConfig : AppConfig, NetworkConfig {
    override val versionName: String = BuildConfig.VERSION_NAME
    override val contactEmailUri: String = "https://github.com/Alexeido/FoodYou/issues"
    override val translationUri: String = "https://crowdin.com/project/food-you"
    override val sourceCodeUri: String = "https://github.com/Alexeido/FoodYou"
    override val issueTrackerUri: String = "https://github.com/Alexeido/FoodYou/issues"
    override val privacyPolicyUri: String = "https://github.com/Alexeido/FoodYou"
    override val openFoodFactsTermsOfUseUri: String = "https://world.openfoodfacts.org/terms-of-use"
    override val openFoodFactsPrivacyPolicyUri: String = "https://world.openfoodfacts.org/privacy"
    override val foodDataCentralPrivacyPolicyUri: String = "https://www.usda.gov/privacy-policy"

    override val userAgent: String = "Food You/$versionName (github.com/Alexeido/FoodYou)"
}
