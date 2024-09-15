package com.kanavi.automotive.nami.music.data.repository

import com.kanavi.automotive.nami.music.data.database.ArtistDAO
import com.kanavi.automotive.nami.music.data.database.model.Artist

class ArtistRepository(private val artistDAO: ArtistDAO) {

    fun getArtistWithAlbumList() = artistDAO.getArtistWithAlbums()
    fun getAll() = artistDAO.getAll()
    fun getAllByUsbID(usbId: String) = artistDAO.getAllByUsbID(usbId)

    suspend fun insert(artist: Artist) = artistDAO.insert(artist)
    suspend fun insertAll(artists: List<Artist>) = artistDAO.insertAll(artists)

    suspend fun deleteArtist(id: Long) = artistDAO.deleteArtist(id)
}