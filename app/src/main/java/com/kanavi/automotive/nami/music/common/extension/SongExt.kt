package com.kanavi.automotive.nami.music.common.extension

import android.support.v4.media.MediaDescriptionCompat
import android.support.v4.media.session.MediaSessionCompat
import com.kanavi.automotive.nami.music.common.util.MusicUtil
import com.kanavi.automotive.nami.music.data.database.model.Song

val Song.albumArtUri get() = MusicUtil.getMediaStoreAlbumCoverUri(albumId)

fun ArrayList<Song>.toMediaSessionQueue(): List<MediaSessionCompat.QueueItem> {
    return map { song ->
        val mediaDescription = MediaDescriptionCompat.Builder()
            .setMediaId(song.id.toString())
            .setTitle(song.title)
            .setSubtitle(song.artist)
            .setIconUri(song.albumArtUri)
            .build()
        MediaSessionCompat.QueueItem(mediaDescription, song.hashCode().toLong())
    }
}

