package com.kanavi.automotive.nami.music

import android.app.Application
import com.kanavi.automotive.nami.music.common.util.TimberDebugTree
import com.kanavi.automotive.nami.music.data.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import timber.log.Timber

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        app = this

        startKoin {
            androidContext(this@App)
            modules(mainModules)
            modules(dataModule)
        }
        Timber.plant(TimberDebugTree())
    }

    companion object {
        lateinit var app: App
    }
}