package com.kanavi.automotive.nami.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_ARTIST_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_TABLE_NAME
import com.kanavi.automotive.nami.music.data.database.model.Album
import com.kanavi.automotive.nami.music.data.database.model.AlbumWithSongs

@Dao
interface AlbumDAO {
    @Transaction
    @Query("SELECT * FROM ${DatabaseEntry.ALBUM_TABLE_NAME}")
    fun getAlbumWithSongs(): List<AlbumWithSongs>

    @Query("SELECT * FROM $ALBUM_TABLE_NAME")
    fun getAll(): List<Album>

    @Query("SELECT * FROM $ALBUM_TABLE_NAME WHERE ${DatabaseEntry.USB_ID} = :usbId")
    fun getAllByUsbID(usbId: String): List<Album>

    @Query("SELECT * FROM $ALBUM_TABLE_NAME WHERE $ALBUM_ID  = :id")
    fun getAlbumWithId(id: Long): Album?

    @Query("SELECT * FROM $ALBUM_TABLE_NAME WHERE $ALBUM_ARTIST_ID = :artistId")
    fun getArtistAlbums(artistId: Long): List<Album>


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(album: Album): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(albums: List<Album>)


    @Query("DELETE FROM $ALBUM_TABLE_NAME WHERE $ALBUM_ID = :id")
    suspend fun deleteAlbum(id: Long)
}