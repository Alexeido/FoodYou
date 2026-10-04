package com.maksimowiczm.foodyou.assistant.infrastructure

import org.koin.core.module.Module

/** Binds [com.maksimowiczm.foodyou.assistant.domain.AssistantKeepAlive] per platform. */
expect fun Module.assistantKeepAliveDefinition()
