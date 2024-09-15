package com.kanavi.automotive.nami.music.data.database.model

import androidx.room.Embedded
import androidx.room.Relation
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry

data class ArtistWithAlbums(
    @Embedded val artist: Artist,
    @Relation(
        parentColumn = DatabaseEntry.ARTIST_ID,
        entityColumn = DatabaseEntry.ALBUM_ARTIST_ID
    )
    val listAlbum: List<Album>
)