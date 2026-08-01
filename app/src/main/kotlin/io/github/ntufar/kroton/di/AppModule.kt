package io.github.ntufar.kroton.di

import io.github.ntufar.kroton.database.KrotonDatabase
import io.github.ntufar.kroton.database.createRoomDatabase
import io.github.ntufar.kroton.domain.BackupRepository
import io.github.ntufar.kroton.domain.ExerciseSeeder
import io.github.ntufar.kroton.domain.HistoryRepository
import io.github.ntufar.kroton.domain.ImportRepository
import io.github.ntufar.kroton.domain.InventorySeeder
import io.github.ntufar.kroton.domain.MeasurementRepository
import io.github.ntufar.kroton.domain.MeasurementSeeder
import io.github.ntufar.kroton.domain.ProfileRepository
import io.github.ntufar.kroton.domain.RoutineRepository
import io.github.ntufar.kroton.domain.StatsRepository
import io.github.ntufar.kroton.domain.WorkoutRepository
import io.github.ntufar.kroton.feature.history.historyFeatureModule
import io.github.ntufar.kroton.feature.measure.measureFeatureModule
import io.github.ntufar.kroton.feature.routines.routinesFeatureModule
import io.github.ntufar.kroton.feature.settings.settingsFeatureModule
import io.github.ntufar.kroton.feature.stats.statsFeatureModule
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
        single { get<KrotonDatabase>().measurementDao() }
        single { get<KrotonDatabase>().progressPhotoDao() }
        single { get<KrotonDatabase>().profileDao() }
        single { get<KrotonDatabase>().statsDao() }
    }

val domainModule =
    module {
        single { ExerciseSeeder(get()) }
        single { InventorySeeder(get()) }
        single { MeasurementSeeder(get()) }
        single { WorkoutRepository(get(), get(), get(), get()) }
        single { RoutineRepository(get(), get(), get()) }
        single { HistoryRepository(get()) }
        single { ProfileRepository(get()) }
        single { MeasurementRepository(get(), get(), get()) }
        single { StatsRepository(get(), get(), get()) }
        single { BackupRepository(get(), get(), get(), get(), get(), get(), get()) }
        single { ImportRepository(get(), get()) }
    }

val appModules =
    listOf(
        databaseModule,
        domainModule,
        workoutFeatureModule,
        routinesFeatureModule,
        historyFeatureModule,
        measureFeatureModule,
        statsFeatureModule,
        settingsFeatureModule,
    )
