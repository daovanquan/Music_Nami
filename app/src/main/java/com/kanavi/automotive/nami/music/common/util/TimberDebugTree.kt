package com.kanavi.automotive.nami.music.common.util

import timber.log.Timber

class TimberDebugTree : Timber.DebugTree() {
    override fun createStackElementTag(element: StackTraceElement): String {
        return String.format(
            "[%s] %s %s() Line: %s",
            "NAMI-UsbMusicBrowserService",
            super.createStackElementTag(element),
            element.methodName,
            element.lineNumber
        )
    }
}