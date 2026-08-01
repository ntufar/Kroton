package io.github.ntufar.kroton.feature.workout

import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val workoutFeatureModule =
    module {
        viewModel { WorkoutViewModel(get()) }
    }
