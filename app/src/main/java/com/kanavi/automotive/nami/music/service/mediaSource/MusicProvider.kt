package com.kanavi.automotive.nami.music.service.mediaSource

import android.content.Context
import android.support.v4.media.MediaBrowserCompat
import androidx.core.net.toUri
import com.kanavi.automotive.nami.music.common.constant.MediaConstant
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.IS_NOT_SELECTED_USB
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.IS_SELECTED_USB
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.MEDIA_ID_MUSICS_BY_FILE
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.MEDIA_ID_USB
import com.kanavi.automotive.nami.music.common.extension.getUsbID
import com.kanavi.automotive.nami.music.common.extension.isAudioFast
import com.kanavi.automotive.nami.music.common.extension.isImageFast
import com.kanavi.automotive.nami.music.common.extension.isVideoFast
import com.kanavi.automotive.nami.music.data.database.model.Image
import com.kanavi.automotive.nami.music.data.database.model.Song
import com.kanavi.automotive.nami.music.data.database.model.Video
import com.kanavi.automotive.nami.music.service.MusicService
import timber.log.Timber
import java.lang.ref.WeakReference

class MusicProvider(private val mContext: Context) {
    private var mMusicService: WeakReference<MusicService>? = null
    fun setMusicService(service: MusicService) {
        mMusicService = WeakReference(service)
    }

    private var selectedUsbID = ""
    fun getSelectedUsbID(): String = selectedUsbID

    private fun setSelectedUsbID(usbID: String) {
        selectedUsbID = usbID
    }

    private val usbSourceMap: HashMap<String, UsbSource> = HashMap()
    fun getUsbSourceMap(): HashMap<String, UsbSource> =
        usbSourceMap.clone() as HashMap<String, UsbSource>

    private var currentParentID: String = ""

    fun getUsbSource(usbID: String? = null): UsbSource? {
        return usbSourceMap[usbID ?: selectedUsbID]
    }

    fun addUsbSource(usbId: String, usbSource: UsbSource) {
        Timber.d("addUsbSource: $usbId")
        usbSourceMap[usbId] = usbSource
        if (selectedUsbID.isEmpty()) setSelectedUsbID(usbId)
        notifyDataChanged(includingUsbList = true)
    }

    fun removeUsbSource(usbId: String) {
        usbSourceMap.values.remove(usbSourceMap[usbId])
        Timber.e("after removeUsbSource: $usbId => ${usbSourceMap.keys}")
        val newSelectedUsbId = usbSourceMap.firstNotNullOfOrNull { it.key } ?: ""
        Timber.d("removeUsbSource: $usbId, current selectedUsbID: $newSelectedUsbId")
        setSelectedUsbID(newSelectedUsbId)
        notifyDataChanged(includingUsbList = true)
    }

    fun notifyDataChanged(includingUsbList: Boolean = false) {
        Timber.e("==========notifyDataChanged is called=============")
        val listMediaIdToNotifyChanged =
            MediaConstant.listOfMediaIdAlwaysHaveToNotifyChanged + currentParentID + if (includingUsbList) MediaConstant.MEDIA_ID_USB_LIST else ""
        listMediaIdToNotifyChanged.forEach { mediaId ->
            mMusicService?.get()?.notifyChildrenChanged(mediaId)
        }
    }

    fun getUsbListItems(): List<MediaBrowserCompat.MediaItem> {
        Timber.d("getUsbListItems")
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        mediaItems.addAll(getUsbList())
        return mediaItems
    }


    private fun getUsbList(): List<MediaBrowserCompat.MediaItem> {
        val usbItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        Timber.d("get USB List")
        usbSourceMap.forEach { usbSource ->
            val itemUsbMediaID = MediaIDHelper.createMediaID(
                usbSource.key,
                MEDIA_ID_USB
            )
            val isSelectedUsb =
                if (usbSource.key == selectedUsbID) IS_SELECTED_USB else IS_NOT_SELECTED_USB
            usbItems.add(
                UsbMediaItem.with(mContext)
                    .asBrowsable()
                    .mediaID(itemUsbMediaID)
                    .title(usbSource.key.getUsbID())
                    .subTitle(isSelectedUsb)
                    .description(selectedUsbID)
                    .build()
            )
        }
        Timber.d("USB List size is ${usbItems.size} $usbItems")
        return usbItems
    }

