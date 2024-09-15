package com.kanavi.automotive.nami.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ARTIST_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ARTIST_TABLE_NAME
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.USB_ID
import com.kanavi.automotive.nami.music.data.database.model.Artist
import com.kanavi.automotive.nami.music.data.database.model.ArtistWithAlbums

@Dao
interface ArtistDAO {
    @Transaction
    @Query("SELECT * FROM $ARTIST_TABLE_NAME")
    fun getArtistWithAlbums(): List<ArtistWithAlbums>

    @Query("SELECT * FROM $ARTIST_TABLE_NAME")
    fun getAll(): List<Artist>

    @Query("SELECT * FROM $ARTIST_TABLE_NAME WHERE $USB_ID = :usbId")
    fun getAllByUsbID(usbId: String): List<Artist>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(artist: Artist): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<Artist>)


    @Query("DELETE FROM $ARTIST_TABLE_NAME WHERE $ARTIST_ID = :id")
    suspend fun deleteArtist(id: Long)
}
