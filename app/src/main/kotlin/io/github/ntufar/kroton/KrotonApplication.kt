package io.github.ntufar.kroton

import android.app.Application
import io.github.ntufar.kroton.di.appModules
import io.github.ntufar.kroton.domain.ExerciseSeeder
import io.github.ntufar.kroton.domain.InventorySeeder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KrotonApplication : Application() {
    private val exerciseSeeder: ExerciseSeeder by inject()
    private val inventorySeeder: InventorySeeder by inject()

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KrotonApplication)
            modules(appModules)
        }
        CoroutineScope(Dispatchers.IO).launch {
            exerciseSeeder.seedIfEmpty(System.currentTimeMillis())
            inventorySeeder.seedIfEmpty()
        }
    }
}
