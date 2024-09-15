package com.kanavi.automotive.nami.music.data

import android.content.ComponentName
import androidx.room.Room
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry
import com.kanavi.automotive.nami.music.data.database.UsbMusicDatabase
import com.kanavi.automotive.nami.music.data.repository.AlbumRepository
import com.kanavi.automotive.nami.music.data.repository.ArtistRepository
import com.kanavi.automotive.nami.music.data.repository.MusicListRepository
import com.kanavi.automotive.nami.music.service.mediaSource.PersistentStorage
import com.kanavi.automotive.nami.music.data.repository.SongRepository
import com.kanavi.automotive.nami.music.data.shared.MediaServiceConnection
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val dataModule = module {

    single { PersistentStorage(get()) }

    single {
        Room.databaseBuilder(
            androidContext(),
            UsbMusicDatabase::class.java,
            DatabaseEntry.DATABASE_NAME
        ).build()
    }

    single { get<UsbMusicDatabase>().songDAO() }
    single { get<UsbMusicDatabase>().albumDAO() }
    single { get<UsbMusicDatabase>().artistDAO() }

    single {
        MediaServiceConnection(
            get(), ComponentName(
                "com.kanavi.automotive.kama.music",
                "com.kanavi.automotive.kama.music.services.MusicService"
            )
        )
    }

    single { SongRepository(get()) }
    single { AlbumRepository(get()) }
    single { ArtistRepository(get()) }
}