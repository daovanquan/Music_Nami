package com.kanavi.automotive.nami.music.data.repository

import com.kanavi.automotive.nami.music.data.database.SongDAO
import com.kanavi.automotive.nami.music.data.database.model.Song

class SongRepository(private val songDAO: SongDAO) {

    fun getAll() = songDAO.getAll()
    fun getAllByUsbID(usbId: String) = songDAO.getAllByUsbID(usbId)
    fun getListSongFromArtist(artistId: Long) = songDAO.getListSongFromArtist(artistId)
    fun getListSongFromAlbum(albumId: Long) = songDAO.getListSongFromAlbum(albumId)
    fun getListSongFromFolder(folderName: String) = songDAO.getListSongFromFolder(folderName)
    fun getSongWithMediaStoreId(mediaStoreId: Long) = songDAO.getSongWithMediaStoreId(mediaStoreId)

    suspend fun insert(song: Song) = songDAO.insert(song)
    suspend fun insertAll(songs: List<Song>) = songDAO.insertAll(songs)

    suspend fun updateSongInfo(newPath: String, artist: String, title: String, oldPath: String) =
        songDAO.updateSongInfo(newPath, artist, title, oldPath)

    suspend fun updateFolderName(folderName: String, id: Long) =
        songDAO.updateFolderName(folderName, id)

    suspend fun removeSong(mediaStoreId: Long) = songDAO.removeSong(mediaStoreId)
}