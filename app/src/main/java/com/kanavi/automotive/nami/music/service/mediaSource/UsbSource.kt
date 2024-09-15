package com.kanavi.automotive.nami.music.service.mediaSource

import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import androidx.annotation.IntDef
import com.google.gson.Gson
import com.google.gson.TypeAdapter
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import com.hierynomus.smbj.share.DiskShare
import com.kanavi.automotive.nami.music.App
import com.kanavi.automotive.nami.music.common.constant.Constants.isFrontDisplay
import com.kanavi.automotive.nami.music.common.util.UsbUtil
import com.kanavi.automotive.nami.music.data.database.model.Image
import com.kanavi.automotive.nami.music.data.database.model.Song
import com.kanavi.automotive.nami.music.data.database.model.Video
import timber.log.Timber
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class UsbSource(
    private val usbID: String,
    private val hostURL: String = "",
    private val share: DiskShare? = null,
) : AbstractMusicSource() {
    var isLoading = true

    // music
    var songs: ArrayList<Song> = arrayListOf()
    var songMap: HashMap<Int, Song> = HashMap()

    // image
    var images: ArrayList<Image> = arrayListOf()
    var imageMap: HashMap<Int, Image> = HashMap()

    // video
    var videos: ArrayList<Video> = arrayListOf()
    var videomap: HashMap<Int, Video> = HashMap()

    var treeNode: TreeNode = TreeNode("")

    // music not metadata
    var songswithNotMetadata: ArrayList<Song> = arrayListOf()
    var songMapswithNotMetadata: HashMap<Int, Song> = HashMap()

    // image
    var imageswithMetadata: ArrayList<Image> = arrayListOf()
    var imageMapswithMetadata: HashMap<Int, Image> = HashMap()

    // video not metadata
    var videoswithNotMetadata: ArrayList<Video> = arrayListOf()
    var videoMapswithNotMetadata: HashMap<Int, Video> = HashMap()

    var treeNodewithNotMetadata: TreeNode = TreeNode("")

    override suspend fun load() {
        if (isFrontDisplay()) {
            UsbUtil.scanAllMusicFromUsbNotMetaData(
                usbID,
                emptyList(),
                songswithNotMetadata,
                songMapswithNotMetadata,
                imageswithMetadata,
                imageMapswithMetadata,
                videoswithNotMetadata,
                videoMapswithNotMetadata,
                treeNodewithNotMetadata
            )
            UsbUtil.scanAllMusicFromUsb(
                usbID,
                emptyList(),
                songs,
                songMap,
                images,
                imageMap,
                videos,
                videomap,
                treeNode
            )
            isLoading = false
        } else {
            isLoading = false
            UsbUtil.scanAllMusicFromSambaServer(
                hostURL,
                usbID,
                share!!,
                emptyList(),
                songs,
                songMap,
                images,
                imageMap,
                videos,
                videomap,
                treeNode
            )
        }
    }

    override fun iterator(): Iterator<Song> {
        return (songs.clone() as ArrayList<Song>).iterator()
    }

    fun exportToJSON() {
        Timber.e(
            "exportToJSON for usbID: $usbID, " +
                    "from \n songList: $songs " +
                    "treeNode: \n $treeNode " +
                    "\n mapsong: $songMap"
        )
        Timber.e("jsonListSong: ${Gson().toJson(songs)}")
        Timber.e("jsonMapSong: ${Gson().toJson(songMap)}")
        Timber.e("jsonTreeNode: ${Gson().toJson(treeNode)}")
    }

    fun importFromJSON1() {
        songs =
            Gson().fromJson(readJSONFromAssets(App.app, "listSong1.json"), Array<Song>::class.java)
                .toList() as ArrayList<Song>
        songMap = Gson().fromJson(
            readJSONFromAssets(App.app, "mapSong1.json"),
            object : TypeToken<HashMap<Int, Song>>() {}.type
        )
        treeNode =
            Gson().fromJson(readJSONFromAssets(App.app, "treeSong1.json"), TreeNode::class.java)
    }

    fun importFromJSON2() {
        songs =
            Gson().fromJson(readJSONFromAssets(App.app, "listSong2.json"), Array<Song>::class.java)
                .toList() as ArrayList<Song>
        songMap = Gson().fromJson(
            readJSONFromAssets(App.app, "mapSong2.json"),
            object : TypeToken<HashMap<Int, Song>>() {}.type
        )
        treeNode =
            Gson().fromJson(readJSONFromAssets(App.app, "treeSong2.json"), TreeNode::class.java)
    }
}


