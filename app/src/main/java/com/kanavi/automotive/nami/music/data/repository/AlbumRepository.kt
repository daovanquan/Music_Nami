package com.kanavi.automotive.nami.music.data.repository

import com.kanavi.automotive.nami.music.data.database.AlbumDAO
import com.kanavi.automotive.nami.music.data.database.model.Album

class AlbumRepository(private val albumDAO: AlbumDAO) {

    fun getAlbumWithSongs() = albumDAO.getAlbumWithSongs()
    fun getAll() = albumDAO.getAll()
    fun getAllByUsbID(usbId: String) = albumDAO.getAllByUsbID(usbId)
    fun getAlbumWithId(id: Long) = albumDAO.getAlbumWithId(id)
    fun getArtistAlbums(artistId: Long) = albumDAO.getArtistAlbums(artistId)

    suspend fun insert(album: Album) = albumDAO.insert(album)
    suspend fun insertAll(albums: List<Album>) = albumDAO.insertAll(albums)

    suspend fun deleteAlbum(id: Long) = albumDAO.deleteAlbum(id)
}