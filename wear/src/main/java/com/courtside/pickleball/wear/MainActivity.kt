package com.courtside.pickleball.wear

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport

/** Wear host activity for PickleCast watch experiences. */
class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {
    private var isAmbient by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearPhoneSync.initialize(applicationContext)
        WearTabletFallbackSync.initialize(applicationContext)
        keepScreenOn()
        AmbientModeSupport.attach(this)
        setContent {
            WearScoreboardApp(isAmbient = isAmbient)
        }
    }

    override fun onResume() {
        super.onResume()
        keepScreenOn()
    }

    override fun onPause() {
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = false
        super.onPause()
    }

    // Some Wear OS system overlays (e.g. the always-on-top status/ambient transition chrome)
    // steal window focus without triggering a full onPause/onResume cycle, which was enough for
    // the screen's own inactivity timeout to sneak in and put the display to sleep mid-use even
    // though onResume had already requested FLAG_KEEP_SCREEN_ON. Re-asserting it every time this
    // window regains focus closes that gap.
    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            keepScreenOn()
        }
    }

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = true
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {
            override fun onEnterAmbient(ambientDetails: Bundle) {
                super.onEnterAmbient(ambientDetails)
                isAmbient = true
            }

            override fun onExitAmbient() {
                super.onExitAmbient()
                isAmbient = false
            }
        }
}
