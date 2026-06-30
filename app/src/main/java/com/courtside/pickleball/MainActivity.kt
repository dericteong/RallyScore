package com.courtside.pickleball

import android.content.pm.ActivityInfo
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.courtside.pickleball.sync.RallyScorePhoneHub
import com.courtside.pickleball.ui.ScoreboardApp
import com.courtside.pickleball.ui.ScoreboardViewModel

/** Phone and tablet host activity for RallyScore. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        RallyScorePhoneHub.initialize(applicationContext)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()

        setContent {
            val viewModel: ScoreboardViewModel = viewModel()
            ScoreboardApp(viewModel = viewModel)
        }
    }
}
