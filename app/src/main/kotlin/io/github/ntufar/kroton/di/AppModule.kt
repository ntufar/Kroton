package io.github.ntufar.kroton.di

import io.github.ntufar.kroton.database.KrotonDatabase
import io.github.ntufar.kroton.database.createRoomDatabase
import io.github.ntufar.kroton.domain.ExerciseSeeder
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.feature.workout.workoutFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule =
    module {
        single<KrotonDatabase> { createRoomDatabase(androidContext()) }
        single { get<KrotonDatabase>().exerciseDao() }
        single { get<KrotonDatabase>().workoutDao() }
        single { get<KrotonDatabase>().recordDao() }
    }

val domainModule =
    module {
        single { ExerciseSeeder(get()) }
        single { WorkoutRepository(get(), get(), get()) }
    }

val appModules = listOf(databaseModule, domainModule, workoutFeatureModule)
