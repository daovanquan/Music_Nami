package com.kanavi.automotive.nami.music.data.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_ALBUM_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_ARTIST
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_ARTIST_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_FOLDER_NAME
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_MEDIA_STORE_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_PATH
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_TABLE_NAME
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.SONG_TITLE
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.USB_ID
import com.kanavi.automotive.nami.music.data.database.model.Song

@Dao
interface SongDAO {
    @Query("SELECT * FROM $SONG_TABLE_NAME")
    fun getAll(): List<Song>

    @Query("SELECT * FROM $SONG_TABLE_NAME WHERE $USB_ID = :usbId")
    fun getAllByUsbID(usbId: String): List<Song>

    @Query("SELECT * FROM $SONG_TABLE_NAME WHERE $SONG_ARTIST_ID = :artistId")
    fun getListSongFromArtist(artistId: Long): List<Song>

    @Query("SELECT * FROM $SONG_TABLE_NAME WHERE $SONG_ALBUM_ID = :albumId")
    fun getListSongFromAlbum(albumId: Long): List<Song>

    @Query(
        "SELECT * FROM $SONG_TABLE_NAME WHERE $SONG_FOLDER_NAME" +
                " = :folderName COLLATE NOCASE GROUP BY $SONG_MEDIA_STORE_ID"
    )
    fun getListSongFromFolder(folderName: String): List<Song>

    @Query("SELECT * FROM $SONG_TABLE_NAME WHERE $SONG_MEDIA_STORE_ID = :mediaStoreId")
    fun getSongWithMediaStoreId(mediaStoreId: Long): Song?


    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(listSong: List<Song>)


    @Query("DELETE FROM $SONG_TABLE_NAME WHERE $SONG_MEDIA_STORE_ID = :mediaStoreId")
    suspend fun removeSong(mediaStoreId: Long)

    @Query(
        "UPDATE $SONG_TABLE_NAME SET $SONG_PATH = :newPath, $SONG_ARTIST = :artist, $SONG_TITLE" +
                " = :title WHERE $SONG_PATH = :oldPath"
    )
    suspend fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String)

    @Query("UPDATE $SONG_TABLE_NAME SET $SONG_FOLDER_NAME = :folderName WHERE $SONG_MEDIA_STORE_ID = :id")
    suspend fun updateFolderName(folderName: String, id: Long)
}