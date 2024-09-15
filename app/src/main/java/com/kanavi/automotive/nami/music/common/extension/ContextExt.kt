package com.kanavi.automotive.nami.music.common.extension

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Point
import android.media.ExifInterface
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.Looper
import android.provider.BaseColumns
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.TextUtils
import com.kanavi.automotive.nami.music.common.constant.Constants.SD_OTG_PATTERN
import com.kanavi.automotive.nami.music.common.constant.Constants.SD_OTG_SHORT
import timber.log.Timber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Collections
import java.util.HashSet
import java.util.Locale
import java.util.regex.Pattern


val Context.sdCardPath: String get() = getSDCardPath()

fun Context.isPathOnSD(path: String) = sdCardPath.isNotEmpty() && path.startsWith(sdCardPath)

fun isOnMainThread() = Looper.myLooper() == Looper.getMainLooper()

fun ensureBackgroundThread(callback: () -> Unit) {
    if (isOnMainThread()) {
        Thread {
            callback()
        }.start()
    } else {
        callback()
    }
}


fun Context.getMediaStoreIdFromPath(path: String): Long {
    var id = 0L
    val projection = arrayOf(
        MediaStore.Audio.Media._ID
    )

    val uri = getFileUri(path)
    val selection = "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs = arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                id = cursor.getLong(MediaStore.Audio.Media._ID)
            }
        }
    } catch (ignored: Exception) {
    }

    return id
}

fun Context.getAlbumIdAndArtistIdFromPath(path: String): Pair<Long, Long> {
    var albumId = 0L
    var artistId = 0L
//    val projection = arrayOf(
//        MediaStore.Audio.Media._ID,
//        MediaStore.Audio.AudioColumns.ALBUM_ID,
//        MediaStore.Audio.AudioColumns.ALBUM,
//        MediaStore.Audio.AudioColumns.ARTIST_ID,
//    )
//
//    val uri = getFileUri(path)
//    val selection = "${MediaStore.MediaColumns.DATA} = ?"
//    val selectionArgs = arrayOf(path)
//
//    try {
//        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
//        cursor?.use {
//            if (cursor.moveToFirst()) {
//                albumId = cursor.getLong(MediaStore.Audio.AudioColumns.ALBUM_ID)
//                artistId = cursor.getLong(MediaStore.Audio.AudioColumns.ARTIST_ID)
//            }
//        }
//    } catch (ignored: Exception) {
//    }

    return Pair(albumId, artistId)
}

fun Context.getDuration(path: String): Long? {
    val projection = arrayOf(
        MediaStore.MediaColumns.DURATION
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return (cursor.getInt(MediaStore.MediaColumns.DURATION).toLong())
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}

fun Context.getTitle(path: String): String? {
    val projection = arrayOf(
        MediaStore.MediaColumns.TITLE
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(path)

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.MediaColumns.TITLE)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}

fun Context.getTitleForImage(path: String): String? {
    val projection = arrayOf(
        MediaStore.Images.Media.TITLE
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.Images.ImageColumns.TITLE} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.Images.Media.TITLE)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }

}

fun Context.getArtist(path: String): String? {
    val projection = arrayOf(
        MediaStore.Audio.Media.ARTIST
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.Audio.Media.ARTIST)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}

fun Context.getAlbum(path: String): String? {
    val projection = arrayOf(
        MediaStore.Audio.Media.ALBUM
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.Audio.Media.ALBUM)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}

fun Context.getMediaStoreLastModified(path: String): Long {
    val projection = arrayOf(
        MediaStore.MediaColumns.DATE_MODIFIED
    )

    val uri = getFileUri(path)
    val selection = "${BaseColumns._ID} = ?"
    val selectionArgs = arrayOf(path.substringAfterLast("/"))

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getLong(MediaStore.MediaColumns.DATE_MODIFIED) * 1000
            }
        }
    } catch (ignored: Exception) {
    }
    return 0
}

fun Context.getYear(path: String): Int {
    val projection = arrayOf(
        MediaStore.Audio.Media.YEAR
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getInt(MediaStore.Audio.Media.YEAR)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.toIntOrNull()
            ?: 0
    } catch (ignored: Exception) {
        retriever.release()
        0
    } finally {
        retriever.release()
    }
}

fun getImageDateTaken(imagePath: String): String? {
    return try {
        val exif = ExifInterface(File(imagePath))
        val dateTaken = exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
        val originalFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        if (dateTaken != null) {
            originalFormat.format(dateTaken)
        } else {
            val file = File(imagePath)
            originalFormat.format(file.lastModified())
        }
    } catch (e: Exception) {
        e.printStackTrace()
        "no date taken"
    }
}

fun Context.getImageResolution(path: String): Point? {
    val options = BitmapFactory.Options()
    options.inJustDecodeBounds = true

    BitmapFactory.decodeFile(path, options)


    val width = options.outWidth
    val height = options.outHeight
    return if (width > 0 && height > 0) {
        Point(options.outWidth, options.outHeight)
    } else {
        null
    }
}

