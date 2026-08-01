package io.github.ntufar.kroton.feature.workout

import io.github.ntufar.kroton.domain.RestTimerController
import io.github.ntufar.kroton.feature.workout.resttimer.AndroidRestTimerController
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val workoutFeatureModule =
    module {
        single<RestTimerController> { AndroidRestTimerController(get()) }
        viewModel { WorkoutViewModel(get(), get()) }
    }
