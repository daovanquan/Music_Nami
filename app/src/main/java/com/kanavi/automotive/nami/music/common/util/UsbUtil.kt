package com.kanavi.automotive.nami.music.common.util

import android.content.ContentResolver
import android.media.MediaMetadataRetriever
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileIdBothDirectoryInformation
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.common.SMBException
import com.hierynomus.smbj.share.DiskShare
import com.kanavi.automotive.nami.music.App
import com.kanavi.automotive.nami.music.common.extension.areDigitsOnly
import com.kanavi.automotive.nami.music.common.extension.containsNoMedia
import com.kanavi.automotive.nami.music.common.extension.getAlbum
import com.kanavi.automotive.nami.music.common.extension.getAlbumForVideo
import com.kanavi.automotive.nami.music.common.extension.getAlbumIdAndArtistIdFromPath
import com.kanavi.automotive.nami.music.common.extension.getArtist
import com.kanavi.automotive.nami.music.common.extension.getArtistVideo
import com.kanavi.automotive.nami.music.common.extension.getDuration
import com.kanavi.automotive.nami.music.common.extension.getFilenameFromPath
import com.kanavi.automotive.nami.music.common.extension.getImageDateTaken
import com.kanavi.automotive.nami.music.common.extension.getLong
import com.kanavi.automotive.nami.music.common.extension.getMediaStoreIdFromPath
import com.kanavi.automotive.nami.music.common.extension.getMediaStoreLastModified
import com.kanavi.automotive.nami.music.common.extension.getParentPath
import com.kanavi.automotive.nami.music.common.extension.getSizeFromContentUri
import com.kanavi.automotive.nami.music.common.extension.getString
import com.kanavi.automotive.nami.music.common.extension.getTitle
import com.kanavi.automotive.nami.music.common.extension.getTitleForImage
import com.kanavi.automotive.nami.music.common.extension.getTitleForVideo
import com.kanavi.automotive.nami.music.common.extension.getUsbID
import com.kanavi.automotive.nami.music.common.extension.getYear
import com.kanavi.automotive.nami.music.common.extension.isAudioFast
import com.kanavi.automotive.nami.music.common.extension.isImageFast
import com.kanavi.automotive.nami.music.common.extension.isVideoFast
import com.kanavi.automotive.nami.music.data.database.model.FileDirItem
import com.kanavi.automotive.nami.music.data.database.model.Image
import com.kanavi.automotive.nami.music.data.database.model.ListItem
import com.kanavi.automotive.nami.music.data.database.model.Song
import com.kanavi.automotive.nami.music.data.database.model.Video
import com.kanavi.automotive.nami.music.service.mediaSource.MusicProvider
import com.kanavi.automotive.nami.music.service.mediaSource.TreeNode
import com.stericson.RootShell.execution.Command
import com.stericson.RootTools.RootTools
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.EnumSet
import java.util.Locale

class IntNumber(var value: Int) {
    fun increase() {
        value++
    }
}

object UsbUtil : KoinComponent {
    private val musicProvider: MusicProvider by inject()

    private const val STEP_SONG_TO_UPDATE = 10

