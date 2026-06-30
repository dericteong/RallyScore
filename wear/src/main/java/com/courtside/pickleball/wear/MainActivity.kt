package com.courtside.pickleball.wear

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity
import androidx.wear.ambient.AmbientModeSupport

/** Wear host activity for RallyScore watch experiences. */
class MainActivity : FragmentActivity(), AmbientModeSupport.AmbientCallbackProvider {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearPhoneSync.initialize(applicationContext)
        WearTabletFallbackSync.initialize(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        AmbientModeSupport.attach(this)
        setContent {
            WearScoreboardApp()
        }
    }

    override fun getAmbientCallback(): AmbientModeSupport.AmbientCallback =
        object : AmbientModeSupport.AmbientCallback() {}
}
