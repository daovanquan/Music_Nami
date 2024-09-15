package com.kanavi.automotive.nami.music

import android.app.NotificationManager
import androidx.core.content.ContextCompat.getSystemService
import com.kanavi.automotive.nami.music.service.mediaSource.MusicProvider
import org.koin.dsl.module

val mainModules = module {
    single { getSystemService(get(), NotificationManager::class.java) }
    single { MusicProvider(get()) }
}