package com.courtside.pickleball.wear

import android.util.Log

object WearSyncLog {
    private val isDebugBuild: Boolean by lazy {
        sequenceOf(
            "com.courtside.pickleball.wear.BuildConfig",
            "com.courtside.pickleball.BuildConfig"
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
