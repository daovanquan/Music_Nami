package com.kanavi.automotive.nami.music.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.DATABASE_VERSION
import com.kanavi.automotive.nami.music.data.database.model.Album
import com.kanavi.automotive.nami.music.data.database.model.Artist
import com.kanavi.automotive.nami.music.data.database.model.Song

@Database(
    entities = [Song::class, Album::class, Artist::class],
    version = DATABASE_VERSION,
    exportSchema = false
)
abstract class UsbMusicDatabase : RoomDatabase() {
    abstract fun songDAO(): SongDAO
    abstract fun albumDAO(): AlbumDAO
    abstract fun artistDAO(): ArtistDAO
}