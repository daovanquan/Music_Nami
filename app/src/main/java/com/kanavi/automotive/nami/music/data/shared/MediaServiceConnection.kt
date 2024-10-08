/*
 * Copyright 2018 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kanavi.automotive.nami.music.data.shared

import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.ResultReceiver
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaBrowserCompat.SearchCallback
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.lifecycle.MutableLiveData
import androidx.media.MediaBrowserServiceCompat
import com.kanavi.automotive.nami.music.common.extension.id
import com.kanavi.automotive.nami.music.common.extension.title
import timber.log.Timber

/**
 * Class that manages a connection to a [MediaBrowserServiceCompat] instance, typically a
 * [MusicService] or one of its subclasses.
 *
 * Typically it's best to construct/inject dependencies either using DI or, as UAMP does,
 * using [InjectorUtils] in the app module. There are a few difficulties for that here:
 * - [MediaBrowserCompat] is a final class, so mocking it directly is difficult.
 * - A [MediaBrowserConnectionCallback] is a parameter into the construction of
 *   a [MediaBrowserCompat], and provides callbacks to this class.
 * - [MediaBrowserCompat.ConnectionCallback.onConnected] is the best place to construct
 *   a [MediaControllerCompat] that will be used to control the [MediaSessionCompat].
 *
 *  Because of these reasons, rather than constructing additional classes, this is treated as
 *  a black box (which is why there's very little logic here).
 *
 *  This is also why the parameters to construct a [MediaServiceConnection] are simple
 *  parameters, rather than private properties. They're only required to build the
 *  [MediaBrowserConnectionCallback] and [MediaBrowserCompat] objects.
 */
class MediaServiceConnection(context: Context, private val serviceComponentName: ComponentName) {
    val isConnected = MutableLiveData<Boolean>().apply { postValue(false) }
    val rootMediaId: String get() = mediaBrowser.root

    val playbackState = MutableLiveData<PlaybackStateCompat>()
        .apply { postValue(EMPTY_PLAYBACK_STATE) }
    val nowPlaying = MutableLiveData<MediaMetadataCompat>()
        .apply { postValue(NOTHING_PLAYING) }
    val nowPlayingList = MutableLiveData<List<MediaSessionCompat.QueueItem>>()
        .apply { postValue(emptyList()) }

    val shuffleMode = MutableLiveData<Int>().apply {
        postValue(mediaController?.shuffleMode ?: PlaybackStateCompat.SHUFFLE_MODE_NONE)
    }

    val repeatMode = MutableLiveData<Int>().apply {
        postValue(mediaController?.repeatMode ?: PlaybackStateCompat.REPEAT_MODE_NONE)
    }

    val transportControls: MediaControllerCompat.TransportControls?
        get() = mediaController?.transportControls

    private val mediaBrowserConnectionCallback = MediaBrowserConnectionCallback(context)
    private val mediaBrowser = MediaBrowserCompat(
        context, serviceComponentName,
        mediaBrowserConnectionCallback,
        null
    ).apply {
        Timber.d("open connection to serviceComponent = $serviceComponentName")
        connect()
    }
    private var mediaController: MediaControllerCompat? = null

