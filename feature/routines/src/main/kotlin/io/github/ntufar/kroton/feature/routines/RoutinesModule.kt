package io.github.ntufar.kroton.feature.routines

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val routinesFeatureModule =
    module {
        viewModel { RoutinesViewModel(get()) }
    }