    fun getChildren(
        mediaId: String,
        usbID: String? = null
    ): List<MediaBrowserCompat.MediaItem> {
        Timber.d("for mediaId: $mediaId, current selectedUsbID: $selectedUsbID")
        if (mediaId.contains(MEDIA_ID_USB)) {
            val usbIdExtracted = MediaIDHelper.extractMusicID(mediaId)
            usbIdExtracted?.let {
                if (usbSourceMap.keys.contains(usbIdExtracted))
                    if (usbIdExtracted != selectedUsbID) {
                        Timber.e("usbId extracted from mediaId request != selectedUsbID => set selectedUsbID to: $usbIdExtracted")
                        setSelectedUsbID(it)
                        notifyDataChanged(includingUsbList = true)
                    }
            }
        }
        val mediaItems: MutableList<MediaBrowserCompat.MediaItem> = ArrayList()
        getFileChildren(mediaId, mediaItems, usbID)
        return mediaItems.take(300)
    }

    private fun getFileChildren(
        mediaID: String,
        mediaItems: MutableList<MediaBrowserCompat.MediaItem>,
        usbID: String? = null
    ) {
        Timber.d("for mediaID: $mediaID")
        currentParentID = mediaID
        val category = MediaIDHelper.extractCategory(mediaID)
        var rootPath = MediaIDHelper.extractMusicID(mediaID)
        if (rootPath == null)
            rootPath = usbID ?: selectedUsbID
        Timber.d("getFileChildren of usbID: $rootPath for category: $category")

        val checkLoading = getUsbSource()?.isLoading
        if (checkLoading == true) {
            val rootNode = getUsbSource()?.let { TreeNode.findNode(it.treeNodewithNotMetadata, rootPath) }
            val children = rootNode?.children?.let { ArrayList(it) }
            Timber.d("children count: ${children?.size}")
            children?.forEach {
                val listSong = mutableListOf<Song?>()
                val listImage = mutableListOf<Image?>()
                val listVideo = mutableListOf<Video?>()
                val listFolderPath = mutableListOf<String>()
                val path = it.value
                Timber.i("checking path: $path")
                if (path.isAudioFast()) {
                    val song = getUsbSource()?.songMapswithNotMetadata?.get(path.hashCode())
                    listSong.add(song)
                } else if (path.isImageFast()) {
                    val image = getUsbSource()?.imageMapswithMetadata?.get(path.hashCode())
                    listImage.add(image)
                } else if (path.isVideoFast()) {
                    val video = getUsbSource()?.videoMapswithNotMetadata?.get(path.hashCode())
                    listVideo.add(video)
                } else {
                    listFolderPath.add(path)
                }

                listSong.forEach { song ->
                    song?.let {
                        mediaItems.add(getPlayableSongNotMetaData(song = song, isLoading = checkLoading))
                    }
                }
                listImage.forEach { image ->
                    image?.let {
                        mediaItems.add(getAvailableImage(image = image))
                    }
                }

                listVideo.forEach { video ->
                    video?.let {
                        mediaItems.add(getPlayableVideoNotMetaData(video = video, isLoading = checkLoading))
                    }
                }

                listFolderPath.forEach { folderPath ->
                    val delimiter = if (folderPath.contains("\\")) "\\" else "/"
                    val folderName = folderPath.substringAfterLast(delimiter)
                    val folderMediaID = MediaIDHelper.createMediaID(
                        folderPath,
                        MEDIA_ID_MUSICS_BY_FILE
                    )
                    mediaItems.add(
                        0,
                        UsbMediaItem.with(mContext)
                            .mediaID(folderMediaID)
                            .asBrowsable()
                            .title(folderName)
                            .setExtraProperties(isLoading = checkLoading)
                            .build()
                    )
                }
            }
        } else {
            val rootNode = getUsbSource()?.let { TreeNode.findNode(it.treeNode, rootPath) }
            val children = rootNode?.children?.let { ArrayList(it) }
            children?.forEach {
                val listSong = mutableListOf<Song?>()
                val listImage = mutableListOf<Image?>()
                val listVideo = mutableListOf<Video?>()
                val listFolderPath = mutableListOf<String>()
                val path = it.value
                Timber.i("checking path: $path")
                if (path.isAudioFast()) {
                    val song = getUsbSource()?.songMap?.get(path.hashCode())
                    listSong.add(song)
                } else if (path.isImageFast()) {
                    val image = getUsbSource()?.imageMap?.get(path.hashCode())
                    listImage.add(image)
                } else if (path.isVideoFast()) {
                    val video = getUsbSource()?.videomap?.get(path.hashCode())
                    listVideo.add(video)
                } else {
                    listFolderPath.add(path)
                }

                listSong.forEach { song ->
                    song?.let {
                        mediaItems.add(getPlayableSong(song = song))
                    }
                }
                listImage.forEach { image ->
                    image?.let {
                        mediaItems.add(getAvailableImage(image = image))
                    }
                }

                listVideo.forEach { video ->
                    video?.let {
                        mediaItems.add(getPlayableVideo(video = video))
                    }
                }

                listFolderPath.forEach { folderPath ->
                    var delimiter = if (folderPath.contains("\\")) "\\" else "/"
                    val folderName = folderPath.substringAfterLast(delimiter)
                    val folderMediaID = MediaIDHelper.createMediaID(
                        folderPath,
                        MEDIA_ID_MUSICS_BY_FILE
                    )
                    mediaItems.add(
                        0,
                        UsbMediaItem.with(mContext)
                            .mediaID(folderMediaID)
                            .asBrowsable()
                            .title(folderName)
                            .build()
                    )
                }
            }
        }
    }

