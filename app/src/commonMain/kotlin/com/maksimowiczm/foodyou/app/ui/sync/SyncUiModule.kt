package com.maksimowiczm.foodyou.app.ui.sync

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf

internal fun Module.sync() {
    viewModelOf(::SyncSettingsViewModel)
}
