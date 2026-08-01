package io.github.ntufar.kroton.feature.stats

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val statsFeatureModule =
    module {
        viewModel { StatsViewModel(get(), get()) }
    }
