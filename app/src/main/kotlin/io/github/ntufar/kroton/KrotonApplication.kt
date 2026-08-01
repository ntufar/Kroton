package io.github.ntufar.kroton

import android.app.Application
import io.github.ntufar.kroton.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class KrotonApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger()
            androidContext(this@KrotonApplication)
            modules(appModules)
        }
    }
}
