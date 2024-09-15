package com.kanavi.automotive.nami.music.service.mediaPlayback

import com.kanavi.automotive.nami.music.data.database.model.Song


interface Playback {

    val isInitialized: Boolean

    val isPlaying: Boolean

    val audioSessionId: Int

    fun setDataSource(
        song: Song, force: Boolean, completion: (success: Boolean) -> Unit,
    )

    fun setNextDataSource(path: String?)

    var callbacks: PlaybackCallbacks?

    fun start(): Boolean

    fun stop()

    fun release()

    fun pause(): Boolean

    fun duration(): Int

    fun position(): Int

    fun seek(whereto: Int, force: Boolean): Int

    fun setVolume(vol: Float): Boolean

    fun setAudioSessionId(sessionId: Int): Boolean

    fun setCrossFadeDuration(duration: Int)

    fun setPlaybackSpeedPitch(speed: Float, pitch: Float)

    interface PlaybackCallbacks {
        fun onTrackWentToNext()

        fun onTrackEnded()

        fun onTrackEndedWithCrossfade()

        fun onPlayStateChanged()

        fun onAudioFocusGained(isGained:Boolean)
    }
}