    private val desiredAccess = EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_EXECUTE)
    private val fileAttributes = EnumSet.of(FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
    private val shareAccess =
        EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE)
    private val createOptions = EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE)
    private val createDisposition = SMB2CreateDisposition.FILE_OPEN

    fun scanAllMusicFromSambaServer(
        hostURL: String,
        usbID: String,
        share: DiskShare,
        pathsToIgnore: List<String>,
        songList: ArrayList<Song>,
        songMap: HashMap<Int, Song>,
        imageList: ArrayList<Image>,
        imageMap: HashMap<Int, Image>,
        videoList: ArrayList<Video>,
        videoMap: HashMap<Int, Video>,
        treeNode: TreeNode,
    ) {
        Timber.e("===========START SCAN ALL MEDIA FILES FROM USB: $usbID in host IP: ${share.smbPath}============")
        var numberFileCount = IntNumber(0)
        findAudioFiles(
            hostURL,
            share,
            "",
            pathsToIgnore,
            numberFileCount,
            songList,
            songMap,
            imageList,
            imageMap,
            videoList,
            videoMap,
            treeNode
        )
        Timber.e("=============FINISH SCAN ALL MEDIA FILES FROM USB: $usbID in host IP: ${share.smbPath} - size of list is ${songList.size}============")
    }


    private fun findAudioFiles(
        hostURL: String,
        share: DiskShare,
        currentPath: String,
        excludedPaths: List<String>,
        numberFileCount: IntNumber,
        songList: ArrayList<Song>,
        songMap: HashMap<Int, Song>,
        imageList: ArrayList<Image>,
        imageMap: HashMap<Int, Image>,
        videoList: ArrayList<Video>,
        videoMap: HashMap<Int, Video>,
        treeNode: TreeNode,
    ) {
        try {
            if (currentPath.contains("sdcard")
                || currentPath.contains("usb1")
                || currentPath.contains("usb2")
            ) {
                Timber.i("ignore usb1 and usb2 path")
                return
            }
            val directory = share.openDirectory(
                currentPath,
                desiredAccess,
                fileAttributes,
                shareAccess,
                createDisposition,
                createOptions
            )
            directory.list()
                .filter { fileId -> fileId.fileName != "." && fileId.fileName != ".." }
                .forEach { fileId ->
                    val pathToContinueBrowsing =
                        if (currentPath.isEmpty()) fileId.fileName else "$currentPath\\${fileId.fileName}"
                    Timber.i("Browsing file: $pathToContinueBrowsing")

                    if ((fileId.fileAttributes and 0x10).toInt() != 0) {
                        Timber.i("$pathToContinueBrowsing is a FOLDER")
                        findAudioFiles(
                            hostURL,
                            share,
                            pathToContinueBrowsing,
                            excludedPaths,
                            numberFileCount,
                            songList,
                            songMap,
                            imageList,
                            imageMap,
                            videoList,
                            videoMap,
                            treeNode
                        )
                    } else {
                        Timber.i("$pathToContinueBrowsing is a FILE, currentPath: $currentPath, fileId.fileName: ${fileId.fileName}")
                        if (pathToContinueBrowsing.isAudioFast() || pathToContinueBrowsing.isImageFast() || pathToContinueBrowsing.isVideoFast()) {
                            if (pathToContinueBrowsing.isAudioFast()) {
                                Timber.i("$pathToContinueBrowsing is a audio ")
                                val song =
                                    getSongFromPath(hostURL, fileId, pathToContinueBrowsing)
                                song.let {
                                    songList.add(it)
                                    songMap[pathToContinueBrowsing.hashCode()] = it
                                }
                            } else if (pathToContinueBrowsing.isImageFast()) {
                                Timber.i("$pathToContinueBrowsing is a image ")
                                val image =
                                    getImageFromPath(hostURL, fileId, pathToContinueBrowsing)
                                imageList.add(image)
                                imageMap[pathToContinueBrowsing.hashCode()] = image
                            } else if (pathToContinueBrowsing.isVideoFast()) {
                                Timber.i("$pathToContinueBrowsing is a video ")
                                val video =
                                    getVideoFromPath(hostURL, fileId, pathToContinueBrowsing)
                                videoList.add(video)
                                videoMap[pathToContinueBrowsing.hashCode()] = video
                            }

                            numberFileCount.increase()
                            TreeNode.createTree(listOf(pathToContinueBrowsing), treeNode)

                            if (numberFileCount.value % STEP_SONG_TO_UPDATE == STEP_SONG_TO_UPDATE - 1) {
                                musicProvider.notifyDataChanged()
                            }
                        } else {
                            Timber.i("$pathToContinueBrowsing is NOT audio/image/video")
                        }
                    }
                }

        } catch (e: SMBException) {
            Timber.e("Failed to list files in folder: ${share.smbPath}, error: ${e.message}")
            handleConnectionIssue(e)
        } catch (e: IOException) {
            Timber.e("Network error: ${e.message}")
            handleNetworkIssue(e)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }

    private fun handleNetworkIssue(ioException: IOException) {

    }

    private fun handleConnectionIssue(smbException: SMBException) {
    }

    private fun getSongFromPath(
        hostIp: String,
        fileId: FileIdBothDirectoryInformation,
        filePath: String
    ): Song {
        val context = App.app
        val filePathCorrected = filePath.replace("\\", "/")
        val path = "$hostIp/$filePathCorrected"
        Timber.i("=============getSongFrom ServerPath: $path==============")

        var title = context.getTitle(path)
        if (title.isNullOrBlank()) {
            title = fileId.fileName.toString()
        }
        val artist = context.getArtist(path)
        val duration = context.getDuration(path)
        val album = context.getAlbum(path)

        val song = Song(
            title = title,
            artist = artist ?: "",
            album = album ?: "",
            path = filePath,
            duration = duration ?: 0,
            folderName = filePath.getParentPath(),
        )
        return song
    }

    private fun getVideoFromPath(
        hostIp: String,
        fileId: FileIdBothDirectoryInformation,
        filePath: String
    ): Video {
        val context = App.app
        val filePathCorrected = filePath.replace("\\", "/")
        val path = "$hostIp/$filePathCorrected"
        Timber.i("=============getVideoFrom ServerPath: $path==============")

        val size = context.getSizeFromContentUri(path.toUri())
        var title = context.getTitleForVideo(path)
        if (title.isNullOrBlank()) {
            title = fileId.fileName.toString()
        }
        val artist = context.getArtistVideo(path)
        val duration = context.getDuration(path)

        return Video(
            title = title,
            artist = artist ?: "",
            path = filePath.getParentPath() + "\\${fileId.fileName}",
            duration = duration ?: 0,
            folderName = filePath.getParentPath(),
            dateAdded = fileId.lastWriteTime.windowsTimeStamp,
            size = fileId.allocationSize,
        )
    }

    private fun getImageFromPath(
        hostIp: String,
        fileId: FileIdBothDirectoryInformation,
        filePath: String
    ): Image {
        val context = App.app
        val filePathCorrected = filePath.replace("\\", "/")
        val path = "$hostIp/$filePathCorrected"
        Timber.i("=============getImageFrom ServerPath: $path==============")

        var title = context.getTitleForImage(path)
        if (title.isNullOrBlank()) {
            title = fileId.fileName.toString()
        }

        return Image(
            title = title,
            path = filePath.getParentPath() + "\\${fileId.fileName}",
            folderName = filePath.getParentPath(),
            dateAdded = fileId.lastWriteTime.windowsTimeStamp,
            usbId = hostIp,
            size = fileId.allocationSize,
            height = 1,
            width = 1
        )
    }

    fun scanAllMusicFromUsb(
        rootPath: String,
        pathsToIgnore: List<String>,
        songList: ArrayList<Song>,
        songMap: HashMap<Int, Song>,
        imageList: ArrayList<Image>,
        imageMap: HashMap<Int, Image>,
        videoList: ArrayList<Video>,
        videoMap: HashMap<Int, Video>,
        treeNode: TreeNode,
    ) {
        Timber.e("===========START SCAN ALL MEDIA FILES FROM USB ID: $rootPath============")

        if (rootPath.isEmpty()) {
            Timber.i("rootPath is empty")
            return
        }

        val rootFile = File(rootPath)
        val numberFileCount = IntNumber(0)
        findAudioFiles(
            rootFile,
            pathsToIgnore,
            numberFileCount,
            songList,
            songMap,
            imageList,
            imageMap,
            videoList,
            videoMap,
            treeNode
        )
        musicProvider.notifyDataChanged()
        if (songList.isEmpty()) {
            Timber.e("Have no song in usb: $rootPath")
        }
        Timber.e("=============FINISH SCAN ALL MEDIA FILES FROM USB ID: $rootPath - size of list is ${songList.size}============")
        DBHelper.updateAllDatabase(songList)
    }

    private fun findAudioFiles(
        file: File,
        excludedPaths: List<String>,
        numberFileCount: IntNumber,
        songList: ArrayList<Song>,
        songMap: HashMap<Int, Song>,
        imageList: ArrayList<Image>,
        imageMap: HashMap<Int, Image>,
        videoList: ArrayList<Video>,
        videoMap: HashMap<Int, Video>,
        treeNode: TreeNode,
    ) {

        Timber.i("scanning file with absolutePath: ${file.absolutePath} path: ${file.path}")
        if (file.isHidden) {
            Timber.i("file is hidden")
            return
        }

        val path = file.absolutePath
        if (path in excludedPaths || path.getParentPath() in excludedPaths) {
            Timber.i("path in excludedPaths: $path")
            return
        }

        if (file.isFile) {
            if (path.isAudioFast() || path.isImageFast() || path.isVideoFast()) {
                if (path.isAudioFast()) {
                    Timber.i("$path is a audio ")
                    val song = getSongFromPath(path)
                    song?.let {
                        songList.add(it)
                        songMap[path.hashCode()] = it
                    }
                } else if (path.isImageFast()) {
                    Timber.i("$path is a image ")
                    val image = getImageFromPath(path)
                    image?.let {
                        imageList.add(it)
                        imageMap[path.hashCode()] = it
                    }
                } else if (path.isVideoFast()) {
                    Timber.i("$path is a video ")
                    val video = getVideoFromPath(path)
                    video?.let {
                        videoList.add(it)
                        videoMap[path.hashCode()] = it
                    }
                }

                numberFileCount.increase()
                TreeNode.createTree(listOf(path), treeNode)

//                if (numberFileCount.value % STEP_SONG_TO_UPDATE == STEP_SONG_TO_UPDATE - 1) {
//                    musicProvider.notifyDataChanged()
//                }
            } else {
                Timber.i("$path is NOT audio/image/video")
            }
        } else if (!file.containsNoMedia()) {
            file.listFiles().orEmpty().forEach { child ->
                findAudioFiles(
                    child,
                    excludedPaths,
                    numberFileCount,
                    songList,
                    songMap,
                    imageList,
                    imageMap,
                    videoList,
                    videoMap,
                    treeNode
                )
            }
        }
    }

    private fun getSongFromPath(path: String): Song? {
        val context = App.app
        val mediaStoreID = context.getMediaStoreIdFromPath(path)
        val title = path.getFilenameFromPath()

        val artist = context.getArtist(path)
        val duration = context.getDuration(path)
        val folderName = path.getParentPath().getFilenameFromPath()
        val album = context.getAlbum(path)
        val year = context.getYear(path)
        val lastModified = context.getMediaStoreLastModified(path)

        val usbID = path.getUsbID()

        val pair = context.getAlbumIdAndArtistIdFromPath(path)
        val albumId = pair.first
        val artistId = pair.second

        val song = Song(
            id = 0,
            mediaStoreId = mediaStoreID,
            title = title,
            artist = artist ?: "",
            path = path,
            duration = duration ?: 0,
            album = album ?: "",
            coverArt = "",
            folderName = folderName,
            albumId = albumId,
            artistId = artistId,
            year = year,
            dateAdded = lastModified,
            usbId = usbID
        )
        Timber.i("song from path $path is $song")
        return song
    }

    private fun getImageFromPath(path: String): Image? {
//        val retriever = MediaMetadataRetriever()
//        var inputStream: FileInputStream? = null

//        try {
//            retriever.setDataSource(path)
//        } catch (ignored: Exception) {
//            try {
//                inputStream = FileInputStream(path)
//                retriever.setDataSource(inputStream.fd)
//            } catch (ignored: Exception) {
//                retriever.release()
//                inputStream?.close()
//                return null
//            }
//        }
        val context = App.app
//        val mediaStoreID = context.getMediaStoreIdFromPath(path)
//        val title = context.getTitleForImage(path)
//        val artist = context.getArtist(path)
//        val folderName = path.getParentPath().getFilenameFromPath()
//        val album = context.getAlbum(path)
//        val year =
//            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
//                ?: 0
//        val lastModified = context.getMediaStoreLastModified(path)
//        val displayName = context.getTitle(path)
        val size = context.getSizeFromContentUri(path.toUri())
//        val description = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
//        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_HEIGHT)?.toLong() ?: 0
//        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_IMAGE_WIDTH)?.toLong() ?: 0

        val usbID = path.getUsbID()

//        val pair = context.getAlbumIdAndArtistIdFromPath(path)
//        val albumId = pair.first
//        val artistId = pair.second
        val dateTaken = getImageDateTaken(path) ?: ""
        val image = Image(
            id = 0,
            mediaStoreId = 0,
            title = path.getFilenameFromPath(),
            artist = "",
            path = path,
            album = "",
            folderName = "",
            albumId = 0,
            artistId = 0,
            year = 1,
            dateAdded = 0,
            usbId = usbID,
            dateTaken = dateTaken,
            description = "",
            displayName = "",
            size = size,
            height = 1,
            width = 1
        )
//        try {
//            inputStream?.close()
//            retriever.release()
//        } catch (ignored: Exception) {
//        }
        return image
    }

    private fun getVideoFromPath(path: String): Video? {
        val context = App.app
        val size = context.getSizeFromContentUri(path.toUri())
        val mediaStoreID = context.getMediaStoreIdFromPath(path)
        val title = path.getFilenameFromPath()
        val artist = context.getArtistVideo(path)
        val duration = context.getDuration(path)
        val folderName = path.getParentPath().getFilenameFromPath()
        val album = context.getAlbumForVideo(path)
        val lastModified = context.getMediaStoreLastModified(path)

        val usbID = path.getUsbID()

        val pair = context.getAlbumIdAndArtistIdFromPath(path)
        val albumId = pair.first
        val artistId = pair.second

        val video = Video(
            id = 0,
            mediaStoreId = mediaStoreID,
            title = title ?: "",
            artist = artist ?: "",
            path = path,
            duration = duration ?: 0,
            album = album ?: "",
            coverArt = "",
            folderName = folderName,
            albumId = albumId,
            artistId = artistId,
            year = 0,
            dateAdded = lastModified,
            usbId = usbID,
            description = "",
            size = size,
            subtitle = ""
        )
        return video
    }


    fun scanAllMusicFromUsbNotMetaData(
        rootPath: String,
        pathsToIgnore: List<String>,
        songList: ArrayList<Song>,
        songMap: HashMap<Int, Song>,
        imageList: ArrayList<Image>,
        imageMap: HashMap<Int, Image>,
        videoList: ArrayList<Video>,
        videoMap: HashMap<Int, Video>,
        treeNode: TreeNode,
    ) {
        Timber.e("===========START SCAN ALL MEDIA FILES NOT METADATA FROM USB ID: $rootPath============")

        if (rootPath.isEmpty()) {
            Timber.i("rootPath is empty")
            return
        }

        val rootFile = File(rootPath)
        val numberFileCount = IntNumber(0)
        findAudioFilesNotMetaData(
            rootFile,
            pathsToIgnore,
            numberFileCount,
            songList,
            songMap,
            imageList,
            imageMap,
            videoList,
            videoMap,
            treeNode
        )
        musicProvider.notifyDataChanged()

        if (songList.isEmpty()) {
            Timber.e("Have no song in usb: $rootPath")
        }
        Timber.e("=============FINISH SCAN ALL MEDIA FILES NOT METADATA FROM USB ID: $rootPath - size of list is ${songList.size}============")
    }

    private fun findAudioFilesNotMetaData(
        file: File,
        excludedPaths: List<String>,
        numberFileCount: IntNumber,
        songList: ArrayList<Song>,
        songMap: HashMap<Int, Song>,
        imageList: ArrayList<Image>,
        imageMap: HashMap<Int, Image>,
        videoList: ArrayList<Video>,
        videoMap: HashMap<Int, Video>,
        treeNode: TreeNode,
    ) {

        Timber.i("scanning file with absolutePath: ${file.absolutePath} path: ${file.path}")
        if (file.isHidden) {
            Timber.i("file is hidden")
            return
        }

        val path = file.absolutePath
        if (path in excludedPaths || path.getParentPath() in excludedPaths) {
            Timber.i("path in excludedPaths: $path")
            return
        }

        if (file.isFile) {
            if (path.isAudioFast() || path.isImageFast() || path.isVideoFast()) {
                if (path.isAudioFast()) {
                    Timber.i("$path is a audio ")
                    val song = getSongNotMetaDataFromPath(path)
                    songList.add(song)
                    songMap[path.hashCode()] = song

                } else if (path.isImageFast()) {
                    Timber.i("$path is a image ")
                    val image = getImageNotMetaDataFromPath(path)
                    imageList.add(image)
                    imageMap[path.hashCode()] = image

                } else if (path.isVideoFast()) {
                    Timber.i("$path is a video ")
                    val video = getVideoNotMetaDataFromPath(path)
                    videoList.add(video)
                    videoMap[path.hashCode()] = video
                }
                numberFileCount.increase()
                TreeNode.createTree(listOf(path), treeNode)
//                musicProvider.notifyDataChanged()

            } else {
                Timber.i("$path is NOT audio/image/video")
            }
        } else if (!file.containsNoMedia()) {
            file.listFiles().orEmpty().forEach { child ->
                findAudioFilesNotMetaData(
                    child,
                    excludedPaths,
                    numberFileCount,
                    songList,
                    songMap,
                    imageList,
                    imageMap,
                    videoList,
                    videoMap,
                    treeNode
                )
            }
        }
    }

    private fun getSongNotMetaDataFromPath(path: String): Song {
        val title = path.getFilenameFromPath()
        val song = Song(
            id = 0,
            title = title,
            path = path,
        )
        Timber.i("song from path $path is $song")
        return song
    }

    private fun getVideoNotMetaDataFromPath(path: String): Video {
        val title = path.getFilenameFromPath()

        val video = Video(
            id = 0,
            title = title,
            path = path
        )
        return video
    }

    private fun getImageNotMetaDataFromPath(path: String): Image {
        val title = path.getFilenameFromPath()
        val image = Image(
            id = 0,
            title = title,
            path = path
        )
        return image
    }

    fun scanAllMusicFromMediaStore(): ArrayList<FileDirItem> {
        val context = App.app
        val fileDirItems = ArrayList<FileDirItem>()
        val uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns.DATA,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.DATE_MODIFIED
        )

        try {
            val queryArgs = bundleOf(
                ContentResolver.QUERY_ARG_SORT_COLUMNS to arrayOf(MediaStore.Files.FileColumns.DATE_MODIFIED),
                ContentResolver.QUERY_ARG_SORT_DIRECTION to ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
            context.contentResolver?.query(uri, projection, queryArgs, null)
                .use { cursor ->
                    if (cursor != null) {
                        if (cursor.moveToFirst()) {
                            do {
                                try {
                                    val name =
                                        cursor.getString(MediaStore.Files.FileColumns.DISPLAY_NAME)

                                    val mimeType =
                                        cursor.getString(MediaStore.Files.FileColumns.MIME_TYPE)
                                            .lowercase(Locale.getDefault())

                                    val size = cursor.getLong(MediaStore.Files.FileColumns.SIZE)
                                    if (size == 0L) {
                                        continue
                                    }

                                    val path =
                                        cursor.getString(MediaStore.Files.FileColumns.DATA)
                                    val lastModified =
                                        cursor.getLong(MediaStore.Files.FileColumns.DATE_MODIFIED) * 1000

                                    if (mimeType.substringBefore("/") == "audio") {
                                        val tempFileDirItem = FileDirItem(
                                            path,
                                            name,
                                            false,
                                            0,
                                            size,
                                            lastModified
                                        )
                                        fileDirItems.add(tempFileDirItem)
                                    }
                                } catch (e: Exception) {
                                    Timber.e(e)
                                }
                            } while (cursor.moveToNext())
                        }
                    }
                }
        } catch (e: Exception) {
            Timber.e(e)
        }
        Timber.d("Finish getAllMusicFilePath : size of list is ${fileDirItems.size}")
        return fileDirItems
    }

    fun getListItemsFromFileDirItems(fileDirItems: ArrayList<FileDirItem>): ArrayList<ListItem> {
        val listItems = ArrayList<ListItem>()
        fileDirItems.forEach {
            val listItem =
                ListItem(it.path, it.name, false, 0, it.size, it.modified, false, false)
            listItems.add(listItem)
        }
        return listItems
    }

    fun getFiles(
        path: String,
        callback: ((originalPath: String, listItems: ArrayList<ListItem>) -> Unit)? = null,
    ) {
        Timber.e("getFiles: $path")
        getFullLines(path) {
            val fullLines = it

            val files = ArrayList<ListItem>()
            val cmd = "ls $path"

            val command = object : Command(0, cmd) {
                override fun commandOutput(id: Int, line: String) {
                    val file = File(path, line)
                    val fullLine = fullLines.firstOrNull { it.endsWith(" $line") }
                    val isDirectory = fullLine?.startsWith('d') ?: file.isDirectory
                    val fileDirItem =
                        ListItem(file.absolutePath, line, isDirectory, 0, 0, 0, false, false)
                    files.add(fileDirItem)
                    super.commandOutput(id, line)
                }

                override fun commandCompleted(id: Int, exitcode: Int) {
                    if (files.isEmpty()) {
                        if (callback != null) {
                            callback(path, files)
                        }
                    } else {
                        getChildrenCount(files, path, callback)
                    }

                    super.commandCompleted(id, exitcode)
                }
            }

            runCommand(command)
        }
    }

    private fun getFullLines(path: String, callback: (ArrayList<String>) -> Unit) {
        Timber.i("path: $path")
        val fullLines = ArrayList<String>()
        val cmd = "ls -l $path"

        val command = object : Command(0, cmd) {
            override fun commandOutput(id: Int, line: String) {
                fullLines.add(line)
                super.commandOutput(id, line)
            }

            override fun commandCompleted(id: Int, exitcode: Int) {
                callback(fullLines)
                super.commandCompleted(id, exitcode)
            }
        }

        runCommand(command)
    }

    private fun getChildrenCount(
        files: ArrayList<ListItem>,
        path: String,
        callback: ((originalPath: String, listItems: ArrayList<ListItem>) -> Unit)? = null,
    ) {
        var cmd = ""
        files.filter { it.isDirectory }.forEach {
            cmd += "ls ${it.path} |wc -l;"
        }
        cmd = cmd.trimEnd(';') + " | cat"

        val lines = ArrayList<String>()
        val command = object : Command(0, cmd) {
            override fun commandOutput(id: Int, line: String) {
                lines.add(line)
                super.commandOutput(id, line)
            }

            override fun commandCompleted(id: Int, exitcode: Int) {
                files.filter { it.isDirectory }.forEachIndexed { index, fileDirItem ->
                    val childrenCount = lines[index]
                    if (childrenCount.areDigitsOnly()) {
                        fileDirItem.children = childrenCount.toInt()
                    }
                }

                if (callback != null) {
                    getFileSizes(files, path, callback)
                }
                super.commandCompleted(id, exitcode)
            }
        }

        runCommand(command)
    }

    private fun getFileSizes(
        files: ArrayList<ListItem>,
        path: String,
        callback: ((originalPath: String, listItems: ArrayList<ListItem>) -> Unit)? = null,
    ) {
        var cmd = ""
        files.filter { !it.isDirectory }.forEach {
            cmd += "stat -t ${it.path};"
        }

        val lines = ArrayList<String>()
        val command = object : Command(0, cmd) {
            override fun commandOutput(id: Int, line: String) {
                lines.add(line)
                super.commandOutput(id, line)
            }

            override fun commandCompleted(id: Int, exitcode: Int) {
                files.filter { !it.isDirectory }.forEachIndexed { index, fileDirItem ->
                    var line = lines[index]
                    if (line.isNotEmpty() && line != "0") {
                        if (line.length >= fileDirItem.path.length) {
                            line = line.substring(fileDirItem.path.length).trim()
                            val size = line.split(" ")[0]
                            if (size.areDigitsOnly()) {
                                fileDirItem.size = size.toLong()
                            }
                        }
                    }
                }

                if (callback != null) {
                    callback(path, files)
                }
                super.commandCompleted(id, exitcode)
            }
        }

        runCommand(command)
    }

    private fun runCommand(command: Command) {
        Timber.i("command: ${command.command}")
        try {
            RootTools.getShell(true).add(command)
        } catch (e: Exception) {
            Timber.e(e)
        }
    }
}