package com.courtside.pickleball.wear

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.fragment.app.FragmentActivity

/** Wear host activity for RallyScore watch experiences. */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WearPhoneSync.initialize(applicationContext)
        WearTabletFallbackSync.initialize(applicationContext)
        keepScreenOn()
        setContent {
            WearScoreboardApp()
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

    private fun keepScreenOn() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.decorView.keepScreenOn = true
    }
}
