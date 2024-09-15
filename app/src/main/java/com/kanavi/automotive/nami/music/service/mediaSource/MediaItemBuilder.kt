package com.kanavi.automotive.nami.music.service.mediaSource

import android.content.Context
import android.net.Uri
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaDescriptionCompat
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.core.os.bundleOf


object UsbMediaItem {
    fun with(context: Context): Builder {
        return Builder(context)
    }

    class Builder(private val mContext: Context) {
        private var mBuilder: MediaDescriptionCompat.Builder?
        private var mFlags = 0
        fun mediaID(mediaID: String): Builder {
            mBuilder?.setMediaId(mediaID)
            return this
        }

        fun mediaID(category: String?, id: Long): Builder {
            return mediaID(MediaIDHelper.createMediaID(id.toString(), category))
        }

        fun title(title: String): Builder {
            mBuilder?.setTitle(title)
            return this
        }

        fun subTitle(subTitle: String): Builder {
            mBuilder?.setSubtitle(subTitle)
            return this
        }

        fun description(description: String): Builder {
            mBuilder?.setDescription(description)
            return this
        }

        fun icon(uri: Uri?): Builder {
            mBuilder?.setIconUri(uri)
            return this
        }

        fun icon(iconDrawableId: Int): Builder {
            mBuilder?.setIconBitmap(
                ResourcesCompat.getDrawable(
                    mContext.resources,
                    iconDrawableId,
                    mContext.theme
                )?.toBitmap()
            )
            return this
        }

        fun gridLayout(isGrid: Boolean): Builder {

            val hints = bundleOf(
                CONTENT_STYLE_SUPPORTED to true,
                CONTENT_STYLE_BROWSABLE_HINT to
                        if (isGrid) CONTENT_STYLE_GRID_ITEM_HINT_VALUE
                        else CONTENT_STYLE_LIST_ITEM_HINT_VALUE,
                CONTENT_STYLE_PLAYABLE_HINT to
                        if (isGrid) CONTENT_STYLE_GRID_ITEM_HINT_VALUE
                        else CONTENT_STYLE_LIST_ITEM_HINT_VALUE
            )
            mBuilder?.setExtras(hints)
            return this
        }

        fun asBrowsable(): Builder {
            mFlags = mFlags or MediaBrowserCompat.MediaItem.FLAG_BROWSABLE
            return this
        }

        fun asPlayable(): Builder {
            mFlags = mFlags or MediaBrowserCompat.MediaItem.FLAG_PLAYABLE
            return this
        }

        fun setExtraProperties(
            duration: Long = 0L,
            path: String = "",
            dateTaken: String = "",
            isLoading: Boolean = false
        ): Builder {
            val property = bundleOf(
                EXTRA_SONG_DURATION to duration,
                EXTRA_SONG_PATH to path,
                EXTRA_DATE_TAKEN to dateTaken,
                EXTRA_IS_LOADING_METADATA to isLoading
            )
            mBuilder?.setExtras(property)
            return this
        }

        fun build(): MediaBrowserCompat.MediaItem {
            val result = MediaBrowserCompat.MediaItem(mBuilder!!.build(), mFlags)
            mBuilder = null
            mFlags = 0
            return result
        }

        init {
            mBuilder = MediaDescriptionCompat.Builder()
        }

        companion object {
            // Hints - see https://developer.android.com/training/cars/media#default-content-style
            const val CONTENT_STYLE_SUPPORTED = "android.media.browse.CONTENT_STYLE_SUPPORTED"
            const val CONTENT_STYLE_BROWSABLE_HINT =
                "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
            const val CONTENT_STYLE_PLAYABLE_HINT =
                "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
            const val CONTENT_STYLE_LIST_ITEM_HINT_VALUE = 1
            const val CONTENT_STYLE_GRID_ITEM_HINT_VALUE = 2

            const val EXTRA_SONG_DURATION = "SONG_DURATION"
            const val EXTRA_SONG_PATH = "SONG_PATH"
            const val EXTRA_DATE_TAKEN = "DATE_TAKEN"
            const val EXTRA_IS_LOADING_METADATA = "KEY_IS_LOADING_METADATA"
        }

    }
}