    fun subscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        Timber.d("parentId = $parentId")
        mediaBrowser.subscribe(parentId, callback)
    }

    fun unsubscribe(parentId: String, callback: MediaBrowserCompat.SubscriptionCallback) {
        Timber.d("parentId = $parentId")
        mediaBrowser.unsubscribe(parentId, callback)
    }

    fun search(
        query: String,
        onSearchResult: (query: String, extras: Bundle?, items: MutableList<MediaBrowserCompat.MediaItem>) -> Unit,
        onError: (query: String, extras: Bundle?) -> Unit,
    ) {
        mediaBrowser.search(query, null, object : SearchCallback() {
            override fun onSearchResult(
                query: String,
                extras: Bundle?,
                items: MutableList<MediaBrowserCompat.MediaItem>,
            ) {
                onSearchResult.invoke(query, extras, items)
            }

            override fun onError(query: String, extras: Bundle?) {
                onError.invoke(query, extras)
            }
        })
    }

    fun sendCommand(command: String, parameters: Bundle?) =
        sendCommand(command, parameters) { _, _ -> }

    fun sendCommand(
        command: String, parameters: Bundle?, resultCallback: ((Int, Bundle?) -> Unit),
    ) = if (mediaBrowser.isConnected) {
        Timber.d("sendCommand: $command, parameters: $parameters")
        mediaController?.sendCommand(command, parameters, object : ResultReceiver(Handler()) {
            override fun onReceiveResult(resultCode: Int, resultData: Bundle?) {
                Timber.d("Received result $resultCode for command $command")
                resultCallback(resultCode, resultData)
            }
        })
        true
    } else {
        false
    }

    private inner class MediaBrowserConnectionCallback(private val context: Context) :
        MediaBrowserCompat.ConnectionCallback() {
        /**
         * Invoked after [MediaBrowserCompat.connect] when the request has successfully
         * completed.
         */
        override fun onConnected() {
            Timber.d("serviceComponent = $serviceComponentName")
            // Get a MediaController for the MediaSession.
            mediaController = MediaControllerCompat(context, mediaBrowser.sessionToken).apply {
                registerCallback(MediaControllerCallback())
            }
            mediaController?.playbackState?.let { playbackState.postValue(it) }
            mediaController?.metadata?.let { nowPlaying.postValue(it) }
            mediaController?.repeatMode?.let { repeatMode.postValue(it) }
            mediaController?.shuffleMode?.let { shuffleMode.postValue(it) }
            Timber.d("mediaController: $mediaController")
            isConnected.postValue(true)
        }

        /**
         * Invoked when the client is disconnected from the media browser.
         */
        override fun onConnectionSuspended() {
            Timber.d("onConnectionSuspended to $serviceComponentName")
            isConnected.postValue(false)
        }

        /**
         * Invoked when the connection to the media browser failed.
         */
        override fun onConnectionFailed() {
            Timber.d("onConnectionFailed to $serviceComponentName")
            isConnected.postValue(false)
        }
    }

    private inner class MediaControllerCallback : MediaControllerCompat.Callback() {

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
//            Timber.d("onPlaybackStateChanged: $state")
            playbackState.postValue(state ?: EMPTY_PLAYBACK_STATE)
        }

        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            // When ExoPlayer stops we will receive a callback with "empty" metadata. This is a
            // metadata object which has been instantiated with default values. The default value
            // for media ID is null so we assume that if this value is null we are not playing
            // anything.
            Timber.d("onMetadataChanged: ${metadata?.id} : ${metadata?.title}")
            nowPlaying.postValue(
                if (metadata?.id == null && metadata?.title.isNullOrEmpty() && metadata?.description?.title.isNullOrEmpty()) {
                    NOTHING_PLAYING
                } else {
                    metadata
                }
            )
        }

        override fun onQueueChanged(queue: MutableList<MediaSessionCompat.QueueItem>?) {
            nowPlayingList.postValue(queue ?: emptyList())
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            super.onRepeatModeChanged(repeatMode)
            this@MediaServiceConnection.repeatMode.postValue(repeatMode)
        }

        override fun onShuffleModeChanged(shuffleMode: Int) {
            super.onShuffleModeChanged(shuffleMode)
            this@MediaServiceConnection.shuffleMode.postValue(shuffleMode)
        }

        override fun onSessionEvent(event: String?, extras: Bundle?) {
            super.onSessionEvent(event, extras)
        }

        /**
         * Normally if a [MediaBrowserServiceCompat] drops its connection the callback comes via
         * [MediaControllerCompat.Callback] (here). But since other connection status events
         * are sent to [MediaBrowserCompat.ConnectionCallback], we catch the disconnect here and
         * send it on to the other callback.
         */
        override fun onSessionDestroyed() {
            mediaBrowserConnectionCallback.onConnectionSuspended()
        }
    }
}

@Suppress("PropertyName")
val EMPTY_PLAYBACK_STATE: PlaybackStateCompat =
    PlaybackStateCompat.Builder().setState(PlaybackStateCompat.STATE_NONE, 0, 0f).build()

@Suppress("PropertyName")
val NOTHING_PLAYING: MediaMetadataCompat =
    MediaMetadataCompat.Builder().putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, "")
        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, 0).build()
