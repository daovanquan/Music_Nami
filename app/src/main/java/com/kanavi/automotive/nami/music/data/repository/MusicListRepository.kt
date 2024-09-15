package com.kanavi.automotive.nami.music.data.repository

import android.support.v4.media.MediaBrowserCompat
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.IS_SELECTED_USB
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.MEDIA_ID_ALL_ALBUM
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.MEDIA_ID_ALL_SONG
import com.kanavi.automotive.nami.music.common.extension.getUsbID
import com.kanavi.automotive.nami.music.common.extension.isAudioFast
import com.kanavi.automotive.nami.music.data.database.model.ItemType
import com.kanavi.automotive.nami.music.data.database.model.MediaItemData
import com.kanavi.automotive.nami.music.data.shared.MediaServiceConnection
import com.kanavi.automotive.nami.music.service.mediaSource.UsbMediaItem.Builder.Companion.EXTRA_DATE_TAKEN
import com.kanavi.automotive.nami.music.service.mediaSource.UsbMediaItem.Builder.Companion.EXTRA_IS_LOADING_METADATA
import com.kanavi.automotive.nami.music.service.mediaSource.UsbMediaItem.Builder.Companion.EXTRA_SONG_DURATION
import com.kanavi.automotive.nami.music.service.mediaSource.UsbMediaItem.Builder.Companion.EXTRA_SONG_PATH
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File

class MusicListRepository(mediaServiceConnection: MediaServiceConnection) {

    private val usbMediaServiceConnection = mediaServiceConnection

    private var rootMediaId: String? = null
    private var currentMediaId: String? = null

    private val _usbList = MutableStateFlow<List<MediaItemData>>(emptyList())
    val usbList = _usbList as StateFlow<List<MediaItemData>>

    private val _mediaItems = MutableStateFlow<List<MediaItemData>>(emptyList())
    val mediaItems = _mediaItems as StateFlow<List<MediaItemData>>

    private val _isSelectUsbSource = MutableStateFlow(true)
    val isSelectUsbSource = _isSelectUsbSource

    private val _isUsbAttached = MutableStateFlow(false)
    val isUsbAttached = _isUsbAttached

    private val _currentUsbSelected = MutableStateFlow("")
    val currentUsbSelected = _currentUsbSelected

    private val _currentImageSelected = MutableLiveData<MediaItemData?>(null)
    val currentImageSelected: LiveData<MediaItemData?> = _currentImageSelected

    private val imageItems = MutableLiveData<List<MediaItemData>>()
    private val displayImageItems = MutableLiveData<List<MediaItemData>>()

    private val _currentVideoSelected = MutableLiveData<MediaItemData?>(null)

    val videosItems = MutableLiveData<List<MediaItemData>>()

    private val subscriptionToRootForGetUSBListCallback =
        object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(
                parentId: String, children: List<MediaBrowserCompat.MediaItem>
            ) {
                val currentUsbId = _currentUsbSelected.value.getUsbID()
                Timber.d("get mediaItems of mediaId: $parentId, UsbIdSelected: $currentUsbId")
                _isUsbAttached.value = children.isNotEmpty()
                if (children.isNotEmpty() && children.find { it.description.title == currentUsbId } == null) {
                    Timber.e("Selected USB is not available in the list, so clear the current selected USB")
                    _isSelectUsbSource.value = true
                }

                val usbList = children.map { child ->
                    val description = child.description
                    val isSelectedUsb = description.subtitle == IS_SELECTED_USB
                    val selectedUsbId = description.description.toString()
                    if (_currentUsbSelected.value != selectedUsbId) {
                        Timber.e("Selected USB is changed from current: ${_currentUsbSelected.value} to new: $selectedUsbId")
                        _currentUsbSelected.value = selectedUsbId
                    }

                    MediaItemData(
                        mediaId = child.mediaId,
                        itemType = ItemType.USB,
                        browsable = child.isBrowsable,
                        title = description.title.toString(),
                        isSelectedUsb = isSelectedUsb,
                    )
                }
                _usbList.value = usbList
                Timber.d("isUsbAttached: ${_isUsbAttached.value}")
            }
        }

    private val subscriptionToMediaIdForGetMediaItemsCallback =
        object : MediaBrowserCompat.SubscriptionCallback() {
            override fun onChildrenLoaded(
                parentId: String, children: List<MediaBrowserCompat.MediaItem>
            ) {
                Timber.d("get mediaItems of mediaId: $parentId")

                val itemsList = children.map { child ->
                    val description = child.description
                    val path = description.extras?.getString(EXTRA_SONG_PATH)
                    val title =
                        if (description.title.toString() != "") description.title.toString() else File(
                            path ?: ""
                        ).name
                    val subtitle = description.subtitle ?: ""

                    val isLoading =
                        description.extras?.getBoolean(EXTRA_IS_LOADING_METADATA) ?: false

                    val itemType = when {
                        child.isBrowsable -> {
                            ItemType.FOLDER
                        }

                        else -> {
                            ItemType.MUSIC
                        }

                    }
                    MediaItemData(
                        mediaId = child.mediaId,
                        browsable = child.isBrowsable,
                        title = title,
                        itemType = itemType,
                        subtitle = subtitle.toString(),
                        path = path ?: "",
                        albumArtUri = description.iconUri,
                        duration = description.extras?.getLong(EXTRA_SONG_DURATION),
                        dateTaken = description.extras?.getString(EXTRA_DATE_TAKEN),
                        isLoading = isLoading
                    )
                }
                _mediaItems.value = sortItemList(itemsList)
            }
        }

