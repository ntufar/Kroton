package io.github.ntufar.kroton.database

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createRoomDatabase(databaseFilePath: String): KrotonDatabase {
    return Room.databaseBuilder<KrotonDatabase>(
        name = databaseFilePath,
        factory = { KrotonDatabaseConstructor.initialize() },
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
