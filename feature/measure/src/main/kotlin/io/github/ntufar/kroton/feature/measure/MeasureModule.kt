package io.github.ntufar.kroton.feature.measure

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val measureFeatureModule =
    module {
        viewModel { MeasureViewModel(get(), get()) }
    }
