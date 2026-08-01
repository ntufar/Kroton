package io.github.ntufar.kroton.feature.history

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val historyFeatureModule =
    module {
        viewModel { HistoryViewModel(get(), get(), get()) }
    }
