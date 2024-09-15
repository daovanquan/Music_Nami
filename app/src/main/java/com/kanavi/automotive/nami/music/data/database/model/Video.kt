package com.kanavi.automotive.nami.music.data.database.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_ARTIST_TITLE
import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_DATE_ADDED
import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_TITLE
import com.kanavi.automotive.nami.music.common.constant.Constants.SORT_DESCENDING
import com.kanavi.automotive.nami.music.common.extension.sortSafely
import com.kanavi.automotive.nami.music.common.util.AlphanumericComparator
import java.io.File

data class Video(
    var id: Long = 0L,
    var mediaStoreId: Long = 0L,
    var title: String = "unknown", var artist: String = "unknown", var path: String = "",
    var album: String = "unknown",
    var duration: Long = 0L,
    var folderName: String = "",
    var coverArt: String = "",
    var albumId: Long = 0L,
    var artistId: Long = 0L,
    var year: Int = 0,
    var dateAdded: Long = 0L,
    var description: String = "",
    var subtitle: String = "",
    var size: Long = 0L,
    var usbId: String = "",
) {
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + albumId.hashCode()
        result = 31 * result + artistId.hashCode()
        result = 31 * result + folderName.hashCode()
        result = 31 * result + year
        result = 31 * result + dateAdded.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + duration.hashCode()
        result = 31 * result + coverArt.hashCode()
        result = 31 * result + usbId.hashCode()
        result = 31 * result + subtitle.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        val emptyVideo = Video(
            id = -1,
            mediaStoreId = -1,
            title = "",
            artist = "",
            path = "",
            album = "",
            folderName = "",
            albumId = -1,
            artistId = -1,
            year = 0,
            dateAdded = -1,
            description = "",
            size = -1,
            usbId = "",
            coverArt = "",
            duration = 0,
            subtitle = ""
        )

        fun getComparator(sorting: Int) = Comparator<Video> { first, second ->
            var result = when {
                sorting and PLAYER_SORT_BY_TITLE != 0 -> {
                    when {
                        first.title == MediaStore.UNKNOWN_STRING && second.title != MediaStore.UNKNOWN_STRING -> 1
                        first.title != MediaStore.UNKNOWN_STRING && second.title == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(
                            first.title.lowercase(),
                            second.title.lowercase()
                        )
                    }
                }

                sorting and PLAYER_SORT_BY_DATE_ADDED != 0 -> first.dateAdded.compareTo(second.dateAdded)
                else -> first.size.compareTo(second.size)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

    fun getBubbleText(sorting: Int) = when {
        sorting and PLAYER_SORT_BY_TITLE != 0 -> title
        sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> artist
        else -> size
    }

    fun getUri(): Uri = if (mediaStoreId == 0L) {
        Uri.fromFile(File(path))
    } else {
        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }
}

fun ArrayList<Video>.sortSafety(sorting: Int) = sortSafely(Video.getComparator(sorting))
