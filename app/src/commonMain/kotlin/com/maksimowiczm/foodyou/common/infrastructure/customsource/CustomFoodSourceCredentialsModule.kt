package com.maksimowiczm.foodyou.common.infrastructure.customsource

import com.maksimowiczm.foodyou.common.domain.customsource.CustomFoodSourceCredentialsRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

fun Module.customFoodSourceCredentialsModule() {
    factoryOf(::SafeCustomFoodSourceCredentialsRepository).bind<CustomFoodSourceCredentialsRepository>()
}
