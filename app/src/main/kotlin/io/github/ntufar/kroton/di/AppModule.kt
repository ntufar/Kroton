package io.github.ntufar.kroton.di

import io.github.ntufar.kroton.database.KrotonDatabase
import io.github.ntufar.kroton.database.createRoomDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule =
    module {
        single<KrotonDatabase> { createRoomDatabase(androidContext()) }
        single { get<KrotonDatabase>().exerciseDao() }
        single { get<KrotonDatabase>().workoutDao() }
    }

val appModules = listOf(databaseModule)
