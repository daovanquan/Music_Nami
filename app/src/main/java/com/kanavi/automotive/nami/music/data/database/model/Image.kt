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

data class Image(
    var id: Long = 0L,
    var mediaStoreId: Long = 0L,
    var title: String = "unknown", var artist: String = "unknown", var path: String = "",
    var album: String = "unknown",
    var folderName: String = "",
    var albumId: Long = 0L,
    var artistId: Long = 0L,
    var year: Int = 0,
    var dateAdded: Long = 0L,
    var dateTaken: String = "",
    var description: String = "",
    var height: Long = 0L,
    var width: Long = 0L,
    var size: Long = 0L,
    var displayName: String = "",
    var usbId: String = "",
) {
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + path.hashCode()
        result = 31 * result + album.hashCode()
        result = 31 * result + folderName.hashCode()
        result = 31 * result + year
        result = 31 * result + dateAdded.hashCode()
        result = 31 * result + dateTaken.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + height.hashCode()
        result = 31 * result + width.hashCode()
        result = 31 * result + displayName.hashCode()
        result = 31 * result + usbId.hashCode()
        return result
    }

    companion object {
        @JvmStatic
        val emptyImage = Image(
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
            dateTaken = "",
            description = "",
            height = -1,
            width = -1,
            size = -1,
            displayName = "",
            usbId = ""
        )

        fun getComparator(sorting: Int) = Comparator<Image> { first, second ->
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
        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, mediaStoreId)
    }
}

fun ArrayList<Image>.sortSafety(sorting: Int) = sortSafely(Image.getComparator(sorting))