//    private val subscriptionToRootForGetAllSong =
//        object : MediaBrowserCompat.SubscriptionCallback() {
//            override fun onChildrenLoaded(
//                parentId: String, children: List<MediaBrowserCompat.MediaItem>
//            ) {
//                Timber.d("get mediaItems of mediaId: $parentId")
//
//                val itemsList = children.map { child ->
//                    val description = child.description
//                    val path = description.extras?.getString(EXTRA_SONG_PATH)
//                    val title =
//                        if (description.title.toString() != "") description.title.toString() else File(
//                            path ?: ""
//                        ).name
//                    val subtitle = description.subtitle ?: ""
//
//                    val isLoading =
//                        description.extras?.getBoolean(EXTRA_IS_LOADING_METADATA) ?: false
//
//                    MediaItemData(
//                        mediaId = child.mediaId,
//                        browsable = child.isBrowsable,
//                        title = title,
//                        itemType = ItemType.MUSIC,
//                        subtitle = subtitle.toString(),
//                        path = path ?: "",
//                        albumArtUri = description.iconUri,
//                        duration = description.extras?.getLong(EXTRA_SONG_DURATION),
//                        dateTaken = description.extras?.getString(EXTRA_DATE_TAKEN),
//                        isLoading = isLoading
//                    )
//                }
//                _songList.value = sortItemList(itemsList)
//            }
//        }
//
//    fun subcribeToGetAllSong(){
//        usbMediaServiceConnection.subscribe(MEDIA_ID_ALL_SONG,subscriptionToRootForGetAllSong)
//    }

    fun subscribeToRootForGetUsbList(rootId: String, firstTime: Boolean = false) {
        _isSelectUsbSource.value = true
        if (firstTime) {
            rootMediaId = rootId
            Timber.d("subscribeToRootForGetUsbList: $rootId")
            usbMediaServiceConnection.subscribe(
                rootMediaId!!,
                subscriptionToRootForGetUSBListCallback
            )
        }
    }

    fun subscribeToMediaIdForGetMediaItems(mediaId: String) {
        if (currentMediaId != null) {
            unsubscribeFromMediaId(currentMediaId!!)
        }
        currentMediaId = mediaId
        _isSelectUsbSource.value = false
        Timber.e("subscribeToMediaIdForGetMediaItems: $mediaId")
        usbMediaServiceConnection.subscribe(
            currentMediaId!!,
            subscriptionToMediaIdForGetMediaItemsCallback
        )
    }

    private fun unsubscribeFromMediaId(mediaId: String) {
        Timber.e("unsubscribeFromMediaId: $mediaId")
        _mediaItems.value = emptyList()
        usbMediaServiceConnection.unsubscribe(
            mediaId,
            subscriptionToMediaIdForGetMediaItemsCallback
        )
    }

    fun destroy() {
        // And then, finally, unsubscribe the media ID that was being watched.
        rootMediaId?.let { unsubscribeFromMediaId(it) }
        currentMediaId?.let { unsubscribeFromMediaId(it) }
        _currentVideoSelected.value = null
    }

    fun handleUsbDisconnect() {
        _isSelectUsbSource.value = true
        _mediaItems.value = emptyList()
        _currentUsbSelected.value = ""
    }

    fun sortItemList(list: List<MediaItemData>): List<MediaItemData> {
        return list.sortedWith(compareBy {
            when (it.itemType) {
                ItemType.USB -> 0
                ItemType.FOLDER -> 1
                ItemType.MUSIC -> 2
            }
        })
    }
}