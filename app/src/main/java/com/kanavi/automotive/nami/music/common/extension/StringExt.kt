package com.kanavi.automotive.nami.music.common.extension

import android.content.Context
import android.provider.MediaStore
import com.kanavi.automotive.nami.music.common.constant.Constants.isFrontDisplay
import java.text.Normalizer

val audioExtensions: Array<String>
    get() = arrayOf(
        ".mp3",
        ".wav",
        ".wma",
        ".ogg",
        ".m4a",
        ".opus",
        ".flac",
        ".aac",
        ".m4b",
        ".amr"
    )
val photoExtensions: Array<String>
    get() = arrayOf(
        ".jpg",
        ".png",
        ".jpeg",
        ".bmp",
        ".webp",
        ".heic",
        ".heif",
        ".apng",
        ".avif",
        ".gif"
    )
val videoExtensions: Array<String>
    get() = arrayOf(
        ".mp4",
        ".mkv",
        ".webm",
        ".avi",
        ".3gp",
        ".mov",
        ".m4v",
        ".m2v",
        ".3gpp",
        ".mts",
        ".m2ts",
    )


val normalizeRegex = "\\p{InCombiningDiacriticalMarks}+".toRegex()

fun String.areDigitsOnly() = matches(Regex("[0-9]+"))


// remove diacritics, for example č -> c
fun String.normalizeString() =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(normalizeRegex, "")


fun String.isMediaFile() = isAudioFast()

// fast extension checks, not guaranteed to be accurate
fun String.isVideoFast() = videoExtensions.any { endsWith(it, true) }
fun String.isImageFast() = photoExtensions.any { endsWith(it, true) }
fun String.isAudioFast() = audioExtensions.any { endsWith(it, true) }

fun String.isVideoSlow() = isVideoFast() || getMimeType().startsWith("video") || startsWith(
    MediaStore.Video.Media.EXTERNAL_CONTENT_URI.toString()
)

fun String.isImageSlow() = isImageFast() || getMimeType().startsWith("image") || startsWith(
    MediaStore.Images.Media.EXTERNAL_CONTENT_URI.toString()
)

fun String.isAudioSlow() = isAudioFast() || getMimeType().startsWith("audio") || startsWith(
    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI.toString()
)


fun String.getFilenameFromPath() =
    if (isFrontDisplay())
        substring(lastIndexOf("/") + 1)
    else
        substring(lastIndexOf("\\") + 1)

fun String.getFilenameExtension() = substring(lastIndexOf(".") + 1)

fun String.getUsbID(): String {
    val pathSegments = split("/")
    return if (pathSegments.size >= 4)
        pathSegments[3]
    else this
}

fun String.getBasePath(context: Context): String {
    return when {
        context.isPathOnSD(this) -> context.sdCardPath
        else -> "/"
    }
}

fun String.getFirstParentDirName(context: Context, level: Int): String? {
    val basePath = getBasePath(context)
    val startIndex = basePath.length + 1
    return if (length > startIndex) {
        val pathWithoutBasePath = substring(startIndex)
        val pathSegments = pathWithoutBasePath.split("/")
        if (level < pathSegments.size) {
            pathSegments.slice(0..level).joinToString("/")
        } else {
            null
        }
    } else {
        null
    }
}

fun String.getFirstParentPath(context: Context, level: Int): String {
    val basePath = getBasePath(context)
    val startIndex = basePath.length + 1
    return if (length > startIndex) {
        val pathWithoutBasePath = substring(basePath.length + 1)
        val pathSegments = pathWithoutBasePath.split("/")
        val firstParentPath = if (level < pathSegments.size) {
            pathSegments.slice(0..level).joinToString("/")
        } else {
            pathWithoutBasePath
        }
        "$basePath/$firstParentPath"
    } else {
        basePath
    }
}

fun String.isAValidFilename(): Boolean {
    val ILLEGAL_CHARACTERS =
        charArrayOf('/', '\n', '\r', '\t', '\u0000', '`', '?', '*', '\\', '<', '>', '|', '\"', ':')
    ILLEGAL_CHARACTERS.forEach {
        if (contains(it))
            return false
    }
    return true
}


fun String.getParentPath() =
    if (isFrontDisplay()) removeSuffix("/${getFilenameFromPath()}") else removeSuffix("\\${getFilenameFromPath()}")

