package com.kanavi.automotive.nami.music.data.database.model

import com.kanavi.automotive.nami.music.common.constant.Constants.PLAYER_SORT_BY_TITLE
import com.kanavi.automotive.nami.music.common.constant.Constants.SORT_DESCENDING
import com.kanavi.automotive.nami.music.common.extension.sortSafely
import com.kanavi.automotive.nami.music.common.util.AlphanumericComparator


data class Folder(val title: String, val trackCount: Int, val path: String) {
    companion object {
        fun getComparator(sorting: Int) = Comparator<Folder> { first, second ->
            var result = when {
                sorting and PLAYER_SORT_BY_TITLE != 0 -> AlphanumericComparator().compare(
                    first.title.lowercase(),
                    second.title.lowercase()
                )

                else -> first.trackCount.compareTo(second.trackCount)
            }

            if (sorting and SORT_DESCENDING != 0) {
                result *= -1
            }

            return@Comparator result
        }
    }
}

fun ArrayList<Folder>.sortSafely(sorting: Int) = sortSafely(Folder.getComparator(sorting))
