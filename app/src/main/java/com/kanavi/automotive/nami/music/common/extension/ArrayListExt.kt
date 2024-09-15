package com.kanavi.automotive.nami.music.common.extension

fun <T> ArrayList<T>.sortSafely(comparator: Comparator<T>) {
    try {
        sortWith(comparator)
    } catch (ignored: Exception) {
    }
}