fun String.getMimeType(): String {
    val typesMap = HashMap<String, String>().apply {
        // audio
        put("aa", "audio/audible")
        put("aac", "audio/aac")
        put("amr", "audio/AMR")
        put("aax", "audio/vnd.audible.aax")
        put("ac3", "audio/ac3")
        put("adt", "audio/vnd.dlna.adts")
        put("adts", "audio/aac")
        put("aif", "audio/aiff")
        put("aifc", "audio/aiff")
        put("aiff", "audio/aiff")
        put("au", "audio/basic")
        put("axa", "audio/annodex")
        put("caf", "audio/x-caf")
        put("flac", "audio/flac")
        put("m3u", "audio/x-mpegurl")
        put("m3u8", "audio/x-mpegurl")
        put("m4a", "audio/m4a")
        put("m4b", "audio/m4b")
        put("m4p", "audio/m4p")
        put("m4r", "audio/x-m4r")
        put("gsm", "audio/x-gsm")
        put("cdda", "audio/aiff")
        put("mid", "audio/mid")
        put("midi", "audio/mid")
        put("mp3", "audio/mpeg")
        put("oga", "audio/ogg")
        put("ogg", "audio/ogg")
        put("wma", "audio/x-ms-wma")
        put("rmi", "audio/mid")
        put("rpm", "audio/x-pn-realaudio-plugin")
        put("sd2", "audio/x-sd2")
        put("smd", "audio/x-smd")
        put("smi", "application/octet-stream")
        put("smx", "audio/x-smd")
        put("smz", "audio/x-smd")
        put("snd", "audio/basic")
        put("spx", "audio/ogg")
        put("opus", "audio/ogg")
        put("wav", "audio/wav")
        put("wave", "audio/wav")
        put("wax", "audio/x-ms-wax")
        // image
        put("cmx", "image/x-cmx")
        put("cod", "image/cis-cod")
        put("dib", "image/bmp")
        put("art", "image/x-jg")
        put("dng", "image/x-adobe-dng")
        put("bmp", "image/bmp")
        put("gif", "image/gif")
        put("ico", "image/x-icon")
        put("ief", "image/ief")
        put("jfif", "image/pjpeg")
        put("jpe", "image/jpeg")
        put("jpeg", "image/jpeg")
        put("jpg", "image/jpeg")
        put("mac", "image/x-macpaint")
        put("pbm", "image/x-portable-bitmap")
        put("pct", "image/pict")
        put("pgm", "image/x-portable-graymap")
        put("pic", "image/pict")
        put("pict", "image/pict")
        put("pls", "audio/scpls")
        put("png", "image/png")
        put("pnm", "image/x-portable-anymap")
        put("pnt", "image/x-macpaint")
        put("pntg", "image/x-macpaint")
        put("pnz", "image/png")
        put("ppm", "image/x-portable-pixmap")
        put("qti", "image/x-quicktime")
        put("qtif", "image/x-quicktime")
        put("ra", "audio/x-pn-realaudio")
        put("ram", "audio/x-pn-realaudio")
        put("ras", "image/x-cmu-raster")
        put("rf", "image/vnd.rn-realflash")
        put("rgb", "image/x-rgb")
        put("svg", "image/svg+xml")
        put("tif", "image/tiff")
        put("tiff", "image/tiff")
        put("wbmp", "image/vnd.wap.wbmp")
        put("wdp", "image/vnd.ms-photo")
        put("webp", "image/webp")
        put("xbm", "image/x-xbitmap")
        put("xpm", "image/x-xpixmap")
        put("xwd", "image/x-xwindowdump")
        // video
        put("flv", "video/x-flv")
        put("mp4", "video/h263")
        put("mp4", "video/h264")
        put("mp4", "video/h265")
        put("flv", "video/x-flv")
        put("mp4", "video/mp4")
        put("avc1.4d002a", "video/mp4")
        put("m3u8", "application/x-mpegURL")
        put("ts", "video/MP2T")
        put("mts", "video/MP2T")
        put("m2ts", "video/MP2T")
        put("m2t", "video/MP2T")
        put("mpg", "video/mpeg")
        put("m2v", "video/x-m2v")
        put("vp8", "video/webm")
        put("vp9", "video/webm")
        put("3gp", "video/3gpp")
        put("mov", "video/quicktime")
        put("avi", "video/x-msvideo")
        put("wmv", "video/x-ms-wmv")
        put("mkv", "video/x-matroska")
    }

    return typesMap[getFilenameExtension().toLowerCase()] ?: ""
}
