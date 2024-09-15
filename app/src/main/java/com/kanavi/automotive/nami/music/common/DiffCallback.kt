package com.kanavi.automotive.nami.music.common

import android.annotation.SuppressLint
import androidx.recyclerview.widget.DiffUtil

@SuppressLint("DiffUtilEquals")
class DiffCallback<T : DiffUtilObject> : DiffUtil.ItemCallback<T>() {
    override fun areItemsTheSame(
        oldItem: T,
        newItem: T
    ) = oldItem.areItemsTheSame(newItem)

    override fun areContentsTheSame(
        oldItem: T,
        newItem: T
    ) = oldItem.areContentsTheSame(newItem)
}

abstract class DiffUtilObject {
    abstract fun areItemsTheSame(item: DiffUtilObject): Boolean
    abstract fun areContentsTheSame(item: DiffUtilObject): Boolean
    abstract fun getChangePayload(item: DiffUtilObject): Any?
}