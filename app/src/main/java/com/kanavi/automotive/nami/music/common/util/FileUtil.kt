package com.kanavi.automotive.nami.music.common.util

import com.kanavi.automotive.nami.music.App
import com.kanavi.automotive.nami.music.common.extension.getAlbum
import com.kanavi.automotive.nami.music.common.extension.getArtist
import com.kanavi.automotive.nami.music.common.extension.getDuration
import com.kanavi.automotive.nami.music.common.extension.getFilenameFromPath
import com.kanavi.automotive.nami.music.common.extension.getMediaStoreLastModified
import com.kanavi.automotive.nami.music.common.extension.getParentPath
import com.kanavi.automotive.nami.music.common.extension.getTitle
import com.kanavi.automotive.nami.music.common.extension.getUsbID
import com.kanavi.automotive.nami.music.data.database.model.MediaData

object FileUtil {
    fun getMediaData(path: String?): MediaData? {
        return if (path.isNullOrEmpty()) null else {
            val context = App.app
            val title = context.getTitle(path)
            val artist = context.getArtist(path)
            val album = context.getAlbum(path)
            val duration = context.getDuration(path)
            val lastModified = context.getMediaStoreLastModified(path)
            val parentFolder = path.getParentPath().getFilenameFromPath()
            val usbID = path.getUsbID()
            MediaData(
                usbId = usbID,
                path = path,
                parentFolder = parentFolder,
                title = title ?: "",
                artist = artist ?: "",
                album = album ?: "",
                duration = duration ?: 0,
                lastModified = lastModified
            )
        }

    }
}