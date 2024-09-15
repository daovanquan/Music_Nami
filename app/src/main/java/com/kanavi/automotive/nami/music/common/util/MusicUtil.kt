package com.kanavi.automotive.nami.music.common.util

import android.content.ContentUris
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Size
import androidx.core.net.toUri
import com.kanavi.automotive.nami.music.App
import com.kanavi.automotive.nami.music.common.extension.ensureBackgroundThread
import com.kanavi.automotive.nami.music.data.database.model.Album
import com.kanavi.automotive.nami.music.data.database.model.Artist
import com.kanavi.automotive.nami.music.data.database.model.Song
import java.io.File

object MusicUtil {
    fun getMediaStoreAlbumCoverUri(albumId: Long): Uri {
        val sArtworkUri = "content://media/external/audio/albumart".toUri()
        return ContentUris.withAppendedId(sArtworkUri, albumId)
    }

    fun getSongCoverArt(song: Song?, callback: (coverArt: Any?) -> Unit) {
        ensureBackgroundThread {
            if (song == null) {
                Handler(Looper.getMainLooper()).post {
                    callback(null)
                }
                return@ensureBackgroundThread
            }

            val coverArt = song.coverArt.ifEmpty {
                loadSongCoverArt(song)
            }

            Handler(Looper.getMainLooper()).post {
                callback(coverArt)
            }
        }
    }

    fun loadSongCoverArt(song: Song?): Bitmap? {
        if (song == null) {
            return null
        }

        val context = App.app

        val path = song.path
        if (path.isNotEmpty() && File(path).exists()) {
            val coverArtHeight = 512
            try {
                try {
                    val mediaMetadataRetriever = MediaMetadataRetriever()
                    mediaMetadataRetriever.setDataSource(path)
                    val rawArt = mediaMetadataRetriever.embeddedPicture
                    if (rawArt != null) {
                        val options = BitmapFactory.Options()
                        val bitmap = BitmapFactory.decodeByteArray(rawArt, 0, rawArt.size, options)
                        if (bitmap != null) {
                            val resultBitmap = if (bitmap.height > coverArtHeight * 2) {
                                val ratio = bitmap.width / bitmap.height.toFloat()
                                Bitmap.createScaledBitmap(
                                    bitmap,
                                    (coverArtHeight * ratio).toInt(),
                                    coverArtHeight,
                                    false
                                )
                            } else {
                                bitmap
                            }

                            return resultBitmap
                        }
                    }
                } catch (ignored: OutOfMemoryError) {
                } catch (ignored: Exception) {
                }

                val trackParentDirectory = File(path).parent?.trimEnd('/')
                val albumArtFiles = arrayListOf("folder.jpg", "albumart.jpg", "cover.jpg")
                albumArtFiles.forEach {
                    val albumArtFilePath = "$trackParentDirectory/$it"
                    if (File(albumArtFilePath).exists()) {
                        val bitmap = BitmapFactory.decodeFile(albumArtFilePath)
                        if (bitmap != null) {
                            val resultBitmap = if (bitmap.height > coverArtHeight * 2) {
                                val ratio = bitmap.width / bitmap.height.toFloat()
                                Bitmap.createScaledBitmap(
                                    bitmap,
                                    (coverArtHeight * ratio).toInt(),
                                    coverArtHeight,
                                    false
                                )
                            } else {
                                bitmap
                            }

                            return resultBitmap
                        }
                    }
                }
            } catch (ignored: Exception) {
            } catch (ignored: Error) {
            }
        }

        if (song.coverArt.startsWith("content://")) {
            try {
                return MediaStore.Images.Media.getBitmap(
                    context.contentResolver,
                    Uri.parse(song.coverArt)
                )
            } catch (ignored: Exception) {
            }
        }

        val size = Size(512, 512)
        if (path.startsWith("content://")) {
            try {
                return context.contentResolver.loadThumbnail(Uri.parse(path), size, null)
            } catch (ignored: Exception) {
            }
        }

        try {
            // ThumbnailUtils.createAudioThumbnail() has better logic for searching thumbnails
            return ThumbnailUtils.createAudioThumbnail(File(path), size, null)
        } catch (ignored: Exception) {
        }

        return null
    }

    fun splitIntoArtist(listSong: ArrayList<Song>): ArrayList<Artist> {
        val artists = arrayListOf<Artist>()
        val songGroupedByArtist = listSong.groupBy { it.artist }
        songGroupedByArtist.forEach { (artistName, listSongByArtist) ->
            val songCount = listSongByArtist.size
            if (songCount > 0) {
                val albumCount = listSongByArtist.groupBy { it.album }.size
                val artistId = listSongByArtist.first().artistId
                val artist = Artist(
                    id = artistId,
                    title = artistName,
                    songCount = songCount,
                    albumCount = albumCount,
                    albumArt = "",
                    usbId = listSongByArtist[0].usbId
                )
                artists.add(artist)
            }
        }
        return artists
    }

    fun splitIntoAlbums(listSong: ArrayList<Song>): ArrayList<Album> {
        val albums = arrayListOf<Album>()
        val songGroupedByAlbum = listSong.groupBy { it.album }
        songGroupedByAlbum.forEach { (albumName, songByAlbum) ->
            val albumId = songByAlbum.first().albumId
            val songCount = songByAlbum.size
            if (songCount > 0) {
                val song = songByAlbum.first()
                val artistName = song.artist
                val year = song.year
                val album = Album(
                    id = albumId,
                    artist = artistName,
                    title = albumName,
                    songCount = songCount,
                    coverArt = "",
                    year = year,
                    artistId = song.artistId,
                    dateAdded = song.dateAdded,
                    usbId = song.usbId
                )
                albums.add(album)
            }
        }
        return albums
    }

    fun indexOfSongInList(songs: List<Song>, songPath: String): Int {
        return songs.indexOfFirst { it.path == songPath }
    }
}