package com.maksimowiczm.foodyou.assistant.infrastructure

import com.maksimowiczm.foodyou.assistant.domain.AssistantKeepAlive
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

actual fun Module.assistantKeepAliveDefinition() {
    factoryOf(::AndroidAssistantKeepAlive).bind<AssistantKeepAlive>()
}
