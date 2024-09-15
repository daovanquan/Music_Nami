package com.kanavi.automotive.nami.music.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbManager
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.preference.PreferenceManager
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.media.MediaBrowserServiceCompat
import com.hierynomus.msdtyp.AccessMask
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.mssmb2.SMB2CreateDisposition
import com.hierynomus.mssmb2.SMB2CreateOptions
import com.hierynomus.mssmb2.SMB2ShareAccess
import com.hierynomus.smbj.SMBClient
import com.hierynomus.smbj.auth.AuthenticationContext
import com.hierynomus.smbj.connection.Connection
import com.hierynomus.smbj.session.Session
import com.hierynomus.smbj.share.DiskShare
import com.kanavi.automotive.nami.music.App
import com.kanavi.automotive.nami.music.R
import com.kanavi.automotive.nami.music.common.constant.Constants.isFrontDisplay
import com.kanavi.automotive.nami.music.common.constant.MediaConstant
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.MEDIA_ID_RECENT_ROOT
import com.kanavi.automotive.nami.music.common.constant.MediaConstant.MEDIA_ID_USB_LIST
import com.kanavi.automotive.nami.music.common.extension.getArtist
import com.kanavi.automotive.nami.music.common.extension.getDuration
import com.kanavi.automotive.nami.music.common.extension.getYear
import com.kanavi.automotive.nami.music.common.extension.toMediaSessionQueue
import com.kanavi.automotive.nami.music.common.util.ShuffleHelper.makeShuffleList
import com.kanavi.automotive.nami.music.data.database.model.Song
import com.kanavi.automotive.nami.music.data.database.model.Song.Companion.emptySong
import com.kanavi.automotive.nami.music.service.mediaPlayback.MediaSessionCallback
import com.kanavi.automotive.nami.music.service.mediaPlayback.Playback
import com.kanavi.automotive.nami.music.service.mediaPlayback.PlaybackManager
import com.kanavi.automotive.nami.music.service.mediaSource.MediaIDHelper
import com.kanavi.automotive.nami.music.service.mediaSource.MusicProvider
import com.kanavi.automotive.nami.music.service.mediaSource.PersistentStorage
import com.kanavi.automotive.nami.music.service.mediaSource.UsbSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.EnumSet
import java.util.Objects

class MusicService : MediaBrowserServiceCompat(), Playback.PlaybackCallbacks, KoinComponent {
    private val musicBind: IBinder = MusicBinder()

