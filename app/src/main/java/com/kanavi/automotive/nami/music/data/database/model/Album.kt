package com.kanavi.automotive.nami.music.data.database.model

import android.provider.MediaStore
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_ARTIST_TITLE
import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_DATE_ADDED
import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_TITLE
import com.kanavi.automotive.nami.music.common.constant.Constants.SORT_DESCENDING
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_ARTIST
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_ARTIST_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_COVER_ART
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_DATE_ADDED
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_ID
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_SONG_COUNT
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_TABLE_NAME
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_TITLE
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.ALBUM_YEAR
import com.kanavi.automotive.nami.music.common.constant.DatabaseEntry.USB_ID
import com.kanavi.automotive.nami.music.common.extension.sortSafely
import com.kanavi.automotive.nami.music.common.util.AlphanumericComparator


@Entity(tableName = ALBUM_TABLE_NAME, indices = [(Index(value = [ALBUM_ID], unique = true))])
data class Album(
    @PrimaryKey
    @ColumnInfo(name = ALBUM_ID)
    var id: Long,
    @ColumnInfo(name = ALBUM_ARTIST) val artist: String,
    @ColumnInfo(name = ALBUM_TITLE) val title: String,
    @ColumnInfo(name = ALBUM_SONG_COUNT) val songCount: Int,
    @ColumnInfo(name = ALBUM_COVER_ART) val coverArt: String,
    @ColumnInfo(name = ALBUM_YEAR) val year: Int,
    @ColumnInfo(name = ALBUM_ARTIST_ID) var artistId: Long,
    @ColumnInfo(name = ALBUM_DATE_ADDED) var dateAdded: Long,
    @ColumnInfo(name = USB_ID) var usbId: String,

    ) {
    override fun hashCode(): Int {
        var result = title.hashCode()
        result = 31 * result + artist.hashCode()
        result = 31 * result + songCount
        result = 31 * result + year
        result = 31 * result + dateAdded.hashCode()
        result = 31 * result + usbId.hashCode()
        return result
    }

    companion object {
        fun getComparator(sorting: Int) = Comparator<Album> { first, second ->
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

                sorting and PLAYER_SORT_BY_ARTIST_TITLE != 0 -> {
                    when {
                        first.artist == MediaStore.UNKNOWN_STRING && second.artist != MediaStore.UNKNOWN_STRING -> 1
                        first.artist != MediaStore.UNKNOWN_STRING && second.artist == MediaStore.UNKNOWN_STRING -> -1
                        else -> AlphanumericComparator().compare(
                            first.artist.lowercase(),
                            second.artist.lowercase()
                        )
                    }
                }

                sorting and PLAYER_SORT_BY_DATE_ADDED != 0 -> first.dateAdded.compareTo(second.dateAdded)
                else -> first.year.compareTo(second.year)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }

}

fun ArrayList<Album>.sortSafely(sorting: Int) = sortSafely(Album.getComparator(sorting))
