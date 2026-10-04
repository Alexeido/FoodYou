package com.maksimowiczm.foodyou.goals.infrastructure

import com.maksimowiczm.foodyou.goals.domain.repository.GoalsRepository
import com.maksimowiczm.foodyou.sync.domain.SyncedGoals
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind

internal fun Module.goalsInfrastructureModule() {
    factoryOf(::DataStoreGoalsRepository).bind<GoalsRepository>()
    factoryOf(::GoalsSyncAdapter).bind<SyncedGoals>()
}
