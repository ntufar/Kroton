package io.github.ntufar.kroton.di

import io.github.ntufar.kroton.database.KrotonDatabase
import io.github.ntufar.kroton.database.createRoomDatabase
import io.github.ntufar.kroton.domain.ExerciseSeeder
import io.github.ntufar.kroton.domain.InventorySeeder
import io.github.ntufar.kroton.domain.RoutineRepository
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.feature.routines.routinesFeatureModule
import io.github.ntufar.kroton.feature.workout.workoutFeatureModule
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule =
    module {
        single<KrotonDatabase> { createRoomDatabase(androidContext()) }
        single { get<KrotonDatabase>().exerciseDao() }
        single { get<KrotonDatabase>().workoutDao() }
        single { get<KrotonDatabase>().recordDao() }
        single { get<KrotonDatabase>().inventoryDao() }
        single { get<KrotonDatabase>().routineDao() }
    }

val domainModule =
    module {
        single { ExerciseSeeder(get()) }
        single { InventorySeeder(get()) }
        single { WorkoutRepository(get(), get(), get(), get()) }
        single { RoutineRepository(get(), get(), get()) }
    }

val appModules = listOf(databaseModule, domainModule, workoutFeatureModule, routinesFeatureModule)