/**
 * Interface used by [MusicService] for looking up [MediaMetadataCompat] objects.
 *
 * Because Kotlin provides methods such as [Iterable.find] and [Iterable.filter],
 * this is a convenient interface to have on sources.
 */
interface MusicSource : Iterable<Song> {

    /**
     * Begins loading the data for this music source.
     */
    suspend fun load()

    /**
     * Method which will perform a given action after this [MusicSource] is ready to be used.
     *
     * @param performAction A lambda expression to be called with a boolean parameter when
     * the source is ready. `true` indicates the source was successfully prepared, `false`
     * indicates an error occurred.
     */
    fun whenReady(performAction: (Boolean) -> Unit): Boolean
}

@IntDef(
    STATE_CREATED,
    STATE_INITIALIZING,
    STATE_INITIALIZED,
    STATE_ERROR
)
@Retention(AnnotationRetention.SOURCE)
annotation class State

/**
 * State indicating the source was created, but no initialization has performed.
 */
const val STATE_CREATED = 1

/**
 * State indicating initialization of the source is in progress.
 */
const val STATE_INITIALIZING = 2

/**
 * State indicating the source has been initialized and is ready to be used.
 */
const val STATE_INITIALIZED = 3

/**
 * State indicating an error has occurred.
 */
const val STATE_ERROR = 4

const val DEFAULT_CONNECT_SHARE_NAME = "Android"

/**
 * Base class for music sources
 */
abstract class AbstractMusicSource : MusicSource {
    @State
    var state: Int = STATE_CREATED
        set(value) {
            if (value == STATE_INITIALIZED || value == STATE_ERROR) {
                synchronized(onReadyListeners) {
                    field = value
                    onReadyListeners.forEach { listener ->
                        listener(state == STATE_INITIALIZED)
                    }
                }
            } else {
                field = value
            }
        }

    private val onReadyListeners = mutableListOf<(Boolean) -> Unit>()


    /**
     * Performs an action when this MusicSource is ready.
     *
     * This method is *not* threadsafe. Ensure actions and state changes are only performed
     * on a single thread.
     */
    override fun whenReady(performAction: (Boolean) -> Unit): Boolean =
        when (state) {
            STATE_CREATED, STATE_INITIALIZING -> {
                Timber.d("whenReady: state == STATE_CREATED || state == STATE_INITIALIZING")
                onReadyListeners += performAction
                false
            }

            else -> {
                Timber.d("whenReady: state == STATE_INITIALIZED")
                performAction(state != STATE_ERROR)
                true
            }
        }
}

private fun readJSONFromAssets(context: Context, path: String): String {
    try {
        val file = context.assets.open("$path")
        Timber.i("Found File: $file")
        val bufferedReader = BufferedReader(InputStreamReader(file))
        val stringBuilder = StringBuilder()
        bufferedReader.useLines { lines ->
            lines.forEach {
                stringBuilder.append(it)
            }
        }
        Timber.i("stringBuilder: $stringBuilder.")
        val jsonString = stringBuilder.toString()
        Timber.i("JSON as String: $jsonString.")
        return jsonString
    } catch (e: Exception) {
        Timber.e(" Error reading JSON: $e.")
        e.printStackTrace()
        return ""
    }
}


internal class MyTypeAdapter<T> : TypeAdapter<T>() {
    @Throws(IOException::class)
    override fun read(reader: JsonReader?): T? {
        return null
    }

    @Throws(IOException::class)
    override fun write(writer: JsonWriter, obj: T?) {
        if (obj == null) {
            writer.nullValue()
            return
        }
        writer.value(obj.toString())
    }
}