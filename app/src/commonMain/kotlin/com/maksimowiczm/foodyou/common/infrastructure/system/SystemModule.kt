package com.maksimowiczm.foodyou.common.infrastructure.system

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

expect fun Module.systemDetailsDefinition()

/** Binds [com.maksimowiczm.foodyou.common.system.InstallationId] per platform. */
expect fun Module.installationIdDefinition()

fun Module.systemModule() {
    systemDetailsDefinition()
    installationIdDefinition()
    factoryOf(::DateProviderImpl).bind<DateProvider>()
}