fun Context.getDoesFilePathExist(path: String): Boolean {
    return File(path).exists()
}

fun Context.getIsPathDirectory(path: String): Boolean {
    return File(path).isDirectory
}

fun Context.getSizeFromContentUri(uri: Uri): Long {
    val projection = arrayOf(OpenableColumns.SIZE)
    try {
        val cursor = contentResolver.query(uri, projection, null, null, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getLong(OpenableColumns.SIZE)
            }
        }
    } catch (e: Exception) {
    }
    return 0L
}


fun Context.getFileUri(path: String) = when {
    path.isImageSlow() -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    path.isAudioSlow() -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
    path.isVideoSlow() -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    else -> MediaStore.Files.getContentUri("external")
}


// http://stackoverflow.com/a/40582634/1967672
fun Context.getSDCardPath(): String {
    val directories = getStorageDirectories().filter {
        it != getInternalStoragePath() && !it.equals(
            "/storage/emulated/0",
            true
        )
    }

    val fullSDpattern = Pattern.compile(SD_OTG_PATTERN)
    var sdCardPath = directories.firstOrNull { fullSDpattern.matcher(it).matches() } ?: ""

    // on some devices no method retrieved any SD card path, so test if its not sdcard1 by any chance. It happened on an Android 5.1
    if (sdCardPath.trimEnd('/').isEmpty()) {
        val file = File("/storage/sdcard1")
        if (file.exists()) {
            return file.absolutePath
        }

        sdCardPath = directories.firstOrNull() ?: ""
    }

    if (sdCardPath.isEmpty()) {
        val SDpattern = Pattern.compile(SD_OTG_SHORT)
        try {
            File("/storage").listFiles()?.forEach {
                if (SDpattern.matcher(it.name).matches()) {
                    sdCardPath = "/storage/${it.name}"
                }
            }
        } catch (e: Exception) {
        }
    }

    return sdCardPath.trimEnd('/')
}


fun Context.getInternalStoragePath() =
    if (File("/storage/emulated/0").exists()) "/storage/emulated/0" else Environment.getExternalStorageDirectory().absolutePath.trimEnd(
        '/'
    )

fun Context.getStorageDirectories(): Array<String> {
    val paths = HashSet<String>()
    val rawExternalStorage = System.getenv("EXTERNAL_STORAGE")
    val rawSecondaryStoragesStr = System.getenv("SECONDARY_STORAGE")
    val rawEmulatedStorageTarget = System.getenv("EMULATED_STORAGE_TARGET")
    if (TextUtils.isEmpty(rawEmulatedStorageTarget)) {
        getExternalFilesDirs(null).filterNotNull().map { it.absolutePath }
            .mapTo(paths) { it.substring(0, it.indexOf("Android/data")) }
    } else {
        val path = Environment.getExternalStorageDirectory().absolutePath
        val folders = Pattern.compile("/").split(path)
        val lastFolder = folders[folders.size - 1]
        var isDigit = false
        try {
            Integer.valueOf(lastFolder)
            isDigit = true
        } catch (ignored: NumberFormatException) {
        }

        val rawUserId = if (isDigit) lastFolder else ""
        if (TextUtils.isEmpty(rawUserId)) {
            paths.add(rawEmulatedStorageTarget!!)
        } else {
            if (rawEmulatedStorageTarget != null) {
                paths.add(rawEmulatedStorageTarget + File.separator + rawUserId)
            }
        }
    }

    if (!TextUtils.isEmpty(rawSecondaryStoragesStr)) {
        val rawSecondaryStorages = rawSecondaryStoragesStr!!.split(File.pathSeparator.toRegex())
            .dropLastWhile(String::isEmpty).toTypedArray()
        Collections.addAll(paths, *rawSecondaryStorages)
    }
    return paths.map { it.trimEnd('/') }.toTypedArray()
}

fun Context.getTitleForVideo(path: String): String? {
    val projection = arrayOf(
        MediaStore.Video.Media.TITLE
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.Video.VideoColumns.TITLE} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.Video.Media.TITLE)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}

fun Context.getArtistVideo(path: String): String? {
    val projection = arrayOf(
        MediaStore.Video.Media.ARTIST
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.Video.Media.ARTIST)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}

fun Context.getAlbumForVideo(path: String): String? {
    val projection = arrayOf(
        MediaStore.Video.Media.ALBUM
    )

    val uri = getFileUri(path)
    val selection =
        if (path.startsWith("content://")) "${BaseColumns._ID} = ?" else "${MediaStore.MediaColumns.DATA} = ?"
    val selectionArgs =
        if (path.startsWith("content://")) arrayOf(path.substringAfterLast("/")) else arrayOf(
            path
        )

    try {
        val cursor = contentResolver.query(uri, projection, selection, selectionArgs, null)
        cursor?.use {
            if (cursor.moveToFirst()) {
                return cursor.getString(MediaStore.Video.Media.ALBUM)
            }
        }
    } catch (ignored: Exception) {
    }

    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(path)
        retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
    } catch (ignored: Exception) {
        retriever.release()
        null
    } finally {
        retriever.release()
    }
}


