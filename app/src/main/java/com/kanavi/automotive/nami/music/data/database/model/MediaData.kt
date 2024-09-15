package com.kanavi.automotive.nami.music.data.database.model

data class MediaData(
    val usbId: String,
    val path: String,
    val parentFolder: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val lastModified: Long,
)