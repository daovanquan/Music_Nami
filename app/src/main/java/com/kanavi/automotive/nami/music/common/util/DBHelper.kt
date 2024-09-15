package com.kanavi.automotive.nami.music.common.util

import com.kanavi.automotive.nami.music.data.database.model.Album
import com.kanavi.automotive.nami.music.data.database.model.Song
import com.kanavi.automotive.nami.music.data.repository.AlbumRepository
import com.kanavi.automotive.nami.music.data.repository.ArtistRepository
import com.kanavi.automotive.nami.music.data.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

object DBHelper : KoinComponent {
    private val songRepository: SongRepository by inject()
    private val albumRepository: AlbumRepository by inject()
    private val artistRepository: ArtistRepository by inject()

    fun getAllSong(): List<Song>{
        return songRepository.getAll()
    }

    fun getAllAlbum(): List<Album>{
        return albumRepository.getAll()
    }

    fun updateAllDatabase(listSong: ArrayList<Song>) = CoroutineScope(Dispatchers.IO).launch {
        songRepository.insertAll(listSong)

        val newAlbums = MusicUtil.splitIntoAlbums(listSong)
        albumRepository.insertAll(newAlbums)

        val newArtists = MusicUtil.splitIntoArtist(listSong)
        artistRepository.insertAll(newArtists)
        cleanupDatabase(listSong)
    }

    private fun cleanupDatabase(newListSong: ArrayList<Song>) =
        CoroutineScope(Dispatchers.IO).launch {
            val usbID = newListSong.firstOrNull()?.usbId ?: return@launch
            Timber.d("*****************START CLEANUP DATABASE FOR USB: $usbID******************")
            //remove invalid song
            val oldSongsInDB = songRepository.getAllByUsbID(usbID)
            val newSongIDs = newListSong.map { it.mediaStoreId } as ArrayList<Long>
            val newSongPaths = newListSong.map { it.path } as ArrayList<String>
            val listSongToDelete: List<Song> =
                oldSongsInDB.filter { it.mediaStoreId !in newSongIDs || it.path !in newSongPaths }

            listSongToDelete.forEach {
                Timber.d("remove invalid song: ${it.title} ${it.path} ${it.mediaStoreId}")
                songRepository.removeSong(it.mediaStoreId)
            }

            //remove invalid album
            val oldAlbumsInDB = albumRepository.getAllByUsbID(usbID)
            val newAlbums = MusicUtil.splitIntoAlbums(newListSong)
            val newAlbumIDs = newAlbums.map { it.id }
            val listAlbumToDelete = oldAlbumsInDB.filter { it.id !in newAlbumIDs }.toMutableList()
            listAlbumToDelete += newAlbums.filter { album -> newListSong.none { it.albumId == album.id } }

            for (album in listAlbumToDelete) {
                Timber.d("remove invalid album: ${album.title} ${album.id}")
                albumRepository.deleteAlbum(album.id)
            }

            //remove invalid artist
            val oldArtistInDB = artistRepository.getAllByUsbID(usbID)
            val newArtists = MusicUtil.splitIntoArtist(newListSong)
            val newArtistIDs = newArtists.map { it.id } as ArrayList<Long>
            val listArtistToDelete = oldArtistInDB.filter { it.id !in newArtistIDs }.toMutableList()

            for (artist in newArtists) {
                val artistId = artist.id
                val albumsByArtist = newAlbums.filter { it.artistId == artistId }
                if (albumsByArtist.isEmpty()) {
                    listArtistToDelete.add(artist)
                    continue
                }

                // update album, track counts
                val albumCount = albumsByArtist.size
                val songCount = albumsByArtist.sumOf { it.songCount }
                if (songCount != artist.songCount || albumCount != artist.albumCount) {
                    artistRepository.deleteArtist(artistId)
                    val updated = artist.copy(songCount = songCount, albumCount = albumCount)
                    artistRepository.insert(updated)
                }
            }

            listAlbumToDelete.forEach { artist ->
                artistRepository.deleteArtist(artist.id)
            }

            Timber.d("****************FINISH CLEANUP DATABASE FOR USB: $usbID****************")
        }
}