    private val notificationManager: NotificationManager by inject()
    private val musicProvider: MusicProvider by inject()
    private val storage: PersistentStorage by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Main)

    private var nanoHttpServer: NanoHttpServer? = null
    private val client = SMBClient()
    private var sambaServerIP = ""
    private var connection: Connection? = null
    private var session: Session? = null
    private var share: DiskShare? = null
    private var isTryingToConnect = false

    private lateinit var playbackManager: PlaybackManager
    private var mediaSession: MediaSessionCompat? = null

    private var trackEndedByCrossFade = false

    private var originalPlayingQueue = ArrayList<Song>()

    val isPlaying: Boolean
        get() = playbackManager.isPlaying

    @JvmField
    var playingQueue = ArrayList<Song>()

    @JvmField
    var position = -1
    private fun setPosition(position: Int) {
        openTrackAndPrepareNextAt(position) { success ->
            if (success) {
                notifyChange(PLAY_STATE_CHANGED)
            }
        }
    }

    private fun getPosition(): Int {
        return position
    }

    private fun getPreviousPosition(force: Boolean): Int {
        var newPosition = getPosition() - 1
        when (repeatMode) {
            REPEAT_MODE_ALL -> if (newPosition < 0) {
                newPosition = playingQueue.size - 1
            }

            REPEAT_MODE_THIS -> if (force) {
                if (newPosition < 0) {
                    newPosition = playingQueue.size - 1
                }
            } else {
                newPosition = getPosition()
            }

            REPEAT_MODE_NONE -> if (newPosition < 0) {
                newPosition = 0
            }

            else -> if (newPosition < 0) {
                newPosition = 0
            }
        }
        return newPosition
    }

    @JvmField
    var nextPosition = -1
    private fun getNextPosition(force: Boolean): Int {
        var position = getPosition() + 1
        when (repeatMode) {
            REPEAT_MODE_ALL -> if (isLastTrack) {
                position = 0
            }

            REPEAT_MODE_THIS -> if (force) {
                if (isLastTrack) {
                    position = 0
                }
            } else {
                position -= 1
            }

            REPEAT_MODE_NONE -> if (isLastTrack) {
                position -= 1
            }

            else -> if (isLastTrack) {
                position -= 1
            }
        }
        return position
    }

    val currentSong: Song
        get() = getSongAt(getPosition())

    val nextSong: Song?
        get() = if (isLastTrack && repeatMode == REPEAT_MODE_NONE) {
            null
        } else {
            getSongAt(getNextPosition(false))
        }

    private val isLastTrack: Boolean
        get() = getPosition() == playingQueue.size - 1

    val songDurationMillis: Int
        get() = playbackManager.songDurationMillis

    val songProgressMillis: Int
        get() = playbackManager.songProgressMillis

    //shuffleMode
    @JvmField
    var shuffleMode = 0

    fun getShuffleMode(): Int {
        return shuffleMode
    }

    fun setShuffleMode(shuffleMode: Int) {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putInt(SAVED_SHUFFLE_MODE, shuffleMode)
        }
        when (shuffleMode) {
            SHUFFLE_MODE_SHUFFLE -> {
                this.shuffleMode = shuffleMode
                makeShuffleList(playingQueue, getPosition())
                position = 0
            }

            SHUFFLE_MODE_NONE -> {
                this.shuffleMode = shuffleMode
                val currentSongId = Objects.requireNonNull(currentSong).id
                playingQueue = ArrayList(originalPlayingQueue)
                var newPosition = 0
                for (song in playingQueue) {
                    if (song.id == currentSongId) {
                        newPosition = playingQueue.indexOf(song)
                    }
                }
                position = newPosition
            }
        }
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED)
        notifyChange(QUEUE_CHANGED)
    }

    fun toggleShuffle() {
        if (getShuffleMode() == SHUFFLE_MODE_NONE) {
            setShuffleMode(SHUFFLE_MODE_SHUFFLE)
        } else {
            setShuffleMode(SHUFFLE_MODE_NONE)
        }
    }

    //repeat mode
    var repeatMode = REPEAT_MODE_NONE
        private set(value) {
            when (value) {
                REPEAT_MODE_NONE, REPEAT_MODE_ALL, REPEAT_MODE_THIS -> {
                    field = value
                    PreferenceManager.getDefaultSharedPreferences(this).edit {
                        putInt(SAVED_REPEAT_MODE, value)
                    }
                    prepareNext()
                    handleAndSendChangeInternal(REPEAT_MODE_CHANGED)
                }
            }
        }

    fun cycleRepeatMode() {
        repeatMode = when (repeatMode) {
            REPEAT_MODE_NONE -> REPEAT_MODE_ALL
            REPEAT_MODE_ALL -> REPEAT_MODE_THIS
            else -> REPEAT_MODE_NONE
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onCreate() {
        super.onCreate()
        Timber.d("onCreate")

        if (isFrontDisplay()) scanUsbAndHandleIfNeeded()

        val powerManager = getSystemService<PowerManager>()
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, javaClass.name)
        }
        wakeLock?.setReferenceCounted(false)

        registerUsbEventReceiver()
        setupPlaybackManager()
        setupMediaSession(isActive = false)
        setErrorPlaybackState()
//        restoreState()
        sessionToken = mediaSession?.sessionToken
        musicProvider.setMusicService(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("onStartCommand")
        initNotification()
        if (intent != null && intent.action != null) {
            serviceScope.launch {
                restoreQueuesAndPositionIfNecessary()
                when (intent.action) {
                    ACTION_TOGGLE_PAUSE -> if (isPlaying) {
                        pause()
                    } else {
                        play()
                    }

                    ACTION_PAUSE -> pause()
                    ACTION_PLAY -> play()
                    ACTION_REWIND -> back(true)
                    ACTION_SKIP -> playNextSong(true)
                }
            }
        }
        return START_STICKY
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot {
        /**
         * By default return the browsable root. Treat the EXTRA_RECENT flag as a special case
         * and return the recent root instead.
         */
        Timber.d("onGetRoot: clientPackageName = $clientPackageName, clientUid = $clientUid, rootHints = $rootHints")

        val isRecentRequest = rootHints?.getBoolean(BrowserRoot.EXTRA_RECENT) ?: false
        val browserRootPath = if (isRecentRequest) MEDIA_ID_RECENT_ROOT else MEDIA_ID_USB_LIST
        return BrowserRoot(browserRootPath, null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem?>>
    ) {
        when (parentId) {
            MEDIA_ID_RECENT_ROOT -> {
                Timber.d("items __MUSIC_RECENT__")
                result.sendResult(storage.loadRecentSong()?.let { song -> listOf(song) })
            }

            MEDIA_ID_USB_LIST -> {
                Timber.d("load items for __MUSIC_USB_LIST__")
                updateMediaSessionPlaybackState()
                result.sendResult(musicProvider.getUsbListItems())
            }

            else -> {
                Timber.d("load items for: $parentId")
                result.sendResult(musicProvider.getChildren(parentId))
            }
        }
    }

    fun openQueue(
        playingQueue: List<Song>?,
        startPosition: Int,
        startPlaying: Boolean,
    ) {
        if (!playingQueue.isNullOrEmpty()
            && startPosition >= 0 && startPosition < playingQueue.size
        ) {
            Timber.d("openQueue: queuesize: ${playingQueue.size} startPosition: $startPosition, startPlaying: $startPlaying")
            // it is important to copy the playing queue here first as we might add/remove songs later
            originalPlayingQueue = ArrayList(playingQueue)
            this.playingQueue = ArrayList(originalPlayingQueue)
            var position = startPosition
            if (shuffleMode == SHUFFLE_MODE_SHUFFLE) {
                makeShuffleList(this.playingQueue, startPosition)
                position = 0
            }
            if (startPlaying) {
                playSongAt(position)
            } else {
                setPosition(position)
            }
            notifyChange(QUEUE_CHANGED)
        }
    }

    fun clearQueue() {
        playingQueue.clear()
        originalPlayingQueue.clear()
        setPosition(-1)
        notifyChange(QUEUE_CHANGED)
    }

    @Synchronized
    private fun openCurrent(completion: (success: Boolean) -> Unit) {
        val force = if (!trackEndedByCrossFade) {
            true
        } else {
            trackEndedByCrossFade = false
            false
        }
        currentSong.let {
            playbackManager.setDataSource(it, force) { success ->
                completion(success)
            }
        }
    }


    @Synchronized
    fun openTrackAndPrepareNextAt(position: Int, completion: (success: Boolean) -> Unit) {
        this.position = position
        openCurrent { success ->
            completion(success)
            if (success) {
                prepareNextImpl()
            }
            notifyChange(META_CHANGED)
        }
    }

    fun addSong(position: Int, song: Song) {
        playingQueue.add(position, song)
        originalPlayingQueue.add(position, song)
        notifyChange(QUEUE_CHANGED)
    }

    fun addSong(song: Song) {
        playingQueue.add(song)
        originalPlayingQueue.add(song)
        notifyChange(QUEUE_CHANGED)
    }

    fun addSongs(position: Int, songs: List<Song>?) {
        playingQueue.addAll(position, songs!!)
        originalPlayingQueue.addAll(position, songs)
        notifyChange(QUEUE_CHANGED)
    }

    fun addSongs(songs: List<Song>?) {
        playingQueue.addAll(songs!!)
        originalPlayingQueue.addAll(songs)
        notifyChange(QUEUE_CHANGED)
    }

    @Synchronized
    fun play() {
        playbackManager.play { playSongAt(getPosition()) }
        handleChangeInternal(META_CHANGED)
        notifyChange(PLAY_STATE_CHANGED)
    }

    fun playNextSong(force: Boolean) {
        playSongAt(getNextPosition(force))
    }

    fun playPreviousSong(force: Boolean) {
        playSongAt(getPreviousPosition(force))
    }

    fun playSongAt(position: Int) {
        // Every chromecast method needs to run on main thread or you are greeted with IllegalStateException
        // So it will use Main dispatcher
        // And by using Default dispatcher for local playback we are reduce the burden of main thread
        serviceScope.launch(Dispatchers.Default) {
            openTrackAndPrepareNextAt(position) { success ->
                if (success) {
                    play()
                } else {
                    Timber.e("Failed to open track at position $position")
                }
            }
        }
    }

    fun back(force: Boolean) {
        if (songProgressMillis > 2000) {
            seek(0)
        } else {
            playPreviousSong(force)
        }
    }

    fun pause(force: Boolean = false) {
        playbackManager.pause(force) {
            notifyChange(PLAY_STATE_CHANGED)
        }
    }

    @Synchronized
    fun seek(millis: Int, force: Boolean = true): Int {
        return try {
            val newPosition = playbackManager.seek(millis, force)
            newPosition
        } catch (e: Exception) {
            -1
        }
    }

    private fun savePosition() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putInt(SAVED_POSITION, getPosition())
        }
    }

    fun savePositionInTrack() {
        PreferenceManager.getDefaultSharedPreferences(this).edit {
            putInt(SAVED_POSITION_IN_TRACK, songProgressMillis)
        }
    }

    private fun prepareNext() {
        prepareNextImpl()
    }

    @Synchronized
    fun prepareNextImpl() {
        try {
            val nextPosition = getNextPosition(false)
            val nextSong = getSongAt(nextPosition)
            Timber.d("prepareNextImpl: title: ${nextSong.title}, uri: ${nextSong.getUri()} path: ${nextSong.path}")
            playbackManager.setNextDataSource(getSongAt(nextPosition).path)
            this.nextPosition = nextPosition
        } catch (ignored: Exception) {
        }
    }


    private fun getSongAt(position: Int): Song {
        return if ((position >= 0) && (position < playingQueue.size)) {
            playingQueue[position]
        } else {
            emptySong
        }
    }

    private fun notifyChange(what: String) {
        handleAndSendChangeInternal(what)
    }

    private fun handleAndSendChangeInternal(what: String) {
        handleChangeInternal(what)
        sendChangeInternal(what)
    }

    private fun sendChangeInternal(what: String) {
    }

    private fun handleChangeInternal(what: String) {
        when (what) {
            PLAY_STATE_CHANGED -> {
                updateMediaSessionPlaybackState()
                val isPlaying = isPlaying
                if (!isPlaying && songProgressMillis > 0) {
                    savePositionInTrack()
                }
            }

            META_CHANGED -> {
                updateMediaSessionMetaData(::updateMediaSessionPlaybackState)
                savePosition()
                savePositionInTrack()
                serviceScope.launch(IO) {
                    val currentSong = currentSong
                    storage.saveRecentSong(currentSong, songProgressMillis.toLong())
                }
            }

            QUEUE_CHANGED -> {
                mediaSession?.setQueueTitle("Now playing queue")
                mediaSession?.setQueue(playingQueue.toMediaSessionQueue())
                updateMediaSessionMetaData(::updateMediaSessionPlaybackState) // because playing queue size might have changed
//                saveQueues()
                if (playingQueue.size > 0) {
                    prepareNext()
                } else {
//                    stopForegroundAndNotification
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Timber.d("onDestroy")
        mediaSession?.isActive = false
        quit()
        releaseResource()
        unregisterUsbEventReceiver()
        wakeLock?.release()
    }

    fun quit() {
        pause()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        notificationManager.cancel(NOTIFICATION_ID)
        stopSelf()
    }

    private fun acquireWakeLock() {
        wakeLock?.acquire(30000)
    }

    private fun releaseWakeLock() {
        if (wakeLock!!.isHeld) {
            wakeLock?.release()
        }
    }

    private fun releaseResource() {
        playbackManager?.release()
        mediaSession?.release()
    }

    private fun setupPlaybackManager() {
        if (!::playbackManager.isInitialized) {
            Timber.d("setupPlaybackManager")
            playbackManager = PlaybackManager(this)
            playbackManager.setCallbacks(this)
            playbackManager.setMusicService(this)
        }
    }

    private fun setupMediaSession(isActive: Boolean = false) {
        if (mediaSession == null) {
            mediaSession = MediaSessionCompat(this, PACKAGE_NAME)
            val mediaSessionCallback = MediaSessionCallback(this)
            mediaSession?.setCallback(mediaSessionCallback)
        }
        Timber.d("setupMediaSession: isActive = $isActive")
        mediaSession?.isActive = isActive
    }

    fun restoreState(completion: () -> Unit = {}) {
        //restore shuffleMode, repeatMode, queue and position...
        shuffleMode = PreferenceManager.getDefaultSharedPreferences(this).getInt(
            SAVED_SHUFFLE_MODE, SHUFFLE_MODE_NONE
        )
        repeatMode = PreferenceManager.getDefaultSharedPreferences(this).getInt(
            SAVED_REPEAT_MODE, REPEAT_MODE_NONE
        )
        handleAndSendChangeInternal(SHUFFLE_MODE_CHANGED)
        handleAndSendChangeInternal(REPEAT_MODE_CHANGED)
        serviceScope.launch {
            restoreQueuesAndPositionIfNecessary()
            completion()
        }
    }

    private suspend fun restoreQueuesAndPositionIfNecessary() {

    }

    private fun initNotification() {
        var notificationChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Music Service",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(notificationChannel)
        val notification: Notification = NotificationCompat.Builder(
            this,
            NOTIFICATION_CHANNEL_ID
        ).setSmallIcon(R.drawable.ic_launcher_background).setOnlyAlertOnce(true).build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onTrackEnded() {
        acquireWakeLock()
        if (repeatMode == REPEAT_MODE_NONE && isLastTrack) {
            notifyChange(PLAY_STATE_CHANGED)
            seek(0, false)
        } else {
            playNextSong(false)
        }
        releaseWakeLock()
    }


    override fun onTrackEndedWithCrossfade() {
        trackEndedByCrossFade = true
        onTrackEnded()
    }

    override fun onTrackWentToNext() {
        if (repeatMode == REPEAT_MODE_NONE && isLastTrack) {
            playbackManager.setNextDataSource(null)
            pause(false)
            seek(0, false)
        } else {
            position = nextPosition
            prepareNextImpl()
            notifyChange(META_CHANGED)
        }
    }

    @SuppressLint("CheckResult")
    fun updateMediaSessionMetaData(onCompletion: () -> Unit) {
        Timber.d("onResourceReady: ")
        val currentUsbSource = musicProvider.getUsbSource()
        currentUsbSource?.let {
            val song =
                if (currentUsbSource.isLoading) currentSong else currentUsbSource.songMap[currentSong.path.hashCode()]
            song?.let{
                if (song.id == -1L) {
                    mediaSession?.setMetadata(null)
                    return
                }
                val songID = MediaIDHelper.createMediaID(
                    song.path,
                    MediaConstant.MEDIA_ID_MUSICS_BY_FILE
                )
                if (musicProvider.getUsbSource()?.isLoading == true) {
                    val context = App.app
                    song.artist = context.getArtist(song.path) ?: ""
                    song.duration = context.getDuration(song.path) ?: 0
                    song.year = context.getYear(song.path)
                }

                val metaData = MediaMetadataCompat.Builder()
                    .putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, songID)
                    .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, song.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, song.artist)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, song.album)
                    .putString(MediaMetadataCompat.METADATA_KEY_TITLE, song.title)
                    .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ART_URI, song.path)
                    .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, song.duration)
                    .putLong(
                        MediaMetadataCompat.METADATA_KEY_TRACK_NUMBER,
                        (getPosition() + 1).toLong()
                    )
                    .putLong(MediaMetadataCompat.METADATA_KEY_YEAR, song.year.toLong())
                    //consider load cover art and put in here
                    .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, null)
                    .putLong(MediaMetadataCompat.METADATA_KEY_NUM_TRACKS, playingQueue.size.toLong())

                mediaSession?.setMetadata(metaData.build())
                onCompletion()
            }
        }
    }

    fun updateMediaSessionPlaybackState() {
        val stateBuilder = PlaybackStateCompat.Builder()
            .setActions(MEDIA_SESSION_ACTIONS)
            .setState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED,
                songProgressMillis.toLong(),
                1f
            )
        setCustomAction(stateBuilder)
        mediaSession?.setPlaybackState(stateBuilder.build())
    }

    private fun setCustomAction(stateBuilder: PlaybackStateCompat.Builder) {
        stateBuilder.addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                CYCLE_REPEAT, repeatMode.toString(), R.drawable.ic_launcher_background
            )
                .build()
        )

        stateBuilder.addCustomAction(
            PlaybackStateCompat.CustomAction.Builder(
                TOGGLE_SHUFFLE, shuffleMode.toString(), R.drawable.ic_launcher_background
            )
                .build()
        )
    }

    override fun onPlayStateChanged() {
        notifyChange(PLAY_STATE_CHANGED)
    }

    override fun onAudioFocusGained(isGained: Boolean) {
        Timber.d("onAudioFocusGained: $isGained")
        setupMediaSession(isActive = isGained)
    }

    private fun registerUsbEventReceiver() {
        Timber.d("registerUsbEventReceiver")
        if (!usbReceiverRegistered) {
            registerReceiver(usbEventReceiver, usbReceiverIntentFilter)
            usbReceiverRegistered = true
        }
    }

    private fun unregisterUsbEventReceiver() {
        Timber.d("unregisterUsbEventReceiver")
        unregisterReceiver(usbEventReceiver)
        usbReceiverRegistered = false
    }

    private var usbReceiverRegistered = false

    private val usbReceiverIntentFilter = IntentFilter().apply {
        addAction(Intent.ACTION_MEDIA_CHECKING)
        addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        addAction(Intent.ACTION_MEDIA_MOUNTED)
        addAction(Intent.ACTION_MEDIA_UNMOUNTED)
        addAction(Intent.ACTION_MEDIA_EJECT)
        addAction(Intent.ACTION_MEDIA_REMOVED)
        addAction(ACTION_GET_HOST_IP)
//        addDataScheme("file")
    }

    private val usbEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Timber.d("action: ${intent.action} data: ${intent.data}")
            when (intent.action) {
                ACTION_GET_HOST_IP -> {
                    Timber.d("ACTION_GET_HOST_IP")
                    sambaServerIP = intent.getStringExtra(EXTRA_HOST_IP)!!
                    startNanoHttpServer(sambaServerIP, SAMBA_SERVER_PORT)
                    scanSambaServerAndHandleIfNeeded(sambaServerIP)
                }

                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Timber.d("ACTION_USB_DEVICE_ATTACHED")
                    if (isFrontDisplay()) scanUsbAndHandleIfNeeded()
                }

                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Timber.d("ACTION_USB_DEVICE_DETACHED")
                    if (isFrontDisplay()) handleUsbDetachEvent()
                }
            }
        }
    }

    fun runShellCommand(command: String): List<String> {
        val process = Runtime.getRuntime().exec(command)
        val bufferedReader = BufferedReader(
            InputStreamReader(process.inputStream)
        )

        val usbList = mutableListOf<String>()
        val log = StringBuilder()
        var line: String?
        line = bufferedReader.readLine()
        while (line != null) {
            if (line.contains("media_rw")) {
                val start = line.indexOf("/mnt/media_rw")
                val end = line.indexOf("type")
                usbList.add(line.substring(start, end).trim())
            }

            line = bufferedReader.readLine()
        }
        val reader = BufferedReader(
            InputStreamReader(process.errorStream)
        )

        // if we had an error during ex we get here
        val errorLog = StringBuilder()
        var errorLine: String?
        errorLine = reader.readLine()
        while (errorLine != null) {
            errorLog.append(errorLine + "\n")
            errorLine = reader.readLine()
        }
        if (errorLog.toString() != "")
            Timber.i("command : $command $log error $errorLog")
        else
            Timber.i("command : $command $log")

        return usbList
    }

    private fun scanUsbAndHandleIfNeeded() {
        Timber.d("===========scanUsbAndHandleIfNeeded=========")
        var usbIsAdded = false
        serviceScope.launch(IO) {
            var count = 0
            while (!usbIsAdded) {
                val usbList = runShellCommand("mount")
                val currentUsbSourceMap = musicProvider.getUsbSourceMap()
                usbList.forEach { usbId ->
                    if (!currentUsbSourceMap.contains(usbId)) {
                        Timber.d("Found USB device:$usbId => stop scan after $count times")
                        musicProvider.apply {
                            addUsbSource(usbId, UsbSource(usbID = usbId))
                            usbIsAdded = true
                            getUsbSource(usbId)?.let {
                                serviceScope.launch(IO) {
                                    it.load()
                                }
                            }
                        }
                    }
                }
                delay(200)
                count++
                if (count >= TIME_TO_TRY_SCAN_USB_DEVICE) {
                    Timber.d("Try to scan over $TIME_TO_TRY_SCAN_USB_DEVICE times and do not found any USB device is ADDED => stop scan")
                    break
                }
                if (usbIsAdded) {
                    setupPlaybackManager()
                    break
                }
            }
        }
    }

    private fun handleUsbDetachEvent() {
        Timber.d("==========handleUsbDetachEvent=========")
        var usbIsRemoved = false
        serviceScope.launch(IO) {
            var count = 0
            while (!usbIsRemoved) {
                val newUsbList = runShellCommand("mount")
                val currentUsbSourceMap = musicProvider.getUsbSourceMap()
                currentUsbSourceMap.iterator().forEach { usbDevice ->
                    if (!newUsbList.contains(usbDevice.key)) {
                        Timber.d("Found USB device is removed :${usbDevice.key} => stop scan after $count times")
                        if (usbDevice.key == musicProvider.getSelectedUsbID()) {
                            playbackManager.stop()
                            setupMediaSession(isActive = false)
                        }
                        musicProvider.removeUsbSource(usbDevice.key)
                        usbIsRemoved = true
                    }
                }
                delay(200)
                count++
                if (count >= TIME_TO_TRY_SCAN_USB_DEVICE) {
                    Timber.d("Try to scan over $TIME_TO_TRY_SCAN_USB_DEVICE times and do not found any USB device is REMOVED => stop scan")
                    break
                }
                if (usbIsRemoved) {
                    break
                }
            }
        }
    }

    private fun startNanoHttpServer(sambaServerIP: String, port: Int) {
        nanoHttpServer = NanoHttpServer(sambaServerIP, port)
        nanoHttpServer?.start()
    }

    private fun scanSambaServerAndHandleIfNeeded(sambaServerIP: String) {
        Timber.d("scanSambaServerAndHandleIfNeeded")
        val client = SMBClient()

        serviceScope.launch(IO) {
            connection = runCatching {
                client.connect(sambaServerIP)
            }.onFailure {
                Timber.e("Failed to connect to host: $sambaServerIP, error: ${it.message}")
            }.getOrNull()

            session = runCatching {
                connection?.authenticate(AuthenticationContext.anonymous())
            }.onFailure {
                Timber.e("Failed to authenticate on host: $sambaServerIP, error: ${it.message}")
            }.getOrNull()

            share = runCatching {
                session?.connectShare(SAMBA_SERVER_SHARE_NAME) as? DiskShare
            }.onFailure {
                Timber.e("Failed to connect to share: $SAMBA_SERVER_SHARE_NAME on host $sambaServerIP, error: ${it.message}")
            }.getOrNull()

            try {
                if (share == null) {
                    Timber.e("$SAMBA_SERVER_SHARE_NAME is not a DiskShare on host $sambaServerIP or failed to connect")
                    return@launch
                } else {
                    Timber.d("Connected to share: $SAMBA_SERVER_SHARE_NAME")
                    observeSambaServerToGetUsbList(
                        sambaServerIP = sambaServerIP,
                        rootPath = "",
                        pathsToIgnore = listOf(".", "..", "usb1", "usb2", "sdcard")
                    )
                }

            } finally {
                runCatching {
                    share?.close()
                }.onFailure {
                    Timber.e("Error closing share: ${it.message}")
                }

                runCatching {
                    session?.close()
                }.onFailure {
                    Timber.e("Error closing session: ${it.message}")
                }

                runCatching {
                    connection?.close()
                }.onFailure {
                    Timber.e("Error closing connection: ${it.message}")
                }
                Timber.d("Connection to Samba server closed.")
            }
        }
    }

    private suspend fun observeSambaServerToGetUsbList(
        sambaServerIP: String,
        rootPath: String,
        pathsToIgnore: List<String>,
        pollInterval: Long = 1000
    ) {
        Timber.d("observeSambaServerToGetUsbList")
        while (true) {
            Timber.i("schedule scan to get USB list in Samba server")
            if (share!!.isConnected) {
                Timber.i("========${share?.smbPath} is connected => scanToGetUsbListThenHandle==========")
                scanToGetUsbListThenHandle(sambaServerIP, share!!, rootPath, pathsToIgnore)
            } else {
                retryConnectToSambaServer {
                    Timber.d("========${share?.smbPath} is NOT connected => retry connect to Samba server=========")
                    scanToGetUsbListThenHandle(sambaServerIP, share, rootPath, pathsToIgnore)
                }
            }
            delay(pollInterval)
        }
    }

    private fun scanToGetUsbListThenHandle(
        sambaServerIP: String,
        share: DiskShare?,
        rootPath: String,
        pathsToIgnore: List<String>,
    ) {
        val rootFolder = runCatching {
            share?.openDirectory(
                rootPath, desiredAccess,
                fileAttributes,
                shareAccess,
                createDisposition,
                createOptions
            )
        }.onFailure {
            Timber.e("Error opening directory: $rootPath, error: ${it.message}")
        }.getOrNull()

        if (rootFolder != null) {
            val listUsbInSambaServer =
                rootFolder.list().filter { it.fileName !in pathsToIgnore }
                    .map { it.fileName }
            val currentListUsb = musicProvider.getUsbSourceMap().keys.toList()
            Timber.i("currentListUsb: $currentListUsb")
            Timber.i("listUsbInSambaServer: $listUsbInSambaServer")
            val usbAdded = listUsbInSambaServer.filter { it !in currentListUsb }
            val usbRemoved = currentListUsb.filter { it !in listUsbInSambaServer }

            if (usbAdded.isNotEmpty()) {
                Timber.d("======Found USB is added======")
                usbAdded.forEach { usbId ->
                    Timber.d("========Processing USB device with path:$usbId===========")
                    val hostURL = "http://localhost:$SAMBA_SERVER_PORT"
                    musicProvider.apply {
                        addUsbSource(
                            usbId,
                            UsbSource(usbID = usbId, hostURL = hostURL, share = share)
                        )
                        getUsbSource(usbId)?.let {
                            CoroutineScope(IO).launch {
                                it.load()
                            }
                        }
                    }
                }
            }
            if (usbRemoved.isNotEmpty()) {
                Timber.d("======Found USB is removed======")
                usbRemoved.forEach { usbId ->
                    Timber.d("========Processing USB device with path:$usbId===========")
                    musicProvider.removeUsbSource(usbId)
                }
            }
        }

    }

    private fun retryConnectToSambaServer(jobToDoAfterReconnect: () -> Unit) {
        if (!isTryingToConnect)
            runCatching {
                isTryingToConnect = true

                connection = runCatching {
                    client.connect(sambaServerIP)
                }.onFailure {
                    Timber.e("Failed to connect to host: $sambaServerIP, error: ${it.message}")
                }.getOrNull()

                session = runCatching {
                    connection?.authenticate(AuthenticationContext.anonymous())
                }.onFailure {
                    Timber.e("Failed to authenticate on host: $sambaServerIP, error: ${it.message}")
                }.getOrNull()

                share = session?.connectShare(SAMBA_SERVER_SHARE_NAME) as? DiskShare

                if (share?.isConnected == true) {
                    Timber.d("Reconnected to share: $SAMBA_SERVER_SHARE_NAME => continue scan usb list")
                    jobToDoAfterReconnect.invoke()
                } else {
                    Timber.e("Failed to reconnect to share: $SAMBA_SERVER_SHARE_NAME on host $sambaServerIP")
                }
                isTryingToConnect = false
            }.onFailure {
                isTryingToConnect = false
                Timber.e("Failed to reconnect to share: $SAMBA_SERVER_SHARE_NAME, error: ${it.message}")
            }
    }

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    override fun onBind(intent: Intent): IBinder {
        // For Android auto, need to call super, or onGetRoot won't be called.
        return if ("android.media.browse.MediaBrowserService" == intent.action) {
            super.onBind(intent)!!
        } else musicBind
    }

    override fun onUnbind(intent: Intent): Boolean {
//        if (!isPlaying) {
//            stopSelf()
//        }
        return true
    }

    private fun setErrorPlaybackState() {
        val errorState = PlaybackStateCompat.Builder()
            .setErrorMessage(getString(R.string.STR_MMS_0358_ID))
            .setState(
                PlaybackStateCompat.STATE_ERROR,
                0, 0f
            ).build()
        mediaSession?.setPlaybackState(errorState)
    }

    companion object {
        private const val PACKAGE_NAME = "com.kanavi.automotive.nami.music"
        const val NOTIFICATION_CHANNEL_ID = "$PACKAGE_NAME.NOTIFICATION_CHANNEL_ID"
        const val NOTIFICATION_ID = 2468
        const val TIME_TO_TRY_SCAN_USB_DEVICE = 68

        const val ACTION_GET_HOST_IP = "$PACKAGE_NAME.ACTION_GET_IP_ADDRESS"
        const val EXTRA_HOST_IP = "IP_ADDRESS"
        const val ACTION_TOGGLE_PAUSE = "$PACKAGE_NAME.togglepause"
        const val ACTION_PLAY = "$PACKAGE_NAME.play"
        const val ACTION_PAUSE = "$PACKAGE_NAME.pause"
        const val ACTION_STOP = "$PACKAGE_NAME.stop"
        const val ACTION_SKIP = "$PACKAGE_NAME.skip"
        const val ACTION_REWIND = "$PACKAGE_NAME.rewind"
        const val ACTION_QUIT = "$PACKAGE_NAME.quitservice"

        const val META_CHANGED = "$PACKAGE_NAME.metachanged"
        const val QUEUE_CHANGED = "$PACKAGE_NAME.queuechanged"
        const val PLAY_STATE_CHANGED = "$PACKAGE_NAME.playstatechanged"
        const val REPEAT_MODE_CHANGED = "$PACKAGE_NAME.repeatmodechanged"
        const val SHUFFLE_MODE_CHANGED = "$PACKAGE_NAME.shufflemodechanged"
        const val MEDIA_STORE_CHANGED = "$PACKAGE_NAME.mediastorechanged"
        const val CYCLE_REPEAT = "$PACKAGE_NAME.cyclerepeat"
        const val TOGGLE_SHUFFLE = "$PACKAGE_NAME.toggleshuffle"
        const val SAVED_POSITION = "$PACKAGE_NAME.POSITION"
        const val SAVED_POSITION_IN_TRACK = "$PACKAGE_NAME.POSITION_IN_TRACK"
        const val SAVED_SHUFFLE_MODE = "$PACKAGE_NAME.SHUFFLE_MODE"
        const val SAVED_REPEAT_MODE = "$PACKAGE_NAME.REPEAT_MODE"
        const val SHUFFLE_MODE_NONE = 0
        const val SHUFFLE_MODE_SHUFFLE = 1
        const val REPEAT_MODE_NONE = 0
        const val REPEAT_MODE_ALL = 1
        const val REPEAT_MODE_THIS = 2
        private const val MEDIA_SESSION_ACTIONS = (PlaybackStateCompat.ACTION_PLAY
                or PlaybackStateCompat.ACTION_PAUSE
                or PlaybackStateCompat.ACTION_PLAY_PAUSE
                or PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS
                or PlaybackStateCompat.ACTION_STOP
                or PlaybackStateCompat.ACTION_SEEK_TO)

        val desiredAccess: EnumSet<AccessMask> =
            EnumSet.of(AccessMask.GENERIC_READ, AccessMask.GENERIC_EXECUTE)
        val fileAttributes: EnumSet<FileAttributes> =
            EnumSet.of(FileAttributes.FILE_ATTRIBUTE_DIRECTORY)
        val shareAccess: EnumSet<SMB2ShareAccess> =
            EnumSet.of(SMB2ShareAccess.FILE_SHARE_READ, SMB2ShareAccess.FILE_SHARE_WRITE)
        val createOptions: EnumSet<SMB2CreateOptions> =
            EnumSet.of(SMB2CreateOptions.FILE_DIRECTORY_FILE)
        val createDisposition = SMB2CreateDisposition.FILE_OPEN

        const val SAMBA_SERVER_SHARE_NAME: String = "Android"
        const val SAMBA_SERVER_PORT: Int = 8086
    }
}