    private fun getPlayableSong(song: Song): MediaBrowserCompat.MediaItem {
        val songPath = MediaIDHelper.createMediaID(
            song.path,
            MEDIA_ID_MUSICS_BY_FILE
        )
        return UsbMediaItem.with(mContext)
            .asPlayable()
            .mediaID(songPath)
            .title(song.title)
            .subTitle(song.artist)
            .icon(song.coverArt.toUri())
            .setExtraProperties(
                duration = song.duration,
                path = song.path
            )
            .build()
    }

    private fun getPlayableVideo(video: Video): MediaBrowserCompat.MediaItem {
        val videoPath = MediaIDHelper.createMediaID(
            video.path,
            MEDIA_ID_MUSICS_BY_FILE
        )
        return UsbMediaItem.with(mContext)
            .asPlayable()
            .mediaID(videoPath)
            .title(video.title)
            .subTitle(video.artist)
            .icon(video.coverArt.toUri())
            .setExtraProperties(
                duration = video.duration,
                path = video.path
            )
            .build()
    }

    private fun getPlayableSongNotMetaData(song: Song, isLoading: Boolean): MediaBrowserCompat.MediaItem {
        val songPath = MediaIDHelper.createMediaID(
            song.path,
            MEDIA_ID_MUSICS_BY_FILE
        )
        return UsbMediaItem.with(mContext)
            .asPlayable()
            .mediaID(songPath)
            .title(song.title)
            .subTitle(song.artist)
            .icon(song.coverArt.toUri())
            .setExtraProperties(
                duration = song.duration,
                path = song.path,
                isLoading = isLoading
            )
            .build()
    }

    private fun getPlayableVideoNotMetaData(video: Video,isLoading: Boolean): MediaBrowserCompat.MediaItem {
        val videoPath = MediaIDHelper.createMediaID(
            video.path,
            MEDIA_ID_MUSICS_BY_FILE
        )
        return UsbMediaItem.with(mContext)
            .asPlayable()
            .mediaID(videoPath)
            .title(video.title)
            .subTitle(video.artist)
            .icon(video.coverArt.toUri())
            .setExtraProperties(
                duration = video.duration,
                path = video.path,
                isLoading = isLoading
            )
            .build()
    }

    private fun getAvailableImage(image: Image): MediaBrowserCompat.MediaItem {
        val imagePath = MediaIDHelper.createMediaID(
            image.path,
            MEDIA_ID_MUSICS_BY_FILE
        )
        return UsbMediaItem.with(mContext)
            .mediaID(imagePath)
            .title(image.title)
            .subTitle(image.artist)
            .setExtraProperties(
                path = image.path,
                dateTaken = image.dateTaken
            )
            .build()
    }
}
