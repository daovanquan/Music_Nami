package com.kanavi.automotive.nami.music.data.database.model

import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.MediaItem
import com.kanavi.automotive.nami.music.common.DiffUtilObject

enum class ItemType {
    USB,
    FOLDER,
    MUSIC
}

/**
 * Data class to encapsulate properties of a [MediaItem].
 *
 * If an item is [browsable] it means that it has a list of child media items that
 * can be retrieved by passing the mediaId to [MediaBrowserCompat.subscribe].
 *
 */
data class MediaItemData(
    val mediaId: String? = null,
    val itemType: ItemType = ItemType.USB,
    val browsable: Boolean = false,
    val selectedUsbId: String? = null,
    val isSelectedUsb: Boolean = false,
    val title: String? = "",
    var subtitle: String? = "",
    val artistName: String? = null,
    val albumName: String? = null,
    var duration: Long? = null,
    val albumArtUri: Uri? = null,
    val path: String = "",
    val playbackRes: Int = -1,
    var dateTaken: String? = "",
    val isLoading: Boolean? = false
) : DiffUtilObject() {

    val PLAYBACK_RES_CHANGED = 1

    override fun areItemsTheSame(item: DiffUtilObject): Boolean =
        this.mediaId == (item as MediaItemData).mediaId

    override fun areContentsTheSame(item: DiffUtilObject): Boolean {
        val newItem = item as MediaItemData
        return this.mediaId == newItem.mediaId &&
                this.isSelectedUsb == newItem.isSelectedUsb &&
                this.title == newItem.title &&
                this.subtitle == newItem.subtitle &&
                this.artistName == newItem.artistName &&
                this.albumName == newItem.albumName &&
                this.duration == newItem.duration &&
                this.browsable == newItem.browsable&&
                this.isLoading == newItem.isLoading
    }

    override fun getChangePayload(item: DiffUtilObject) =
        if (this.playbackRes != (item as MediaItemData).playbackRes) {
            PLAYBACK_RES_CHANGED
        } else null
}
