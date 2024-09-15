package com.kanavi.automotive.nami.music.data.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry

data class AlbumWithSongs(
    @Embedded val album: Album,
    @Relation(
        parentColumn = DatabaseEntry.ALBUM_ID,
        entityColumn = DatabaseEntry.SONG_ALBUM_ID
    )
    val listSong: List<Song>
)