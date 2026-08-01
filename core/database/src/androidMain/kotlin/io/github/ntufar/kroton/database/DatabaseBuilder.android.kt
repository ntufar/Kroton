package io.github.ntufar.kroton.database

import android.content.Context
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.Dispatchers

fun createRoomDatabase(context: Context): KrotonDatabase {
    val dbFile = context.getDatabasePath(KROTON_DATABASE_FILE_NAME)
    return Room.databaseBuilder<KrotonDatabase>(
        context = context.applicationContext,
        name = dbFile.absolutePath,
        factory = { KrotonDatabaseConstructor.initialize() },
    )
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()
}
