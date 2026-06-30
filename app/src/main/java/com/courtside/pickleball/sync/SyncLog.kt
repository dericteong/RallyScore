package com.courtside.pickleball.sync

import android.util.Log

object SyncLog {
    private val isDebugBuild: Boolean by lazy {
        sequenceOf(
            "com.courtside.pickleball.BuildConfig",
            "com.courtside.pickleball.sync.BuildConfig"
        ).mapNotNull { className ->
            runCatching {
                Class.forName(className).getField("DEBUG").getBoolean(null)
            }.getOrNull()
        }.firstOrNull() ?: false
    }

    fun debug(tag: String, message: () -> String) {
        if (isDebugBuild) {
            Log.d(tag, message())
        }
    }

    fun warn(
        tag: String,
        releaseMessage: String,
        debugMessage: String = releaseMessage,
        error: Throwable? = null
    ) {
        if (isDebugBuild) {
            if (error != null) {
                Log.w(tag, debugMessage, error)
            } else {
                Log.w(tag, debugMessage)
            }
        } else {
            Log.w(tag, releaseMessage)
        }
    }
}
